package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class SimulationRuleVersionSchema {
    static final String USE = "SIMULATION_EVALUATION";
    static final String CONTENT_REF = "urn:medical:rule-expressions:1.0.0#/$defs/Content";
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final TemplateApplicabilitySchema contentSchema;
    private final RuleExpression engine;
    private final String manifest;

    public SimulationRuleVersionSchema(PermissionSchema json, TemplateApplicabilitySchema contentSchema, RuleExpression engine)
            throws IOException, NoSuchAlgorithmException {
        this.json = json; this.contentSchema = contentSchema; this.engine = engine; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/simulation-rule-version-workflow.schema.json"))) { bytes = input.readAllBytes(); }
        var document = reader.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); document.required("$defs").fieldNames().forEachRemaining(name -> definition(name).initializeValidators());
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("workflow_schema", DigestUtils.sha256Hex(bytes));
        identity.put("rule_manifest", contentManifestSha256()); identity.put("permission_manifest", json.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    SimulationRuleVersionProtocol.Validated version(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > 65536) invalid();
        var node = reader.readTree(bytes); validate("VersionInput", node);
        var content = contentSchema.content(node.required("content"));
        try { engine.compile(content.expression()); }
        catch (org.springframework.expression.ParseException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        var request = new SimulationRuleVersionProtocol.VersionInput("1.0.0", node.required("base_version").isNull() ? null : json.basis(node.required("base_version")),
                CONTENT_REF, content, node.required("reason").textValue(), USE, false);
        ProjectSchema.text(request.reason(), 2000);
        var refs = new LinkedHashMap<String,PermissionProtocol.Basis>();
        for (var ref : content.basis_manifest()) {
            if (refs.putIfAbsent(ref.version_id(), ref) != null) invalid();
        }
        if (request.base_version() != null) {
            var previous = refs.putIfAbsent(request.base_version().version_id(), request.base_version());
            if (previous != null && !previous.equals(request.base_version())) invalid();
        }
        String canonical;
        try {
            canonical = json.canonicalObject(content);
            // 医疗阈值不能因RFC8785数值编码而舍入；保存前要求完整数值精确往返。
            var canonicalTree = reader.readTree(canonical);
            if (!sameExactValues(json.tree(content), canonicalTree)) invalid();
            content = contentSchema.content(canonicalTree);
        } catch (IOException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        var normalized = new SimulationRuleVersionProtocol.VersionInput(request.schema_version(), request.base_version(), request.content_schema_ref(), content, request.reason(), USE, false);
        return new SimulationRuleVersionProtocol.Validated(normalized, canonical, DigestUtils.sha256Hex(canonical), new ArrayList<>(refs.values()));
    }
    SimulationRuleVersionProtocol.Validated version(SimulationRuleVersionProtocol.VersionInput request) throws IOException {
        return version(reader.writeValueAsBytes(request));
    }
    private static boolean sameExactValues(JsonNode original, JsonNode canonical) {
        if (canonical == null) return false;
        if (original.isNumber()) return canonical.isNumber() && original.decimalValue().compareTo(canonical.decimalValue()) == 0;
        if (original.isArray()) {
            if (!canonical.isArray() || original.size() != canonical.size()) return false;
            for (int index = 0; index < original.size(); index++) if (!sameExactValues(original.get(index), canonical.get(index))) return false;
            return true;
        }
        if (original.isObject()) {
            if (!canonical.isObject() || original.size() != canonical.size()) return false;
            var fields = original.fields();
            while (fields.hasNext()) { var field = fields.next(); if (!sameExactValues(field.getValue(), canonical.get(field.getKey()))) return false; }
            return true;
        }
        return original.equals(canonical);
    }
    SimulationRuleVersionProtocol.Decide decide(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > 65536) invalid();
        var node = reader.readTree(bytes); validate("Decide", node);
        var request = reader.treeToValue(node, SimulationRuleVersionProtocol.Decide.class);
        ProjectSchema.text(request.reason(), 2000); positive(request.expected_revision());
        if (!request.target_revision().equals(request.baseline_manifest().rule_version())) invalid();
        baseline(request.baseline_manifest()); return request;
    }
    void baseline(SimulationRuleVersionProtocol.Baseline value) {
        validate("Baseline", json.tree(value));
        if (value.dependency_refs().stream().map(PermissionProtocol.Basis::version_id).distinct().count() != value.dependency_refs().size()) invalid();
    }
    private void stored(SimulationRuleVersionProtocol.Stored value) throws IOException {
        validate("StoredRule", json.tree(value)); positive(value.revision()); positive(value.authority_revision());
        var validated = version(new SimulationRuleVersionProtocol.VersionInput("1.0.0", value.base_version(), CONTENT_REF, value.content(), value.reason(), USE, false));
        if (!bindingHash(value.tenant_id(), value.rule_version(), value.content_sha256()).equals(value.binding_sha256())
                || !validated.content_sha256().equals(value.content_sha256()) || !validated.dependency_refs().equals(value.dependency_refs())) invalid();
    }
    JsonNode created(SimulationRuleVersionProtocol.Stored stored, String operationId, String requestId) throws IOException {
        stored(stored); if (!stored.status().equals("DRAFT") || !stored.revision().equals("1")) invalid();
        var data = json.object(); data.put("schema_version", "1.0.0"); data.put("operation_id", operationId);
        data.set("rule", json.tree(stored)); data.set("allowed_actions", json.tree(List.of())); validate("Created", data);
        return envelope(data, requestId, stored.revision());
    }
    JsonNode readView(SimulationRuleVersionProtocol.Stored stored, String requestId) throws IOException {
        stored(stored); var data = json.object(); data.put("schema_version", "1.0.0"); data.set("rule", json.tree(stored));
        data.set("allowed_actions", json.tree(List.of())); validate("Read", data); return envelope(data, requestId, stored.revision());
    }
    JsonNode creationReceipt(String value) throws IOException {
        var result = reader.readTree(value); shared.validate("interfaces", result, "Envelope"); var data = result.required("data");
        validate("Created", data); var stored = reader.treeToValue(data.required("rule"), SimulationRuleVersionProtocol.Stored.class);
        if (!created(stored, data.required("operation_id").textValue(), result.required("request_id").textValue()).equals(result)) invalid();
        return result;
    }
    JsonNode preview(SimulationRuleVersionProtocol.Stored stored, SimulationRuleVersionProtocol.Baseline baseline, String requestId) throws IOException {
        stored(stored); baseline(baseline);
        if (!stored.binding_sha256().equals(baseline.binding_sha256()) || !stored.rule_version().equals(baseline.rule_version()) || !stored.content_sha256().equals(baseline.content_sha256())
                || !stored.dependency_refs().equals(baseline.dependency_refs()) || !contentManifestSha256().equals(baseline.definition_manifest_sha256())) invalid();
        var value = json.object(); value.put("schema_version", "1.0.0"); value.set("rule", json.tree(stored)); value.set("baseline_manifest", json.tree(baseline));
        value.set("allowed_actions", json.tree(stored.status().equals("DISABLED") ? List.of() : List.of("rule.approve")));
        validate("Preview", value); return envelope(value, requestId, stored.revision());
    }
    String hash(String id, Object request, String context, String session) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/simulation-rule-versions" + (id == null ? "" : "/" + id + "/approval-decisions"));
        value.put("schema_version", "1.0.0"); value.putNull("query"); value.put("context_header", context);
        value.put("session_id", session); value.set("body", json.tree(request)); return DigestUtils.sha256Hex(json.canonical(value));
    }
    JsonNode envelope(JsonNode value, String requestId) {
        validate("Receipt", value); positive(value.required("revision").textValue());
        for (String key : List.of("context_revision", "auth_epoch", "governance_epoch")) positive(value.required("current_context").required(key).textValue());
        String decision = value.required("decision").textValue();
        String expected = switch (decision) { case "APPROVE" -> "APPROVED"; case "RETURN" -> "RETURNED"; case "DISABLE" -> "DISABLED"; default -> throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); };
        if (!expected.equals(value.required("status").textValue())) invalid();
        return envelope(value, requestId, value.required("revision").textValue());
    }
    private JsonNode envelope(JsonNode value, String requestId, String revision) {
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode receipt(String content) throws IOException {
        var node = reader.readTree(content); shared.validate("interfaces", node, "Envelope");
        if (!envelope(node.required("data"), node.required("request_id").textValue()).equals(node)) invalid(); return node;
    }
    JsonNode read(String content) throws IOException { return reader.readTree(content); }
    JsonNode binding(String tenant, String id, String hash) {
        var b = json.object().put("schema_version", "1.0.0").put("tenant_id", tenant).put("rule_version", id)
            .put("content_sha256", hash).put("dependency_use", USE).put("production_eligible", false);
        return b;
    }
    String bindingHash(String tenant, String id, String hash) throws IOException {
        return DigestUtils.sha256Hex(json.canonical(binding(tenant,id,hash)));
    }
    String manifestSha256() { return manifest; }
    String contentManifestSha256() { return contentSchema.ruleManifestSha256(); }
    private static void positive(String value) { if (PermissionSchema.revision(value).signum() <= 0) invalid(); }
    private void validate(String name, JsonNode value) { if (value == null || !definition(name).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
