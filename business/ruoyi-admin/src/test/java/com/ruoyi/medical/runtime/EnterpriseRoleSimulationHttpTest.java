package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationTaskData;
import com.ruoyi.medical.persistence.TaskInputSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class EnterpriseRoleSimulationHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void publishesReadsRevisesAndRevokesWithActualAuthority() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = secret(root, "service-secrets"); var management = secret(root, "local-management-secret");
        var invitation = secret(root, "invitation-response-secret"); var token = secret(root, "ruoyi-token-secret");
        var owner = secret(root, "owner-credentials");
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
        var evidence = JSON.createObjectNode();
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            long original = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_role_version", Long.class);
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue());
            form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                data.enableRoleAdministration();
                var selected = success(post(http, origin, "/api/session/tenant-selections", "0", UUID.randomUUID().toString(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200);
                String context = selected.required("current_context").required("context_revision").textValue();
                assertTrue(success(read(http, origin, "/api/enterprise-roles", context), 200).required("roles").isEmpty());
                var publish = JSON.createObjectNode().put("schema_version", "1.0.0").put("name", "SIMULATION 岗位验收")
                    .put("valid_from", "2026-10-07T01:00:00Z").putNull("valid_to").put("reason", "虚拟岗位发布验收")
                    .put("approval_attested", true).put("expected_head_revision", "0");
                var definition = publish.putObject("definition").put("schema_version", "1.0.0").put("role_id", data.role);
                definition.putArray("actions").add("task.read");
                var condition = definition.putObject("condition").put("schema_version", "1.0.0");
                condition.putArray("identity_modes").add("local"); condition.putArray("purposes").add("TASK_READING");
                var delegation = condition.putObject("delegation");
                for (String field : java.util.List.of("actions", "role_ids", "resource_kinds", "project_ids", "resource_ids")) delegation.putArray(field);
                publish.set("approval_basis", data.projectBasis());
                String key = UUID.randomUUID().toString(), path = "/api/enterprise-roles/" + data.role + "/versions";
                var excessive = publish.deepCopy();
                assertInstanceOf(com.fasterxml.jackson.databind.node.ArrayNode.class, excessive.required("definition").required("actions")).add("task.create");
                assertEquals(403, post(http, origin, path, context, UUID.randomUUID().toString(), excessive).statusCode());
                var response = post(http, origin, path, context, key, publish); var receipt = success(response, 201);
                assertEquals(JSON.readTree(response.body()), JSON.readTree(post(http, origin, path, context, key, publish).body()));
                assertEquals(409, post(http, origin, path, context, key, publish.deepCopy().put("name", "SIMULATION 其他岗位")).statusCode());
                String version = receipt.required("role").required("version_id").textValue();
                context = receipt.required("current_context").required("context_revision").textValue();
                assertEquals(receipt.required("role"), success(read(http, origin, path + "/" + version, context), 200));
                assertEquals(1, success(read(http, origin, "/api/enterprise-roles", context), 200).required("roles").size());
                assertEquals(409, post(http, origin, path, context, UUID.randomUUID().toString(), publish).statusCode());
                publish.put("expected_head_revision", "1").put("name", "SIMULATION 岗位第二版本");
                var second = success(post(http, origin, path, context, UUID.randomUUID().toString(), publish), 201);
                assertEquals("2", second.required("role").required("head").required("revision").textValue());
                assertNotEquals(version, second.required("role").required("version_id").textValue());
                context = second.required("current_context").required("context_revision").textValue();
                assertEquals("1", success(read(http, origin, path + "/" + version, context), 200).required("revision").textValue());
                var revoke = JSON.createObjectNode().put("schema_version", "1.0.0").put("expected_revision", "1")
                    .put("reason", "虚拟岗位撤销验收").put("approval_attested", true);
                String revocation = path + "/" + version + "/revocations", revokeKey = UUID.randomUUID().toString();
                var revoked = post(http, origin, revocation, context, revokeKey, revoke);
                var revokedReceipt = success(revoked, 200);
                assertEquals(JSON.readTree(revoked.body()), JSON.readTree(post(http, origin, revocation, context, revokeKey, revoke).body()));
                context = revokedReceipt.required("current_context").required("context_revision").textValue();
                assertEquals("REVOKED", success(read(http, origin, path + "/" + version, context), 200).required("status").textValue());
                var originalPublish = publish.deepCopy().put("expected_head_revision", "0").put("name", "SIMULATION 岗位验收");
                assertEquals(409, post(http, origin, path, selected.required("current_context").required("context_revision").textValue(), key, originalPublish).statusCode());
                assertEquals(409, post(http, origin, revocation, context, UUID.randomUUID().toString(), revoke).statusCode());
                assertEquals(403, read(http, origin, "/api/enterprise-roles/" + UUID.randomUUID() + "/versions/" + version, context).statusCode());
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name IN ('ROLE-03','ROLE-04')", Integer.class, data.tenant));
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action IN ('role.published','role.revoked')", Integer.class, data.tenant));
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id IN (?,?)", Integer.class,
                    data.tenant, version, second.required("role").required("version_id").textValue()));
                data.revokeRoleAdministration(); assertEquals(403, read(http, origin, "/api/enterprise-roles", context).statusCode());
                evidence.put("http_role_writes", 3); evidence.put("history_replay_conflict_authority_verified", true);
            }
            if (Boolean.getBoolean("medical.roleSimulationBrowser")) {
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                    data.enableRoleAdministration(); Path output = Path.of(System.getProperty("medical.roleSimulationEvidence")).getParent();
                    var basis = data.projectBasis();
                    var browser = new ProcessBuilder("/usr/local/bin/node", root.resolve("tests/browser/role-simulation-checks.js").toString(),
                        output.toString(), origin, data.tenant, data.role + "," + data.secondRole, basis.required("version_id").textValue(),
                        basis.required("content_sha256").textValue(), System.getProperty("medical.roleSimulationFrontend"))
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("role-browser-output.log").toFile()).start();
                    try {
                        assertTrue(browser.waitFor(240, java.util.concurrent.TimeUnit.SECONDS), "岗位浏览器验证必须完成");
                        assertEquals(0, browser.exitValue(), Files.readString(output.resolve("role-browser-output.log")));
                    } finally { if (browser.isAlive()) browser.destroyForcibly(); }
                    evidence.put("simulation_browser_verified", true);
                }
            }
            assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_role_version", Long.class));
            evidence.put("role_counts_unchanged", true); evidence.put("actual_http_port", application.getWebServer().getPort());
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        evidence.put("data_kind", "SIMULATION"); evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.roleSimulationEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static JsonNode secret(Path root, String name) throws Exception {
        return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + name + ".json")));
    }
    private static HttpResponse<byte[]> read(HttpClient http, String origin, String path, String context) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> post(HttpClient http, String origin, String path, String context, String key, JsonNode body) throws Exception {
        var session = success(read(http, origin, "/api/session", context), 200); var csrf = session.required("csrf");
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).header("Idempotency-Key", key).header("Origin", "http://127.0.0.1:18080")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return JSON.readTree(response.body()).required("data");
    }
}
