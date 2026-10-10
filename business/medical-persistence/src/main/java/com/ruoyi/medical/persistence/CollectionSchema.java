package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class CollectionSchema {
    static final String MANIFEST_REF = "urn:medical:collections:1.0.0#/$defs/Manifest";
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String manifest;

    public CollectionSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        for (String path : List.of("collections.schema.json", "templates.schema.json", "backend-design/interfaces.schema.yaml")) {
            try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))) {
                byte[] bytes = input.readAllBytes(); identity.put(path, DigestUtils.sha256Hex(bytes));
                if (path.equals("collections.schema.json")) shared.requirePublished("urn:medical:collections:1.0.0", DigestUtils.sha256Hex(bytes));
            }
        }
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    CollectionProtocol.Create create(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("Create", node);
        var value = reader.treeToValue(node, CollectionProtocol.Create.class);
        content(value.content()); ProjectSchema.text(value.reason(), 2000); return value;
    }
    CollectionProtocol.VersionInput version(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("VersionInput", node);
        var value = reader.treeToValue(node, CollectionProtocol.VersionInput.class);
        positive(value.expected_revision()); json.basis(node.required("base_version"));
        content(value.content()); ProjectSchema.text(value.reason(), 2000); return value;
    }
    CollectionProtocol.Manifest manifest(String value) throws IOException {
        var node = reader.readTree(value); verify("Manifest", node);
        var result = reader.treeToValue(node, CollectionProtocol.Manifest.class);
        positive(result.revision()); positive(result.authority_revision()); WireValues.utcTime(result.created_at());
        ProjectSchema.text(result.title(), 256); ProjectSchema.text(result.reason(), 2000);
        var ids = new HashSet<String>();
        for (var member : result.members()) if (!ids.add(member.source_version())) invalid();
        return result;
    }
    private void content(CollectionProtocol.Content value) {
        verify("Content", json.tree(value)); ProjectSchema.text(value.title(), 256);
        var versions = new HashSet<String>();
        for (var member : value.members()) if (!versions.add(member.source_version())) invalid();
    }
    JsonNode envelope(String definition, JsonNode data, String requestId, String revision) {
        verify(definition, data); positive(revision);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode receipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        var value = result.required("data"); verify("Created", value);
        var view = value.required("collection"); var stored = manifest(json.canonical(view.required("manifest")));
        if (!stored.revision().equals(result.required("resource_revision").textValue())
                || !DigestUtils.sha256Hex(json.canonical(view.required("manifest"))).equals(view.required("content_sha256").textValue())
                || !manifest.equals(view.required("schema_manifest_sha256").textValue())) invalid();
        return result;
    }
    String requestHash(String path, Object body, String context, String session) throws IOException {
        var node = json.object(); node.put("method", "POST"); node.put("path", path); node.put("schema_version", "1.0.0");
        node.putNull("query"); node.put("context_header", context); node.put("session_id", session); node.set("body", json.tree(body));
        return DigestUtils.sha256Hex(json.canonical(node));
    }
    CollectionProtocol.Query query(Map<String,String[]> parameters) {
        for (var entry : parameters.entrySet())
            if (!List.of("page", "page_size", "dataset_watermark").contains(entry.getKey()) || entry.getValue().length != 1) invalid();
        int page = number(parameters, "page", 1, Integer.MAX_VALUE), size = number(parameters, "page_size", 20, 100);
        String watermark = parameters.containsKey("dataset_watermark") ? parameters.get("dataset_watermark")[0] : null;
        if (watermark != null) positive(watermark);
        if (page > 1 && watermark == null) invalid();
        return new CollectionProtocol.Query(page, size, watermark);
    }
    private static int number(Map<String,String[]> values, String key, int defaultValue, int maximum) {
        if (!values.containsKey(key)) return defaultValue;
        var value = PermissionSchema.revision(values.get(key)[0]);
        if (value.signum() <= 0 || value.compareTo(java.math.BigInteger.valueOf(maximum)) > 0) invalid();
        return value.intValueExact();
    }
    void verify(String definition, JsonNode value) { shared.validate("collections", value, definition); }
    JsonNode read(String value) throws IOException { return reader.readTree(value); }
    String manifestSha256() { return manifest; }
    static void positive(String value) { if (PermissionSchema.revision(value).signum() <= 0) invalid(); }
    static void id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) invalid();
        WireValues.uuid(value);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
