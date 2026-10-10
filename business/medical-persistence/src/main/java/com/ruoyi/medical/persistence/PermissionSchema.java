package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class PermissionSchema {
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifest;

    public PermissionSchema() throws IOException, NoSuchAlgorithmException {
        shared = SchemaCatalogue.fromClasspath();
        byte[] bytes = resource("permission-decisions.schema.json");
        JsonNode document = json.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = json.createObjectNode();
        metadata.put("schema_version", "1.0.0");
        metadata.put("permission-decisions.schema.json", DigestUtils.sha256Hex(bytes));
        metadata.put("contracts/catalogue.json", DigestUtils.sha256Hex(resource("contracts/catalogue.json")));
        manifest = DigestUtils.sha256Hex(canonical(metadata));
    }

    public void headers(String context, String key) {
        revision(context);
        if (key == null || !key.matches("[!-~]{16,128}")) reject();
    }

    public PermissionProtocol.Request request(byte[] body) throws IOException {
        JsonNode node = json.readTree(body); validate("Request", node);
        var request = json.treeToValue(node, PermissionProtocol.Request.class);
        revision(request.expected_revision());
        var scope = request.scope();
        switch (scope.resource_kind()) {
            case "ENTERPRISE" -> { if (scope.project_id() != null || !scope.tenant_id().equals(scope.resource_id())) reject(); }
            case "PROJECT" -> { if (scope.project_id() == null || !scope.project_id().equals(scope.resource_id())) reject(); }
            case "MEMBER" -> { if (scope.project_id() != null) reject(); }
            case "TEMPLATE", "RULE" -> { if (scope.project_id() != null) reject(); }
            default -> { }
        }
        if (request.decision().equals("DISABLE_MEMBER") && (!scope.resource_kind().equals("MEMBER") || scope.resource_id() == null)) reject();
        if (request.valid_from() != null) WireValues.utcTime(request.valid_from());
        if (request.valid_to() != null) WireValues.utcTime(request.valid_to());
        if (request.reason().isBlank()) reject();
        return request;
    }

    public PermissionProtocol.Condition condition(String value) throws IOException {
        JsonNode node = json.readTree(value); validate("Condition", node);
        return json.treeToValue(node, PermissionProtocol.Condition.class);
    }

    public PermissionProtocol.Scope scope(JsonNode node) throws IOException {
        validate("Scope", node);
        var value = json.treeToValue(node, PermissionProtocol.Scope.class);
        if (value.resource_kind().equals("ENTERPRISE") && (value.project_id() != null || !value.tenant_id().equals(value.resource_id()))
                || value.resource_kind().equals("PROJECT") && (value.project_id() == null || !value.project_id().equals(value.resource_id()))
                || java.util.List.of("MEMBER", "TEMPLATE", "RULE").contains(value.resource_kind()) && value.project_id() != null) reject();
        return value;
    }

    public PermissionProtocol.Basis basis(JsonNode node) throws IOException {
        validate("Basis", node); return json.treeToValue(node, PermissionProtocol.Basis.class);
    }

    public String requestSha256(PermissionProtocol.Request request, String context, String session) throws IOException {
        var node = json.createObjectNode();
        node.put("method", "POST"); node.put("path", "/api/permission-decisions"); node.put("schema_version", "1.0.0");
        node.putNull("query"); node.put("context_header", context); node.put("session_id", session); node.set("body", tree(request));
        return DigestUtils.sha256Hex(canonical(node));
    }

    public JsonNode envelope(JsonNode receipt, String requestId, String targetRevision) {
        JsonNode node = tree(new InterfaceProtocol.Envelope("1.0.0", requestId, targetRevision, receipt));
        validateEnvelope(node); return node;
    }

    public JsonNode readEnvelope(String value) throws IOException {
        JsonNode node = json.readTree(value); validateEnvelope(node); return node;
    }

    public void validateEnvelope(JsonNode node) {
        shared.validate("interfaces", node, "Envelope");
        JsonNode receipt = node.required("data"); validate("Receipt", receipt);
        for (String field : java.util.List.of("revoked_grants", "terminated_invitations")) revision(receipt.required(field).textValue());
        JsonNode context = receipt.required("current_context");
        for (String field : java.util.List.of("context_revision", "auth_epoch", "governance_epoch")) revision(context.required(field).textValue());
        if (!receipt.required("scope").required("tenant_id").equals(context.required("tenant_id"))) reject();
        boolean member = receipt.required("decision").textValue().equals("DISABLE_MEMBER");
        JsonNode target = receipt.required(member ? "member" : "grant");
        if (target.isNull() || !receipt.required(member ? "grant" : "member").isNull()) reject();
        String status = member ? "DISABLED" : receipt.required("decision").textValue().equals("GRANT") ? "ACTIVE" : "REVOKED";
        if (!status.equals(target.required("status").textValue()) || !node.required("resource_revision").equals(target.required("revision"))) reject();
        revision(target.required("revision").textValue());
    }

    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public void requireId(String value) {
        shared.validate("interfaces", tree(value), "Id");
        if (!value.equals(value.toLowerCase(java.util.Locale.ROOT))) reject();
        WireValues.uuid(value);
    }
    public com.fasterxml.jackson.databind.node.ObjectNode object() { return json.createObjectNode(); }
    public String canonical(JsonNode node) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedString(); }
    public String canonicalObject(Object value) throws IOException { return canonical(tree(value)); }
    public String manifestSha256() { return manifest; }
    static BigInteger revision(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,19})") || new BigInteger(value).compareTo(MAX) > 0) reject();
        return new BigInteger(value);
    }
    static String next(String value) { String next = revision(value).add(BigInteger.ONE).toString(); revision(next); return next; }
    private static void reject() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private void validate(String name, JsonNode node) {
        if (node == null || !definition(name).validate(node).isEmpty()) reject();
    }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
}
