package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.web.controller.system.SysUserController;
import com.ruoyi.web.controller.system.SysRoleController;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;

public final class FrameworkMethodSecurityHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";
    private static final java.util.List<String> TABLES = java.util.List.of("tenant", "project", "tenant_member", "template_version",
            "permission_grant", "task_head", "operation_record", "sys_user", "sys_role", "sys_menu", "sys_user_role", "sys_dept",
            "sys_post", "sys_dict_type", "sys_dict_data", "sys_config", "sys_notice", "sys_notice_read", "sys_oper_log", "sys_logininfor");

    @Test void originalControllerRequiresCurrentFrameworkPermissionBeforeReadingAccounts() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var frameworkTokenPath = root.resolve(".implementation-work/runtime/ruoyi-token-secret.json");
        var frameworkToken = JSON.readTree(Files.readAllBytes(frameworkTokenPath));
        var tokenReceipt = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-configuration-receipt.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
                new SpringApplicationBuilder(MedicalApplication.class)
                .properties(Map.of("MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                        "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                        "MEDICAL_RUOYI_TOKEN_SECRET", frameworkToken.required("key").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                        "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
        var evidence = JSON.createObjectNode();
        evidence.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        try (application) {
            var mappings = application.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
            var nativeControllers = mappings.values().stream().map(method -> method.getBeanType())
                    .filter(type -> type.getPackageName().equals("com.ruoyi.web.controller.system")
                            || type.getPackageName().equals("com.ruoyi.web.controller.monitor"))
                    .distinct().map(Class::getName).sorted().toList();
            assertEquals(java.util.List.of(
                    "com.ruoyi.web.controller.monitor.SysLogininforController",
                    "com.ruoyi.web.controller.monitor.SysOperlogController",
                    "com.ruoyi.web.controller.system.SysConfigController",
                    "com.ruoyi.web.controller.system.SysDeptController",
                    "com.ruoyi.web.controller.system.SysDictDataController",
                    "com.ruoyi.web.controller.system.SysDictTypeController",
                    "com.ruoyi.web.controller.system.SysMenuController",
                    "com.ruoyi.web.controller.system.SysNoticeController",
                    "com.ruoyi.web.controller.system.SysPostController",
                    "com.ruoyi.web.controller.system.SysRoleController",
                    "com.ruoyi.web.controller.system.SysUserController"), nativeControllers,
                    "正式应用必须直接注册原管理Controller");
            for (var entry : mappings.entrySet()) {
                if (!nativeControllers.contains(entry.getValue().getBeanType().getName())) continue;
                assertTrue(entry.getKey().getPatternValues().stream().allMatch(path -> path.startsWith("/api/system/")
                        || path.startsWith("/api/monitor/")));
            }
            var roleController = application.getBean(SysRoleController.class);
            var tokenService = application.getBean(TokenService.class);
            assertEquals(SysRoleController.class, AopUtils.getTargetClass(roleController));
            assertTrue(AopUtils.isAopProxy(roleController));
            assertEquals(15, mappings.values().stream().filter(method -> method.getBeanType().equals(SysRoleController.class)).count());
            assertSame(tokenService, org.springframework.test.util.ReflectionTestUtils.getField(roleController, "tokenService"));
            assertTrue(frameworkToken.required("key").textValue().equals(
                    org.springframework.test.util.ReflectionTestUtils.getField(tokenService, "secret")), "原TokenService必须读取实际独立密钥");
            assertEquals(64, java.util.Base64.getDecoder().decode(frameworkToken.required("key").textValue()).length);
            assertEquals(java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(frameworkTokenPath));
            assertEquals(tokenReceipt.required("key_file_sha256").textValue(), org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                    Files.readAllBytes(frameworkTokenPath)));
            assertEquals("RUOYI_TOKEN_SIGNING", frameworkToken.required("purpose").textValue());
            assertEquals(tokenReceipt.required("key_id").textValue(), frameworkToken.required("key_id").textValue());
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var before = counts(jdbc);
            var originalChecksums = checksums(jdbc);
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            var http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var anonymous = get(http, origin, "/api/system/user/list");
            assertEquals(401, anonymous.statusCode());
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue());
            form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin", ORIGIN)
                    .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            var user = application.getBean(ISysUserService.class).selectUserByUserName(owner.required("username").textValue());
            assertNotNull(user); assertNotNull(user.getUserId());
            var navigation = get(http, origin, "/api/framework/navigation"); assertEquals(200, navigation.statusCode());
            var permissions = JSON.readTree(navigation.body()).required("data").required("permissions");
            assertFalse(JSON.convertValue(permissions, java.util.List.class).contains("system:user:list"));
            assertFalse(JSON.convertValue(permissions, java.util.List.class).contains("system:user:query"));
            var list = get(http, origin, "/api/system/user/list");
            assertEquals(403, list.statusCode(), "原用户列表必须执行原PreAuthorize权限检查");
            assertEquals("ACCESS_DENIED", JSON.readTree(list.body()).required("code").textValue());
            var detail = get(http, origin, "/api/system/user/" + user.getUserId());
            assertEquals(403, detail.statusCode());
            assertEquals("ACCESS_DENIED", JSON.readTree(detail.body()).required("code").textValue());
            var refusals = JSON.createObjectNode();
            for (String path : java.util.List.of("/api/system/dept/list", "/api/system/menu/list",
                    "/api/system/post/list", "/api/system/dict/type/list", "/api/system/dict/data/list",
                    "/api/system/config/list", "/api/system/notice/list", "/api/monitor/operlog/list",
                    "/api/system/role/list", "/api/system/role/1", "/api/system/role/optionselect",
                    "/api/system/role/deptTree/1", "/api/system/role/authUser/allocatedList",
                    "/api/system/role/authUser/unallocatedList",
                    "/api/monitor/logininfor/list", "/api/system/menu/treeselect",
                    "/api/system/menu/roleMenuTreeselect/1", "/api/system/dict/type/optionselect",
                    "/api/system/dict/data/type/sys_user_sex", "/api/system/config/configKey/sys.user.initPassword",
                    "/api/system/notice/1", "/api/system/notice/listTop")) {
                var refusal = get(http, origin, path);
                assertEquals(403, refusal.statusCode(), path);
                assertEquals("ACCESS_DENIED", JSON.readTree(refusal.body()).required("code").textValue());
                refusals.put(path, refusal.statusCode());
            }
            assertTrue(AopUtils.isAopProxy(application.getBean(SysUserController.class)));
            assertEquals(PermissionService.class, AopUtils.getTargetClass(application.getBean("ss")));
            var interceptor = application.getBean("preAuthorizeAuthorizationMethodInterceptor",
                    org.springframework.security.authorization.method.AuthorizationAdvisor.class);
            var controller = assertInstanceOf(org.springframework.aop.framework.Advised.class, application.getBean(SysUserController.class));
            assertTrue(java.util.Arrays.stream(controller.getAdvisors()).anyMatch(advisor -> advisor.getAdvice() == interceptor));
            assertTrue(interceptor.getPointcut().getMethodMatcher().matches(SysUserController.class.getMethod("list", SysUser.class), SysUserController.class));
            var roleProxy = assertInstanceOf(org.springframework.aop.framework.Advised.class, roleController);
            assertTrue(java.util.Arrays.stream(roleProxy.getAdvisors()).anyMatch(advisor -> advisor.getAdvice() == interceptor));
            assertTrue(interceptor.getPointcut().getMethodMatcher().matches(SysRoleController.class.getMethod("list",
                    com.ruoyi.common.core.domain.entity.SysRole.class), SysRoleController.class));
            assertEquals(403, get(http, origin, "/system/user/list").statusCode());
            var currentCsrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var writes = JSON.createObjectNode();
            for (String path : java.util.List.of("/api/system/user/importTemplate", "/api/monitor/operlog/clean",
                    "/api/monitor/logininfor/clean")) {
                String verb = path.endsWith("/clean") ? "DELETE" : "POST";
                var deniedWrite = http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("Origin", ORIGIN)
                        .header(currentCsrf.required("header_name").textValue(), currentCsrf.required("token").textValue())
                        .method(verb, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(403, deniedWrite.statusCode(), path);
                assertEquals("ACCESS_DENIED", JSON.readTree(deniedWrite.body()).required("code").textValue());
                writes.put(verb + " " + path, deniedWrite.statusCode());
            }
            var noCsrf = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/system/user/importTemplate"))
                    .header("Origin", ORIGIN).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(403, noCsrf.statusCode());
            assertEquals(before, counts(jdbc));
            assertEquals(originalChecksums, checksums(jdbc), "拒绝原管理写操作后原表内容必须保持一致");
            var current = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            assertEquals(204, client.post().uri("/auth/logout").header("Origin", ORIGIN)
                    .header(current.required("header_name").textValue(), current.required("token").textValue()).retrieve().toBodilessEntity().getStatusCode().value());
            assertEquals(401, get(http, origin, "/api/system/user/list").statusCode());
            evidence.set("original_controller_classes", JSON.valueToTree(nativeControllers));
            evidence.put("original_role_controller", SysRoleController.class.getName());
            evidence.put("original_role_operation_count", 15);
            evidence.put("original_token_service", tokenService.getClass().getName());
            evidence.put("token_service_configuration_verified", true);
            evidence.put("token_key_id", frameworkToken.required("key_id").textValue());
            evidence.put("token_key_file_sha256", tokenReceipt.required("key_file_sha256").textValue());
            evidence.set("permission_refusals", refusals);
            evidence.set("write_permission_refusals", writes);
            evidence.put("missing_csrf_http_status", noCsrf.statusCode());
            evidence.put("original_controller", SysUserController.class.getName());
            evidence.put("original_permission_service", PermissionService.class.getName());
            evidence.put("original_controller_proxy_verified", true);
            evidence.put("original_method_interceptor", interceptor.getClass().getName());
            evidence.put("anonymous_http_status", 401); evidence.put("list_http_status", 403); evidence.put("detail_http_status", 403);
            evidence.put("navigation_http_status", 200); evidence.put("original_paths_http_status", 403);
            evidence.put("logout_http_status", 204); evidence.put("after_logout_http_status", 401);
            evidence.put("actual_http_port", application.getWebServer().getPort());
            evidence.set("business_counts", before); evidence.put("business_counts_unchanged", true);
            evidence.set("table_checksums", originalChecksums); evidence.put("table_contents_unchanged", true);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.methodSecurityEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static HttpResponse<byte[]> get(HttpClient client, String origin, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static JsonNode counts(JdbcTemplate jdbc) {
        var result = JSON.createObjectNode();
        for (String table : TABLES)
            result.put(table, java.util.Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }

    private static JsonNode checksums(JdbcTemplate jdbc) {
        var result = JSON.createObjectNode();
        for (String table : TABLES) {
            var values = jdbc.queryForList("CHECKSUM TABLE " + table + " EXTENDED");
            assertEquals(1, values.size());
            result.put(table, assertInstanceOf(Number.class, values.get(0).get("Checksum")).longValue());
        }
        return result;
    }

}
