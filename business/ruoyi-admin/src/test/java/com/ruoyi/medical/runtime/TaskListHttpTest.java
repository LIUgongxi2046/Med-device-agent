package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.json.JsonMapper;
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

public final class TaskListHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void taskListingUsesCurrentApplicationAndVerifiedEnterpriseAdmission() throws Exception {
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
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var before = counts(jdbc);
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
            String path = "/api/projects/" + actor + "/tasks";
            var denied = read(http, origin, path, "1.0.0", "0");
            assertEquals(403, denied.statusCode(), "项目任务列表需要执行实际企业准入");
            assertEquals("ACCESS_DENIED", JSON.readTree(denied.body()).required("code").textValue());
            for (String query : java.util.List.of("?page=0", "?page=2", "?page_size=101", "?status=UNKNOWN",
                    "?owner_id=" + owner.required("username").textValue(), "?updated_from=INVALID", "?unknown=1",
                    "?page=1&page=1", "?dataset_watermark=0", "?filter_sha256=INVALID")) {
                var invalid = read(http, origin, path + query, "1.0.0", "0"); assertEquals(422, invalid.statusCode(), query);
                assertEquals("SCHEMA_INVALID", JSON.readTree(invalid.body()).required("code").textValue());
            }
            assertEquals(422, read(http, origin, path, "INVALID", "0").statusCode());
            assertEquals(422, read(http, origin, path, "1.0.0", "INVALID").statusCode());
            for (var status : com.ruoyi.medical.contracts.ResourceProtocol.TaskStatus.values()) {
                assertEquals(403, read(http, origin, path + "?status=" + status.name() + "&owner_id=" + actor, "1.0.0", "0").statusCode());
            }
            var anonymous = HttpClient.newHttpClient(); assertEquals(401, read(anonymous, origin, path, "1.0.0", "0").statusCode());
            try (var data = new com.ruoyi.medical.persistence.SimulationTaskData(
                    application.getBean("masterDataSource", javax.sql.DataSource.class), root, actor,
                    application.getBean(com.ruoyi.medical.persistence.PermissionSchema.class),
                    application.getBean(com.ruoyi.medical.persistence.TaskInputSchema.class))) {
                var session = JSON.readTree(client.get().uri("/api/session").header("X-Schema-Version", "1.0.0")
                    .retrieve().body(byte[].class)).required("data");
                var selection = JSON.createObjectNode(); selection.put("tenant_id", data.tenant); selection.put("expected_context_revision", "0");
                var selected = JSON.readTree(client.post().uri("/api/session/tenant-selections")
                    .contentType(MediaType.APPLICATION_JSON).header("Origin", "http://127.0.0.1:18080")
                    .header(session.required("csrf").required("header_name").textValue(), session.required("csrf").required("token").textValue())
                    .header("X-Schema-Version", "1.0.0").header("X-Context-Revision", "0")
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).body(selection)
                    .retrieve().body(byte[].class));
                String context = selected.required("data").required("current_context").required("context_revision").textValue();
                String tasks = "/api/projects/" + data.project + "/tasks";
                var first = successful(read(http, origin, tasks + "?page_size=1", "1.0.0", context));
                assertEquals("2", first.required("page_info").required("total").textValue());
                var projectDetail = successful(read(http, origin, "/api/projects/" + data.project, "1.0.0", context));
                assertEquals("2", projectDetail.required("children").required("tasks").textValue());
                assertEquals(1, first.required("items").size());
                assertEquals(data.first, first.required("items").get(0).required("detail").required("task_id").textValue());
                assertTrue(first.required("items").get(0).required("goal").textValue().startsWith("SIMULATION "));
                var detail = successful(read(http, origin, "/api/tasks/" + data.first, "1.0.0", context));
                assertEquals(first.required("items").get(0).required("detail"), detail);
                assertEquals(1, detail.required("missing_requirements").size());
                assertEquals(JSON.readTree("[\"task.read\"]"), detail.required("missing_requirements").get(0).required("allowed_actions"));
                String next = tasks + "?page=2&page_size=1&dataset_watermark=" + first.required("page_info").required("watermark").textValue()
                    + "&filter_sha256=" + first.required("page_info").required("filter_sha256").textValue();
                var second = successful(read(http, origin, next, "1.0.0", context));
                assertEquals(data.second, second.required("items").get(0).required("detail").required("task_id").textValue());
                assertEquals("2", second.required("page_info").required("total").textValue());
                for (String filter : java.util.List.of("?status=PLAN_PENDING", "?owner_id=" + data.otherOwner)) {
                    var filtered = successful(read(http, origin, tasks + filter, "1.0.0", context));
                    assertEquals("1", filtered.required("page_info").required("total").textValue());
                    assertEquals(data.second, filtered.required("items").get(0).required("detail").required("task_id").textValue());
                }
                var dated = successful(read(http, origin, tasks + "?updated_from=2026-10-07T01:00:00Z&updated_to=2026-10-07T01:00:00Z", "1.0.0", context));
                assertEquals("1", dated.required("page_info").required("total").textValue());
                assertEquals(data.first, dated.required("items").get(0).required("detail").required("task_id").textValue());
                assertEquals(403, read(http, origin, "/api/tasks/" + data.hidden, "1.0.0", context).statusCode());
                assertEquals(403, read(http, origin, "/api/projects/" + data.otherProject + "/tasks", "1.0.0", context).statusCode());
                assertEquals(409, read(http, origin, tasks, "1.0.0", "0").statusCode());
                data.updateSecond();
                var changed = read(http, origin, next, "1.0.0", context);
                assertEquals(409, changed.statusCode()); assertEquals("REVISION_CONFLICT", JSON.readTree(changed.body()).required("code").textValue());
                data.revokeFirst();
                var remaining = successful(read(http, origin, tasks, "1.0.0", context));
                assertEquals("1", remaining.required("page_info").required("total").textValue());
                assertEquals("1", successful(read(http, origin, "/api/projects/" + data.project, "1.0.0", context))
                    .required("children").required("tasks").textValue());
                assertEquals(data.second, remaining.required("items").get(0).required("detail").required("task_id").textValue());
                assertEquals(403, read(http, origin, "/api/tasks/" + data.first, "1.0.0", context).statusCode());
                data.revokeProject(); assertEquals(403, read(http, origin, tasks, "1.0.0", context).statusCode());
                evidence.put("simulation_task_read_verified", true); evidence.put("simulation_data_kind", "SIMULATION");
                evidence.put("authorized_task_total", 2); evidence.put("unauthorized_task_hidden", true);
                evidence.put("pagination_and_filters_verified", true); evidence.put("revision_change_rejected", true);
                evidence.put("revoked_task_and_project_rejected", true);
            }
            if (Boolean.getBoolean("medical.taskListBrowser")) {
                try (var data = new com.ruoyi.medical.persistence.SimulationTaskData(
                        application.getBean("masterDataSource", javax.sql.DataSource.class), root, actor,
                        application.getBean(com.ruoyi.medical.persistence.PermissionSchema.class),
                        application.getBean(com.ruoyi.medical.persistence.TaskInputSchema.class))) {
                    data.addTasks(21);
                    Path output = Path.of(System.getProperty("medical.taskListEvidence")).getParent();
                    var browser = new ProcessBuilder("/usr/local/bin/node", root.resolve("tests/browser/task-simulation-checks.js").toString(),
                        output.toString(), origin, data.tenant, data.project, data.otherProject, data.first, data.otherOwner,
                        System.getProperty("medical.taskListFrontend"))
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("task-browser-output.log").toFile()).start();
                    assertTrue(browser.waitFor(180, java.util.concurrent.TimeUnit.SECONDS), "任务浏览器验收必须完成");
                    assertEquals(0, browser.exitValue(), Files.readString(output.resolve("task-browser-output.log")));
                    evidence.put("simulation_browser_verified", true);
                }
            }
            assertEquals(before, counts(jdbc));
            evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("anonymous_status", 401);
            evidence.put("no_enterprise_status", 403); evidence.put("invalid_filters_status", 422);
            evidence.put("known_status_filters", com.ruoyi.medical.contracts.ResourceProtocol.TaskStatus.values().length);
            evidence.set("business_counts", before); evidence.put("business_counts_unchanged", true);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.taskListEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static HttpResponse<byte[]> read(HttpClient http, String origin, String path, String version, String context) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", version)
            .header("X-Context-Revision", context).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static com.fasterxml.jackson.databind.JsonNode successful(HttpResponse<byte[]> response) throws Exception {
        assertEquals(200, response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return JSON.readTree(response.body()).required("data");
    }
    private static com.fasterxml.jackson.databind.node.ObjectNode counts(JdbcTemplate jdbc) {
        var result = JSON.createObjectNode();
        for (String table : java.util.List.of("tenant", "project", "tenant_member", "task_head", "task_revision", "permission_grant", "operation_record"))
            result.put(table, java.util.Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }
}
