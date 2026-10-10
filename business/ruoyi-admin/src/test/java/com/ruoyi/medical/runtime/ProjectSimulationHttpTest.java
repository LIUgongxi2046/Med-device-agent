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

public final class ProjectSimulationHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void createsRevisesAndDecidesProjectWithActualTransactions() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var token = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
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
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class);
            var jdbc = new JdbcTemplate(source);
            long original = jdbc.queryForObject("SELECT COUNT(*) FROM project", Long.class);
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>();
            form.add("username", owner.required("username").textValue()); form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                data.enableProjectAdministration();
                var selected = post(http, origin, "/api/session/tenant-selections", "0", UUID.randomUUID().toString(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0"));
                String context = success(selected, 200).required("current_context").required("context_revision").textValue();
                var create = JSON.createObjectNode().put("schema_version", "1.0.0").put("name", "SIMULATION 项目写入验收")
                    .put("responsible_user_id", actor).put("reason", "虚拟项目创建验收").put("approval_attested", true);
                create.set("attributes", JSON.createArrayNode()); create.set("creation_basis", data.projectBasis());
                String key = UUID.randomUUID().toString();
                var created = post(http, origin, "/api/projects", context, key, create);
                var receipt = success(created, 201);
                assertEquals("PENDING", receipt.required("status").textValue()); assertEquals("1", receipt.required("revision").textValue());
                var replayed = post(http, origin, "/api/projects", context, key, create);
                success(replayed, 201);
                assertEquals(JSON.readTree(created.body()), JSON.readTree(replayed.body()));
                assertEquals(409, post(http, origin, "/api/projects", context, key, create.deepCopy().put("name", "SIMULATION 其他项目")).statusCode());
                String project = receipt.required("project_id").textValue();
                context = receipt.required("current_context").required("context_revision").textValue();
                var detail = success(read(http, origin, "/api/projects/" + project, context), 200);
                assertEquals("SIMULATION 项目写入验收", detail.required("name").textValue());
                var revise = JSON.createObjectNode().put("schema_version", "1.0.0").put("expected_revision", "1")
                    .put("based_on_profile_revision", receipt.required("profile_revision").textValue())
                    .put("reason", "虚拟项目属性修订验收").put("approval_attested", true);
                var attributes = revise.putArray("attributes");
                var attribute = attributes.addObject().put("key", "simulation_usage").put("status", "UNKNOWN")
                    .putNull("value").put("reason", "虚拟项目用途待确认"); attribute.putArray("authority_refs");
                var revised = success(post(http, origin, "/api/projects/" + project + "/profile-revisions", context, UUID.randomUUID().toString(), revise), 200);
                assertEquals("2", revised.required("revision").textValue());
                assertNotEquals(receipt.required("profile_revision"), revised.required("profile_revision"));
                context = revised.required("current_context").required("context_revision").textValue();
                assertEquals(attributes, success(read(http, origin, "/api/projects/" + project, context), 200).required("profile").required("attributes"));
                assertEquals(409, post(http, origin, "/api/projects/" + project + "/profile-revisions", context, UUID.randomUUID().toString(), revise).statusCode());
                String revision = "2";
                for (var decision : java.util.List.of("ACTIVATE", "PAUSE", "RESTORE", "ARCHIVE")) {
                    var body = JSON.createObjectNode().put("schema_version", "1.0.0").put("decision", decision)
                        .put("expected_revision", revision).put("reason", "虚拟项目状态验收").put("approval_attested", true);
                    String originalContext = context, decisionKey = UUID.randomUUID().toString();
                    var response = post(http, origin, "/api/projects/" + project + "/state-decisions", context, decisionKey, body);
                    var decided = success(response, 200);
                    var decisionReplay = post(http, origin, "/api/projects/" + project + "/state-decisions", originalContext, decisionKey, body);
                    success(decisionReplay, 200);
                    assertEquals(JSON.readTree(response.body()), JSON.readTree(decisionReplay.body()));
                    revision = decided.required("revision").textValue();
                    context = decided.required("current_context").required("context_revision").textValue();
                    assertEquals(decided.required("status"), success(read(http, origin, "/api/projects/" + project, context), 200).required("status"));
                }
                assertEquals("6", revision);
                assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND target_id=?", Integer.class, data.tenant, project));
                assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND project_id=?", Integer.class, data.tenant, project));
                assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=?", Integer.class, data.tenant, project));
                data.revokeProjectReading(); assertEquals(403, read(http, origin, "/api/projects/" + project, context).statusCode());
                evidence.put("data_kind", "SIMULATION"); evidence.put("project_actions", 6);
                evidence.put("create_replay_and_conflict_verified", true); evidence.put("revision_conflict_verified", true);
                evidence.put("status_replay_and_reopening_verified", true); evidence.put("audit_and_outbox_verified", true);
            }
            if (Boolean.getBoolean("medical.projectSimulationBrowser")) {
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                    data.enableProjectAdministration();
                    Path output = Path.of(System.getProperty("medical.projectSimulationEvidence")).getParent();
                    var basis = data.projectBasis();
                    var browser = new ProcessBuilder("/usr/local/bin/node", root.resolve("tests/browser/project-simulation-checks.js").toString(),
                        output.toString(), origin, data.tenant, actor, basis.required("version_id").textValue(),
                        basis.required("content_sha256").textValue(), System.getProperty("medical.projectSimulationFrontend"))
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("project-browser-output.log").toFile()).start();
                    assertTrue(browser.waitFor(240, java.util.concurrent.TimeUnit.SECONDS), "项目浏览器验收必须完成");
                    assertEquals(0, browser.exitValue(), Files.readString(output.resolve("project-browser-output.log")));
                    evidence.put("simulation_browser_verified", true);
                }
            }
            assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM project", Long.class));
            evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("project_counts_unchanged", true);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true); evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.projectSimulationEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static HttpResponse<byte[]> read(HttpClient http, String origin, String path, String context) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpResponse<byte[]> post(HttpClient http, String origin, String path, String context, String key, JsonNode body) throws Exception {
        var session = success(read(http, origin, "/api/session", context), 200);
        var csrf = session.required("csrf");
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
