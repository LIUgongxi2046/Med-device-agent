package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class BusinessSessionSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifestSha256;
    public BusinessSessionSchema() throws IOException, NoSuchAlgorithmException {
        shared = SchemaCatalogue.fromClasspath();
        schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(resource("identity-session.schema.json")),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        byte[] manifestBytes = resource("identity-schema-manifest.json");
        JsonNode manifest = json.readTree(manifestBytes);
        validateDefinition("IdentityManifest", manifest);
        var paths = new HashSet<String>();
        for (JsonNode entry : manifest.required("schemas")) {
            String path = entry.required("path").textValue();
            if (!paths.add(path) || !DigestUtils.sha256Hex(resource(path)).equals(entry.required("sha256").textValue())) {
                throw new IllegalStateException("IDENTITY_SCHEMA_MANIFEST_INVALID");
            }
        }
        if (!paths.equals(java.util.Set.of("identity-session.schema.json", "contracts/catalogue.json"))) {
            throw new IllegalStateException("IDENTITY_SCHEMA_MANIFEST_INVALID");
        }
        manifestSha256 = DigestUtils.sha256Hex(manifestBytes);
    }
    public JsonNode bind(BusinessSessionProtocol.Session session) {
        var value = json.valueToTree(session);
        if (!schema.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (session.current_context() != null) {
            WireValues.revision(session.current_context().context_revision());
            WireValues.revision(session.current_context().auth_epoch());
            WireValues.revision(session.current_context().governance_epoch());
        }
        for (var member : session.memberships()) WireValues.revision(member.revision());
        return value;
    }
    public JsonNode envelope(BusinessSessionProtocol.Session session, String requestId) {
        JsonNode value = bind(session);
        JsonNode result = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId,
                session.current_context() == null ? null : session.current_context().context_revision(), value));
        shared.validate("interfaces", result, "Envelope");
        return result;
    }

    public BusinessSessionProtocol.SelectionRequest selection(byte[] body, String contextHeader, String key) throws IOException {
        JsonNode value = json.readTree(body);
        if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        validateDefinition("SelectionRequest", value);
        String expected = value.required("expected_context_revision").textValue();
        requireRevision(expected);
        requireRevision(contextHeader);
        if (new BigInteger(expected).equals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))
                || key == null || !key.matches("[!-~]{16,128}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return json.treeToValue(value, BusinessSessionProtocol.SelectionRequest.class);
    }

    public JsonNode selectionEnvelope(BusinessSessionProtocol.SelectionReceipt receipt, String requestId) {
        var value = json.valueToTree(receipt);
        validateDefinition("SelectionReceipt", value);
        WireValues.revision(receipt.current_context().context_revision());
        WireValues.revision(receipt.current_context().auth_epoch());
        WireValues.revision(receipt.current_context().governance_epoch());
        JsonNode result = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId,
                receipt.current_context().context_revision(), value));
        validateSelectionEnvelope(result);
        return result;
    }

    public void validateSelectionEnvelope(JsonNode value) {
        shared.validate("interfaces", value, "Envelope");
        validateDefinition("SelectionReceipt", value.required("data"));
        var context = value.required("data").required("current_context");
        if (!value.required("resource_revision").equals(context.required("context_revision"))) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
        for (String field : java.util.List.of("context_revision", "auth_epoch", "governance_epoch")) {
            requireRevision(context.required(field).textValue());
        }
    }

    public String audit(BusinessSessionProtocol.SelectionAudit value) throws IOException {
        JsonNode node = json.valueToTree(value);
        validateDefinition("SelectionAudit", node);
        return canonical(node);
    }

    public String event(BusinessSessionProtocol.SelectionEvent value) throws IOException {
        JsonNode node = json.valueToTree(value);
        validateDefinition("SelectionEvent", node);
        return canonical(node);
    }

    public String requestSha256(BusinessSessionProtocol.SelectionRequest body, String contextHeader, String sessionId) throws IOException {
        var value = json.createObjectNode();
        value.put("schema_version", "1.0.0"); value.put("path", "/api/session/tenant-selections");
        value.putNull("query"); value.put("context_header", contextHeader); value.put("session_id", sessionId);
        value.set("body", json.valueToTree(body));
        return DigestUtils.sha256Hex(new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedUTF8());
    }

    public String canonical(JsonNode value) throws IOException {
        return new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedString();
    }

    public JsonNode readSelectionEnvelope(String value) throws IOException {
        JsonNode node = json.readTree(value);
        validateSelectionEnvelope(node);
        return node;
    }

    public String manifestSha256() { return manifestSha256; }

    void validateDefinition(String definition, JsonNode value) {
        var validator = schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition));
        if (!validator.validate(Objects.requireNonNull(value)).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }

    private static void requireRevision(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,19})")
                || new BigInteger(value).compareTo(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)) > 0) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
    }

    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) {
            return input.readAllBytes();
        }
    }
}
