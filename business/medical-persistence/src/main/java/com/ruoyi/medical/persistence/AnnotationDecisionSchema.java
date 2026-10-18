package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
public final class AnnotationDecisionSchema {
    static final int MAXIMUM_DECISIONS = 100;
    static final int MAXIMUM_CONTENT_CODE_POINTS = 16000;
    static final int MAXIMUM_REQUEST_BYTES = 65536;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final String manifest;

    public AnnotationDecisionSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath(); byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/annotation-decisions.schema.json"))) {
            bytes = input.readAllBytes();
        }
        var document = reader.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var definitions = document.required("$defs").fieldNames();
        while (definitions.hasNext()) definition(definitions.next()).initializeValidators();
        var identity = json.object().put("schema_version", "1.0.0")
                .put("annotation-decisions.schema.json", DigestUtils.sha256Hex(bytes)).put("permission_manifest", json.manifestSha256());
        manifest = hash(identity);
    }

    AnnotationDecisionProtocol.Create create(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var node = reader.readTree(bytes); validate("Create", node);
        var request = reader.treeToValue(node, AnnotationDecisionProtocol.Create.class);
        json.requireId(request.document_revision());
        if (PermissionSchema.revision(request.expected_revision()).signum() <= 0) invalid();
        PermissionSchema.next(request.expected_revision()); if (PermissionSchema.revision(request.expected_document_revision()).signum() <= 0) invalid(); PermissionSchema.next(request.expected_document_revision()); content(request.reason());
        return request;
    }

    static void content(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > MAXIMUM_CONTENT_CODE_POINTS) invalid();
        // Preserve the user's exact Unicode and whitespace. Reject malformed Unicode and non-text controls.
        for (int at = 0; at < value.length(); at++) {
            char c = value.charAt(at);
            if (Character.isHighSurrogate(c)) {
                if (at + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++at))) invalid();
            } else if (Character.isLowSurrogate(c) || Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') invalid();
        }
    }

    void validate(String name, JsonNode value) {
        if (value == null || !definition(name).validate(value).isEmpty()) invalid();
    }
    JsonNode envelope(String name, JsonNode value, String requestId, String revision) {
        validate(name, value); PermissionSchema.revision(revision);
        if (!value.required("revision").asText().equals(revision)) invalid();
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    JsonNode receipt(String content) throws IOException {
        var response = read(content); shared.validate("interfaces", response, "Envelope");
        var data = response.required("data"); validate("Receipt", data);
        if (!data.required("revision").asText().equals(PermissionSchema.next(data.required("previous_revision").asText()))
                || !response.required("resource_revision").equals(data.required("revision"))
                || !data.required("annotation_id").equals(data.required("decision").required("annotation_id"))
                || !hash(data.required("decision")).equals(data.required("decision_sha256").asText())) invalid();
        content(data.required("decision").required("reason").asText());
        if (!data.required("document_head_revision").asText().equals(PermissionSchema.next(data.required("previous_document_revision").asText()))
            || !data.required("revision").equals(data.required("decision").required("revision"))
            || !data.required("previous_revision").equals(data.required("decision").required("previous_revision"))
            || !data.required("status").equals(data.required("decision").required("status"))) invalid();
        transition(data.required("decision")); return response;
    }
    void transition(JsonNode value) {
        String old = value.required("previous_status").asText(), next = value.required("status").asText(), action = value.required("decision").asText();
        if (!(action.equals("RESOLVE") && old.equals("OPEN") && next.equals("RESOLVED")
            || action.equals("REOPEN") && old.equals("RESOLVED") && next.equals("OPEN"))
            || !value.required("revision").asText().equals(PermissionSchema.next(value.required("previous_revision").asText()))) invalid();
    }
    String requestHash(String annotationId, AnnotationDecisionProtocol.Create request, String context, String session) throws IOException {
        var identity = json.object().put("method", "POST").put("path", "/api/annotation-threads/" + annotationId + "/decisions")
                .put("schema_version", "1.0.0").put("schema_manifest_sha256", manifest).put("context_header", context).put("session_id", session);
        identity.putNull("query"); identity.set("body", json.tree(request)); return hash(identity);
    }
    String hash(Object value) throws IOException { return DigestUtils.sha256Hex(json.canonicalObject(value)); }
    String manifestSha256() { return manifest; }
    JsonNode read(String value) throws IOException { return reader.readTree(value); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
