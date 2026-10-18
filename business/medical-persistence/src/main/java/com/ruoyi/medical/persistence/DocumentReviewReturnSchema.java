package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class DocumentReviewReturnSchema {
    static final int LIMIT = 100;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;
    private final String manifest;
    public DocumentReviewReturnSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath(); byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/document-review-return.schema.json"))) {
            bytes = input.readAllBytes();
        }
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        manifest = hash(json.object().put("document-review-return.schema.json", DigestUtils.sha256Hex(bytes))
                .put("permission_manifest", json.manifestSha256())
                .put("interfaces_manifest", shared.bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256()));
    }
    JsonNode request(byte[] body) throws IOException {
        if (body == null || body.length > 262144) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var value = reader.readTree(body); validate("Request", value);
        var basis = value.required("basis"); verifyDigest(basis, "basis_sha256");
        if (!basis.required("return_available").booleanValue() || !basis.required("blockers").isEmpty()) invalid();
        if (!value.required("document_revision").equals(basis.required("document_revision"))
                || !value.required("expected_revision").equals(basis.required("revision"))
                || !value.required("expected_task_revision").equals(basis.required("task_head_revision"))) invalid();
        AnnotationDecisionSchema.content(value.required("reason").asText());
        return value;
    }
    void validate(String name, JsonNode value) {
        if (value == null || !schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty()) invalid();
    }
    JsonNode envelope(String name, JsonNode value, String requestId) {
        validate(name, value);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, value.required("revision").asText(), value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode receipt(String text) throws IOException {
        var result = read(text); shared.validate("interfaces", result, "Envelope");
        var value = result.required("data"); validate("Receipt", value);
        if (!result.required("resource_revision").equals(value.required("revision"))
                || !PermissionSchema.next(value.required("previous_revision").asText()).equals(value.required("revision").asText())
                || !PermissionSchema.next(value.required("previous_task_revision").asText()).equals(value.required("task_head_revision").asText())
                || !hash(value.required("decision")).equals(value.required("decision_sha256").asText())) invalid();
        verifyDigest(value.required("decision").required("baseline_manifest"), "basis_sha256");
        return result;
    }
    void verifyDigest(JsonNode value, String field) throws IOException {
        var copy = (ObjectNode) value.deepCopy(); String expected = copy.remove(field).asText();
        if (!hash(copy).equals(expected)) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
    String requestHash(String documentId, JsonNode value) throws IOException {
        return hash(json.object().put("operation", "DOCUMENT-RETURN-FOR-REVISION-1").put("document_id", documentId)
                .put("schema_manifest_sha256", manifest).set("request", value));
    }
    String hash(Object value) throws IOException { return DigestUtils.sha256Hex(json.canonicalObject(value)); }
    String manifestSha256() { return manifest; }
    JsonNode read(String text) throws IOException { return reader.readTree(text); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
