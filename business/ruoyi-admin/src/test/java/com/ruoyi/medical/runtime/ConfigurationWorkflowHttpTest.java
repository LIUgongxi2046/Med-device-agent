package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.InvitationCredential;
import com.ruoyi.medical.persistence.ConfigurationWorkflowSimulationData;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationAccountData;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real local HTTP/MySQL/Redis verification with fresh normal users and explicitly synthetic dependencies. */
public final class ConfigurationWorkflowHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    @Test void normalAuthorizedUsersSaveReadApproveAndDiscoverWithActualTransactions() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path config = Path.of(required("medical.configurationWorkflowConfig")).toRealPath();
        assertTrue(config.startsWith(root), "The additional configuration must belong to this isolated project");
        var service = secret(root, "service-secrets"); var management = secret(root, "local-management-secret");
        var invitation = secret(root, "invitation-response-secret"); var token = secret(root, "ruoyi-token-secret");
        var ownerAccount = secret(root, "owner-credentials"); var identity = secret(root, "service-identity");
        var arguments = new ArrayList<>(List.of("--spring.config.name=application-medical-local",
            "--spring.config.additional-location=" + config.toUri(), "--server.address=127.0.0.1", "--server.port=0",
            "--server.shutdown=immediate", "--spring.main.banner-mode=off",
            "--spring.session.redis.namespace=medical:configuration-workflow:" + UUID.randomUUID(),
            "--medical.templates.environment=LOCAL", "--medical.templates.region=LOCAL",
            "--medical.templates.maximum-versions=100", "--medical.templates.maximum-relations=200",
            "--medical.templates.artifact-profiles[0].role=DEPENDENCY",
            "--medical.templates.artifact-profiles[0].resource-kind=ARTIFACT",
            "--medical.templates.artifact-profiles[0].action=artifact.read",
            "--medical.templates.artifact-profiles[0].schema-ref=" + ConfigurationWorkflowSimulationData.textSchemaRef(),
            "--medical.templates.artifact-profiles[0].schema-document-sha256=" + ConfigurationWorkflowSimulationData.interfacesHash(),
            "--medical.templates.artifact-profiles[0].artifact-schema-manifest=" + ConfigurationWorkflowSimulationData.interfacesHash()));
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
            new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
                "MEDICAL_LOCAL_DATABASE_PASSWORD", service.required("database_password").textValue(),
                "MEDICAL_LOCAL_REDIS_PASSWORD", service.required("redis_password").textValue(),
                "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue())).run(arguments.toArray(String[]::new)));
        var evidence = JSON.createObjectNode(); evidence.put("data_kind", "SIMULATION");
        evidence.put("scope", "MODEL_RULE_DISCOVERY_REAL_LOCAL_HTTP_TRANSACTIONS");
        evidence.put("provider_calls", false); evidence.put("customer_approval", false); evidence.put("production_eligible", false);
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class); var jdbc = new JdbcTemplate(source);
            assertTrue(jdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
            assertEquals("medical_local", jdbc.queryForObject("SELECT DATABASE()", String.class));
            assertEquals(identity.required("server_uuid").textValue(), jdbc.queryForObject("SELECT @@server_uuid", String.class));
            assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN ('1.0.0-302-model-configuration-definition',"
                + "'1.0.0-305-rule-version-digest','1.0.0-306-rule-definition','1.0.0-307-rule-approval',"
                + "'1.0.0-308-evaluation-definition','1.0.0-309-evaluation-observation','1.0.0-310-evaluation-result')", Integer.class));
            var before = globalCounts(jdbc);
            String base = "http://127.0.0.1:" + application.getWebServer().getPort();
            var owner = login(base, ownerAccount);
            String ownerId = JSON.readTree(get(owner, base, "/auth/account", null).body()).required("user_id").textValue();
            try (var accounts = new SimulationAccountData(source, ownerId)) {
                var writerAccount = account(owner, base, accounts); var outsiderAccount = account(owner, base, accounts);
                var writer = login(base, writerAccount); var outsider = login(base, outsiderAccount);
                assertEquals("USER", JSON.readTree(get(writer, base, "/auth/account", null).body()).required("platform_role").textValue());
                assertEquals("USER", JSON.readTree(get(outsider, base, "/auth/account", null).body()).required("platform_role").textValue());
                try (var data = new ConfigurationWorkflowSimulationData(source, root, writerAccount.required("user_id").textValue(),
                        outsiderAccount.required("user_id").textValue(), application.getBean(PermissionSchema.class))) {
                    select(writer, base, data.tenant); select(outsider, base, data.otherTenant);
                    evidence.put("normal_user_authorization_verified", true);
                    verifyModels(writer, outsider, base, data, evidence);
                    verifyRules(writer, outsider, base, data, evidence);
                    data.revokeModelReading();
                    var list = success(get(writer, base, "/api/model-config-revisions", context(writer, base)), 200);
                    assertEquals(0, list.required("items").size()); assertTrue(list.required("next_after").isNull());
                    assertEquals(0L, data.releaseCount());
                    evidence.put("revoked_read_permission_filters_discovery", true);
                    evidence.put("no_release_records_created", true);
                }
            }
            assertEquals(before, globalCounts(jdbc), "All temporary tenants, users and domain rows must be removed with foreign keys enabled");
            evidence.put("owned_fixture_cleanup_verified", true);
            evidence.put("actual_http_port", application.getWebServer().getPort());
            evidence.put("database_server_uuid", identity.required("server_uuid").textValue());
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        Path output = Path.of(required("medical.configurationWorkflowEvidence"));
        Files.createDirectories(output.toAbsolutePath().getParent()); Files.write(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence));
    }

    private static void verifyModels(HttpClient writer, HttpClient outsider, String base,
            ConfigurationWorkflowSimulationData data, ObjectNode evidence) throws Exception {
        var first = data.modelCreate("alpha"); String initial = context(writer, base), key = id(); var before = data.counts();
        var created = post(writer, base, "/api/model-config-revisions", initial, key, first); var receipt = success(created, 201);
        assertEquals("DRAFT", receipt.required("status").textValue());
        delta(before, data.counts(), Map.of("model_configuration_definition", 1L, "model_config_revision", 1L, "configuration_head", 1L,
            "version_registry", 1L, "lineage_edge", 2L, "operation_record", 1L, "audit_event", 1L, "outbox_event", 1L));
        var after = data.counts();
        assertEquals(JSON.readTree(created.body()), JSON.readTree(post(writer, base, "/api/model-config-revisions", initial, key, first).body()));
        assertEquals(after, data.counts());
        error(post(writer, base, "/api/model-config-revisions", initial, key, first.deepCopy().put("reason", "SIMULATION different request")), 409, "IDEMPOTENCY_CONFLICT");
        assertEquals(after, data.counts());
        String version = receipt.required("model_config_revision").textValue();
        var read = success(get(writer, base, "/api/model-config-revisions/" + version, context(writer, base)), 200);
        assertEquals("REGISTRY_REFERENCE_ONLY", read.required("dependency_validation").textValue());
        assertFalse(read.toString().contains("secret://")); assertFalse(read.toString().contains("SIMULATION-NOT-AVAILABLE"));
        // The existing model controller maps unavailable fixed versions to HTTP 409.
        error(get(outsider, base, "/api/model-config-revisions/" + version, context(outsider, base)), 409, "OBJECT_UNAVAILABLE");
        var revision = data.modelAppend(first, receipt, "SIMULATION-v2");
        var second = success(post(writer, base, "/api/model-config-revisions", context(writer, base), id(), revision), 201);
        assertEquals("2", second.required("revision").textValue());
        var old = success(get(writer, base, "/api/model-config-revisions/" + version, context(writer, base)), 200);
        assertEquals("SIMULATION-v1", old.required("definition").required("model_version").textValue());
        assertEquals(second.required("model_config_revision"), old.required("current_model_revision"));
        after = data.counts();
        error(post(writer, base, "/api/model-config-revisions", context(writer, base), id(), revision), 409, "REVISION_CONFLICT");
        assertEquals(after, data.counts(), "Stale CAS must not leave version, audit, operation or outbox writes");

        // Two real transactions race for the same current session/head. Exactly one can commit the next revision.
        String raceContext = context(writer, base); var csrf = JSON.readTree(get(writer, base, "/auth/csrf", null).body());
        var a = request(base, "/api/model-config-revisions", raceContext, id(), data.modelAppend(first, second, "SIMULATION-v3-a"), csrf);
        var b = request(base, "/api/model-config-revisions", raceContext, id(), data.modelAppend(first, second, "SIMULATION-v3-b"), csrf);
        before = data.counts();
        var one = writer.sendAsync(a, HttpResponse.BodyHandlers.ofByteArray()); var two = writer.sendAsync(b, HttpResponse.BodyHandlers.ofByteArray());
        var responses = List.of(one.get(40, TimeUnit.SECONDS), two.get(40, TimeUnit.SECONDS));
        assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList(),
            "Concurrent writes must produce one commit and one conflict, never a partial second commit");
        delta(before, data.counts(), Map.of("model_configuration_definition", 1L, "model_config_revision", 1L,
            "version_registry", 1L, "lineage_edge", 3L, "operation_record", 1L, "audit_event", 1L, "outbox_event", 1L));
        success(post(writer, base, "/api/model-config-revisions", context(writer, base), id(), data.modelCreate("beta")), 201);
        String current = context(writer, base);
        var page = success(get(writer, base, "/api/model-config-revisions?limit=1", current), 200);
        assertEquals(1, page.required("items").size()); assertTrue(page.required("next_after").isTextual());
        var tail = success(get(writer, base, "/api/model-config-revisions?limit=1&after=" + encode(page.required("next_after").textValue()), current), 200);
        assertEquals(1, tail.required("items").size()); assertTrue(tail.required("next_after").isNull());
        assertNotEquals(page.required("items").get(0).required("config_id"), tail.required("items").get(0).required("config_id"));
        assertEquals(0, success(get(writer, base, "/api/model-config-revisions?q=%25", current), 200).required("items").size());
        assertEquals(1, success(get(writer, base, "/api/model-config-revisions?q=SIMULATION-model-alpha", current), 200).required("items").size());
        assertEquals(0, success(get(outsider, base, "/api/model-config-revisions", context(outsider, base)), 200).required("items").size());
        evidence.put("model_create_read_revision_replay_verified", true); evidence.put("model_secret_reference_redaction_verified", true);
        evidence.put("model_concurrent_cas_and_atomic_ledger_counts_verified", true);
        evidence.put("discovery_pagination_literal_search_and_cross_tenant_isolation_verified", true);
    }

    private static void verifyRules(HttpClient writer, HttpClient outsider, String base,
            ConfigurationWorkflowSimulationData data, ObjectNode evidence) throws Exception {
        String initial = context(writer, base), createKey = id(); var body = data.ruleCreate(); var before = data.counts();
        var created = post(writer, base, "/api/rule-versions", initial, createKey, body); var draft = success(created, 201).required("rule");
        assertEquals("DRAFT", draft.required("status").textValue()); String rule = draft.required("rule_version").textValue();
        delta(before, data.counts(), Map.of("rule_definition", 1L, "rule_version", 1L, "version_registry", 1L,
            "lineage_edge", 1L, "operation_record", 1L, "audit_event", 1L, "outbox_event", 1L));
        var after = data.counts();
        assertEquals(JSON.readTree(created.body()), JSON.readTree(post(writer, base, "/api/rule-versions", initial, createKey, body).body()));
        assertEquals(after, data.counts());
        var baseline = success(get(writer, base, "/api/rule-versions/" + rule + "/approval-baseline", context(writer, base)), 200);
        var approve = JSON.createObjectNode().put("schema_version", "1.0.0").put("target_revision", rule)
            .put("expected_revision", baseline.required("rule").required("revision").textValue()).put("decision", "APPROVE")
            .put("reason", "SIMULATION rule workflow approval only; no clinical validation or product release");
        approve.set("baseline_manifest", baseline.required("baseline_manifest"));
        String approvalContext = context(writer, base), decisionKey = id(); before = data.counts();
        var approved = post(writer, base, "/api/rule-versions/" + rule + "/approval-decisions", approvalContext, decisionKey, approve);
        var receipt = success(approved, 200); assertEquals("APPROVED", receipt.required("status").textValue());
        assertEquals("2", receipt.required("revision").textValue());
        delta(before, data.counts(), Map.of("rule_approval_decision", 1L, "operation_record", 1L, "audit_event", 1L, "outbox_event", 1L));
        after = data.counts();
        assertEquals(JSON.readTree(approved.body()), JSON.readTree(post(writer, base, "/api/rule-versions/" + rule + "/approval-decisions", approvalContext, decisionKey, approve).body()));
        assertEquals(after, data.counts());
        var read = success(get(writer, base, "/api/rule-versions/" + rule, context(writer, base)), 200).required("rule");
        assertEquals("APPROVED", read.required("status").textValue()); assertEquals(draft.required("content_sha256"), read.required("content_sha256"));
        assertEquals(draft.required("content"), read.required("content"));
        error(get(outsider, base, "/api/rule-versions/" + rule, context(outsider, base)), 403, "ACCESS_DENIED");
        assertEquals(0, success(get(outsider, base, "/api/rule-versions", context(outsider, base)), 200).required("items").size());
        assertEquals(1, success(get(writer, base, "/api/rule-versions", context(writer, base)), 200).required("items").size());
        error(post(writer, base, "/api/rule-versions/" + rule + "/approval-decisions", context(writer, base), id(), approve), 409, "REVISION_CONFLICT");
        assertEquals(after, data.counts());
        for (String decision : List.of("RETURN", "DISABLE")) {
            var current = success(get(writer, base, "/api/rule-versions/" + rule + "/approval-baseline", context(writer, base)), 200);
            var changed = approve.deepCopy().put("decision", decision).put("expected_revision", current.required("rule").required("revision").textValue());
            changed.set("baseline_manifest", current.required("baseline_manifest"));
            var result = success(post(writer, base, "/api/rule-versions/" + rule + "/approval-decisions", context(writer, base), id(), changed), 200);
            assertEquals(decision.equals("RETURN") ? "RETURNED" : "DISABLED", result.required("status").textValue());
        }
        evidence.put("rule_create_read_approve_return_disable_verified", true);
        evidence.put("rule_fixed_content_approval_replay_and_atomic_ledger_counts_verified", true);
    }

    private static void delta(Map<String,Long> before, Map<String,Long> after, Map<String,Long> expected) {
        assertEquals(before.keySet(), after.keySet());
        before.forEach((table, count) -> assertEquals(expected.getOrDefault(table, 0L), after.get(table)-count, "Unexpected transaction delta for " + table));
    }
    private static Map<String,Long> globalCounts(JdbcTemplate jdbc) {
        var counts = new LinkedHashMap<String,Long>();
        for (String table : List.of("tenant", "platform_local_account", "configuration_head", "model_config_revision", "model_configuration_definition",
                "rule_version", "rule_definition", "rule_approval_decision", "release_record", "version_registry", "permission_grant", "artifact",
                "artifact_content_binding", "dependency_access_set", "dependency_access_manifest", "operation_record", "audit_event", "outbox_event"))
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return counts;
    }
    private static JsonNode account(HttpClient owner, String base, SimulationAccountData accounts) throws Exception {
        String username = accounts.nextUsername(), password = InvitationCredential.issue().secret();
        var value = JSON.createObjectNode().put("username", username).put("display_name", "SIMULATION configuration user")
            .put("password", password).put("platform_role", "USER");
        var account = success(post(owner, base, "/api/local-accounts", null, id(), value), 201).required("account");
        return JSON.createObjectNode().put("username", username).put("password", password).put("user_id", account.required("user_id").textValue());
    }
    private static HttpClient login(String base, JsonNode account) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
        var csrf = JSON.readTree(get(client, base, "/auth/csrf", null).body());
        var request = HttpRequest.newBuilder(URI.create(base + "/auth/login")).timeout(Duration.ofSeconds(30)).header("Origin", ORIGIN)
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=" + encode(account.required("username").textValue()) + "&password=" + encode(account.required("password").textValue()))).build();
        assertEquals(302, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()); return client;
    }
    private static void select(HttpClient client, String base, String tenant) throws Exception {
        var options = success(get(client, base, "/api/session/tenant-selection-options", null), 200);
        String context = options.required("expected_context_revision").textValue();
        success(post(client, base, "/api/session/tenant-selections", context, id(), JSON.createObjectNode().put("tenant_id", tenant).put("expected_context_revision", context)), 200);
    }
    private static String context(HttpClient client, String base) throws Exception { return success(get(client, base, "/api/session", null), 200).required("current_context").required("context_revision").textValue(); }
    private static HttpResponse<byte[]> get(HttpClient client, String base, String path, String context) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).header("X-Schema-Version", "1.0.0");
        if (context != null) request.header("X-Context-Revision", context);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpRequest request(String base, String path, String context, String key, JsonNode body, JsonNode csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).header("Origin", ORIGIN)
            .header("X-Schema-Version", "1.0.0").header("Idempotency-Key", key).header("Content-Type", "application/json")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue());
        if (context != null) request.header("X-Context-Revision", context);
        return request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();
    }
    private static HttpResponse<byte[]> post(HttpClient client, String base, String path, String context, String key, JsonNode body) throws Exception {
        var csrf = JSON.readTree(get(client, base, "/auth/csrf", null).body());
        return client.send(request(base, path, context, key, body, csrf), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int expected) throws Exception {
        assertEquals(expected, response.statusCode(), () -> "Unexpected HTTP result: " + safeError(response));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()); return JSON.readTree(response.body()).required("data");
    }
    private static void error(HttpResponse<byte[]> response, int expected, String code) throws Exception {
        assertEquals(expected, response.statusCode(), () -> "Unexpected HTTP result: " + safeError(response));
        assertEquals(code, JSON.readTree(response.body()).required("code").textValue());
    }
    private static String safeError(HttpResponse<byte[]> response) {
        try { var value=JSON.readTree(response.body()); return "status=" + response.statusCode() + ", code=" + value.path("code").asText("not-present") + ", request_id=" + value.path("request_id").asText("not-present"); }
        catch (Exception ignored) { return "status=" + response.statusCode() + ", non-JSON response"; }
    }
    private static JsonNode secret(Path root, String name) throws Exception { return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + name + ".json"))); }
    private static String required(String name) { String value=System.getProperty(name); if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing test property: " + name); return value; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String id() { return UUID.randomUUID().toString(); }
}
