package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class RetrievalSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final PermissionSchema json;
    private final String manifest;

    public RetrievalSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        byte[] document = resource("retrieval-view.schema.json");
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(document),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for (String name : List.of("SearchInput", "Hit", "PageInfo", "Page")) definition(name).initializeValidators();
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("retrieval-view.schema.json", DigestUtils.sha256Hex(document));
        identity.put("backend-design/interfaces.schema.yaml", DigestUtils.sha256Hex(resource("backend-design/interfaces.schema.yaml")));
        manifest = DigestUtils.sha256Hex(json.canonical(identity));
    }

    RetrievalProtocol.SearchInput input(byte[] bytes) throws IOException {
        var node = reader.readTree(bytes); verify("SearchInput", node);
        var value = reader.treeToValue(node, RetrievalProtocol.SearchInput.class);
        ProjectSchema.text(value.query(), 500);
        if (value.query().codePointCount(0, value.query().length()) < 2) invalid();
        WireValues.uuid(value.project_id());
        return value;
    }

    RetrievalProtocol.PageQuery page(Map<String,String[]> parameters) {
        for (var entry : parameters.entrySet()) {
            if (!List.of("page", "page_size").contains(entry.getKey()) || entry.getValue().length != 1) invalid();
        }
        return new RetrievalProtocol.PageQuery(number(parameters, "page", 1, Integer.MAX_VALUE),
                number(parameters, "page_size", 20, 100));
    }

    JsonNode page(JsonNode value, String requestId) {
        verify("Page", value); WireValues.uuid(requestId);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, null, value));
        shared.validate("interfaces", result, "Envelope"); return result;
    }

    JsonNode operationReceipt(String content) throws IOException {
        var result = reader.readTree(content); shared.validate("interfaces", result, "Envelope");
        if (!result.required("resource_revision").isNull()) invalid();
        shared.validate("interfaces", result.required("data"), "Operation");
        return result;
    }

    String requestHash(RetrievalProtocol.SearchInput value, String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST"); request.put("path", "/api/retrievals");
        request.put("schema_version", "1.0.0"); request.putNull("query"); request.put("context_header", context);
        request.put("session_id", session); request.set("body", json.tree(value));
        return DigestUtils.sha256Hex(json.canonical(request));
    }

    JsonNode read(String value) throws IOException { return reader.readTree(value); }
    JsonNode locator(String value) throws IOException {
        var result = reader.readTree(value); shared.validate("dataset", result, "locator"); return result;
    }
    String manifestSha256() { return manifest; }
    void verify(String definition, JsonNode value) { if (value == null || !definition(definition).validate(value).isEmpty()) invalid(); }

    private int number(Map<String,String[]> values, String key, int defaultValue, int maximum) {
        if (!values.containsKey(key)) return defaultValue;
        var value = PermissionSchema.revision(values.get(key)[0]);
        if (value.signum() <= 0 || value.compareTo(java.math.BigInteger.valueOf(maximum)) > 0) invalid();
        return value.intValueExact();
    }

    private JsonSchema definition(String name) {
        return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
    }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
