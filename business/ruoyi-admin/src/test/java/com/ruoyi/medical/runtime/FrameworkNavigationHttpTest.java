package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysUserService;
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
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class FrameworkNavigationHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    @Test void actualNavigationUsesOriginalMenusRolesAndCurrentCookieIdentity() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var frameworkToken = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
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
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var before = counts(jdbc);
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            var http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var anonymous = get(http, origin, "1.0.0"); assertEquals(401, anonymous.statusCode());
            assertEquals("AUTHENTICATION_REQUIRED", JSON.readTree(anonymous.body()).required("code").textValue());
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue());
            form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin", ORIGIN)
                    .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            var account = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class));
            var response = get(http, origin, "1.0.0"); assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var envelope = JSON.readTree(response.body()); var value = envelope.required("data");
            assertEquals("1.0.0", envelope.required("schema_version").textValue()); assertTrue(envelope.required("resource_revision").isNull());
            assertEquals(envelope.required("request_id").textValue(), response.headers().firstValue("X-Request-ID").orElseThrow());
            assertEquals(account.required("user_id").textValue(), value.required("user_id").textValue());
            var actualUser = application.getBean(ISysUserService.class).selectUserByUserName(owner.required("username").textValue());
            assertEquals(actualUser.getUserId().toString(), value.required("framework_user_id").textValue());
            var permissions = application.getBean(SysPermissionService.class); var menus = application.getBean(ISysMenuService.class);
            assertEquals(JSON.valueToTree(permissions.getRolePermission(actualUser).stream().sorted().toList()), value.required("roles"));
            assertEquals(JSON.valueToTree(permissions.getMenuPermission(actualUser).stream().sorted().toList()), value.required("permissions"));
            assertEquals(JSON.valueToTree(menus.buildMenus(menus.selectMenuTreeByUserId(actualUser.getUserId()))), value.required("routers"));
            var profile = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/system/user/profile"))
                    .header("X-Schema-Version", "1.0.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, profile.statusCode(), "本人资料必须能够通过原个人信息入口读取");
            assertEquals("no-store", profile.headers().firstValue("Cache-Control").orElseThrow());
            var profileValue = JSON.readTree(profile.body());
            assertEquals(200, profileValue.required("code").intValue());
            assertEquals(actualUser.getUserId(), profileValue.required("data").required("userId").longValue());
            assertEquals(actualUser.getUserName(), profileValue.required("data").required("userName").textValue());
            assertEquals(actualUser.getNickName(), profileValue.required("data").required("nickName").textValue());
            assertFalse(profileValue.required("data").has("password"));
            String profilePath = "/api/local-account/profile-revisions";
            var profileBody = JSON.createObjectNode().put("nickName", actualUser.getNickName()).put("email", actualUser.getEmail())
                    .put("phonenumber", actualUser.getPhonenumber()).put("sex", actualUser.getSex())
                    .put("user_id", account.required("user_id").textValue())
                    .put("expected_revision", profileValue.required("account_revision").textValue());
            var profileCsrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            String profileKey = java.util.UUID.randomUUID().toString();
            long profileOperations = jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_operation WHERE operation_name='LOCAL-07'", Long.class);
            long profileAudits = jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_audit WHERE action='LOCAL-07'", Long.class);
            String credentialRevision = jdbc.queryForObject("SELECT CAST(credential_revision AS CHAR) FROM platform_local_account WHERE user_id=?",
                    String.class, account.required("user_id").textValue());
            var noProfileCsrf = profileChange(http, origin, profilePath, profileBody, profileKey, null);
            assertEquals(403, noProfileCsrf.statusCode());
            var actualDeployment = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/application-deployment.json")));
            String foreignActor = actualDeployment.required("deployment_id").textValue();
            assertNotEquals(account.required("user_id").textValue(), foreignActor);
            var switchedActor = profileBody.deepCopy().put("user_id", foreignActor);
            assertEquals(403, profileChange(http, origin, profilePath, switchedActor, java.util.UUID.randomUUID().toString(), profileCsrf).statusCode(),
                    "请求主体与当前账户不一致时必须拒绝资料写入");
            var profileSaved = profileChange(http, origin, profilePath, profileBody, profileKey, profileCsrf);
            assertEquals(200, profileSaved.statusCode());
            var profileReceipt = JSON.readTree(profileSaved.body());
            assertFalse(profileReceipt.required("data").required("reauthentication_required").booleanValue());
            assertEquals(new java.math.BigInteger(profileBody.required("expected_revision").textValue()).add(java.math.BigInteger.ONE).toString(),
                    profileReceipt.required("resource_revision").textValue());
            var profileReplayed = profileChange(http, origin, profilePath, profileBody, profileKey, profileCsrf);
            assertEquals(200, profileReplayed.statusCode()); assertEquals(profileReceipt, JSON.readTree(profileReplayed.body()));
            var profileStale = profileChange(http, origin, profilePath, profileBody, java.util.UUID.randomUUID().toString(), profileCsrf);
            assertEquals(409, profileStale.statusCode()); assertEquals("REVISION_CONFLICT", JSON.readTree(profileStale.body()).required("code").textValue());
            var injection = profileBody.deepCopy().put("userId", actualUser.getUserId());
            assertEquals(422, profileChange(http, origin, profilePath, injection, java.util.UUID.randomUUID().toString(), profileCsrf).statusCode());
            var conflictingProfile = profileBody.deepCopy().put("expected_revision", profileReceipt.required("resource_revision").textValue());
            assertEquals(409, profileChange(http, origin, profilePath, conflictingProfile, profileKey, profileCsrf).statusCode());
            var refreshedProfile = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/system/user/profile"))
                    .header("X-Schema-Version", "1.0.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, refreshedProfile.statusCode());
            assertEquals(profileReceipt.required("resource_revision"), JSON.readTree(refreshedProfile.body()).required("account_revision"));
            assertEquals(profileOperations + 1, jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_operation WHERE operation_name='LOCAL-07'", Long.class));
            assertEquals(profileAudits + 1, jdbc.queryForObject("SELECT COUNT(*) FROM platform_local_audit WHERE action='LOCAL-07'", Long.class));
            assertEquals(credentialRevision, jdbc.queryForObject("SELECT CAST(credential_revision AS CHAR) FROM platform_local_account WHERE user_id=?",
                    String.class, account.required("user_id").textValue()));
            var profileCurrentUser = application.getBean(ISysUserService.class).selectUserById(actualUser.getUserId());
            assertEquals(actualUser.getNickName(), profileCurrentUser.getNickName()); assertEquals(actualUser.getEmail(), profileCurrentUser.getEmail());
            assertEquals(actualUser.getPhonenumber(), profileCurrentUser.getPhonenumber()); assertEquals(actualUser.getSex(), profileCurrentUser.getSex());
            evidence.put("native_profile_read_save_replay_verified", true); evidence.put("profile_stale_version_status", 409);
            evidence.put("profile_unknown_field_status", 422); evidence.put("profile_missing_csrf_status", 403);
            evidence.put("profile_actor_changed_status", 403); evidence.put("profile_conflicting_replay_status", 409);
            var unsupported = get(http, origin, "unsupported"); assertEquals(422, unsupported.statusCode());
            assertEquals("SCHEMA_UNSUPPORTED", JSON.readTree(unsupported.body()).required("code").textValue());
            var injected = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/framework/navigation?user_id=" + account.required("user_id").textValue()))
                    .header("X-Schema-Version", "1.0.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(422, injected.statusCode()); assertEquals("SCHEMA_INVALID", JSON.readTree(injected.body()).required("code").textValue());
            var current = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            assertEquals(204, client.post().uri("/auth/logout").header("Origin", ORIGIN)
                    .header(current.required("header_name").textValue(), current.required("token").textValue()).retrieve().toBodilessEntity().getStatusCode().value());
            assertEquals(401, get(http, origin, "1.0.0").statusCode()); assertEquals(before, counts(jdbc));
            if (Boolean.getBoolean("medical.navigationBrowser")) {
                var browser = new ProcessBuilder("/usr/local/bin/node", "tests/browser/framework-navigation-checks.js",
                        System.getProperty("medical.navigationOutput"), origin).directory(root.toFile()).redirectErrorStream(true)
                        .redirectOutput(Path.of(System.getProperty("medical.navigationOutput")).resolve("browser-output.log").toFile()).start();
                try {
                    assertTrue(browser.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, browser.exitValue());
                } finally { if (browser.isAlive()) browser.destroyForcibly(); }
                evidence.put("actual_browser_verified", true);
                if (Boolean.getBoolean("medical.passwordBrowser")) {
                    var passwords = new ProcessBuilder("/usr/local/bin/node", "tests/browser/medical-account-checks.js",
                            System.getProperty("medical.navigationOutput"), origin).directory(root.toFile()).redirectErrorStream(true)
                            .redirectOutput(Path.of(System.getProperty("medical.navigationOutput")).resolve("password-browser-output.log").toFile()).start();
                    try {
                        assertTrue(passwords.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, passwords.exitValue());
                    } finally { if (passwords.isAlive()) passwords.destroyForcibly(); }
                    evidence.put("actual_password_browser_verified", true);
                }
            }
            assertEquals(before, counts(jdbc));
            evidence.put("original_services_verified", true); evidence.put("anonymous_http_status", 401);
            evidence.put("navigation_http_status", 200); evidence.put("unsupported_version_http_status", 422);
            evidence.put("query_injection_http_status", 422);
            evidence.put("logout_http_status", 204); evidence.put("after_logout_http_status", 401);
            evidence.put("roles", value.required("roles").size()); evidence.put("permissions", value.required("permissions").size());
            evidence.put("routers", value.required("routers").size()); evidence.put("actual_http_port", application.getWebServer().getPort());
            evidence.set("business_counts", before); evidence.put("business_counts_unchanged", true);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true); evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.navigationEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static HttpResponse<byte[]> get(HttpClient client, String origin, String version) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(origin + "/api/framework/navigation"))
                .header("X-Schema-Version", version).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpResponse<byte[]> profileChange(HttpClient client, String origin, String path, JsonNode body,
            String key, JsonNode csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).header("Origin", ORIGIN)
                .header("Content-Type", "application/json").header("X-Schema-Version", "1.0.0").header("Idempotency-Key", key);
        if (csrf != null) request.header(csrf.required("header_name").textValue(), csrf.required("token").textValue());
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static JsonNode counts(JdbcTemplate jdbc) {
        var result = JSON.createObjectNode();
        for (String table : java.util.List.of("tenant", "project", "tenant_member", "template_version", "permission_grant", "task_head", "operation_record", "sys_role", "sys_menu", "sys_user_role"))
            result.put(table, java.util.Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }
}
