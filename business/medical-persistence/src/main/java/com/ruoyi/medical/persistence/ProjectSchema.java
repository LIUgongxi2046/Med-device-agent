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
import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class ProjectSchema {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final String manifest;
    public ProjectSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var document = mapper.readTree(resource("projects.schema.json"));
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = json.object(); metadata.put("schema_version", "1.0.0");
        for (String name : List.of("projects.schema.json", "permission-decisions.schema.json", "contracts/catalogue.json"))
            metadata.put(name, DigestUtils.sha256Hex(resource(name)));
        manifest = DigestUtils.sha256Hex(json.canonical(metadata));
    }
    ProjectProtocol.Create create(byte[] body) throws IOException {
        var node = parse("Create", body); var value = mapper.treeToValue(node, ProjectProtocol.Create.class);
        text(value.name(), 256); text(value.reason(), 2000); attributes(json.tree(value.attributes()));
        json.basis(node.required("creation_basis")); return value;
    }
    ProjectProtocol.Revise revise(byte[] body) throws IOException {
        var value = mapper.treeToValue(parse("Revise", body), ProjectProtocol.Revise.class);
        positive(value.expected_revision()); text(value.reason(), 2000); attributes(json.tree(value.attributes())); return value;
    }
    ProjectProtocol.Decide decide(byte[] body) throws IOException {
        var value = mapper.treeToValue(parse("Decide", body), ProjectProtocol.Decide.class);
        positive(value.expected_revision()); text(value.reason(), 2000); return value;
    }
    List<ProjectProtocol.Attribute> attributes(JsonNode node) throws IOException {
        verify("Attributes", node);
        var values = mapper.readerForListOf(ProjectProtocol.Attribute.class).<List<ProjectProtocol.Attribute>>readValue(node);
        var keys = new java.util.HashSet<String>();
        for (var value : values) {
            text(value.key(), 128); text(value.reason(), 2000);
            if (!keys.add(value.key())) invalid();
            var refs = new java.util.HashSet<String>();
            for (var basis : value.authority_refs()) {
                json.basis(json.tree(basis)); if (!refs.add(basis.version_id())) invalid();
            }
        }
        return List.copyOf(values);
    }
    List<PermissionProtocol.Basis> bases(JsonNode node) throws IOException {
        verify("Bases", node);
        var values = mapper.readerForListOf(PermissionProtocol.Basis.class).<List<PermissionProtocol.Basis>>readValue(node);
        if (values.stream().map(PermissionProtocol.Basis::version_id).distinct().count() != values.size()) invalid();
        return List.copyOf(values);
    }
    JsonNode read(String value) throws IOException { return mapper.readTree(value); }
    JsonNode envelope(String kind, JsonNode value, String request, String revision) {
        verify(kind, value); if (revision != null) PermissionSchema.revision(revision);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", request, revision, value));
        shared.validate("interfaces", result, "Envelope");
        if (List.of("Project", "Receipt").contains(kind) && !result.required("resource_revision").equals(value.required("revision"))) invalid();
        return result;
    }
    JsonNode receipt(String content) throws IOException {
        var value = read(content); shared.validate("interfaces", value, "Envelope");
        if (!envelope("Receipt", value.required("data"), value.required("request_id").textValue(), value.required("resource_revision").textValue()).equals(value)) invalid();
        return value;
    }
    String hash(String path, Object request, String context, String session) throws IOException {
        var value = json.object(); value.put("method", "POST"); value.put("path", path); value.put("schema_version", "1.0.0");
        value.putNull("query"); value.put("context_header", context); value.put("session_id", session); value.set("body", json.tree(request));
        return DigestUtils.sha256Hex(json.canonical(value));
    }
    String manifestSha256() { return manifest; }
    void verify(String kind, JsonNode value) { if (value == null || !definition(kind).validate(value).isEmpty()) invalid(); }
    static void text(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || value.codePointCount(0, value.length()) > maximum || value.codePoints().anyMatch(Character::isISOControl)) invalid();
    }
    private JsonNode parse(String kind, byte[] body) throws IOException { var value = mapper.readTree(body); verify(kind, value); return value; }
    private static void positive(String value) { if (PermissionSchema.revision(value).signum() == 0) invalid(); }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
