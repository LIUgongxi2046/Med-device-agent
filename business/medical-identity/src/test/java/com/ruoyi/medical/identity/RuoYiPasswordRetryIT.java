package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.SysPasswordService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class RuoYiPasswordRetryIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    public static void main(String[] args) throws Exception {
        assertEquals(1, args.length);
        var root = Path.of(System.getProperty("medical.project-root"));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        assertEquals(System.getProperty("user.name"), owner.required("username").textValue());
        var result = JSON.createObjectNode();
        try (var application = RuoYiNativeIdentityIT.startActualApplication(root, secrets)) {
            int port = application.getWebServer().getPort();
            assertTrue(port > 0);
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            var client = RestClient.builder().baseUrl("http://127.0.0.1:" + port)
                    .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().cookieHandler(cookies).build()))
                    .messageConverters(converters -> converters.add(0, new FormHttpMessageConverter())).build();
            String username = owner.required("username").textValue();
            String password = owner.required("password").textValue();
            var passwords = application.getBean(SysPasswordService.class);
            var redis = application.getBean(RedisCache.class);
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", DataSource.class));
            int maximum = application.getEnvironment().getRequiredProperty("user.password.maxRetryCount", Integer.class);
            assertEquals(5, maximum);
            assertEquals(302, login(client, username, password));
            var account = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class));
            String userId = account.required("user_id").textValue();
            String incorrect = (password.charAt(0) == 'a' ? "b" : "a") + password.substring(1);
            // 错误凭据只用于真实本人账户的拒绝检查，不建立其他账户或授权。
            for (int attempt = 1; attempt <= maximum; attempt++) {
                assertEquals(401, login(client, username, incorrect));
                assertEquals(attempt, redis.<Integer>getCacheObject(CacheConstants.PWD_ERR_CNT_KEY + username));
            }
            assertEquals(401, login(client, username, password), "原重试上限必须拒绝正确密码");
            var snapshot = jdbc.queryForMap("""
                    SELECT failed_attempts,(locked_until>UTC_TIMESTAMP(6)) AS locked
                      FROM platform_local_identity_metadata WHERE user_id=?
                    """, userId);
            assertEquals(maximum, ((Number) snapshot.get("failed_attempts")).intValue());
            assertEquals(1, ((Number) snapshot.get("locked")).intValue());
            passwords.clearLoginRecordCache(username);
            assertFalse(redis.hasKey(CacheConstants.PWD_ERR_CNT_KEY + username));
            assertEquals(302, login(client, username, password), "原服务解除锁定后必须能够使用正确密码登录");
            var current = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class));
            assertEquals(userId, current.required("user_id").textValue());
            assertEquals(username, current.required("username").textValue());
            var stored = jdbc.queryForMap("""
                    SELECT failed_attempts,(locked_until IS NULL) AS unlocked
                      FROM platform_local_identity_metadata WHERE user_id=?
                    """, userId);
            assertEquals(0, ((Number) stored.get("failed_attempts")).intValue());
            assertEquals(1, ((Number) stored.get("unlocked")).intValue());
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            int logoutStatus = client.post().uri("/auth/logout").header("Origin", ORIGIN)
                    .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                    .exchange((request, response) -> response.getStatusCode().value());
            assertEquals(204, logoutStatus);
            result.put("scope", "ACTUAL_OWNER_ORIGINAL_PASSWORD_RETRY_AUTHORITY");
            result.put("user_id", userId);
            result.put("http_port", port);
            result.put("native_retry_limit", maximum);
            result.put("limit_http_status", 401);
            result.put("audit_snapshot_locked_before_clear", true);
            result.put("native_retry_cache_cleared", true);
            result.put("correct_password_http_status", 302);
            result.put("account_http_status", 200);
            result.put("audit_snapshot_reset_after_login", true);
            result.put("logout_http_status", 204);
        }
        result.put("http_application_closed", true);
        Files.write(Path.of(args[0]), JSON.writeValueAsBytes(result));
        System.out.println(result);
    }

    private static int login(RestClient client, String username, String password) throws Exception {
        JsonNode csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", username);
        form.add("password", password);
        return client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Origin", ORIGIN)
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).exchange((request, response) -> response.getStatusCode().value());
    }
}
