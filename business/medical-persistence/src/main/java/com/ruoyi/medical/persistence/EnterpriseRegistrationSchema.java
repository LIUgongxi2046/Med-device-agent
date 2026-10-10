package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifest;

    public EnterpriseRegistrationSchema() throws IOException, java.security.NoSuchAlgorithmException {
        shared = SchemaCatalogue.fromClasspath();
        byte[] bytes = resource("enterprise-registration.schema.json");
        JsonNode document = json.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = json.createObjectNode();
        metadata.put("schema_version", "1.0.0");
        metadata.put("enterprise-registration.schema.json", DigestUtils.sha256Hex(bytes));
        metadata.put("contracts/catalogue.json", DigestUtils.sha256Hex(resource("contracts/catalogue.json")));
        manifest = DigestUtils.sha256Hex(canonical(metadata));
    }

    public JsonNode request(byte[] bytes, String key) throws IOException {
        if (bytes.length > 32768 || key == null || !key.matches("[!-~]{16,128}")) invalid();
        JsonNode node = json.readTree(bytes);
        validate("Request", node);
        for (String field : java.util.List.of("legal_name", "jurisdiction", "legal_identifier", "authorization_basis")) {
            String value = node.required(field).textValue();
            if (value.isBlank() || !value.equals(value.strip()) || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                    || value.codePoints().anyMatch(code -> Character.isISOControl(code) || Character.getType(code) == Character.FORMAT)
                    || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)) invalid();
        }
        WireValues.uuid(node.required("first_admin_user_id").textValue());
        return node;
    }

    String requestHash(JsonNode body, String subject) throws IOException {
        var value = json.createObjectNode();
        value.put("method", "POST"); value.put("path", "/api/enterprise-registrations");
        value.put("schema_version", "1.0.0"); value.put("subject_id", subject); value.set("body", body);
        return hash(value);
    }
    String companyHash(JsonNode body) throws IOException {
        var value = json.createObjectNode();
        value.set("jurisdiction", body.required("jurisdiction")); value.set("legal_identifier", body.required("legal_identifier"));
        return hash(value);
    }
    JsonNode envelope(String definition, JsonNode data, String requestId, String revision) {
        validate(definition, data);
        JsonNode value = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data));
        shared.validate("interfaces", value, "Envelope");
        return value;
    }
    JsonNode readEnvelope(String value) throws IOException {
        JsonNode node = json.readTree(value);
        shared.validate("interfaces", node, "Envelope");
        detail(node.required("data"));
        if (!node.required("resource_revision").equals(node.required("data").required("revision"))) invalid();
        return node;
    }
    void detail(JsonNode value) throws IOException {
        validate("Detail", value);
        if (!value.required("application").required("first_admin_user_id").equals(value.required("first_admin_binding").required("user_id"))) invalid();
        WireValues.revision(value.required("first_admin_revision").textValue());
        var content = value.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) content).remove("content_sha256");
        if (!hash(content).equals(value.required("content_sha256").textValue())) invalid();
    }
    void validate(String name, JsonNode node) { if (node == null || !definition(name).validate(node).isEmpty()) invalid(); }
    com.fasterxml.jackson.databind.node.ObjectNode object() { return json.createObjectNode(); }
    String hash(JsonNode node) throws IOException { return DigestUtils.sha256Hex(canonical(node)); }
    String canonical(JsonNode node) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedString(); }
    String canonicalStoredApplication(String value) throws IOException {
        JsonNode node = json.readTree(value); validate("Request", node); return canonical(node);
    }
    JsonNode readAudit(String value) throws IOException {
        JsonNode node = json.readTree(value); validate("Audit", node); return node;
    }
    String manifest() { return manifest; }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
