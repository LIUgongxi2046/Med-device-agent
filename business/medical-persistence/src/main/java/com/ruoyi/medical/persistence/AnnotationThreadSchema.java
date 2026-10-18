package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class AnnotationThreadSchema {
    static final int MAXIMUM_ANNOTATIONS = 100;
    static final int MAXIMUM_REQUEST_BYTES = 1_048_576;
    static final int MAXIMUM_LIST_BYTES = 8_388_608;
    static final int MAXIMUM_ANNOTATION_BYTES = 65_536;
    static final int MAXIMUM_INVALIDATIONS = 1000;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final JsonSchema schema;
    private final String manifest;
    public AnnotationThreadSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath(); byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/annotation-threads.schema.json"))) { bytes = input.readAllBytes(); }
        var document = reader.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        manifest = hash(json.object().put("annotation-threads.schema.json", DigestUtils.sha256Hex(bytes))
                .put("permission_manifest", json.manifestSha256()).put("schema_version", "1.0.0"));
    }
    InterfaceProtocol.AnnotationInput create(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var value = readUtf8(bytes); validate("Create", value);
        String expectedRevision = value.required("expected_revision").asText();
        if (PermissionSchema.revision(expectedRevision).signum() <= 0) invalid();
        PermissionSchema.next(expectedRevision);
        json.requireId(value.required("document_revision").asText()); AnnotationReplySchema.content(value.required("content").asText());
        return TypedSchemaBindings.bind(shared, InterfaceBindings.ANNOTATION_INPUT, value).read();
    }
    CommonProtocol.Selection selection(JsonNode value) throws IOException {
        return TypedSchemaBindings.bind(shared, CommonBindings.SELECTION, value).read();
    }
    JsonNode readUtf8(byte[] bytes) throws IOException {
        String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return read(value);
    }
    JsonNode read(String value) throws IOException { var result = reader.readTree(value); if (result == null) invalid(); return result; }
    AnnotationThreadProtocol.Command command(String value) throws IOException {
        return reader.treeToValue(read(value), AnnotationThreadProtocol.Command.class);
    }
    JsonNode envelope(String name, JsonNode data, String requestId, String revision) throws IOException {
        validate(name, data);
        if (!data.required("revision").asText().equals(revision)) invalid();
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data));
        shared.validate("interfaces", result, "Envelope");
        if (json.canonical(result).getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LIST_BYTES)
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        return result;
    }
    JsonNode receipt(String value) throws IOException {
        var response = read(value); shared.validate("interfaces", response, "Envelope"); var data = response.required("data");
        validate("Receipt", data); annotation(data.required("annotation"));
        if (!response.required("resource_revision").equals(data.required("revision"))
                || !PermissionSchema.next(data.required("previous_revision").asText()).equals(data.required("revision").asText())
                || !hash(data.required("annotation")).equals(data.required("annotation_sha256").asText())) invalid();
        return response;
    }
    void annotation(JsonNode value) throws IOException {
        validate("Annotation", value); selection(value.required("selection")); AnnotationReplySchema.content(value.required("content").asText());
        for (String name : List.of("annotation_id", "document_revision", "selection_artifact_id", "actor_id")) json.requireId(value.required(name).asText());
        if (!value.required("document_revision").equals(value.required("selection").required("document_revision"))
                || !hash(value.required("selection")).equals(value.required("selection_sha256").asText())
                || PermissionSchema.revision(value.required("revision").asText()).signum() <= 0) invalid();
        PermissionSchema.revision(value.required("reply_count").asText());
    }
    String requestHash(String documentId, InterfaceProtocol.AnnotationInput input, String context, String session) throws IOException {
        var value = json.object().put("method", "POST").put("path", "/api/documents/" + documentId + "/annotation-threads")
                .put("schema_manifest_sha256", manifest).put("context_header", context).put("session_id", session);
        value.set("body", json.tree(input)); return hash(value);
    }
    String hash(Object value) throws IOException { return DigestUtils.sha256Hex(json.canonicalObject(value)); }
    String manifestSha256() { return manifest; }
    SchemaCatalogue shared() { return shared; }
    void validate(String name, JsonNode value) { if (value == null || !definition(name).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
