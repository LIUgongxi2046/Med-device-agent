package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
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

public final class SourceUploadHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void acceptsTheOriginalSourceCompletionCommandAndReopensItsPendingOperation() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
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
                "--server.shutdown=immediate", "--spring.main.banner-mode=off", "--spring.profiles.active=source-lease-native"));
        ObjectNode evidence = JSON.createObjectNode();
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            long original = jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class);
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class)); var form = new LinkedMultiValueMap<String,String>();
            form.add("username", owner.required("username").textValue()); form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Origin", "http://127.0.0.1:18080").header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).retrieve().toBodilessEntity().getStatusCode().value());
            String actor = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class)).required("user_id").textValue();
            try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class));
                    var objects = new SourceUploadObjectChecks(root, data.tenant)) {
                data.enableSourceUploading(); data.enableOtherTenantSelection();
                String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200)
                    .required("current_context").required("context_revision").textValue();
                var body = request(root, data.project, actor);
                String upload = success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue();
                byte[] bytes = Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"));
                var completion = JSON.createObjectNode(); completion.put("schema_version", "1.0.0"); completion.put("expected_revision", "1");
                completion.put("version_intent", "NEW_SOURCE"); completion.set("size_bytes", body.required("size_bytes")); completion.set("sha256", body.required("sha256"));
                var part = completion.putArray("parts").addObject(); part.put("part_no", 1); part.set("size_bytes", body.required("size_bytes")); part.set("sha256", body.required("sha256"));
                String key = id(), path = "/api/uploads/" + upload + "/completions";
                var premature = post(http, origin, path, context, key, completion);
                assertEquals(409, premature.statusCode()); assertEquals("STEP_NOT_READY", JSON.readTree(premature.body()).required("code").textValue());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-03'", Integer.class, data.tenant));
                success(put(http, origin, "/api/uploads/" + upload + "/parts/1", context, id(), body.required("sha256").textValue(), bytes), 200);
                var snapshot = read(http, origin, "/api/uploads/" + upload, context); success(snapshot, 200);
                completion.set("expected_revision", JSON.readTree(snapshot.body()).required("resource_revision"));
                var stale = post(http, origin, path, context, key, completion.deepCopy().put("expected_revision", "1"));
                assertEquals(409, stale.statusCode()); assertEquals("REVISION_CONFLICT", JSON.readTree(stale.body()).required("code").textValue());
                var conflicting = completion.deepCopy(); ((ObjectNode) conflicting.required("parts").get(0)).put("sha256",
                    org.apache.commons.codec.digest.DigestUtils.sha256Hex(java.util.Arrays.copyOf(bytes, 16384)));
                var mismatched = post(http, origin, path, context, key, conflicting);
                assertEquals(422, mismatched.statusCode()); assertEquals("CONTENT_MISMATCH", JSON.readTree(mismatched.body()).required("code").textValue());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-03'", Integer.class, data.tenant));
                var accepted = post(http, origin, path, context, key, completion); var operation = success(accepted, 202);
                assertEquals("PROCESSING", operation.required("status").textValue()); assertEquals(upload, operation.required("resource_id").textValue());
                assertArrayEquals(accepted.body(), post(http, origin, path, context, key, completion).body());
                assertEquals(operation, success(read(http, origin, operation.required("status_url").textValue(), context), 200));
                var lookup = success(post(http, origin, "/api/operation-lookups", context, id(), JSON.createObjectNode()
                    .put("operation_id", "KN-03").put("idempotency_key", key)), 200);
                assertEquals("FOUND", lookup.required("lookup_status").textValue()); assertEquals(operation, lookup.required("operation"));
                var reopened = success(read(http, origin, "/api/uploads/" + upload, context), 200);
                assertEquals("COMPLETING", reopened.required("transfer_state").textValue()); assertEquals("UPLOADING", reopened.required("upload_status").textValue());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-03' AND state='PROCESSING' AND http_status IS NULL AND response_ref IS NULL", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.completion.requested'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.completion.requested'", Integer.class, data.tenant));
                assertPendingCompletionDelivery(jdbc, data.tenant, actor, operation.required("operation_id").textValue());
                var command = JSON.readTree(jdbc.queryForObject("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.completion.requested'", String.class, data.tenant));
                assertEquals(completion, command.required("request")); assertEquals(operation.required("operation_id"), command.required("operation_id"));
                assertEquals(jdbc.queryForObject("SELECT event_id FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.completion.requested'", String.class, data.tenant), command.required("event_id").textValue());
                assertEquals("SOURCE_UPLOAD_COMPLETION", command.required("consumer_id").textValue());
                assertEquals(JSON.readTree(accepted.body()), command.required("response"));
                assertEquals(1, command.required("stored_parts").size());
                assertEquals(body.required("sha256"), command.required("stored_parts").get(0).required("object_ref").required("sha256"));
                assertEquals(409, post(http, origin, path, context, key, completion.deepCopy().put("expected_revision", "3")).statusCode());
                assertEquals(409, post(http, origin, path, context, id(), completion).statusCode());
                assertEquals(409, post(http, origin, path, "0", key, completion).statusCode());
                assertEquals(422, post(http, origin, path, context, key, completion.deepCopy().put("approved_by", actor)).statusCode());
                assertEquals(422, post(http, origin, path + "?status=COMPLETED", context, key, completion).statusCode());
                var deniedCsrf = http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
                    .header("X-Context-Revision", context).header("Idempotency-Key", key).header("Origin", "http://127.0.0.1:18080")
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(completion))).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(403, deniedCsrf.statusCode());
                String otherUpload = success(post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("batch_id", id())), 201)
                    .required("upload_id").textValue();
                var reusedKey = post(http, origin, "/api/uploads/" + otherUpload + "/completions", context, key, completion);
                assertEquals(409, reusedKey.statusCode()); assertEquals("IDEMPOTENCY_CONFLICT", JSON.readTree(reusedKey.body()).required("code").textValue());
                var leaseEvidence = verifyCompletionLeases(http, origin, context, jdbc, data.tenant, upload,
                    operation.required("operation_id").textValue(), command, path, key, completion, accepted.body());
                String other = success(post(http, origin, "/api/session/tenant-selections", context, id(), JSON.createObjectNode()
                    .put("tenant_id", data.otherTenant).put("expected_context_revision", context)), 200).required("current_context").required("context_revision").textValue();
                assertEquals(403, post(http, origin, path, other, key, completion).statusCode());
                context = success(post(http, origin, "/api/session/tenant-selections", other, id(), JSON.createObjectNode()
                    .put("tenant_id", data.tenant).put("expected_context_revision", other)), 200).required("current_context").required("context_revision").textValue();
                jdbc.update("UPDATE permission_grant SET status='REVOKED' WHERE tenant_id=? AND action='source.write'", data.tenant);
                assertEquals(403, post(http, origin, path, context, key, completion).statusCode());
                assertEquals(403, post(http, origin, "/api/source-completion-lease-native", context, id(), JSON.createObjectNode()
                    .put("upload_id", upload).put("operation_id", operation.required("operation_id").textValue()).put("action", "CLAIM_A")).statusCode());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-03'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.completion.requested'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.completion.requested'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE tenant_id=? AND attempt_no=6 AND lease_generation=6 AND revision=8 AND status='DELIVERING'", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM consumer_receipt WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM source_version WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rights_revision WHERE tenant_id=?", Integer.class, data.tenant));
                objects.verify(jdbc, data.tenant, upload, bytes);
                evidence.put("data_kind", "SIMULATION"); evidence.put("pending_original_command_save_reopen_verified", true);
                evidence.put("original_response_operation_and_single_events_verified", true);
                evidence.put("pending_consumer_delivery_save_reopen_verified", true);
                evidence.set("lease_checks", leaseEvidence);
                evidence.put("lease_claim_renew_expiry_and_fencing_verified", true);
                evidence.put("lease_attempt_limit_verified", true);
                evidence.put("worker_identity_verified", false);
                evidence.put("current_context_cross_tenant_and_revocation_denial_verified", true);
                evidence.put("csrf_revision_manifest_query_and_cross_upload_key_denial_verified", true);
                evidence.put("source_upload_complete", false); evidence.put("processing_consumer_complete", false);
                evidence.put("professional_approval_created", false); evidence.put("customer_success_verified", false);
            }
            assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class));
            evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("upload_counts_unchanged", true);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling("source-upload-completion-result.json"), JSON.writeValueAsBytes(evidence));
    }

    private static void assertPendingCompletionDelivery(JdbcTemplate jdbc, String tenant, String actor, String operation) {
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE tenant_id=?", Integer.class, tenant));
        assertEquals(1, jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbox_delivery d
            JOIN outbox_event e ON e.tenant_id=d.tenant_id AND e.event_id=d.event_id
            JOIN operation_record o ON o.tenant_id=e.tenant_id AND o.operation_id=JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.operation_id'))
            WHERE d.tenant_id=? AND d.consumer_id='SOURCE_UPLOAD_COMPLETION' AND d.status='PENDING'
              AND d.attempt_no=0 AND d.lease_generation=0 AND d.revision=1
              AND d.lease_owner IS NULL AND d.lease_until IS NULL AND d.last_error IS NULL
              AND d.next_attempt_at=d.created_at AND d.updated_at=d.created_at
              AND d.created_by=? AND d.created_by=o.created_by AND d.created_at=o.created_at
              AND d.classification=o.classification AND e.event_type='source.upload.completion.requested'
              AND e.aggregate_id=o.target_id AND o.operation_id=? AND o.operation_name='KN-03' AND o.state='PROCESSING'
            """, Integer.class, tenant, actor, operation));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM consumer_receipt WHERE tenant_id=?", Integer.class, tenant));
    }

    private static JsonNode verifyCompletionLeases(HttpClient http, String origin, String context, JdbcTemplate jdbc, String tenant,
            String upload, String operation, JsonNode command, String path, String key, JsonNode completion, byte[] accepted) throws Exception {
        var call = JSON.createObjectNode().put("upload_id", upload).put("operation_id", operation);
        String endpoint = "/api/source-completion-lease-native";
        var first = success(post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "CLAIM_A")), 200);
        assertEquals("DELIVERING", first.required("status").textValue()); assertEquals("1", first.required("lease_generation").asText());
        assertEquals("1", first.required("attempt_no").asText()); assertEquals("2", first.required("revision").asText());
        assertFalse(first.required("recover_existing").booleanValue());
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(new org.erdtman.jcs.JsonCanonicalizer(JSON.writeValueAsBytes(command)).getEncodedUTF8());
        assertEquals(digest, first.required("command_sha256").textValue());
        assertEquals(409, post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "CLAIM_B")).statusCode());
        var renewed = success(post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "RENEW_A")), 200);
        assertEquals(first.required("lease_owner"), renewed.required("lease_owner")); assertEquals("3", renewed.required("revision").asText());
        assertEquals(first.required("attempt_no"), renewed.required("attempt_no")); assertEquals(first.required("lease_generation"), renewed.required("lease_generation"));
        var old = post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "RENEW_OLD_A"));
        assertEquals(409, old.statusCode()); assertEquals("LEASE_STALE", JSON.readTree(old.body()).required("code").textValue());
        var rounds = JSON.createArrayNode(); rounds.add(first); rounds.add(renewed);
        for (int generation = 2; generation <= 6; generation++) {
            org.awaitility.Awaitility.await().pollInterval(java.time.Duration.ofMillis(250)).atMost(java.time.Duration.ofSeconds(35)).until(() ->
                jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE tenant_id=? AND event_id=? AND consumer_id='SOURCE_UPLOAD_COMPLETION' AND lease_until<=UTC_TIMESTAMP(6)", Integer.class, tenant, command.required("event_id").textValue()) == 1);
            var next = success(post(http, origin, endpoint, context, id(), call.deepCopy().put("action", generation % 2 == 0 ? "CLAIM_B" : "CLAIM_A")), 200);
            assertEquals(Integer.toString(generation), next.required("lease_generation").asText());
            assertEquals(Integer.toString(generation), next.required("attempt_no").asText());
            assertTrue(next.required("recover_existing").booleanValue()); assertEquals(digest, next.required("command_sha256").textValue());
            assertNotEquals(rounds.get(rounds.size()-1).required("lease_owner"), next.required("lease_owner"));
            var stale = post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "RENEW_OLD_A"));
            assertEquals(409, stale.statusCode()); assertEquals("LEASE_STALE", JSON.readTree(stale.body()).required("code").textValue());
            assertArrayEquals(accepted, post(http, origin, path, context, key, completion).body());
            assertEquals(Integer.valueOf(generation), jdbc.queryForObject("SELECT lease_generation FROM outbox_delivery WHERE tenant_id=?", Integer.class, tenant));
            rounds.add(next);
        }
        org.awaitility.Awaitility.await().pollInterval(java.time.Duration.ofMillis(250)).atMost(java.time.Duration.ofSeconds(35)).until(() ->
            jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE tenant_id=? AND lease_until<=UTC_TIMESTAMP(6)", Integer.class, tenant) == 1);
        assertEquals(409, post(http, origin, endpoint, context, id(), call.deepCopy().put("action", "CLAIM_A")).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_delivery WHERE tenant_id=? AND attempt_no=6 AND lease_generation=6 AND revision=8", Integer.class, tenant));
        return rounds;
    }

    @Test void persistsCompleteSourceDeclarationsAndReplaysTheOriginalTransaction() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
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
        ObjectNode evidence = JSON.createObjectNode();
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class);
            var jdbc = new JdbcTemplate(source);
            long original = jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class);
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
            try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class));
                    var objects = new SourceUploadObjectChecks(root, data.tenant)) {
                data.enableSourceUploading();
                data.enableOtherTenantSelection();
                var grantDeadline = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)+INTERVAL 300 SECOND", java.time.LocalDateTime.class);
                jdbc.update("UPDATE permission_grant SET valid_to=? WHERE tenant_id=? AND action='source.write'", grantDeadline, data.tenant);
                String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200)
                    .required("current_context").required("context_revision").textValue();
                ObjectNode body = request(root, data.project, actor); String key = id();
                var created = post(http, origin, "/api/uploads", context, key, body);
                var receipt = success(created, 201);
                String upload = receipt.required("upload_id").textValue();
                assertEquals("8388608", receipt.required("max_part_bytes").textValue());
                assertEquals(grantDeadline.toInstant(java.time.ZoneOffset.UTC),
                    com.ruoyi.medical.contracts.WireValues.utcTime(receipt.required("expires_at").textValue()));
                assertEquals("1", JSON.readTree(created.body()).required("resource_revision").textValue());
                var reopened = success(read(http, origin, "/api/uploads/" + upload, context), 200);
                assertEquals(body, reopened.required("request"));
                assertEquals("0", reopened.required("received_size").textValue());
                assertEquals("PENDING", reopened.required("upload_status").textValue());
                assertTrue(reopened.required("parts").isEmpty());
                assertEquals(receipt.required("expires_at"), reopened.required("expires_at"));
                var replayed = post(http, origin, "/api/uploads", context, key, body);
                success(replayed, 201); assertArrayEquals(created.body(), replayed.body());
                var changed = body.deepCopy(); ((ObjectNode) changed.required("rights_declaration")).put("license", "SIMULATION 用途声明修订");
                assertEquals(409, post(http, origin, "/api/uploads", context, key, changed).statusCode());
                assertEquals(body, JSON.readTree(jdbc.queryForObject("SELECT request_json FROM source_upload_request WHERE tenant_id=? AND upload_id=?", String.class, data.tenant, upload)));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-01'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.created'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=?", Integer.class, data.tenant, upload));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM source_version WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rights_revision WHERE tenant_id=?", Integer.class, data.tenant));
                Path originalFile = root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx");
                byte[] originalBytes = Files.readAllBytes(originalFile);
                String partKey = body.required("sha256").textValue();
                var storedPart = putWithConcurrentReplay(http, origin, context, upload, partKey, originalBytes, root, jdbc, data.tenant);
                var partReceipt = success(storedPart, 200);
                assertEquals(1, partReceipt.required("part_no").intValue());
                assertEquals(body.required("size_bytes"), partReceipt.required("size_bytes"));
                assertEquals(body.required("sha256"), partReceipt.required("sha256"));
                assertEquals("STORED", partReceipt.required("state").textValue());
                var received = success(read(http, origin, "/api/uploads/" + upload, context), 200);
                assertEquals(body.required("size_bytes"), received.required("received_size"));
                assertEquals("UPLOADING", received.required("upload_status").textValue());
                assertEquals(1, received.required("parts").size());
                assertEquals(partReceipt, received.required("parts").get(0));
                var partReplay = put(http, origin, "/api/uploads/" + upload + "/parts/1", context,
                    body.required("sha256").textValue(), originalBytes);
                assertEquals(partReceipt, success(partReplay, 200));
                assertArrayEquals(storedPart.body(), partReplay.body());
                verifyCompletedConcurrentReplay(http, origin, context, upload, partKey, originalBytes, storedPart.body(), root, jdbc, data.tenant);
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='COMPLETED' AND http_status=200", Integer.class, data.tenant, partKey));
                assertEquals(JSON.readTree(storedPart.body()), JSON.readTree(jdbc.queryForObject("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=?", String.class, data.tenant, partKey)));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant, upload));
                var lookup = success(post(http, origin, "/api/operation-lookups", context, id(),
                    JSON.createObjectNode().put("operation_id", "KN-02").put("idempotency_key", partKey)), 200);
                assertEquals("FOUND", lookup.required("lookup_status").textValue());
                var originalOperation = lookup.required("operation");
                assertEquals("COMPLETED", originalOperation.required("status").textValue());
                assertEquals(upload, originalOperation.required("resource_id").textValue());
                assertEquals(originalOperation, success(read(http, origin, originalOperation.required("status_url").textValue(), context), 200));
                var secondUpload = success(post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("batch_id", id())), 201)
                    .required("upload_id").textValue();
                assertEquals(409, put(http, origin, "/api/uploads/" + secondUpload + "/parts/1", context, partKey,
                    body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, data.tenant, secondUpload));
                putAfterInterruptedReception(http, origin, context, secondUpload, id(), originalBytes, root, jdbc, data.tenant);
                objects.verify(jdbc, data.tenant, secondUpload, originalBytes);
                assertEquals(422, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, null,
                    body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(422, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, "short-key",
                    body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, data.tenant, upload));
                objects.verify(jdbc, data.tenant, upload, originalBytes);
                String otherSha = com.ruoyi.medical.contracts.WireValues.sha256(Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/safety-principles-2021.doc")));
                assertEquals(409, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, partKey, otherSha, originalBytes).statusCode());
                assertEquals(409, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, otherSha, originalBytes).statusCode());
                assertEquals(422, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, body.required("sha256").textValue(), new byte[0]).statusCode());
                assertEquals(409, put(http, origin, "/api/uploads/" + upload + "/parts/1", "0", body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(origin + "/api/uploads/" + upload + "/parts/1"))
                    .header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context).header("Content-SHA256", body.required("sha256").textValue())
                    .header("Origin", "http://127.0.0.1:18080").header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(originalBytes)).build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode());
                assertEquals(409, post(http, origin, "/api/uploads", "0", id(), body).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("project_id", data.otherProject)).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("ownership", "ENTERPRISE")).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("ownership", "SESSION").put("session_id", id())).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("version_intent", "NEW_VERSION").put("source_id", id())).statusCode());
                assertEquals(422, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("approved", true)).statusCode());
                assertEquals(413, post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("size_bytes", "104857601")).statusCode());
                var unsupported = post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("media_type", "text/plain"));
                assertEquals(415, unsupported.statusCode()); assertEquals("FILE_TYPE_UNSUPPORTED", JSON.readTree(unsupported.body()).required("code").textValue());
                assertEquals(403, http.send(HttpRequest.newBuilder(URI.create(origin + "/api/uploads"))
                    .header("X-Schema-Version", "1.0.0").header("X-Context-Revision", context).header("Idempotency-Key", id())
                    .header("Origin", "http://127.0.0.1:18080").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode());
                jdbc.update("UPDATE tenant SET auth_epoch=auth_epoch+1 WHERE tenant_id=?", data.tenant);
                assertEquals(409, read(http, origin, "/api/uploads/" + upload, context).statusCode());
                assertEquals(409, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(409, post(http, origin, "/api/uploads", context, key, body).statusCode());
                var refreshed = success(post(http, origin, "/api/session/tenant-selections", context, id(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", context)), 200);
                context = refreshed.required("current_context").required("context_revision").textValue();
                assertArrayEquals(created.body(), post(http, origin, "/api/uploads", context, key, body).body());
                assertArrayEquals(storedPart.body(), put(http, origin, "/api/uploads/" + upload + "/parts/1", context,
                    body.required("sha256").textValue(), originalBytes).body());
                var other = success(post(http, origin, "/api/session/tenant-selections", context, id(),
                    JSON.createObjectNode().put("tenant_id", data.otherTenant).put("expected_context_revision", context)), 200);
                context = other.required("current_context").required("context_revision").textValue();
                assertEquals(403, read(http, origin, "/api/uploads/" + upload, context).statusCode());
                assertEquals(403, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, body.required("sha256").textValue(), originalBytes).statusCode());
                var returned = success(post(http, origin, "/api/session/tenant-selections", context, id(),
                    JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", context)), 200);
                context = returned.required("current_context").required("context_revision").textValue();
                assertArrayEquals(created.body(), post(http, origin, "/api/uploads", context, key, body).body());
                jdbc.update("UPDATE permission_grant SET valid_to=UTC_TIMESTAMP(6) WHERE tenant_id=? AND action='source.write'", data.tenant);
                assertEquals(403, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, key, body).statusCode());
                assertEquals(403, read(http, origin, "/api/uploads/" + upload, context).statusCode());
                jdbc.update("UPDATE permission_grant SET valid_to=? WHERE tenant_id=? AND action='source.write'", grantDeadline, data.tenant);
                String revokedUpload = success(post(http, origin, "/api/uploads", context, id(), body.deepCopy().put("batch_id", id())), 201)
                    .required("upload_id").textValue();
                verifyRevocationDuringReception(http, origin, context, revokedUpload, originalBytes, root, jdbc, data.tenant);
                objects.verifyAbsent(revokedUpload);
                assertEquals(403, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, body.required("sha256").textValue(), originalBytes).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, id(), body).statusCode());
                assertEquals(403, post(http, origin, "/api/uploads", context, key, body).statusCode());
                assertEquals(403, read(http, origin, "/api/uploads/" + upload, context).statusCode());
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item WHERE tenant_id=?", Integer.class, data.tenant));
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02'", Integer.class, data.tenant));
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant));
                evidence.put("data_kind", "SIMULATION"); evidence.put("declaration_save_reopen_verified", true);
                evidence.put("idempotency_audit_outbox_verified", true); evidence.put("current_authority_and_cross_tenant_denial_verified", true);
                evidence.put("csrf_and_stale_watermark_denial_verified", true);
                evidence.put("grant_deadline_and_expiry_denial_verified", true);
                evidence.put("professional_approval_created", false);
                evidence.put("source_part_save_reopen_and_original_object_verified", true);
                evidence.put("source_part_conflict_size_csrf_watermark_and_authority_denial_verified", true);
                evidence.put("source_part_original_operation_response_audit_outbox_verified", true);
                evidence.put("source_part_original_operation_lookup_and_request_identity_denial_verified", true);
                evidence.put("source_part_processing_concurrent_original_operation_verified", true);
                evidence.put("source_part_completed_concurrent_original_response_verified", true);
                evidence.put("source_part_interrupted_original_operation_resume_verified", true);
                evidence.put("source_part_inflight_revocation_stops_without_storing_verified", true);
            }
            assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class));
            evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("upload_counts_unchanged", true);
        }
        assertFalse(application.isActive());
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos(); boolean closed;
        do {
            closed = Thread.getAllStackTraces().keySet().stream().noneMatch(thread -> thread.isAlive()
                && (thread.getName().startsWith("source-upload-receiver-") || thread.getName().startsWith("source-upload-admission-")
                    || thread.getName().startsWith("source-upload-deadlines-")));
            if (!closed) Thread.sleep(50);
        } while (!closed && System.nanoTime() < deadline);
        assertTrue(closed, "应用关闭后接收线程仍在运行");
        evidence.put("source_part_application_transfer_threads_closed_verified", true);
        evidence.put("temporary_application_closed", true); evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")), JSON.writeValueAsBytes(evidence));
    }

    @Test void recoversOriginalPartAfterActualS3ResponseInterruption() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var token = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var evidence = JSON.createObjectNode();
        try (var network = new SourceUploadNetworkInterruption(root)) {
            network.start();
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
                    "--server.shutdown=immediate", "--spring.main.banner-mode=off",
                    "--medical.source-upload-transport.storage-directory=" + network.storage));
            try (application) {
                var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
                long original = jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class);
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
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class));
                        var objects = new SourceUploadObjectChecks(root, data.tenant)) {
                    data.enableSourceUploading();
                    String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(),
                        JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200)
                        .required("current_context").required("context_revision").textValue();
                    ObjectNode body = request(root, data.project, actor); String key = id();
                    String upload = success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue();
                    byte[] bytes = Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"));
                    network.interruptResponses();
                    var sending = java.util.concurrent.Executors.newSingleThreadExecutor();
                    try {
                        var response = sending.submit(() -> put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key,
                            body.required("sha256").textValue(), bytes));
                        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                            jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=? AND state='WRITING'", Integer.class, data.tenant, upload) == 1);
                        var writing = jdbc.queryForMap("SELECT write_id,object_key,revision FROM object_upload_part WHERE tenant_id=? AND upload_id=?", data.tenant, upload);
                        String objectKey = (String) writing.get("object_key");
                        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> objects.originalStored(objectKey));
                        String version = objects.originalVersion(objectKey);
                        assertEquals(500, response.get(50, java.util.concurrent.TimeUnit.SECONDS).statusCode());
                        assertEquals(writing, jdbc.queryForMap("SELECT write_id,object_key,revision FROM object_upload_part WHERE tenant_id=? AND upload_id=?", data.tenant, upload));
                        assertEquals("0", success(read(http, origin, "/api/uploads/" + upload, context), 200).required("received_size").textValue());
                        var originalOperation = success(post(http, origin, "/api/operation-lookups", context, id(),
                            JSON.createObjectNode().put("operation_id", "KN-02").put("idempotency_key", key)), 200).required("operation");
                        assertEquals("PROCESSING", originalOperation.required("status").textValue());
                        network.restoreResponses();
                        String conflictingSha = com.ruoyi.medical.contracts.WireValues.sha256(Files.readAllBytes(
                            root.resolve("ui-delivery/assets/public-evidence/safety-principles-2021.doc")));
                        assertEquals(409, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key, conflictingSha, bytes).statusCode());
                        assertEquals(writing, jdbc.queryForMap("SELECT write_id,object_key,revision FROM object_upload_part WHERE tenant_id=? AND upload_id=?", data.tenant, upload));
                        assertEquals(version, objects.originalVersion(objectKey));
                        var recovered = put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key,
                            body.required("sha256").textValue(), bytes);
                        assertEquals("STORED", success(recovered, 200).required("state").textValue());
                        assertEquals(writing.get("write_id"), jdbc.queryForObject("SELECT write_id FROM object_upload_part WHERE tenant_id=? AND upload_id=?", String.class, data.tenant, upload));
                        assertEquals(objectKey, jdbc.queryForObject("SELECT object_key FROM object_upload_part WHERE tenant_id=? AND upload_id=?", String.class, data.tenant, upload));
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, data.tenant, upload));
                        assertEquals(version, objects.originalVersion(objectKey)); objects.verify(jdbc, data.tenant, upload, bytes);
                        var received = success(read(http, origin, "/api/uploads/" + upload, context), 200);
                        assertEquals(body.required("size_bytes"), received.required("received_size"));
                        assertEquals("UPLOADING", received.required("upload_status").textValue()); assertEquals(1, received.required("parts").size());
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='COMPLETED'", Integer.class, data.tenant, key));
                        var finished = success(read(http, origin, originalOperation.required("status_url").textValue(), context), 200);
                        assertEquals(originalOperation.required("operation_id"), finished.required("operation_id"));
                        assertEquals("COMPLETED", finished.required("status").textValue());
                        assertArrayEquals(recovered.body(), put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key,
                            body.required("sha256").textValue(), bytes).body());
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant));
                        try (var files = Files.list(network.storage.resolve("source-upload-spool"))) { assertEquals(0, files.count()); }
                        evidence.put("original_write_id", (String) writing.get("write_id")); evidence.put("original_object_version", version);
                        evidence.put("actual_s3_response_interruption_verified", true); evidence.put("original_operation_and_object_recovery_verified", true);
                        evidence.put("recovery_identity_conflict_denial_verified", true); evidence.put("recovery_progress_and_private_cleanup_verified", true);
                    } finally { sending.shutdownNow(); assertTrue(sending.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)); }
                }
                assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class));
                evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("upload_counts_unchanged", true);
            }
            assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        }
        evidence.put("data_kind", "SIMULATION"); evidence.put("customer_success_verified", false); evidence.put("professional_approval_created", false);
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling("source-upload-recovery-result.json"), JSON.writeValueAsBytes(evidence));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 4})
    void rejectsExhaustedConfiguredCapacityWithoutWaitingForExistingInput(int tenantPermits) throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var token = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var evidence = JSON.createObjectNode();
        int duration = tenantPermits == 1 ? 20 : 60;
        evidence.put("capacity_scope", tenantPermits == 1 ? "TENANT" : "USER");
        try (var network = new SourceUploadNetworkInterruption(root)) {
            network.start();
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
                    "--server.shutdown=immediate", "--spring.main.banner-mode=off",
                    "--medical.source-upload-transport.storage-directory=" + network.storage,
                    "--medical.source-upload-transport.tenant-permits=" + tenantPermits, "--medical.source-upload-transport.user-permits=2",
                    "--medical.source-upload-transport.maximum-duration=" + duration + "s",
                    "--medical.source-upload-transport.namespace=medical:source-capacity:" + id(),
                    "--medical.source-upload-transport.revision=" + id()));
            try (application) {
                var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
                long original = jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class);
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
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class));
                        var objects = new SourceUploadObjectChecks(root, data.tenant)) {
                    data.enableSourceUploading();
                    String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(),
                        JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200)
                        .required("current_context").required("context_revision").textValue();
                    ObjectNode body = request(root, data.project, actor);
                    String first = success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue();
                    String second = success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue();
                    var activeUploads = new java.util.ArrayList<String>(); activeUploads.add(first);
                    if (tenantPermits == 4)
                        activeUploads.add(success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue());
                    byte[] bytes = Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"));
                    String secondKey = id();
                    var paused = new java.util.ArrayList<PausedPart>();
                    long started = System.nanoTime();
                    var sending = java.util.concurrent.Executors.newSingleThreadExecutor();
                    try {
                        for (var upload : activeUploads) paused.add(startPausedPart(http, origin, context, upload, id(), bytes));
                        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> {
                            try (var files = Files.list(network.storage.resolve("source-upload-spool"))) {
                                return files.filter(file -> org.springframework.util.function.ThrowingSupplier.of(() -> Files.size(file) >= 1024).get()).count() == activeUploads.size();
                            }
                        });
                        for (var part : paused) assertFalse(part.receiving().isDone());
                        capacityPhase(evidence, "incoming", started);
                        var rejected = sending.submit(() -> put(http, origin, "/api/uploads/" + second + "/parts/1", context,
                            secondKey, body.required("sha256").textValue(), bytes));
                        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
                        while (!rejected.isDone() && System.nanoTime() < deadline) Thread.sleep(25);
                        assertTrue(rejected.isDone(), "接收名额耗尽的请求必须明确拒绝，当前请求仍在等待另一上传输入");
                        var denied = rejected.get(1, java.util.concurrent.TimeUnit.SECONDS);
                        assertEquals(429, denied.statusCode()); assertEquals("RATE_LIMITED", JSON.readTree(denied.body()).required("code").textValue());
                        capacityPhase(evidence, "denied", started);
                        for (var part : paused) assertFalse(part.receiving().isDone());
                        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=?", Integer.class, data.tenant));
                        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                        for (var upload : activeUploads) objects.verifyAbsent(upload);
                        objects.verifyAbsent(second);
                        capacityPhase(evidence, "objects_absent", started);
                        var pending = success(post(http, origin, "/api/operation-lookups", context, id(),
                            JSON.createObjectNode().put("operation_id", "KN-02").put("idempotency_key", secondKey)), 200).required("operation");
                        assertEquals("PROCESSING", pending.required("status").textValue());
                        capacityPhase(evidence, "original_operation", started);
                        for (var part : paused) part.resume().complete(null);
                        for (var part : paused) success(part.receiving().get(duration + 10L, java.util.concurrent.TimeUnit.SECONDS), 200);
                        capacityPhase(evidence, "first_stored", started);
                        var stored = put(http, origin, "/api/uploads/" + second + "/parts/1", context, secondKey, body.required("sha256").textValue(), bytes);
                        assertEquals("STORED", success(stored, 200).required("state").textValue());
                        var completed = success(read(http, origin, pending.required("status_url").textValue(), context), 200);
                        assertEquals(pending.required("operation_id"), completed.required("operation_id"));
                        assertEquals("COMPLETED", completed.required("status").textValue());
                        for (var upload : activeUploads) objects.verify(jdbc, data.tenant, upload, bytes);
                        objects.verify(jdbc, data.tenant, second, bytes);
                        assertEquals(activeUploads.size() + 1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                        assertEquals(activeUploads.size() + 1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant));
                        try (var files = Files.list(network.storage.resolve("source-upload-spool"))) { assertEquals(0, files.count()); }
                        evidence.put(tenantPermits == 1 ? "tenant_capacity_denial_before_input_completion_verified" : "user_capacity_denial_before_input_completion_verified", true);
                        evidence.put("rejected_original_operation_resume_verified", true);
                    } finally {
                        for (var part : paused) part.resume().complete(null);
                        for (var part : paused) part.receiving().get(duration + 10L, java.util.concurrent.TimeUnit.SECONDS);
                        sending.shutdown(); assertTrue(sending.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
                    }
                }
                assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class));
                evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("upload_counts_unchanged", true);
            }
            assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        }
        evidence.put("tenant_permits", tenantPermits); evidence.put("user_permits", 2); evidence.put("maximum_duration_seconds", duration);
        evidence.put("data_kind", "SIMULATION"); evidence.put("customer_success_verified", false); evidence.put("professional_approval_created", false);
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling(
            tenantPermits == 1 ? "source-upload-capacity-result.json" : "source-upload-user-capacity-result.json"), JSON.writeValueAsBytes(evidence));
    }

    @Test void expiresStoppedActualInputAndResumesTheOriginalOperation() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var token = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var evidence = JSON.createObjectNode();
        try (var network = new SourceUploadNetworkInterruption(root)) {
            network.start();
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
                    "--server.shutdown=immediate", "--spring.main.banner-mode=off",
                    "--medical.source-upload-transport.storage-directory=" + network.storage,
                    "--medical.source-upload-transport.tenant-permits=4", "--medical.source-upload-transport.user-permits=2",
                    "--medical.source-upload-transport.maximum-duration=30s",
                    "--medical.source-upload-transport.namespace=medical:source-deadline:" + id(),
                    "--medical.source-upload-transport.revision=" + id()));
            try (application) {
                var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
                long original = jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class);
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
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class));
                        var objects = new SourceUploadObjectChecks(root, data.tenant)) {
                    data.enableSourceUploading();
                    String context = success(post(http, origin, "/api/session/tenant-selections", "0", id(),
                        JSON.createObjectNode().put("tenant_id", data.tenant).put("expected_context_revision", "0")), 200)
                        .required("current_context").required("context_revision").textValue();
                    ObjectNode body = request(root, data.project, actor);
                    String upload = success(post(http, origin, "/api/uploads", context, id(), body), 201).required("upload_id").textValue();
                    byte[] bytes = Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"));
                    String key = id(); long started = System.nanoTime();
                    var paused = startPausedPart(http, origin, context, upload, key, bytes);
                    try {
                        Path spool = network.storage.resolve("source-upload-spool");
                        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> {
                            try (var files = Files.list(spool)) {
                                return files.anyMatch(file -> org.springframework.util.function.ThrowingSupplier.of(() -> Files.size(file) >= 1024).get());
                            }
                        });
                        assertFalse(paused.receiving().isDone());
                        var pending = success(post(http, origin, "/api/operation-lookups", context, id(),
                            JSON.createObjectNode().put("operation_id", "KN-02").put("idempotency_key", key)), 200).required("operation");
                        assertEquals("PROCESSING", pending.required("status").textValue());
                        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(40)).until(() -> {
                            try (var files = Files.list(spool)) { return files.findAny().isEmpty(); }
                        });
                        long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                        assertTrue(elapsed >= 30000 && elapsed < 40000, "公开上传必须按原总期限终止实际输入");
                        evidence.put("expired_elapsed_ms", elapsed);
                        paused.resume().complete(null);
                        var expired = paused.receiving().get(40, java.util.concurrent.TimeUnit.SECONDS);
                        assertEquals(409, expired.statusCode()); assertEquals("LEASE_STALE", JSON.readTree(expired.body()).required("code").textValue());
                        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, data.tenant, upload));
                        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant));
                        var reopened = success(read(http, origin, "/api/uploads/" + upload, context), 200);
                        assertEquals("0", reopened.required("received_size").textValue()); assertTrue(reopened.required("parts").isEmpty());
                        objects.verifyAbsent(upload);
                        var originalOperation = success(read(http, origin, pending.required("status_url").textValue(), context), 200);
                        assertEquals(pending.required("operation_id"), originalOperation.required("operation_id"));
                        assertEquals("PROCESSING", originalOperation.required("status").textValue());
                        var stored = put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key, body.required("sha256").textValue(), bytes);
                        assertEquals("STORED", success(stored, 200).required("state").textValue());
                        var completed = success(read(http, origin, pending.required("status_url").textValue(), context), 200);
                        assertEquals(pending.required("operation_id"), completed.required("operation_id"));
                        assertEquals("COMPLETED", completed.required("status").textValue());
                        var replay = put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key, body.required("sha256").textValue(), bytes);
                        success(replay, 200); assertArrayEquals(stored.body(), replay.body());
                        objects.verify(jdbc, data.tenant, upload, bytes);
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, data.tenant));
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='source.upload.part.stored'", Integer.class, data.tenant));
                        try (var files = Files.list(spool)) { assertEquals(0, files.count()); }
                        evidence.put("stopped_actual_input_deadline_and_cleanup_verified", true);
                        evidence.put("expired_original_operation_resume_verified", true);
                        evidence.put("deadline_original_response_object_and_single_events_verified", true);
                    } finally {
                        paused.resume().complete(null); paused.receiving().get(40, java.util.concurrent.TimeUnit.SECONDS);
                    }
                }
                assertEquals(original, jdbc.queryForObject("SELECT COUNT(*) FROM upload_item", Long.class));
                evidence.put("actual_http_port", application.getWebServer().getPort()); evidence.put("upload_counts_unchanged", true);
            }
            assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        }
        evidence.put("tenant_permits", 4); evidence.put("user_permits", 2); evidence.put("maximum_duration_seconds", 30);
        evidence.put("data_kind", "SIMULATION"); evidence.put("customer_success_verified", false); evidence.put("professional_approval_created", false);
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling("source-upload-deadline-result.json"), JSON.writeValueAsBytes(evidence));
    }

    private static void capacityPhase(ObjectNode evidence, String phase, long started) throws Exception {
        evidence.put(phase + "_elapsed_ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling(
            "source-upload-capacity-diagnostic-" + evidence.required("capacity_scope").textValue() + ".json"), JSON.writeValueAsBytes(evidence));
    }

    private static ObjectNode request(Path root, String project, String actor) throws Exception {
        var dataset = JSON.readTree(Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        JsonNode source = null;
        for (var candidate : dataset.required("sources"))
            if ("official-clinical-2021-73-a4".equals(candidate.required("source_id").textValue())) source = candidate;
        assertNotNull(source);
        ObjectNode value = JSON.createObjectNode().put("schema_version", "1.0.0").put("batch_id", id())
            .put("project_id", project).put("ownership", "PROJECT").put("file_name", "clinical-evaluation-2021.docx")
            .put("media_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .put("size_bytes", source.required("size_bytes").bigIntegerValue().toString()).put("version_intent", "NEW_SOURCE");
        value.set("sha256", source.required("sha256")); value.set("classification", source.required("sensitivity"));
        var provenance = value.putObject("provenance"); provenance.set("owner", source.required("owner"));
        provenance.set("publisher", source.required("owner")); provenance.put("maintainer_id", actor);
        provenance.set("original_url", source.required("official_url")); provenance.set("final_url", source.required("official_url"));
        provenance.set("publisher_version", source.required("publisher_version")); provenance.putArray("status_evidence");
        for (var field : List.of("published_at", "issued_on", "effective_from", "effective_to", "observed_status", "verified_at",
                "acquired_at", "acquired_by", "acquisition_method", "update_frequency", "next_check_at", "retention_policy_id", "legal_hold"))
            provenance.putNull(field);
        var rights = value.putObject("rights_declaration"); rights.put("purpose", "LOCAL_PARSER_CHECK"); rights.set("license", source.required("license"));
        rights.putArray("allowed_actions").add("READ").add("STORE"); rights.putArray("providers"); rights.putArray("regions");
        rights.putNull("valid_from"); rights.putNull("valid_to"); rights.putArray("authorization_evidence"); return value;
    }

    private static HttpResponse<byte[]> read(HttpClient http, String origin, String path, String context) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> post(HttpClient http, String origin, String path, String context, String key, JsonNode body) throws Exception {
        var token = read(http, origin, "/auth/csrf", context); assertEquals(200, token.statusCode());
        var csrf = JSON.readTree(token.body());
        return http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).header("Idempotency-Key", key).header("Origin", "http://127.0.0.1:18080")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> put(HttpClient http, String origin, String path, String context, String sha256, byte[] content) throws Exception {
        return put(http, origin, path, context, sha256, sha256, content);
    }
    private static HttpResponse<byte[]> put(HttpClient http, String origin, String path, String context, String key, String sha256, byte[] content) throws Exception {
        var token = read(http, origin, "/auth/csrf", context); assertEquals(200, token.statusCode());
        var csrf = JSON.readTree(token.body());
        var builder = HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).header("Content-SHA256", sha256).header("Origin", "http://127.0.0.1:18080")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Content-Type", "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        if (key != null) builder.header("Idempotency-Key", key);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"));
        assertEquals(List.of("1.0.0"), response.headers().allValues("X-Schema-Version"));
        return JSON.readTree(response.body()).required("data");
    }
    private static HttpResponse<byte[]> putWithConcurrentReplay(HttpClient http, String origin, String context, String upload,
            String key, byte[] content, Path root, JdbcTemplate jdbc, String tenant) throws Exception {
        String path = "/api/uploads/" + upload + "/parts/1";
        var paused = startPausedPart(http, origin, context, upload, key, content);
        var receiving = paused.receiving();
        {
            try {
                // 暂停发送登记原件的剩余字节，观察实际在途请求及公开重复提交。
                awaitIncomingPart(root, true); assertFalse(receiving.isDone());
                String operation = jdbc.queryForObject("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='PROCESSING'", String.class, tenant, key);
                var pending = success(put(http, origin, path, context, key, key, content), 202);
                assertEquals(operation, pending.required("operation_id").textValue());
                assertEquals(upload, pending.required("resource_id").textValue());
                assertEquals("PROCESSING", pending.required("status").textValue());
                assertEquals("/api/operations/" + operation, pending.required("status_url").textValue());
                assertEquals(pending, success(read(http, origin, pending.required("status_url").textValue(), context), 200));
                var lookup = success(post(http, origin, "/api/operation-lookups", context, id(),
                    JSON.createObjectNode().put("operation_id", "KN-02").put("idempotency_key", key)), 200);
                assertEquals("FOUND", lookup.required("lookup_status").textValue()); assertEquals(pending, lookup.required("operation"));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, tenant, upload));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, tenant));
                assertEquals("0", success(read(http, origin, "/api/uploads/" + upload, context), 200).required("received_size").textValue());
                String otherSha = com.ruoyi.medical.contracts.WireValues.sha256(Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/safety-principles-2021.doc")));
                assertEquals(409, put(http, origin, path, context, key, otherSha, content).statusCode());
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02'", Integer.class, tenant));
            } finally {
                paused.resume().complete(null);
                receiving.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            return receiving.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private static void putAfterInterruptedReception(HttpClient http, String origin, String context, String upload, String key,
            byte[] content, Path root, JdbcTemplate jdbc, String tenant) throws Exception {
        var csrf = JSON.readTree(read(http, origin, "/auth/csrf", context).body());
        var address = URI.create(origin + "/api/uploads/" + upload + "/parts/1");
        var request = new org.apache.http.client.methods.HttpPut(address);
        request.setHeader("X-Schema-Version", "1.0.0"); request.setHeader("X-Context-Revision", context);
        request.setHeader("Idempotency-Key", key); request.setHeader("Content-SHA256", com.ruoyi.medical.contracts.WireValues.sha256(content));
        request.setHeader("Origin", "http://127.0.0.1:18080");
        request.setHeader(csrf.required("header_name").textValue(), csrf.required("token").textValue());
        for (var header : http.cookieHandler().orElseThrow().get(address, Map.of()).entrySet())
            for (var value : header.getValue()) request.addHeader(header.getKey(), value);
        var sender = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var channel = org.apache.http.impl.client.HttpClients.createMinimal();
                var remaining = new java.io.PipedInputStream();
                var remainder = new java.io.PipedOutputStream(remaining);
                var input = new java.io.SequenceInputStream(new java.io.ByteArrayInputStream(content, 0, 16384), remaining)) {
            request.setEntity(new org.apache.http.entity.InputStreamEntity(input, content.length, org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM));
            var receiving = sender.submit(() -> { try (var response = channel.execute(request)) { return response.getStatusLine().getStatusCode(); } });
            awaitIncomingPart(root, true); assertFalse(receiving.isDone());
            String operation = jdbc.queryForObject("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='PROCESSING'", String.class, tenant, key);
            // 实际请求终止关闭HTTP连接；暂停的登记原件输入随后关闭并核验真实IO异常。
            request.abort(); assertTrue(request.isAborted()); remainder.close();
            var interrupted = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> receiving.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(java.io.IOException.class, interrupted.getCause());
            awaitIncomingPart(root, false);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, tenant, upload));
            assertEquals("0", success(read(http, origin, "/api/uploads/" + upload, context), 200).required("received_size").textValue());
            var pending = success(read(http, origin, "/api/operations/" + operation, context), 200);
            assertEquals("PROCESSING", pending.required("status").textValue()); assertEquals(upload, pending.required("resource_id").textValue());
            String sha = com.ruoyi.medical.contracts.WireValues.sha256(content);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            HttpResponse<byte[]> completed;
            do {
                completed = put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key, sha, content);
                if (completed.statusCode() == 202) {
                    assertEquals(operation, success(completed, 202).required("operation_id").textValue()); Thread.sleep(50);
                }
            } while (completed.statusCode() == 202 && System.nanoTime() < deadline);
            assertEquals("STORED", success(completed, 200).required("state").textValue());
            assertEquals("COMPLETED", success(read(http, origin, "/api/operations/" + operation, context), 200).required("status").textValue());
            assertEquals(operation, jdbc.queryForObject("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=?", String.class, tenant, key));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, tenant, upload));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='source.upload.part.stored'", Integer.class, tenant, upload));
            assertArrayEquals(completed.body(), put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key, sha, content).body());
        } finally {
            request.abort(); sender.shutdownNow(); assertTrue(sender.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static void verifyCompletedConcurrentReplay(HttpClient http, String origin, String context, String upload, String key,
            byte[] content, byte[] originalResponse, Path root, JdbcTemplate jdbc, String tenant) throws Exception {
        var replay = startPausedPart(http, origin, context, upload, key, content);
        try {
            // 已保存分段的实际重复请求仍占用接收名额，原操作保持COMPLETED。
            awaitIncomingPart(root, true); assertFalse(replay.receiving().isDone());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='COMPLETED'", Integer.class, tenant, key));
            var completed = put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key,
                com.ruoyi.medical.contracts.WireValues.sha256(content), content);
            success(completed, 200); assertArrayEquals(originalResponse, completed.body());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, tenant, upload));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='source.upload.part.stored'", Integer.class, tenant));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='source.upload.part.stored'", Integer.class, tenant, upload));
        } finally {
            replay.resume().complete(null);
            var original = replay.receiving().get(30, java.util.concurrent.TimeUnit.SECONDS);
            success(original, 200); assertArrayEquals(originalResponse, original.body());
            awaitIncomingPart(root, false);
        }
    }

    private record PausedPart(java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> receiving,
            java.util.concurrent.CompletableFuture<Void> resume) { }

    private static void verifyRevocationDuringReception(HttpClient http, String origin, String context, String upload,
            byte[] content, Path root, JdbcTemplate jdbc, String tenant) throws Exception {
        String key = id(); var paused = startPausedPart(http, origin, context, upload, key, content);
        try {
            awaitIncomingPart(root, true); assertFalse(paused.receiving().isDone());
            String operation = jdbc.queryForObject("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='PROCESSING'", String.class, tenant, key);
            assertEquals(1, jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='source.write'", tenant));
            awaitIncomingPart(root, false); paused.resume().complete(null);
            var denied = paused.receiving().get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(403, denied.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(denied.body()).required("code").textValue());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM object_upload_part WHERE tenant_id=? AND upload_id=?", Integer.class, tenant, upload));
            assertEquals(0L, jdbc.queryForObject("SELECT received_size FROM upload_item WHERE tenant_id=? AND upload_id=?", Long.class, tenant, upload));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='source.upload.part.stored'", Integer.class, tenant, upload));
            assertEquals(operation, jdbc.queryForObject("SELECT operation_id FROM operation_record WHERE tenant_id=? AND operation_name='KN-02' AND idempotency_key=? AND state='PROCESSING'", String.class, tenant, key));
            assertEquals(403, put(http, origin, "/api/uploads/" + upload + "/parts/1", context, key,
                com.ruoyi.medical.contracts.WireValues.sha256(content), content).statusCode());
        } finally { paused.receiving().cancel(true); paused.resume().complete(null); }
    }

    private static PausedPart startPausedPart(HttpClient http, String origin, String context, String upload, String key, byte[] content) throws Exception {
        var csrf = JSON.readTree(read(http, origin, "/auth/csrf", context).body());
        var resume = new java.util.concurrent.CompletableFuture<Void>();
        var bytes = reactor.core.publisher.Flux.concat(reactor.core.publisher.Mono.just(java.nio.ByteBuffer.wrap(content, 0, 16384)),
            reactor.core.publisher.Mono.fromFuture(resume).thenReturn(java.nio.ByteBuffer.wrap(content, 16384, content.length - 16384)));
        var publisher = HttpRequest.BodyPublishers.fromPublisher(reactor.adapter.JdkFlowAdapter.publisherToFlowPublisher(bytes), content.length);
        var request = HttpRequest.newBuilder(URI.create(origin + "/api/uploads/" + upload + "/parts/1")).header("X-Schema-Version", "1.0.0")
            .header("X-Context-Revision", context).header("Idempotency-Key", key).header("Content-SHA256", com.ruoyi.medical.contracts.WireValues.sha256(content))
            .header("Origin", "http://127.0.0.1:18080").header("Content-Type", "application/octet-stream")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).PUT(publisher).build();
        return new PausedPart(http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()), resume);
    }

    private static void awaitIncomingPart(Path root, boolean active) throws Exception {
        Path spool = root.resolve(".implementation-work/runtime/object-storage/source-upload-spool");
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos(); boolean observed;
        do {
            try (var files = Files.list(spool)) {
                observed = active ? files.anyMatch(file -> org.springframework.util.function.ThrowingSupplier.of(() -> Files.size(file) >= 1024).get()) : files.findAny().isEmpty();
            }
            if (!observed) Thread.sleep(50);
        } while (!observed && System.nanoTime() < deadline);
        if (!observed) {
            var diagnostics = JSON.createObjectNode(); var threads = diagnostics.putArray("threads");
            for (var entry : Thread.getAllStackTraces().entrySet()) {
                var thread = entry.getKey();
                if (!thread.getName().startsWith("source-upload-")) continue;
                var item = threads.addObject(); item.put("name", thread.getName()); item.put("state", thread.getState().name());
                var calls = item.putArray("calls"); for (var call : entry.getValue()) calls.add(call.toString());
            }
            var files = diagnostics.putArray("files");
            try (var paths = Files.list(spool)) {
                for (var path : paths.toList()) files.addObject().put("name", path.getFileName().toString()).put("size_bytes", Files.size(path));
            }
            Files.write(Path.of(System.getProperty("medical.sourceUploadEvidence")).resolveSibling("source-upload-reception-diagnostic.json"), JSON.writeValueAsBytes(diagnostics));
        }
        assertTrue(observed, active ? "实际HTTP原件输入未进入接收目录" : "实际HTTP输入尚未终止并清理私有文件");
    }
    private static String id() { return UUID.randomUUID().toString(); }

    private static final class SourceUploadObjectChecks implements AutoCloseable {
        private final software.amazon.awssdk.services.s3.S3Client storage;
        private final String bucket, prefix;
        SourceUploadObjectChecks(Path root, String tenant) throws Exception {
            var directory = root.resolve(".implementation-work/runtime/object-storage");
            var deployment = JSON.readTree(Files.readAllBytes(directory.resolve("deployment.json")));
            var secret = JSON.readTree(Files.readAllBytes(directory.resolve("service-secret.json")));
            assertEquals(deployment.required("service_id"), secret.required("service_id"));
            bucket = deployment.required("bucket").textValue(); prefix = "tenants/" + tenant + "/uploads/";
            storage = software.amazon.awssdk.services.s3.S3Client.builder().endpointOverride(URI.create(deployment.required("endpoint").textValue()))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1).forcePathStyle(true)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(secret.required("access_key").textValue(), secret.required("secret_key").textValue())))
                .httpClientBuilder(software.amazon.awssdk.http.apache.ApacheHttpClient.builder().proxyConfiguration(
                    software.amazon.awssdk.http.apache.ProxyConfiguration.builder().useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()))
                .overrideConfiguration(options -> options.retryStrategy(software.amazon.awssdk.awscore.retry.AwsRetryStrategy.standardRetryStrategy()
                    .toBuilder().maxAttempts(1).build())).build();
        }
        void verify(JdbcTemplate jdbc, String tenant, String upload, byte[] original) throws Exception {
            var reference = JSON.readTree(jdbc.queryForObject("SELECT object_ref FROM object_upload_part WHERE tenant_id=? AND upload_id=? AND part_no=1", String.class, tenant, upload));
            var actual = storage.getObject(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder().bucket(bucket)
                .key(reference.required("key").textValue()).versionId(reference.required("version_id").textValue()).build(),
                software.amazon.awssdk.core.sync.ResponseTransformer.toBytes());
            assertArrayEquals(original, actual.asByteArray()); assertEquals("AES256", actual.response().serverSideEncryptionAsString());
            assertEquals(reference.required("version_id").textValue(), actual.response().versionId());
            assertEquals(reference.required("sha256").textValue(), com.ruoyi.medical.contracts.WireValues.sha256(actual.asByteArray()));
            assertEquals(1, storage.listObjectVersions(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder()
                .bucket(bucket).prefix(reference.required("key").textValue()).build()).versions().size());
        }
        boolean originalStored(String key) {
            assertTrue(key.startsWith(prefix));
            var versions = storage.listObjectVersions(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder().bucket(bucket).prefix(key).build());
            assertFalse(versions.isTruncated()); assertTrue(versions.deleteMarkers().isEmpty());
            return versions.versions().stream().anyMatch(version -> version.key().equals(key));
        }
        String originalVersion(String key) {
            assertTrue(key.startsWith(prefix));
            var versions = storage.listObjectVersions(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder().bucket(bucket).prefix(key).build());
            assertFalse(versions.isTruncated()); assertTrue(versions.deleteMarkers().isEmpty()); assertEquals(1, versions.versions().size());
            assertEquals(key, versions.versions().get(0).key()); return versions.versions().get(0).versionId();
        }
        void verifyAbsent(String upload) {
            var versions = storage.listObjectVersions(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder()
                .bucket(bucket).prefix(prefix + upload + "/").build());
            assertTrue(versions.versions().isEmpty()); assertTrue(versions.deleteMarkers().isEmpty()); assertFalse(versions.isTruncated());
        }
        @Override public void close() {
            try {
                for (var page : storage.listObjectVersionsPaginator(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder().bucket(bucket).prefix(prefix).build())) {
                    for (var version : page.versions()) storage.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                        .bucket(bucket).key(version.key()).versionId(version.versionId()).build());
                    for (var marker : page.deleteMarkers()) storage.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                        .bucket(bucket).key(marker.key()).versionId(marker.versionId()).build());
                }
                assertTrue(storage.listObjectVersions(software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.builder().bucket(bucket).prefix(prefix).build()).versions().isEmpty());
            } finally { storage.close(); }
        }
    }
}
