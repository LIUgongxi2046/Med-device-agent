package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationDocumentData;
import com.ruoyi.medical.persistence.SimulationTaskData;
import com.ruoyi.medical.persistence.SourceRightsSchema;
import com.ruoyi.medical.persistence.TaskInputSchema;
import com.ruoyi.medical.persistence.TemplateDefinitionSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class DocumentRevisionHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void uploadsSavesReadsAndReplaysFullSimulationDocumentThroughActualServices() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")); var secrets = read(root, "service-secrets.json");
        var management = read(root, "local-management-secret.json"); var invitation = read(root, "invitation-response-secret.json");
        var token = read(root, "ruoyi-token-secret.json"); var owner = read(root, "owner-credentials.json");
        var schemas = SchemaCatalogue.fromClasspath(); String interfacesHash = schemas.bind("interfaces", JSON.nullNode(), "NullableId").schemaSha256();
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var options = new ArrayList<>(List.of("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
            "--server.shutdown=immediate", "--spring.main.banner-mode=off", "--medical.document-upload.environment=LOCAL", "--medical.document-upload.region=LOCAL",
            "--medical.document-upload.maximum-sources=100", "--medical.document-upload.maximum-versions=1000", "--medical.document-upload.maximum-relations=10000",
            "--medical.document-upload.maximum-bytes=1048576", "--medical.document-upload.expires-after-seconds=3600", "--medical.document-upload.artifact-schema-manifest=" + interfacesHash,
            "--medical.document-upload.artifact-profiles=",
            "--medical.document-upload.maximum-nodes=1000", "--medical.document-upload.maximum-table-cells=1000", "--medical.document-upload.maximum-body-relationships=10000",
            "--medical.document-read.cursor-key-id=" + id(), "--medical.document-read.cursor-key=" + Base64.getUrlEncoder().withoutPadding().encodeToString(key),
            "--medical.document-read.cursor-lifetime=PT60S", "--medical.document-read.maximum-page-bytes=1048576"));
        var app = assertInstanceOf(ServletWebServerApplicationContext.class, new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
            "MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(), "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
            "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(), "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
            "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(), "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
            "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue())).run(options.toArray(String[]::new)));
        try (app) {
            var source = app.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            String origin = "http://127.0.0.1:" + app.getWebServer().getPort();
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue()); form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin", "http://127.0.0.1:18080")
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try (var tasks = new SimulationTaskData(source, root, actor, app.getBean(PermissionSchema.class), app.getBean(TaskInputSchema.class));
                    var data = new SimulationDocumentData(source, root, actor, tasks, app.getBean(PermissionSchema.class), app.getBean(TaskInputSchema.class),
                        app.getBean(TemplateDefinitionSchema.class), app.getBean(SourceRightsSchema.class))) {
                data.initialize();
                String context = success(send(http, origin, "/api/session/tenant-selections", "POST", "0", id(), JSON.writeValueAsBytes(JSON.createObjectNode()
                    .put("tenant_id", tasks.tenant).put("expected_context_revision", "0"))), 200).required("data").required("current_context").required("context_revision").textValue();
                var sourceView = success(send(http, origin, "/api/source-versions/" + data.sourceVersion, "GET", context, null, null), 200).required("data");
                assertEquals(data.sourceVersion, sourceView.required("source_version").textValue());
                assertEquals("ACTIVE", sourceView.required("lifecycle_state").textValue());
                assertEquals("1", sourceView.required("lifecycle_revision").textValue());
                assertEquals(jdbc.queryForObject("SELECT original_sha256 FROM source_version WHERE tenant_id=? AND source_version=?",
                    String.class, tasks.tenant, data.sourceVersion), sourceView.required("original_sha256").textValue());
                String documentPath = "/api/documents/" + data.document;
                var initial = success(send(http, origin, documentPath, "GET", context, null, null), 200).required("data");
                assertEquals(data.initialRevision, initial.required("document_revision").textValue());
                String dependencyPath = documentPath + "/dependency-access-set?document_revision=" + data.initialRevision + "&expected_revision=1";
                var dependency = success(send(http, origin, dependencyPath, "GET", context, null, null), 200);
                assertEquals(data.accessSet, dependency.required("data").textValue());
                assertEquals("1", dependency.required("resource_revision").textValue());
                assertEquals("REVISION_CONFLICT", error(send(http, origin, dependencyPath.replace("expected_revision=1", "expected_revision=2"), "GET", context, null, null), 409));
                assertEquals("SCHEMA_INVALID", error(send(http, origin, documentPath + "/dependency-access-set", "GET", context, null, null), 422));
                byte[] body = data.updatedBody(); String sha256 = DigestUtils.sha256Hex(body);
                var uploadInput = JSON.createObjectNode().put("purpose", "DOCUMENT_BODY").put("document_id", data.document).put("expected_revision", "1")
                    .put("media_type", "application/json").put("size_bytes", String.valueOf(body.length)).put("sha256", sha256)
                    .put("schema_ref", "interfaces.schema.yaml#/$defs/DocumentBody").put("dependency_access_set_id", data.accessSet);
                String issuanceKey = id(); var grant = success(send(http, origin, "/api/artifact-upload-grants", "POST", context, issuanceKey, JSON.writeValueAsBytes(uploadInput)), 201).required("data");
                assertEquals(grant, success(send(http, origin, "/api/artifact-upload-grants", "POST", context, issuanceKey, JSON.writeValueAsBytes(uploadInput)), 201).required("data"));
                success(send(http, origin, grant.required("upload_url").textValue() + "1", "PUT", context, id(), body), 200);
                String completePath = "/api/artifact-upload-grants/" + grant.required("grant_id").textValue() + "/completions";
                var completion = JSON.createObjectNode().put("sha256", sha256).put("size_bytes", String.valueOf(body.length)).put("expected_revision", "1");
                var artifact = success(send(http, origin, completePath, "POST", context, id(), JSON.writeValueAsBytes(completion)), 200).required("data");
                assertEquals(sha256, artifact.required("sha256").textValue());
                var revisionInput = JSON.createObjectNode().put("base_revision", data.initialRevision).put("expected_revision", "1")
                    .put("body_artifact_id", artifact.required("artifact_id").textValue()).put("body_sha256", sha256).put("body_schema_revision", data.bodySchema).put("reason", "SIMULATION 人工修改正文与数值字段");
                String revisionKey = id(); byte[] revisionBytes = JSON.writeValueAsBytes(revisionInput);
                var saved = success(send(http, origin, documentPath + "/revisions", "POST", context, revisionKey, revisionBytes), 201);
                var snapshot = saved.required("data"); String newRevision = snapshot.required("document_revision").textValue();
                assertNotEquals(data.initialRevision, newRevision); assertEquals("2", saved.required("resource_revision").textValue());
                assertEquals(data.plan, snapshot.required("plan_revision").textValue()); assertEquals(data.template, snapshot.required("template_version").textValue());
                assertEquals(artifact.required("artifact_id"), snapshot.required("body").required("artifact_id"));
                assertNotEquals(snapshot.required("fields").required("artifact_id"), snapshot.required("evidence_links").required("artifact_id"));
                assertEquals(saved, success(send(http, origin, documentPath + "/revisions", "POST", context, revisionKey, revisionBytes), 201));
                assertEquals(snapshot, success(send(http, origin, documentPath, "GET", context, null, null), 200).required("data"));
                assertEquals(snapshot, success(send(http, origin, documentPath + "/revisions/" + newRevision, "GET", context, null, null), 200).required("data"));
                String historyPath = documentPath + "/revisions/" + data.initialRevision;
                var history = success(send(http, origin, historyPath, "GET", context, null, null), 200).required("data");
                assertEquals(initial.required("document_revision"), history.required("document_revision"));
                assertEquals(initial.required("body"), history.required("body"));
                assertEquals(initial.required("fields"), history.required("fields"));
                assertEquals(initial.required("evidence_links"), history.required("evidence_links"));
                assertEquals(0, history.required("allowed_actions").size());
                assertEquals("SCHEMA_INVALID", error(send(http, origin, historyPath + "?document_revision=" + newRevision, "GET", context, null, null), 422));
                var actualNodes = new ArrayList<JsonNode>(); var actualFields = new ArrayList<JsonNode>(); var actualEvidence = new ArrayList<JsonNode>();
                String cursor = null;
                for (int page = 0; page < 4; page++) {
                    String path = documentPath + "/body?document_revision=" + newRevision + "&limit=1" + (cursor == null ? "" : "&cursor=" + java.net.URLEncoder.encode(cursor, StandardCharsets.UTF_8));
                    var current = success(send(http, origin, path, "GET", context, null, null), 200).required("data");
                    current.required("nodes").forEach(actualNodes::add); current.required("fields").forEach(actualFields::add); current.required("evidence_links").forEach(actualEvidence::add);
                    cursor = current.required("coverage").required("next_cursor").isNull() ? null : current.required("coverage").required("next_cursor").textValue();
                    assertEquals(page == 3, current.required("coverage").required("complete").booleanValue());
                }
                var expected = JSON.readTree(body); assertEquals(expected.required("nodes"), JSON.valueToTree(actualNodes));
                assertEquals(expected.required("fields"), JSON.valueToTree(actualFields)); assertEquals(expected.required("evidence_links"), JSON.valueToTree(actualEvidence)); assertNull(cursor);
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM document_revision WHERE tenant_id=? AND document_id=?", Integer.class, tasks.tenant, data.document));
                var storedField = jdbc.queryForMap("SELECT field_id,field_definition_id,value_json FROM field_value WHERE tenant_id=? AND document_revision=?", tasks.tenant, newRevision);
                assertEquals(data.field, storedField.get("field_id")); assertEquals(data.fieldDefinition, storedField.get("field_definition_id"));
                assertEquals(expected.required("fields").get(0), JSON.readTree((String) storedField.get("value_json")));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM document_evidence_projection WHERE tenant_id=? AND document_revision=? AND link_id=?", Integer.class, tasks.tenant, newRevision, data.evidenceLink));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM document_section WHERE tenant_id=? AND document_revision=?", Integer.class, tasks.tenant, newRevision));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='document.check.requested'", Integer.class, tasks.tenant, data.document));
                assertEquals("IDEMPOTENCY_CONFLICT", error(send(http, origin, documentPath + "/revisions", "POST", context, revisionKey,
                    JSON.writeValueAsBytes(revisionInput.deepCopy().put("reason", "SIMULATION 不同请求"))), 409));
                assertEquals("REVISION_CONFLICT", error(send(http, origin, documentPath + "/revisions", "POST", context, id(), revisionBytes), 409));
                jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='document.read'", tasks.tenant);
                assertEquals("ACCESS_DENIED", error(send(http, origin, documentPath, "GET", context, null, null), 403));
                assertEquals("ACCESS_DENIED", error(send(http, origin, historyPath, "GET", context, null, null), 403));
                TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_SNAPSHOT, snapshot);
                var receipt = JSON.createObjectNode().put("data_kind", "SIMULATION").put("tenant_id", tasks.tenant).put("document_id", data.document)
                    .put("document_revision", newRevision).put("body_sha256", sha256).put("public_upload_save_read_verified", true)
                    .put("fixed_historical_snapshot_verified", true).put("formal_customer_acceptance", false);
                Path evidence = root.resolve(".implementation-work/document-revision-http-result.json"); Files.write(evidence, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt));
            }
        }
    }

    private static JsonNode read(Path root, String file) throws Exception { return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + file))); }
    private static HttpResponse<byte[]> send(HttpClient http, String origin, String path, String method, String context, String key, byte[] body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(60)).header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) {
            var csrf = JSON.readTree(http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/csrf")).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body());
            request.header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue());
            request.header("Content-Type", method.equals("PUT") ? "application/octet-stream" : "application/json");
            if (method.equals("PUT")) request.header("Content-SHA256", DigestUtils.sha256Hex(body));
        }
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()); return JSON.readTree(response.body());
    }
    private static String error(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8)); return JSON.readTree(response.body()).required("code").textValue();
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
