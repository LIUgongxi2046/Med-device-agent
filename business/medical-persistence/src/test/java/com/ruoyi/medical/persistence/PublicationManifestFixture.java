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

class PublicationManifestFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of(System.getProperty("medical.projectRoot"));
    private static SchemaCatalogue catalogue;
    private static JsonNode publications;

    static void load() throws Exception {
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
    static ObjectNode input() throws Exception {
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
        runtime.set("python_runtime", JSON.createObjectNode().put("name", "Python").put("version", "3.12").set("artifact", artifact("business/pom.xml")));
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

}
