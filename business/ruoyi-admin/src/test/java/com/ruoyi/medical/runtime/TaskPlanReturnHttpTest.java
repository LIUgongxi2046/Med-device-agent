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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

public final class TaskPlanReturnHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void actualPlanDecisionRoutesRequireCurrentAuthorityAndCannotPublishIncompleteInputs() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = read(root, "service-secrets.json"); var management = read(root, "local-management-secret.json");
        var invitation = read(root, "invitation-response-secret.json"); var token = read(root, "ruoyi-token-secret.json");
        var owner = read(root, "owner-credentials.json");
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
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            var before = counts(jdbc); String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var anonymous = get(http, origin, "/api/tasks/" + id() + "/plan-decision-basis", "1", null);
            assertEquals(401, anonymous.statusCode());
            assertEquals(401, get(http, origin, "/api/scenarios", "1", null).statusCode());
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue());
            form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                    .body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                var selection = JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0");
                String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(), JSON.writeValueAsBytes(selection)), 200)
                        .required("data").required("current_context").required("context_revision").textValue();
                String path = "/api/tasks/" + data.first;
                success(get(http, origin, path, context, null), 200);
                String documentPath = "/api/documents/" + data.first;
                var invalidDocumentQuery = get(http, origin, documentPath + "?object_key=untrusted", context, null);
                assertEquals(422, invalidDocumentQuery.statusCode(), "文稿快照入口必须拒绝未定义的查询字段");
                assertEquals("SCHEMA_INVALID", JSON.readTree(invalidDocumentQuery.body()).required("code").textValue());
                assertEquals(403, get(http, origin, documentPath, context, null).statusCode());
                var missingBodyRevision = get(http, origin, documentPath + "/body", context, null);
                assertEquals(422, missingBodyRevision.statusCode(), "正文入口需要固定document_revision");
                assertEquals("SCHEMA_INVALID", JSON.readTree(missingBodyRevision.body()).required("code").textValue());
                assertEquals(422, get(http, origin, documentPath + "/body?document_revision=" + data.first + "&limit=0", context, null).statusCode());
                assertEquals(403, get(http, origin, documentPath + "/body?document_revision=" + data.first, context, null).statusCode());
                assertEquals(409, get(http, origin, documentPath + "/body?document_revision=" + data.first, "0", null).statusCode());
                var missingRevision = post(http, origin, documentPath + "/revisions", context, id(), JSON.writeValueAsBytes(JSON.createObjectNode()));
                assertEquals(422, missingRevision.statusCode(), "正文保存入口必须完整校验RevisionInput");
                assertEquals("SCHEMA_INVALID", JSON.readTree(missingRevision.body()).required("code").textValue());
                var missingUploadFields = post(http, origin, "/api/artifact-upload-grants", context, id(), JSON.writeValueAsBytes(JSON.createObjectNode()));
                assertEquals(422, missingUploadFields.statusCode(), "正文许可入口必须执行完整UploadRequest校验");
                assertEquals("SCHEMA_INVALID", JSON.readTree(missingUploadFields.body()).required("code").textValue());
                byte[] originalDocument = Files.readAllBytes(root.resolve("backend-design/接口补充设计.md"));
                var upload = JSON.createObjectNode().put("purpose", "DOCUMENT_BODY").put("document_id", data.first)
                        .put("expected_revision", "1").put("media_type", "application/json").put("size_bytes", String.valueOf(originalDocument.length))
                        .put("sha256", org.apache.commons.codec.digest.DigestUtils.sha256Hex(originalDocument))
                        .put("schema_ref", "interfaces.schema.yaml#/$defs/DocumentBody").put("dependency_access_set_id", data.first);
                // 已有任务身份不能替代文稿及依赖实体；工程原件摘要不表示专业正文已获准。
                var wrongEntity = post(http, origin, "/api/artifact-upload-grants", context, id(), JSON.writeValueAsBytes(upload));
                assertEquals(403, wrongEntity.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(wrongEntity.body()).required("code").textValue());
                var invalidUploadPurpose = upload.deepCopy().put("purpose", "AGENT_ARTIFACT");
                assertEquals(422, post(http, origin, "/api/artifact-upload-grants", context, id(), JSON.writeValueAsBytes(invalidUploadPurpose)).statusCode());
                assertEquals(422, post(http, origin, "/api/artifact-upload-grants?purpose=DOCUMENT_BODY", context, id(), JSON.writeValueAsBytes(upload)).statusCode());
                var staleUploadContext = post(http, origin, "/api/artifact-upload-grants", "0", id(), JSON.writeValueAsBytes(upload));
                assertEquals(409, staleUploadContext.statusCode());
                assertEquals("CONTEXT_CHANGED", JSON.readTree(staleUploadContext.body()).required("code").textValue());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM artifact_upload_grant WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_session WHERE tenant_id=?", Integer.class, data.tenant));
                String completionPath = "/api/artifact-upload-grants/" + data.first + "/completions";
                int initialArtifacts = jdbc.queryForObject("SELECT COUNT(*) FROM artifact WHERE tenant_id=?", Integer.class, data.tenant);
                var missingCompletionFields = post(http, origin, completionPath, context, id(), JSON.writeValueAsBytes(JSON.createObjectNode()));
                assertEquals(422, missingCompletionFields.statusCode(), "正文上传完成入口必须执行完整UploadCompletion校验");
                assertEquals("SCHEMA_INVALID", JSON.readTree(missingCompletionFields.body()).required("code").textValue());
                var completion = JSON.createObjectNode().put("sha256", org.apache.commons.codec.digest.DigestUtils.sha256Hex(originalDocument))
                        .put("size_bytes", String.valueOf(originalDocument.length)).put("expected_revision", "1");
                var wrongGrant = post(http, origin, completionPath, context, id(), JSON.writeValueAsBytes(completion));
                assertEquals(403, wrongGrant.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(wrongGrant.body()).required("code").textValue());
                assertEquals(422, post(http, origin, completionPath + "?purpose=DOCUMENT_BODY", context, id(), JSON.writeValueAsBytes(completion)).statusCode());
                assertEquals(422, post(http, origin, completionPath, context, id(), JSON.writeValueAsBytes(completion.deepCopy().put("object_key", "untrusted"))).statusCode());
                var oldCompletionContext = post(http, origin, completionPath, "0", id(), JSON.writeValueAsBytes(completion));
                assertEquals(409, oldCompletionContext.statusCode());
                assertEquals("CONTEXT_CHANGED", JSON.readTree(oldCompletionContext.body()).required("code").textValue());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='BX-04'", Integer.class, data.tenant));
                assertEquals(initialArtifacts, jdbc.queryForObject("SELECT COUNT(*) FROM artifact WHERE tenant_id=?", Integer.class, data.tenant));
                var wrongUpload = put(http, origin, "/api/uploads/" + data.first + "/parts/1", context, id(), originalDocument);
                assertEquals(403, wrongUpload.statusCode());
                assertEquals("ACCESS_DENIED", JSON.readTree(wrongUpload.body()).required("code").textValue());
                assertEquals(422, put(http, origin, "/api/uploads/" + data.first + "/parts/1?purpose=DOCUMENT_BODY", context, id(), originalDocument).statusCode());
                assertEquals(409, put(http, origin, "/api/uploads/" + data.first + "/parts/1", "0", id(), originalDocument).statusCode());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02'", Integer.class, data.tenant));
                var scenarios = success(get(http, origin, "/api/scenarios", context, null), 200).required("data");
                assertEquals(data.tenant, scenarios.required("tenant_id").textValue());
                assertTrue(scenarios.required("items").isEmpty());
                assertEquals(5, scenarios.required("planned_requirements").size());
                assertEquals("0", scenarios.required("total").textValue());
                assertEquals(scenarios, success(get(http, origin, "/api/scenarios?dataset_watermark="
                        + scenarios.required("dataset_watermark").textValue(), context, null), 200).required("data"));
                for (var requirement : scenarios.required("planned_requirements")) {
                    assertEquals("PLANNED", requirement.required("status").textValue());
                    assertTrue(requirement.required("task_entry").isNull());
                    assertFalse(requirement.required("required_definition_fields").isEmpty());
                }
                assertEquals(422, get(http, origin, "/api/scenarios?subject_id=" + actor, context, null).statusCode());
                assertEquals(422, get(http, origin, "/api/scenarios?limit=101", context, null).statusCode());
                assertEquals(422, get(http, origin, "/api/scenarios?limit=1&limit=2", context, null).statusCode());
                assertEquals(422, get(http, origin, "/api/scenarios?document_type=UNKNOWN", context, null).statusCode());
                var missingSpecification = get(http, origin, "/api/scenarios?document_type=REGISTRATION_DOSSIER", context, null);
                assertEquals(422, missingSpecification.statusCode());
                assertEquals("DEPENDENCY_UNAVAILABLE", JSON.readTree(missingSpecification.body()).required("code").textValue());
                var invalidScenarioCursor = get(http, origin, "/api/scenarios?after=" + id() + "&dataset_watermark="
                        + scenarios.required("dataset_watermark").textValue(), context, null);
                assertEquals(409, invalidScenarioCursor.statusCode());
                assertEquals("CURSOR_INVALID", JSON.readTree(invalidScenarioCursor.body()).required("code").textValue());
                var staleScenarioCursor = get(http, origin, "/api/scenarios?dataset_watermark=" + "0".repeat(64), context, null);
                assertEquals(409, staleScenarioCursor.statusCode());
                assertEquals("CURSOR_EXPIRED", JSON.readTree(staleScenarioCursor.body()).required("code").textValue());
                assertEquals(data.project, success(get(http, origin, "/api/scenarios?project_id=" + data.project, context, null), 200)
                        .required("data").required("project_id").textValue());
                assertEquals(409, get(http, origin, "/api/scenarios", "0", null).statusCode());
                assertEquals(403, get(http, origin, "/api/scenarios?project_id=" + id(), context, null).statusCode());
                var invalidCreate = post(http, origin, "/api/tasks", context, id(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertEquals(422, invalidCreate.statusCode());
                assertEquals("SCHEMA_INVALID", JSON.readTree(invalidCreate.body()).required("code").textValue());
                String originalCreation = "/api/projects/" + data.project + "/task-create-operations";
                assertEquals(403, get(http, origin, originalCreation, context, id()).statusCode());
                byte[] createInput = jdbc.queryForObject("SELECT CAST(request_json AS CHAR) FROM task_input_revision WHERE tenant_id=? AND project_id=? AND task_id=?",
                        String.class, data.tenant, data.project, data.first).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var noCreateGrant = post(http, origin, "/api/tasks", context, id(), createInput);
                assertEquals(403, noCreateGrant.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(noCreateGrant.body()).required("code").textValue());
                String draftPath = "/api/projects/" + data.project + "/task-create-drafts";
                assertEquals(403, get(http, origin, draftPath, context, id()).statusCode());
                assertEquals(403, post(http, origin, draftPath, context, id(), createInput).statusCode());
                String ownersPath = "/api/projects/" + data.project + "/task-creation-owners";
                assertEquals(403, get(http, origin, ownersPath, context, null).statusCode());
                data.enableTaskCreation();
                var owners = success(get(http, origin, ownersPath + "?limit=1", context, null), 200).required("data");
                assertEquals(data.project, owners.required("project_id").textValue());
                assertEquals(data.tenant, owners.required("tenant_id").textValue());
                assertEquals(1, owners.required("items").size());
                assertEquals(actor, owners.required("items").get(0).required("user_id").textValue());
                assertTrue(owners.required("next_after").isNull());
                assertEquals(422, get(http, origin, ownersPath + "?subject_id=" + actor, context, null).statusCode());
                assertEquals(422, get(http, origin, ownersPath + "?limit=101", context, null).statusCode());
                assertEquals(409, get(http, origin, ownersPath, "0", null).statusCode());
                var staleOwners = get(http, origin, ownersPath + "?limit=1&dataset_watermark=" + "0".repeat(64), context, null);
                assertEquals(409, staleOwners.statusCode());
                assertEquals("CURSOR_EXPIRED", JSON.readTree(staleOwners.body()).required("code").textValue());
                for (String kind : List.of("sources", "collections")) {
                    String choicesPath = "/api/projects/" + data.project + "/task-creation-" + kind;
                    var choices = success(get(http, origin, choicesPath + "?limit=1", context, null), 200).required("data");
                    assertEquals(data.tenant, choices.required("tenant_id").textValue());
                    assertEquals(data.project, choices.required("project_id").textValue());
                    assertEquals(kind.equals("sources") ? "SOURCE" : "COLLECTION", choices.required("scope_kind").textValue());
                    assertTrue(choices.required("items").isEmpty()); assertTrue(choices.required("next_after").isNull());
                    String choicesWatermark = choices.required("dataset_watermark").textValue();
                    var unchangedChoices = success(get(http, origin, choicesPath + "?limit=1&dataset_watermark=" + choicesWatermark, context, null), 200).required("data");
                    assertEquals(choices, unchangedChoices);
                    var invalidCursor = get(http, origin, choicesPath + "?limit=1&after=" + id() + "&dataset_watermark=" + choicesWatermark, context, null);
                    assertEquals(409, invalidCursor.statusCode());
                    assertEquals("CURSOR_INVALID", JSON.readTree(invalidCursor.body()).required("code").textValue());
                    assertEquals(422, get(http, origin, choicesPath + "?subject_id=" + actor, context, null).statusCode());
                    assertEquals(422, get(http, origin, choicesPath + "?after=" + id(), context, null).statusCode());
                    assertEquals(409, get(http, origin, choicesPath, "0", null).statusCode());
                    assertEquals(403, get(http, origin, "/api/projects/" + id() + "/task-creation-" + kind, context, null).statusCode());
                    var staleChoices = get(http, origin, choicesPath + "?dataset_watermark=" + "0".repeat(64), context, null);
                    assertEquals(409, staleChoices.statusCode());
                    assertEquals("CURSOR_EXPIRED", JSON.readTree(staleChoices.body()).required("code").textValue());
                }
                var emptyDraft = success(get(http, origin, draftPath, context, id()), 200);
                assertTrue(emptyDraft.required("data").isNull()); assertTrue(emptyDraft.required("resource_revision").isNull());
                assertEquals(422, post(http, origin, draftPath, context, id(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)).statusCode());
                assertEquals(422, get(http, origin, draftPath + "?project_id=" + data.project, context, id()).statusCode());
                assertEquals(409, get(http, origin, draftPath, "0", id()).statusCode());
                String draftKey = id();
                var savedDraft = success(post(http, origin, draftPath, context, draftKey, createInput), 201);
                assertEquals(JSON.readTree(createInput), savedDraft.required("data").required("content"));
                assertEquals(data.project, savedDraft.required("data").required("project_id").textValue());
                assertEquals(actor, savedDraft.required("data").required("actor_id").textValue());
                assertTrue(savedDraft.required("resource_revision").isNull());
                assertEquals(savedDraft, success(get(http, origin, draftPath, context, draftKey), 201));
                assertEquals(savedDraft, success(post(http, origin, draftPath, context, draftKey, createInput), 201));
                var changedDraft = JSON.readTree(createInput).deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) changedDraft).put("goal", "SIMULATION 修订后的任务输入");
                var draftConflict = post(http, origin, draftPath, context, draftKey, JSON.writeValueAsBytes(changedDraft));
                assertEquals(409, draftConflict.statusCode());
                assertEquals("IDEMPOTENCY_CONFLICT", JSON.readTree(draftConflict.body()).required("code").textValue());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name='TK-01-DRAFT'",
                        Integer.class, data.tenant, actor));
                String draftOperation = savedDraft.required("data").required("operation_id").textValue();
                var draftEvent = JSON.readTree(jdbc.queryForObject("SELECT CAST(payload AS CHAR) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='task.input_draft.saved'",
                        String.class, data.tenant, draftOperation));
                assertFalse(draftEvent.has("content")); assertFalse(draftEvent.has("goal"));
                assertEquals(savedDraft.required("data").required("body_sha256"), draftEvent.required("body_sha256"));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='task.input_draft.saved'",
                        Integer.class, data.tenant));
                var noCreation = success(get(http, origin, originalCreation, context, id()), 200);
                assertTrue(noCreation.required("data").isNull()); assertTrue(noCreation.required("resource_revision").isNull());
                assertEquals(422, get(http, origin, originalCreation + "?project_id=" + data.project, context, id()).statusCode());
                var oldCreateContext = get(http, origin, originalCreation, "0", id());
                assertEquals(409, oldCreateContext.statusCode()); assertEquals("CONTEXT_CHANGED", JSON.readTree(oldCreateContext.body()).required("code").textValue());
                var unavailableTemplate = post(http, origin, "/api/tasks", context, draftKey, createInput);
                assertEquals(403, unavailableTemplate.statusCode());
                assertEquals("ACCESS_DENIED", JSON.readTree(unavailableTemplate.body()).required("code").textValue());
                assertEquals(savedDraft, success(get(http, origin, draftPath, context, draftKey), 201));
                data.revokeCreationOwnerRead();
                var noOwners = success(get(http, origin, ownersPath + "?limit=1", context, null), 200).required("data");
                assertTrue(noOwners.required("items").isEmpty()); assertTrue(noOwners.required("next_after").isNull());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='TK-01'", Integer.class, data.tenant));
                data.revokeTaskCreation(); assertEquals(403, get(http, origin, originalCreation, context, id()).statusCode());
                assertEquals(403, get(http, origin, ownersPath, context, null).statusCode());
                assertEquals(403, get(http, origin, "/api/projects/" + data.project + "/task-creation-sources", context, null).statusCode());
                assertEquals(403, get(http, origin, "/api/projects/" + data.project + "/task-creation-collections", context, null).statusCode());
                assertEquals(403, get(http, origin, draftPath, context, draftKey).statusCode());
                assertEquals(403, post(http, origin, draftPath, context, draftKey, createInput).statusCode());
                assertEquals(403, get(http, origin, path + "/plan-decision-basis", context, null).statusCode());
                data.enablePlanDecisions();
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM permission_grant WHERE tenant_id=? AND subject_id=? AND action='plan.confirm' AND status='ACTIVE' AND schema_manifest_sha256=?",
                        Integer.class, data.tenant, actor, application.getBean(PermissionSchema.class).manifestSha256()));
                var empty = success(get(http, origin, path + "/plan-decision-operations", context, id()), 200);
                assertTrue(empty.required("data").isNull()); assertEquals("1", empty.required("resource_revision").textValue());
                var missing = get(http, origin, path + "/plan-decision-basis", context, null);
                assertEquals(409, missing.statusCode());
                assertEquals("PLAN_STALE", JSON.readTree(missing.body()).required("code").textValue());
                var malformed = post(http, origin, path + "/plan-decisions", context, id(), "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertEquals(422, malformed.statusCode()); assertEquals("SCHEMA_INVALID", JSON.readTree(malformed.body()).required("code").textValue());
                var unbound = get(http, origin, path + "/plan-decision-basis?decision=RETURN", context, null);
                assertEquals(422, unbound.statusCode());
                var stale = get(http, origin, path + "/plan-decision-basis", "0", null);
                assertEquals(409, stale.statusCode()); assertEquals("CONTEXT_CHANGED", JSON.readTree(stale.body()).required("code").textValue());
                var unsupported = getDecision(http, origin, path + "/plan-decision-basis", context, null, "APPROVE");
                assertEquals(422, unsupported.statusCode()); assertEquals("SCHEMA_INVALID", JSON.readTree(unsupported.body()).required("code").textValue());
                var confirmWithoutPlan = getDecision(http, origin, "/api/tasks/" + data.second + "/plan-decision-basis", context, null, "CONFIRM");
                assertEquals(409, confirmWithoutPlan.statusCode());
                assertEquals("PLAN_STALE", JSON.readTree(confirmWithoutPlan.body()).required("code").textValue());
                var revision = jdbc.queryForMap("SELECT current_revision,status,revision FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=?",
                        data.tenant, data.project, data.first);
                assertEquals("1", revision.get("revision").toString()); assertEquals("NEEDS_MATERIALS", revision.get("status"));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM task_plan_decision WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='TK-04'", Integer.class, data.tenant));
                data.revokePlanDecisions(); assertEquals(403, get(http, origin, path + "/plan-decision-operations", context, id()).statusCode());
            }
            assertEquals(before, counts(jdbc));
            var logout = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            assertEquals(204, client.post().uri("/auth/logout").header("Origin", "http://127.0.0.1:18080")
                    .header(logout.required("header_name").textValue(), logout.required("token").textValue()).retrieve().toBodilessEntity().getStatusCode().value());
        }
        assertFalse(application.isActive());
    }
    private static JsonNode read(Path root, String file) throws Exception { return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + file))); }
    private static HttpResponse<byte[]> get(HttpClient http, String origin, String path, String context, String key) throws Exception {
        return getDecision(http, origin, path, context, key, null);
    }
    private static HttpResponse<byte[]> getDecision(HttpClient http, String origin, String path, String context, String key, String decision) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20))
                .header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context);
        if (key != null) request.header("Idempotency-Key", key);
        if (decision != null) request.header("X-Plan-Decision", decision);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> post(HttpClient http, String origin, String path, String context, String key, byte[] body) throws Exception {
        var csrf = JSON.readTree(http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/csrf")).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:18080")
                .header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context).header("Idempotency-Key", key)
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()); return JSON.readTree(response.body());
    }
    private static HttpResponse<byte[]> put(HttpClient http, String origin, String path, String context, String key, byte[] body) throws Exception {
        var csrf = JSON.readTree(http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/csrf")).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body());
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/octet-stream").header("Content-SHA256", org.apache.commons.codec.digest.DigestUtils.sha256Hex(body))
                .header("Origin", "http://127.0.0.1:18080").header("X-Schema-Version", "1.0.0")
                .header("X-Context-Revision", context).header("Idempotency-Key", key)
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode counts(JdbcTemplate jdbc) {
        var value = JSON.createObjectNode();
        for (String table : List.of("tenant", "project", "tenant_member", "task_head", "task_revision", "task_plan_decision", "operation_record", "permission_grant"))
            value.put(table, Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return value;
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
