package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class TemplateApprovalSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String manifest;

    public TemplateApprovalSchema(PermissionSchema json, TemplateDefinitionSchema definitions) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/template-approval.schema.json"))) { bytes = input.readAllBytes(); }
        var document = reader.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); document.required("$defs").fieldNames().forEachRemaining(name -> definition(name).initializeValidators());
        var identity = json.object(); identity.put("schema_version", "1.0.0"); identity.put("approval_schema", DigestUtils.sha256Hex(bytes));
        identity.put("definition_manifest", definitions.manifestSha256()); identity.put("permission_manifest", json.manifestSha256());
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    TemplateApprovalProtocol.Decide decide(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); validate("Decide", node);
        var request = reader.treeToValue(node, TemplateApprovalProtocol.Decide.class);
        ProjectSchema.text(request.reason(), 2000); positive(request.expected_revision());
        if (!request.target_revision().equals(request.baseline_manifest().template_version())) invalid();
        baseline(request.baseline_manifest()); return request;
    }
    void baseline(TemplateApprovalProtocol.Baseline value) {
        validate("Baseline", json.tree(value));
        if (value.dependency_refs().stream().map(PermissionProtocol.Basis::version_id).distinct().count() != value.dependency_refs().size()) invalid();
    }
    JsonNode preview(TemplateProtocol.Stored stored, TemplateApprovalProtocol.Baseline baseline, String requestId) throws IOException {
        return preview(stored,baseline,requestId,true);
    }
    JsonNode preview(TemplateProtocol.Stored stored, TemplateApprovalProtocol.Baseline baseline, String requestId,boolean approval) throws IOException {
        var value = json.object(); value.put("schema_version", "1.0.0"); value.set("template", json.tree(stored));
        value.set("baseline_manifest", json.tree(baseline));
        value.set("allowed_actions", json.tree(approval&&!stored.status().equals("DISABLED") ? java.util.List.of("template.approve") : java.util.List.of()));
        validate("Preview", value); shared.validate("templates", json.tree(stored.content()), "Content"); baseline(baseline);
        positive(stored.revision()); positive(stored.authority_revision()); ProjectSchema.text(stored.reason(), 2000);
        if (!stored.template_version().equals(baseline.template_version()) || !stored.content_sha256().equals(baseline.content_sha256())
                || !DigestUtils.sha256Hex(json.canonicalObject(stored.content())).equals(stored.content_sha256())
                || !stored.dependency_refs().equals(baseline.dependency_refs())) invalid();
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, stored.revision(), value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    String hash(String id, TemplateApprovalProtocol.Decide request, String context, String session) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", "/api/template-versions/" + id + "/approval-decisions");
        value.put("schema_version", "1.0.0"); value.putNull("query"); value.put("context_header", context);
        value.put("session_id", session); value.set("body", json.tree(request)); return DigestUtils.sha256Hex(json.canonical(value));
    }
    JsonNode envelope(JsonNode value, String requestId) {
        validate("Receipt", value); positive(value.required("revision").textValue());
        String expected = switch (value.required("decision").textValue()) { case "APPROVE" -> "APPROVED"; case "RETURN" -> "RETURNED"; case "DISABLE" -> "DISABLED"; default -> throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); };
        if (!expected.equals(value.required("status").textValue())) invalid();
        for (String field : java.util.List.of("context_revision", "auth_epoch", "governance_epoch")) positive(value.required("current_context").required(field).textValue());
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, value.required("revision").textValue(), value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode receipt(String content) throws IOException {
        var node = reader.readTree(content); shared.validate("interfaces", node, "Envelope");
        if (!envelope(node.required("data"), node.required("request_id").textValue()).equals(node)) invalid(); return node;
    }
    JsonNode read(String content) throws IOException { return reader.readTree(content); }
    String manifestSha256() { return manifest; }
    private static void positive(String value) { if (PermissionSchema.revision(value).signum() <= 0) invalid(); }
    private void validate(String name, JsonNode value) { if (value == null || !definition(name).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
