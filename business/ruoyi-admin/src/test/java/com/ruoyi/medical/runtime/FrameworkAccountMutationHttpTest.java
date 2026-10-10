package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.web.controller.system.SysProfileController;
import com.ruoyi.web.controller.system.SysUserController;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

public final class FrameworkAccountMutationHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";
    private static final String PATH = "/api/local-account/credential-revisions";

    @Test void originalAccountControllersOwnWritesAndPreserveMedicalRevisionAuditAndRevocation() throws Exception {
        var first = verifyOriginalAccountControllers();
        var second = verifyOriginalAccountControllers();
        var evidence = JSON.createObjectNode();
        evidence.setAll(second);
        evidence.put("application_instances_verified", 2);
        evidence.set("application_instances", JSON.createArrayNode().add(first).add(second));
        Files.write(Path.of(System.getProperty("medical.accountActionsEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private ObjectNode verifyOriginalAccountControllers() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var runtime = root.resolve(".implementation-work/runtime");
        var secrets = JSON.readTree(Files.readAllBytes(runtime.resolve("service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(runtime.resolve("local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(runtime.resolve("invitation-response-secret.json")));
        var token = JSON.readTree(Files.readAllBytes(runtime.resolve("ruoyi-token-secret.json")));
        var ownerPath = runtime.resolve("owner-credentials.json");
        var owner = (ObjectNode) JSON.readTree(Files.readAllBytes(ownerPath));
        String originalPassword = owner.required("password").textValue();
        var evidence = JSON.createObjectNode();
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
                new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
                        "MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                        "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                        "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                        "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
        try (application) {
            assertTrue(application.containsBean("ruoYiAccountOperations"), "生产账户动作必须执行原Controller");
            var actions = application.getBean("ruoYiAccountOperations");
            var profiles = ReflectionTestUtils.getField(actions, "profiles");
            assertEquals(SysProfileController.class, AopUtils.getTargetClass(profiles));
            assertTrue(AopUtils.isAopProxy(profiles), "原个人信息操作日志必须保持执行");
            assertSame(application.getBean(SysUserController.class), ReflectionTestUtils.getField(actions, "users"));
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var businessBefore = counts(jdbc, "tenant", "project", "tenant_member", "permission_grant", "task_head");
            long operationsBefore = count(jdbc, "platform_local_operation");
            long auditBefore = count(jdbc, "platform_local_audit");
            long lastLog = jdbc.queryForObject("SELECT COALESCE(MAX(oper_id),0) FROM sys_oper_log", Long.class);
            String base = "http://127.0.0.1:" + application.getWebServer().getPort();
            var first = login(base, owner);
            var other = login(base, owner);
            var before = get(first, base, "/auth/account");
            assertEquals(200, before.statusCode());
            var account = JSON.readTree(before.body());
            String revision = account.required("revision").textValue();
            String id = account.required("user_id").textValue();
            var snapshot = jdbc.queryForMap("SELECT revision,credential_revision,password_hash FROM platform_local_account WHERE user_id=?", id);
            var profile = JSON.createObjectNode().put("display_name", account.required("display_name").textValue())
                    .put("platform_role", account.required("platform_role").textValue()).put("expected_revision", revision);
            var forbidden = change(first, base, "/api/local-accounts/" + id + "/profile-revisions", profile, UUID.randomUUID().toString(), true);
            assertEquals(403, forbidden.statusCode(), "缺少原编辑权限时不能进入业务状态判断或读取原操作结果");
            assertEquals("ACCESS_DENIED", JSON.readTree(forbidden.body()).required("code").textValue());
            var wrong = change(first, base, credential(originalPassword + "!", originalPassword, revision), UUID.randomUUID().toString(), true);
            assertEquals(401, wrong.statusCode());
            assertEquals("AUTHENTICATION_FAILED", JSON.readTree(wrong.body()).required("code").textValue());
            var same = change(first, base, credential(originalPassword, originalPassword, revision), UUID.randomUUID().toString(), true);
            assertEquals(401, same.statusCode(), "同一新旧密码必须按原Controller拒绝");
            assertTrue(snapshot.equals(jdbc.queryForMap("SELECT revision,credential_revision,password_hash FROM platform_local_account WHERE user_id=?", id)),
                    "原Controller拒绝以后不得改变凭据或医疗版本");
            var noCsrf = change(first, base, credential(originalPassword, originalPassword, revision), UUID.randomUUID().toString(), false);
            assertEquals(403, noCsrf.statusCode());
            var bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String nextPassword = java.util.Base64.getEncoder().encodeToString(bytes);
            String key = UUID.randomUUID().toString();
            var body = credential(originalPassword, nextPassword, revision);
            var changed = change(first, base, body, key, true);
            assertEquals(200, changed.statusCode());
            // 确认真实写入后同步保存本人凭据，后续检查始终使用实际有效密码。
            owner.put("password", nextPassword); saveOwner(ownerPath, owner);
            var result = JSON.readTree(changed.body());
            String nextRevision = new java.math.BigInteger(revision).add(java.math.BigInteger.ONE).toString();
            assertEquals(nextRevision, result.required("resource_revision").textValue());
            assertEquals(id, result.required("data").required("account").required("user_id").textValue());
            assertTrue(result.required("data").required("reauthentication_required").booleanValue());
            assertTrue(jdbc.queryForObject("SELECT password_hash FROM platform_local_account WHERE user_id=?", String.class, id)
                    .startsWith("$2a$12$"), "原Controller必须采用应用配置的BCrypt工作因子");
            assertEquals(403, get(first, base, "/auth/account").statusCode());
            assertEquals(403, get(other, base, "/auth/account").statusCode());
            var current = login(base, owner);
            var replay = change(current, base, body, key, true);
            assertEquals(200, replay.statusCode()); assertEquals(result, JSON.readTree(replay.body()));
            var conflict = change(current, base, credential(nextPassword, originalPassword, revision), key, true);
            assertEquals(409, conflict.statusCode());
            assertEquals("IDEMPOTENCY_CONFLICT", JSON.readTree(conflict.body()).required("code").textValue());
            assertEquals(operationsBefore + 1, count(jdbc, "platform_local_operation"));
            assertEquals(auditBefore + 1, count(jdbc, "platform_local_audit"));
            String currentRevision = new java.math.BigInteger(nextRevision).add(java.math.BigInteger.ONE).toString();
            assertEquals(currentRevision, jdbc.queryForObject("SELECT CAST(revision AS CHAR) FROM platform_local_identity_metadata WHERE user_id=?", String.class, id));
            assertEquals(new java.math.BigInteger(snapshot.get("credential_revision").toString()).add(java.math.BigInteger.ONE).toString(),
                    jdbc.queryForObject("SELECT CAST(credential_revision AS CHAR) FROM platform_local_identity_metadata WHERE user_id=?", String.class, id));
            var restored = change(current, base, credential(nextPassword, originalPassword, currentRevision), UUID.randomUUID().toString(), true);
            assertEquals(200, restored.statusCode());
            owner.put("password", originalPassword); saveOwner(ownerPath, owner);
            var restoredSession = login(base, owner);
            assertEquals(200, get(restoredSession, base, "/auth/account").statusCode());
            assertEquals(operationsBefore + 2, count(jdbc, "platform_local_operation"));
            assertEquals(auditBefore + 2, count(jdbc, "platform_local_audit"));
            assertEquals(businessBefore, counts(jdbc, "tenant", "project", "tenant_member", "permission_grant", "task_head"));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE oper_id>? AND method=?", Long.class,
                    lastLog, SysProfileController.class.getName() + ".updatePwd()") < 4 && System.nanoTime() < deadline) Thread.sleep(100);
            var logs = jdbc.queryForList("SELECT oper_param,json_result,error_msg FROM sys_oper_log WHERE oper_id>? AND method=?",
                    lastLog, SysProfileController.class.getName() + ".updatePwd()");
            assertEquals(4, logs.size(), "原日志覆盖两次拒绝和两次真实密码写入，重放不产生第二次操作");
            String logContent = JSON.writeValueAsString(logs);
            assertFalse(logContent.contains(originalPassword), "原日志不能包含本人秘密");
            assertFalse(logContent.contains(nextPassword), "原日志不能包含更新秘密");
            assertTrue(originalPassword.equals(JSON.readTree(Files.readAllBytes(ownerPath)).required("password").textValue()));
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(ownerPath));
            evidence.put("original_profile_controller", AopUtils.getTargetClass(profiles).getName());
            evidence.put("original_user_controller", SysUserController.class.getName());
            evidence.put("actual_http_port", application.getWebServer().getPort());
            evidence.put("wrong_password_status", 401); evidence.put("same_password_status", 401);
            evidence.put("administrator_without_native_permission_status", 403);
            evidence.put("missing_csrf_status", 403); evidence.put("password_change_status", 200);
            evidence.put("both_previous_sessions_status", 403); evidence.put("replay_status", 200);
            evidence.put("conflicting_replay_status", 409); evidence.put("password_restore_status", 200);
            evidence.put("synchronous_operations_added", 2); evidence.put("synchronous_audits_added", 2);
            evidence.put("original_logs_added", logs.size()); evidence.put("password_log_redaction_verified", true);
            evidence.put("owner_credential_available", true); evidence.put("customer_business_counts_unchanged", true);
            evidence.put("configured_bcrypt_work_factor", 12);
            evidence.set("customer_business_counts", businessBefore);
            evidence.put("administrator_legal_mutations_verified", false);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        return evidence;
    }

    private static HttpClient login(String base, JsonNode owner) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var csrf = JSON.readTree(get(client, base, "/auth/csrf").body());
        String body = "username=" + URLEncoder.encode(owner.required("username").textValue(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(owner.required("password").textValue(), StandardCharsets.UTF_8);
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/login")).header("Origin", ORIGIN)
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(302, response.statusCode()); return client;
    }

    private static HttpResponse<byte[]> get(HttpClient client, String base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static ObjectNode credential(String current, String password, String revision) {
        return JSON.createObjectNode().put("current_password", current).put("password", password).put("expected_revision", revision);
    }

    private static HttpResponse<byte[]> change(HttpClient client, String base, JsonNode body, String key, boolean withCsrf) throws Exception {
        return change(client, base, PATH, body, key, withCsrf);
    }

    private static HttpResponse<byte[]> change(HttpClient client, String base, String path, JsonNode body, String key, boolean withCsrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Origin", ORIGIN)
                .header("X-Schema-Version", "1.0.0").header("Idempotency-Key", key).header("Content-Type", "application/json");
        if (withCsrf) {
            var csrf = JSON.readTree(get(client, base, "/auth/csrf").body());
            request.header(csrf.required("header_name").textValue(), csrf.required("token").textValue());
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void saveOwner(Path path, ObjectNode owner) throws Exception {
        var temporary = Files.createTempFile(path.getParent(), "owner-credential-", ".json");
        Files.setPosixFilePermissions(temporary, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        try (var channel = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
            var bytes = java.nio.ByteBuffer.wrap(JSON.writeValueAsBytes(owner));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (var directory = java.nio.channels.FileChannel.open(path.getParent(), java.nio.file.StandardOpenOption.READ)) { directory.force(true); }
    }

    private static long count(JdbcTemplate jdbc, String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static ObjectNode counts(JdbcTemplate jdbc, String... tables) {
        var result = JSON.createObjectNode(); for (String table : tables) result.put(table, count(jdbc, table)); return result;
    }
}
