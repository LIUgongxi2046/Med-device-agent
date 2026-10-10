package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TaskPlanDecisionSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String manifest;

    public TaskPlanDecisionSchema(PermissionSchema json, TaskInputSchema inputs) throws IOException, java.security.NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath(); byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/task-plan-decision.schema.json"))) { bytes = input.readAllBytes(); }
        var document = reader.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); document.required("$defs").fieldNames().forEachRemaining(name -> definition(name).initializeValidators());
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("decision_schema", DigestUtils.sha256Hex(bytes));
        identity.put("task_input_manifest", inputs.manifestSha256()); identity.put("permission_manifest", json.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    TaskPlanDecisionProtocol.Request request(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); validate("Request", node);
        var value = reader.treeToValue(node, TaskPlanDecisionProtocol.Request.class);
        ProjectSchema.text(value.reason(), 2000); baseline(value.baselineManifest()); positive(value.expectedRevision());
        if (!value.targetRevision().equals(value.baselineManifest().planRevision())
                || !value.expectedRevision().equals(value.baselineManifest().expectedRevision())) invalid();
        if ("CONFIRM".equals(value.decision()) && !"PLAN_PENDING".equals(value.baselineManifest().taskStatus())) invalid();
        return value;
    }
    void baseline(TaskPlanDecisionProtocol.Baseline value) {
        validate("Baseline", json.tree(value));
        for (String number : List.of(value.expectedRevision(), value.planHeadRevision(), value.contextRevision(), value.authEpoch(), value.governanceEpoch()))
            positive(number);
    }
    JsonNode envelope(TaskPlanDecisionProtocol.Receipt value, String requestId) {
        validate("Receipt", json.tree(value)); ProjectSchema.text(value.reason(), 2000);
        if (!PermissionSchema.next(value.expectedRevision()).equals(value.resultingRevision())) invalid();
        if ("RETURN".equals(value.decision())) {
            if (!value.basisTaskRevision().equals(value.resultingTaskRevision()) || !value.fromStatus().equals(value.toStatus())
                    || value.resultingPlanRevision() != null) invalid();
        } else if (!"PLAN_PENDING".equals(value.fromStatus()) || !"READY".equals(value.toStatus())
                || value.basisTaskRevision().equals(value.resultingTaskRevision()) || value.resultingPlanRevision() == null
                || value.targetPlanRevision().equals(value.resultingPlanRevision())) invalid();
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, value.resultingRevision(), json.tree(value)));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    JsonNode response(String value) throws IOException {
        var node = reader.readTree(value); shared.validate("interfaces", node, "Envelope");
        var receipt = reader.treeToValue(node.required("data"), TaskPlanDecisionProtocol.Receipt.class);
        if (!node.equals(envelope(receipt, node.required("request_id").textValue()))) invalid(); return node;
    }
    JsonNode read(String value) throws IOException { return reader.readTree(value); }
    String manifestSha256() { return manifest; }
    private void validate(String name, JsonNode value) { if (value == null || !definition(name).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void positive(String value) { if (WireValues.revision(value).signum() <= 0) invalid(); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
