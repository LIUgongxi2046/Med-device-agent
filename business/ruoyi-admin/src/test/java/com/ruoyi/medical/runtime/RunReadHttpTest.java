package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.persistence.BudgetSchema;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationRunData;
import com.ruoyi.medical.persistence.SimulationTaskData;
import com.ruoyi.medical.persistence.TaskInputSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

public final class RunReadHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void readsActualPersistedSimulationRunAndRefusesCurrentRevocation() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = read(root, "service-secrets.json"); var management = read(root, "local-management-secret.json");
        var invitation = read(root, "invitation-response-secret.json"); var token = read(root, "ruoyi-token-secret.json"); var owner = read(root, "owner-credentials.json");
        var app = assertInstanceOf(ServletWebServerApplicationContext.class, new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
            "MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(), "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
            "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(), "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
            "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(), "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
            "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue())).run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
        try (app) {
            var source = app.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            String origin = "http://127.0.0.1:" + app.getWebServer().getPort();
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
            assertEquals(401, send(http, origin, "/api/runs/" + id(), "GET", "1", null).statusCode());
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue()); form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin", "http://127.0.0.1:18080")
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            {
                var tasks = new SimulationTaskData(source, root, actor, app.getBean(PermissionSchema.class), app.getBean(TaskInputSchema.class));
                var data = new SimulationRunData(source, tasks, actor, app.getBean(PermissionSchema.class), app.getBean(BudgetSchema.class));
                String context = success(send(http, origin, "/api/session/tenant-selections", "POST", "0", JSON.writeValueAsBytes(JSON.createObjectNode()
                    .put("tenant_id", tasks.tenant).put("expected_context_revision", "0"))), 200).required("data").required("current_context").required("context_revision").textValue();
                String path = "/api/runs/" + data.run;
                var result = success(send(http, origin, path, "GET", context, null), 200).required("data");
                assertEquals(data.run, result.required("run_id").textValue()); assertEquals(tasks.first, result.required("task_id").textValue());
                assertEquals("WAITING_INPUT", result.required("status").textValue()); assertEquals("1", result.required("revision").textValue());
                assertEquals(data.step, result.required("steps").get(0).required("step_id").textValue());
                assertEquals(data.waiting, result.required("waiting_items").get(0).required("waiting_item_id").textValue());
                assertEquals("SIMULATION 请补充项目资料", result.required("waiting_items").get(0).required("question").textValue());
                assertEquals("2.50000000", result.required("budget").required("spent").textValue());
                assertEquals("1.23000000", result.required("budget").required("reserved").textValue());
                assertEquals("20.00000000", result.required("budget").required("limit").textValue());
                assertEquals(data.event, result.required("event_cursor").textValue());
                assertEquals(result, success(send(http, origin, path, "GET", context, null), 200).required("data"));
                var event = event(http, origin, path, context, null);
                assertEquals(data.event, event.id()); assertEquals("run.updated", event.event());
                var content = assertInstanceOf(JsonNode.class, event.data());
                com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath().validate("interfaces", content, "PublicEvent");
                assertEquals(data.run, content.required("run_id").textValue()); assertEquals(data.stream, content.required("stream").required("stream_id").textValue());
                assertEquals("1", content.required("sequence").textValue());
                String successor = data.updateStepAndAppendEvent();
                var replay = event(http, origin, path, context, data.event);
                assertEquals(successor, replay.id()); assertEquals("step.updated", replay.event());
                var replayContent = assertInstanceOf(JsonNode.class, replay.data());
                assertEquals("2", replayContent.required("sequence").textValue()); assertEquals(data.step, replayContent.required("resource_id").textValue());
                var latest = success(send(http, origin, path, "GET", context, null), 200).required("data");
                assertEquals(successor, latest.required("event_cursor").textValue()); assertEquals("2", latest.required("steps").get(0).required("revision").textValue());
                assertEquals(409, send(http, origin, path + "/events?after=" + id(), "GET", context, null).statusCode());
                assertEquals(422, send(http, origin, path + "/events?after=" + data.event + "&after=" + data.event, "GET", context, null).statusCode());
                assertEquals(422, send(http, origin, path + "/events?after=invalid", "GET", context, null).statusCode());
                assertEquals(422, send(http, origin, "/api/runs/invalid/events", "GET", context, null).statusCode());
                assertEquals(422, send(http, origin, path + "/events", "GET", "invalid", null).statusCode());
                assertEquals(403, send(http, origin, "/api/runs/" + id(), "GET", context, null).statusCode());
                assertEquals(422, send(http, origin, path + "?tenant_id=" + tasks.otherTenant, "GET", context, null).statusCode());
                assertEquals(409, send(http, origin, path, "GET", "0", null).statusCode());
                data.enableCancellation();
                String cancelKey = id();
                byte[] cancellation = JSON.writeValueAsBytes(JSON.createObjectNode().put("expected_revision", "1").put("reason", "SIMULATION 请求停止当前运行"));
                var cancelled = success(send(http, origin, path + "/cancel-requests", "POST", context, cancellation, cancelKey), 200);
                var cancelledRun = cancelled.required("data").required("run");
                assertEquals("CANCEL_REQUESTED", cancelledRun.required("status").textValue()); assertEquals("2", cancelledRun.required("revision").textValue());
                assertEquals(cancelled, success(send(http, origin, path + "/cancel-requests", "POST", context, cancellation, cancelKey), 200));
                assertEquals(409, send(http, origin, path + "/cancel-requests", "POST", context,
                    JSON.writeValueAsBytes(JSON.createObjectNode().put("expected_revision", "1").put("reason", "SIMULATION 不同取消理由")), cancelKey).statusCode());
                assertEquals(409, send(http, origin, path + "/cancel-requests", "POST", context, cancellation, id()).statusCode());
                assertEquals("CANCEL_REQUESTED", jdbc.queryForObject("SELECT status FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=?", String.class, tasks.tenant, tasks.project, tasks.first));
                assertNotNull(jdbc.queryForObject("SELECT dispatch_closed_at FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=?", java.time.LocalDateTime.class, tasks.tenant, tasks.project, data.run));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='run.cancel.requested'", Integer.class, tasks.tenant, data.run));
                var cancelEvent = event(http, origin, path, context, successor);
                assertEquals("3", assertInstanceOf(JsonNode.class, cancelEvent.data()).required("sequence").textValue());
                assertEquals("CANCEL_REQUESTED", assertInstanceOf(JsonNode.class, cancelEvent.data()).required("payload").required("status").textValue());
                jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='task.run'", tasks.tenant);
                assertEquals(403, send(http, origin, path + "/cancel-requests", "POST", context, cancellation, cancelKey).statusCode());
                jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='run.read'", tasks.tenant);
                assertEquals(403, send(http, origin, path, "GET", context, null).statusCode());
                assertEquals(403, send(http, origin, path + "/events?after=" + data.event, "GET", context, null).statusCode());
                var receipt = JSON.createObjectNode().put("data_kind", "SIMULATION").put("tenant_id", tasks.tenant).put("project_id", tasks.project)
                    .put("run_id", data.run).put("persistent_run_http_verified", true).put("records_retained", true).put("formal_customer_acceptance", false);
                receipt.set("snapshot", result);
                receipt.set("public_event", content); receipt.set("replayed_event", replayContent);
                receipt.set("cancellation", cancelled);
                Files.write(root.resolve(".implementation-work/run-read-http-result.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt));
            }
        }
    }

    private static JsonNode read(Path root, String name) throws Exception { return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + name))); }
    private static org.springframework.http.codec.ServerSentEvent<?> event(HttpClient http, String origin, String path, String context, String cursor) {
        var connector = new org.springframework.http.client.reactive.JdkClientHttpConnector(http); connector.setReadTimeout(Duration.ofSeconds(30));
        var reader = new org.springframework.http.codec.ServerSentEventHttpMessageReader(new org.springframework.http.codec.json.Jackson2JsonDecoder(JSON));
        reader.setMaxInMemorySize(1048576);
        var type = org.springframework.core.ResolvableType.forClassWithGenerics(org.springframework.http.codec.ServerSentEvent.class, JsonNode.class);
        var found = connector.connect(org.springframework.http.HttpMethod.GET, URI.create(origin + path + "/events"), request -> {
            request.getHeaders().set("X-Schema-Version", "1.0.0"); request.getHeaders().set("X-Context-Revision", context);
            if (cursor != null) request.getHeaders().set("Last-Event-ID", cursor); return request.setComplete();
        }).flatMapMany(response -> {
            assertEquals(200, response.getStatusCode().value()); assertEquals("no-store", response.getHeaders().getFirst("Cache-Control"));
            assertEquals(new org.springframework.http.MediaType("text", "event-stream", StandardCharsets.UTF_8), response.getHeaders().getContentType());
            return reader.read(type, response, Map.of());
        }).filter(value -> value instanceof org.springframework.http.codec.ServerSentEvent<?> item && item.data() != null).next().block(Duration.ofSeconds(30));
        return assertInstanceOf(org.springframework.http.codec.ServerSentEvent.class, found);
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static HttpResponse<byte[]> send(HttpClient http, String origin, String path, String method, String context, byte[] body) throws Exception {
        return send(http, origin, path, method, context, body, id());
    }
    private static HttpResponse<byte[]> send(HttpClient http, String origin, String path, String method, String context, byte[] body, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(60)).header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context);
        if (body != null) {
            var csrf = JSON.readTree(http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body());
            request.header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Idempotency-Key", key).header("Content-Type", "application/json");
        }
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int expected) throws Exception {
        assertEquals(expected, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()); return JSON.readTree(response.body());
    }
}
