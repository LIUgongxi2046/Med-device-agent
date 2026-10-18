package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AgentReleaseManifestSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("medical.projectRoot"));
    private static SchemaCatalogue catalogue;
    private static JsonNode publications;

    @BeforeAll static void load() throws Exception {
        catalogue = SchemaCatalogue.fromProject(ROOT);
        publications = JSON.readTree(Files.readAllBytes(ROOT.resolve("contracts/catalogue.json"))).required("schemas");
    }

    private static String id() { return UUID.randomUUID().toString(); }
    private static ObjectNode version() throws Exception {
        return JSON.createObjectNode().put("version_id", id()).put("content_sha256", WireValues.sha256(Files.readAllBytes(ROOT.resolve("LLD-Agent.md"))));
    }
    private static ObjectNode artifact(String path) throws Exception {
        byte[] content = Files.readAllBytes(ROOT.resolve(path));
        return JSON.createObjectNode().put("artifact_id", id()).put("sha256", WireValues.sha256(content))
            .put("size_bytes", Integer.toString(content.length)).put("media_type", "application/octet-stream");
    }
    private static ObjectNode schema(String name, String definition) throws Exception {
        JsonNode found = null;
        for (var item : publications) if (name.equals(item.required("name").textValue())) found = item;
        assertNotNull(found);
        return JSON.createObjectNode().put("schema_ref", catalogue.schemaRef(name, definition)).put("schema_sha256", found.required("sha256").textValue());
    }
    private static ObjectNode input() throws Exception {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("tenant_id", id()).put("release_id", id());
        var agent = version();
        value.put("agent_version_id", agent.required("version_id").textValue()).put("agent_version_sha256", agent.required("content_sha256").textValue());
        for (String group : List.of("role_versions", "skill_versions", "tool_versions", "prompt_revisions", "template_versions", "rule_versions", "retrieval_profiles", "model_config_revisions"))
            value.putArray(group).add(version());
        var bindings = value.putArray("input_output_schema_refs");
        for (String group : List.of("role_versions", "tool_versions")) for (String direction : List.of("INPUT", "OUTPUT"))
            bindings.add(JSON.createObjectNode().put("consumer_version_id", value.required(group).get(0).required("version_id").textValue())
                .put("direction", direction).set("schema", schema("artifacts", null)));
        var limits = value.putObject("limits");
        limits.put("activity_start_to_close_seconds", 600).put("heartbeat_interval_seconds", 15).put("heartbeat_timeout_seconds", 45)
            .put("sdk_initialize_timeout_seconds", 30).put("sdk_request_timeout_seconds", 540).put("model_request_timeout_seconds", 300)
            .put("paid_request_extra_retries", 0).put("model_spawned_subagents", 0).put("max_wall_time_seconds", 600);
        for (String name : List.of("max_model_requests", "max_tool_calls", "max_research_questions", "max_research_rounds", "max_revision_rounds", "max_parallel_steps",
                "max_input_tokens", "max_output_tokens", "max_total_tokens", "max_consecutive_no_progress", "max_repeated_failure_signature")) limits.put(name, "1");
        limits.put("max_cost_amount", "0.00000001").put("currency", "CNY");
        var checkpoint = value.putArray("human_checkpoints").addObject().put("checkpoint_id", "plan-confirmation")
            .put("operation_id", "TK-04").put("required_action", "plan.confirm");
        checkpoint.set("policy_revision", version());
        checkpoint.set("trigger_schema", schema("artifacts", "PlanCandidate"));
        checkpoint.set("decision_schema", schema("interfaces", "Id"));
        checkpoint.putArray("role_version_ids").add(value.required("role_versions").get(0).required("version_id").textValue());
        var runtime = value.putObject("runtime_manifest");
        runtime.put("source_repository", ROOT.toUri().toString()).put("source_commit", "a51a838b71b446ea27256900efe7ed2faa2a02fd").put("source_version", "3.9.2");
        runtime.set("harness_distribution", JSON.createObjectNode().put("name", "medical-source").put("version", "3.9.2").set("artifact", artifact("business/pom.xml")));
        runtime.set("python_runtime", JSON.createObjectNode().put("name", "Python").put("version", "3.12").set("artifact", artifact(System.getProperty("medical.test.python", ".implementation-work/document-venv/bin/python"))));
        runtime.putObject("platform").put("operating_system", System.getProperty("os.name")).put("architecture", System.getProperty("os.arch")).put("python_abi", "cp312");
        var plugins = runtime.putArray("plugins");
        plugins.addObject().put("plugin_id", "consumer").put("version", "1.0.0").set("artifact", artifact("agent-design/artifacts.schema.json"));
        ((ObjectNode) plugins.get(0)).putArray("dependency_ids").add("base");
        plugins.addObject().put("plugin_id", "base").put("version", "1.0.0").set("artifact", artifact("agent-design/model-stream.schema.json"));
        ((ObjectNode) plugins.get(1)).putArray("dependency_ids");
        runtime.set("profile", JSON.createObjectNode().set("artifact", artifact("agent-design/artifacts.schema.json")));
        ((ObjectNode) runtime.required("profile")).set("schema", schema("artifacts", null));
        runtime.set("sdk_configuration", JSON.createObjectNode().set("artifact", artifact("agent-design/model-stream.schema.json")));
        ((ObjectNode) runtime.required("sdk_configuration")).set("schema", schema("model-stream", null));
        runtime.set("session_format", schema("interfaces", null));
        runtime.set("artifact_schema", schema("artifacts", null));
        runtime.set("model_stream_schema", schema("model-stream", null));
        runtime.set("body_schema", schema("resource-details", null));
        var validators = runtime.putArray("validators");
        for (int number = 1; number <= 15; number++) {
            var item = validators.addObject().put("rule_id", String.format(java.util.Locale.ROOT, "AS-%02d", number));
            item.set("version", version()); item.set("implementation", artifact("LLD-Agent.md"));
        }
        var adapters = runtime.putArray("event_adapters");
        for (String component : List.of("C07", "C12")) {
            var item = adapters.addObject().put("component", component);
            item.set("version", version()); item.set("implementation", artifact("agent-design/model-stream.schema.json"));
            item.set("input_schema", schema("model-stream", null)); item.set("output_schema", schema("artifacts", null));
        }
        var evaluation = version();
        value.put("evaluation_id", evaluation.required("version_id").textValue()).put("evaluation_sha256", evaluation.required("content_sha256").textValue());
        var scope = value.putObject("release_scope").put("tenant_id", value.required("tenant_id").textValue()).put("project_mode", "PROJECTS").put("audience_mode", "MEMBERS");
        scope.putArray("project_ids").add(id()); scope.putArray("member_ids").add(id()); scope.putArray("role_ids");
        scope.set("source_scope_policy", version()); scope.putArray("allowed_classifications").add("INTERNAL");
        return value;
    }

    private static Object bind(byte[] value) throws Exception {
        var resource = AgentReleaseManifestSchemaTest.class.getResourceAsStream("/agent-release.schema.json");
        assertNotNull(resource, "任务创建需要完整Agent发布清单Schema");
        byte[] definition;
        try (resource) { definition = resource.readAllBytes(); }
        var type = Class.forName("com.ruoyi.medical.persistence.AgentReleaseManifestSchema");
        var consumer = type.getConstructor(byte[].class, SchemaCatalogue.class).newInstance(definition, catalogue);
        var method = type.getDeclaredMethod("bind", byte[].class); method.setAccessible(true);
        return method.invoke(consumer, (Object) value);
    }
    private static Object bind(JsonNode value) throws Exception { return bind(JSON.writeValueAsBytes(value)); }
    private static Object field(Object value, String name) throws Exception { return value.getClass().getMethod(name).invoke(value); }
    private static void rejected(JsonNode input) { assertThrows(InvocationTargetException.class, () -> bind(input)); }

    @Test void bindsCompleteCurrentSchemaAndPreservesEveryInput() throws Exception {
        try (var definition = getClass().getResourceAsStream("/agent-release.schema.json")) {
            assertNotNull(definition, "任务创建需要完整Agent发布清单Schema");
        }
        var input = input(); var bound = bind(input);
        String canonical = (String) field(bound, "canonicalJson");
        assertEquals(input, JSON.readTree(canonical));
        assertEquals(WireValues.sha256(canonical.getBytes(StandardCharsets.UTF_8)), field(bound, "sha256"));
        assertEquals(List.of("base", "consumer"), field(bound, "pluginOrder"));
        assertEquals(new BigDecimal("0.00000001"), field(bound, "maximumCost"));
        assertEquals(29, ((List<?>) field(bound, "dependencies")).size());
        var original = JSON.writeValueAsBytes(input);
        input.put("tenant_id", id());
        assertEquals(JSON.readTree(original), JSON.readTree((String) field(bound, "canonicalJson")));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) field(bound, "dependencies")).clear());
    }
    @ParameterizedTest
    @ValueSource(strings = {"schema_version", "tenant_id", "agent_version_id", "agent_version_sha256", "release_id", "role_versions", "skill_versions", "tool_versions", "prompt_revisions", "input_output_schema_refs", "template_versions", "rule_versions", "retrieval_profiles", "model_config_revisions", "limits", "human_checkpoints", "runtime_manifest", "evaluation_id", "evaluation_sha256", "release_scope"})
    void requiresEveryManifestField(String field) throws Exception { var value = input(); value.remove(field); rejected(value); }
    @Test void rejectsUnknownAndDuplicateFieldsTrailingValuesAndInvalidUtf8() throws Exception {
        var value = input(); value.put("approved", true); rejected(value); value.remove("approved");
        String body = JSON.writeValueAsString(value);
        assertThrows(InvocationTargetException.class, () -> bind((body + body).getBytes(StandardCharsets.UTF_8)));
        assertThrows(InvocationTargetException.class, () -> bind(("{\"schema_version\":\"1.0.0\"," + body.substring(1)).getBytes(StandardCharsets.UTF_8)));
        assertThrows(InvocationTargetException.class, () -> bind(body.getBytes(StandardCharsets.UTF_16)));
    }
    @Test void requiresPublishedSchemasAndTheirExactOriginalDigests() throws Exception {
        var value = input(); ((ObjectNode) value.required("runtime_manifest").required("body_schema")).put("schema_ref", "urn:medical:unregistered:1.0.0"); rejected(value);
        value = input(); ((ObjectNode) value.required("runtime_manifest").required("artifact_schema")).set("schema_sha256", schema("model-stream", null).required("schema_sha256")); rejected(value);
        value = input(); ((ObjectNode) value.required("runtime_manifest")).set("model_stream_schema", schema("artifacts", null)); rejected(value);
    }
    @Test void rejectsPluginCyclesUnknownDependenciesAndRepeatedIdentities() throws Exception {
        var value = input(); ((ObjectNode) value.required("runtime_manifest").required("plugins").get(1)).withArray("dependency_ids").add("consumer"); rejected(value);
        value = input(); ((ObjectNode) value.required("runtime_manifest").required("plugins").get(0)).withArray("dependency_ids").add("missing"); rejected(value);
        value = input(); ((ObjectNode) value.required("runtime_manifest").required("plugins").get(1)).put("plugin_id", "consumer"); rejected(value);
    }
    @Test void rejectsConflictingVersionsAndUnboundConsumers() throws Exception {
        var value = input(); ((ObjectNode) value.required("tool_versions").get(0)).set("version_id", value.required("role_versions").get(0).required("version_id")); rejected(value);
        value = input(); ((ObjectNode) value.required("human_checkpoints").get(0).required("policy_revision")).set("version_id", value.required("role_versions").get(0).required("version_id"));
        ((ObjectNode) value.required("human_checkpoints").get(0).required("policy_revision")).set("content_sha256", schema("model-stream", null).required("schema_sha256")); rejected(value);
        value = input(); ((ObjectNode) value.required("input_output_schema_refs").get(0)).put("consumer_version_id", id()); rejected(value);
        value = input(); ((com.fasterxml.jackson.databind.node.ArrayNode) value.required("input_output_schema_refs")).remove(1); rejected(value);
    }
    @Test void requiresAllValidationRulesAndBothEventAdapters() throws Exception {
        var value = input(); ((ObjectNode) value.required("runtime_manifest").required("validators").get(14)).put("rule_id", "AS-01"); rejected(value);
        value = input(); ((ObjectNode) value.required("runtime_manifest").required("event_adapters").get(1)).put("component", "C07"); rejected(value);
        value = input(); ((ObjectNode) value.required("human_checkpoints").get(0)).withArray("role_version_ids").add(value.required("tool_versions").get(0).required("version_id").textValue()); rejected(value);
    }
    @Test void rejectsTimeoutBudgetAndAutomaticPaidRetriesThatExceedTheExplicitLimits() throws Exception {
        for (String name : List.of("heartbeat_interval_seconds", "heartbeat_timeout_seconds", "sdk_initialize_timeout_seconds", "model_request_timeout_seconds")) {
            var value = input(); ((ObjectNode) value.required("limits")).put(name, 600); rejected(value);
        }
        for (String name : List.of("paid_request_extra_retries", "model_spawned_subagents")) {
            var value = input(); ((ObjectNode) value.required("limits")).put(name, 1); rejected(value);
        }
        for (String name : List.of("max_input_tokens", "max_output_tokens", "max_parallel_steps")) {
            var value = input(); ((ObjectNode) value.required("limits")).put(name, "18446744073709551616"); rejected(value);
        }
        var value = input(); ((ObjectNode) value.required("limits")).put("max_input_tokens", "2"); rejected(value);
    }
    @Test void preservesAllExplicitScopeModesAndRejectsUnspecifiedAudiences() throws Exception {
        var value = input(); var scope = (ObjectNode) value.required("release_scope");
        scope.put("project_mode", "ENTERPRISE"); scope.withArray("project_ids").removeAll(); assertNotNull(bind(value));
        scope.put("audience_mode", "ROLES"); scope.withArray("member_ids").removeAll(); scope.withArray("role_ids").add(id()); assertNotNull(bind(value));
        scope.put("audience_mode", "MEMBERS_OR_ROLES"); scope.withArray("member_ids").add(id()); assertNotNull(bind(value));
        scope.withArray("member_ids").removeAll(); rejected(value);
        value = input(); ((ObjectNode) value.required("release_scope")).put("tenant_id", id()); rejected(value);
        value = input(); value.set("release_id", value.required("agent_version_id")); rejected(value);
    }
}
