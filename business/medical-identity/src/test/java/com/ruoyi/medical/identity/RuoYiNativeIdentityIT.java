package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.aspectj.DataScopeAspect;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysRoleService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class RuoYiNativeIdentityIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    public static void main(String[] args) throws Exception {
        assertEquals(2, args.length);
        Path root = Path.of(System.getProperty("medical.project-root"));
        JsonNode owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        assertEquals(System.getProperty("user.name"), owner.required("username").textValue());
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        var http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        var client = client(http);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", owner.required("username").textValue());
        form.add("password", owner.required("password").textValue());
        var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
        assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Origin", ORIGIN).header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).retrieve().toBodilessEntity().getStatusCode().value());
        var account = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class));
        assertEquals(owner.required("username").textValue(), account.required("username").textValue());
        var cookie = cookies.getCookieStore().getCookies().stream().filter(item -> item.getName().equals("MEDICAL_SESSION"))
                .findFirst().orElseThrow();
        String sessionId = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var configuration = new RedisStandaloneConfiguration("127.0.0.1", 16379);
        configuration.setPassword(secrets.required("redis_password").textValue());
        var connection = new LettuceConnectionFactory(configuration);
        connection.afterPropertiesSet();
        connection.start();
        try {
            var redis = new RedisTemplate<String, Object>();
            redis.setConnectionFactory(connection);
            redis.setKeySerializer(StringRedisSerializer.UTF_8);
            redis.setHashKeySerializer(StringRedisSerializer.UTF_8);
            var serializer = new JdkSerializationRedisSerializer(RuoYiNativeIdentityIT.class.getClassLoader());
            redis.setValueSerializer(serializer);
            redis.setHashValueSerializer(serializer);
            redis.afterPropertiesSet();
            var repository = new RedisIndexedSessionRepository(redis);
            repository.setRedisKeyNamespace("medical:local:session");
            var session = repository.findById(sessionId);
            assertNotNull(session);
            var context = session.<SecurityContext>getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertNotNull(context);
            assertTrue(context.getAuthentication().isAuthenticated());
            var result = JSON.createObjectNode();
            result.put("user_id", account.required("user_id").textValue());
            if (args[0].equals("prepare")) {
                // 只注销实际本机所有者已经建立的会话，使用原 LogoutHandler 保存撤销。
                var sessions = repository.findByIndexNameAndIndexValue(
                        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, account.required("user_id").textValue());
                assertTrue(sessions.containsKey(sessionId));
                for (var entry : sessions.entrySet()) {
                    var previous = entry.getValue().<SecurityContext>getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                    assertNotNull(previous);
                    assertEquals(account.required("user_id").textValue(), previous.getAuthentication().getName());
                    String header = "MEDICAL_SESSION=" + Base64.getEncoder().encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
                    var direct = client(HttpClient.newHttpClient());
                    var token = JSON.readTree(direct.get().uri("/auth/csrf").header("Cookie", header).retrieve().body(byte[].class));
                    assertEquals(204, direct.post().uri("/auth/logout").header("Cookie", header).header("Origin", ORIGIN)
                            .header(token.required("header_name").textValue(), token.required("token").textValue())
                            .retrieve().toBodilessEntity().getStatusCode().value());
                    assertNull(repository.findById(entry.getKey()));
                }
                var denied = http.send(HttpRequest.newBuilder(URI.create(ORIGIN + "/auth/account")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(401, denied.statusCode());
                result.put("scope", "ACTUAL_OWNER_NATIVE_IDENTITY_SESSION_TRANSITION");
                result.put("revoked_sessions", sessions.size());
                result.put("previous_cookie_http_status", denied.statusCode());
            } else {
                assertEquals("identity", args[0]);
                assertInstanceOf(LocalPrincipal.class, context.getAuthentication().getPrincipal());
                var principal = (LocalPrincipal) context.getAuthentication().getPrincipal();
                SecurityContextHolder.setContext(context);
                try {
                    assertSame(principal, SecurityUtils.getLoginUser());
                    assertEquals(owner.required("username").textValue(), SecurityUtils.getUsername());
                    assertEquals(account.required("user_id").textValue(), principal.businessUserId);
                    assertEquals(principal.businessUserId, session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
                    assertTrue(repository.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                            principal.businessUserId).containsKey(sessionId));
                    assertNotNull(principal.getUser().getRoles());
                    assertNotNull(principal.getPermissions());
                    assertFalse(SecurityUtils.isAdmin());
                    assertFalse(new PermissionService().hasPermi("system:user:list"));
                    assertTrue(principal.getPermissions().isEmpty());
                    assertTrue(principal.getUser().getRoles().isEmpty());
                    result.put("scope", "ACTUAL_OWNER_ORIGINAL_RUOYI_SECURITY_IDENTITY");
                    result.put("ruoyi_user_id", SecurityUtils.getUserId().toString());
                    result.put("username", SecurityUtils.getUsername());
                    result.put("uuid_session_index", true);
                    result.put("native_permissions", principal.getPermissions().size());
                    result.put("native_roles", principal.getUser().getRoles().size());
                    result.put("native_user_management_allowed", false);
                    verifyOriginalDataScope(root, secrets, http, principal, result);
                } finally {
                    SecurityContextHolder.clearContext();
                    var token = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
                    assertEquals(204, client.post().uri("/auth/logout").header("Origin", ORIGIN)
                            .header(token.required("header_name").textValue(), token.required("token").textValue())
                            .retrieve().toBodilessEntity().getStatusCode().value());
                    assertNull(repository.findById(sessionId));
                }
            }
            Files.write(Path.of(args[1]), JSON.writeValueAsBytes(result));
            System.out.println(result);
        } finally {
            connection.destroy();
        }
    }

    private static void verifyOriginalDataScope(Path root, JsonNode secrets, HttpClient http,
            LocalPrincipal principal, ObjectNode result) throws Exception {
        // 使用部署制品中的完整应用、实际Servlet请求和原会话；检查Controller只存在于test-classes。
        try (var application = startActualApplication(root, secrets)) {
            int port = application.getWebServer().getPort();
            assertTrue(port > 0);
            var value = JSON.readTree(client(http).get().uri("http://127.0.0.1:" + port + "/api/framework-integration/data-scope")
                    .retrieve().body(byte[].class));
            assertEquals(principal.businessUserId, value.required("scope_user_id").textValue());
            assertEquals(principal.getUserId().toString(), value.required("scope_ruoyi_user_id").textValue());
            result.setAll(assertInstanceOf(ObjectNode.class, value));
            result.put("data_scope_http_port", port);
        }
        result.put("data_scope_http_application_closed", true);
    }

    static ServletWebServerApplicationContext startActualApplication(Path root, JsonNode secrets) throws Exception {
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        return assertInstanceOf(ServletWebServerApplicationContext.class,
                new SpringApplicationBuilder(Class.forName("com.ruoyi.medical.runtime.MedicalApplication"))
                .properties(Map.of("MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                        "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                        "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
    }

    @RestController
    public static final class ActualDataScopeController {
        private final ApplicationContext application;

        public ActualDataScopeController(ApplicationContext application) {
            this.application = application;
        }

        @GetMapping("/api/framework-integration/data-scope")
        public ObjectNode verify() {
            var principal = assertInstanceOf(LocalPrincipal.class, SecurityUtils.getLoginUser());
            assertTrue(principal.getUser().getRoles().isEmpty());
            assertTrue(principal.getPermissions().isEmpty());
            assertFalse(SecurityUtils.isAdmin());
            var users = application.getBean(ISysUserService.class);
            assertEquals(principal.getUserId(), users.selectUserById(principal.getUserId()).getUserId());
            var query = new SysUser();
            var userList = users.selectUserList(query);
            assertTrue(userList.isEmpty(), "没有原角色的实际账户不能查询原用户管理集合");
            assertFalse(query.getParams().get(DataScopeAspect.DATA_SCOPE).toString().isBlank());
            assertTrue(AopUtils.isAopProxy(users), "原DataScope必须经过实际Spring代理执行");
            assertInstanceOf(DataScopeAspect.class, application.getBean(DataScopeAspect.class));
            query.getParams().put(DataScopeAspect.DATA_SCOPE, " OR u.user_id = " + principal.getUserId());
            assertTrue(users.selectUserList(query).isEmpty(), "客户端范围参数不能增加实际访问范围");
            assertFalse(query.getParams().get(DataScopeAspect.DATA_SCOPE).toString().contains("u.user_id"));
            assertTrue(users.selectUserList(query).isEmpty());
            assertThrows(ServiceException.class, () -> users.checkUserDataScope(principal.getUserId()),
                    "原服务内部的数据范围核查必须继续使用实际代理");
            var departments = application.getBean(ISysDeptService.class);
            var departmentQuery = new SysDept();
            var departmentList = departments.selectDeptList(departmentQuery);
            assertTrue(departmentList.isEmpty());
            assertFalse(departmentQuery.getParams().get(DataScopeAspect.DATA_SCOPE).toString().isBlank());
            var roles = application.getBean(ISysRoleService.class);
            var roleQuery = new SysRole();
            var roleList = roles.selectRoleList(roleQuery);
            assertTrue(roleList.isEmpty());
            assertFalse(roleQuery.getParams().get(DataScopeAspect.DATA_SCOPE).toString().isBlank());
            assertTrue(AopUtils.isAopProxy(departments));
            assertTrue(AopUtils.isAopProxy(roles));
            var result = JSON.createObjectNode();
            result.put("scope_user_id", principal.businessUserId);
            result.put("scope_ruoyi_user_id", principal.getUserId().toString());
            result.put("original_data_scope_applied", true);
            result.put("native_user_list_count", userList.size());
            result.put("client_data_scope_cleared", true);
            result.put("internal_user_scope_denied", true);
            result.put("native_department_list_count", departmentList.size());
            result.put("native_role_list_count", roleList.size());
            return result;
        }
    }

    private static RestClient client(HttpClient http) {
        return RestClient.builder().baseUrl(ORIGIN).requestFactory(new JdkClientHttpRequestFactory(http))
                .messageConverters(converters -> converters.add(new FormHttpMessageConverter())).build();
    }
}
