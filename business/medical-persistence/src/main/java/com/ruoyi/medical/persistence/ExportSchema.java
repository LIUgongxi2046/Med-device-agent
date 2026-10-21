package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class ExportSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifest;

    public ExportSchema() throws Exception {
        byte[] bytes = resource("export-api.schema.json"); var document = json.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        shared = SchemaCatalogue.fromClasspath(); manifest = DigestUtils.sha256Hex(bytes);
    }
    ExportProtocol.CreateInput create(byte[] bytes) throws IOException {
        var node = json.readTree(bytes); verify("CreateInput", node);
        return json.treeToValue(node, ExportProtocol.CreateInput.class);
    }
    ExportProtocol.RetryInput retry(byte[] bytes) throws IOException {
        var node = json.readTree(bytes); verify("RetryInput", node);
        var value = json.treeToValue(node, ExportProtocol.RetryInput.class);
        PermissionSchema.revision(value.expected_revision()); return value;
    }
    ExportProtocol.ProcessInput process(byte[] bytes) throws IOException {
        var node = json.readTree(bytes); verify("ProcessInput", node);
        var value = json.treeToValue(node, ExportProtocol.ProcessInput.class);
        PermissionSchema.revision(value.expected_revision()); return value;
    }
    JsonNode envelope(String kind, JsonNode data, String requestId, String revision) {
        verify(kind, data); var result = json.createObjectNode(); result.put("schema_version", "1.0.0");
        result.put("request_id", requestId); if (revision == null) result.putNull("resource_revision"); else result.put("resource_revision", revision);
        result.set("data", data); shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode receipt(String value) throws IOException { var node = json.readTree(value); shared.validate("interfaces", node, "Envelope"); verify("Receipt", node.required("data")); return node; }
    void verify(String kind, JsonNode value) { if (value == null || !definition(kind).validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    String requestHash(String path, Object input, String context, String session) throws IOException {
        var value = json.createObjectNode(); value.put("method", "POST"); value.put("path", path); value.put("schema_version", "1.0.0");
        value.put("context_header", context); value.put("session_id", session); value.set("body", json.valueToTree(input));
        return DigestUtils.sha256Hex(canonical(value));
    }
    String canonical(JsonNode value) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedString(); }
    com.fasterxml.jackson.databind.node.ObjectNode object() { return json.createObjectNode(); }
    JsonNode tree(Object value) { return json.valueToTree(value); }
    JsonNode read(String value) throws IOException { return json.readTree(value); }
    String manifestSha256() { return manifest; }
    boolean legacyBody(String ref, String digest) { return shared.schemaRef("interfaces", "DocumentBody").equals(ref) && shared.schemaDigest("interfaces").equals(digest); }
    String bodyV2Ref() { return shared.schemaRef("document-body-v2", "DocumentBody"); }
    String bodyV2Digest() { return shared.schemaDigest("document-body-v2"); }
    void verifyCommandV2(JsonNode value) { shared.validate("document-export-command-v2", value, null); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private byte[] resource(String name) throws IOException { try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); } }
}
