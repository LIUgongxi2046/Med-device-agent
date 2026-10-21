package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Separate transport schema. It never changes the immutable 088 content manifest. */
@Component
public final class FormalEvaluationContextHttpSchema {
    static final int MAXIMUM_BYTES = 1048576;
    static final int MAXIMUM_QUERY_BYTES = 4096;
    private final JsonSchema schema;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public FormalEvaluationContextHttpSchema() throws IOException {
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-context-http.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(in),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }

    JsonNode request(String type, byte[] bytes) throws IOException {
        int maximum = "Query".equals(type) ? MAXIMUM_QUERY_BYTES : MAXIMUM_BYTES;
        if (bytes == null || bytes.length == 0 || bytes.length > maximum) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return validate(type, reader.readTree(bytes));
    }

    JsonNode validate(String type, JsonNode value) {
        if (value == null || !schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(value).isEmpty())
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return value;
    }
}
