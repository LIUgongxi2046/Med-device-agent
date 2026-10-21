package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.digest.DigestUtils;
import com.networknt.schema.*;
import java.util.Objects;

/** Strict input-byte integrity only. Neither a parsed Input nor its hash is an authority proof. */
public final class TaskNodeInputSchema {
    public static final String REF = "urn:medical:task-node-input:1.0.0";
    public static final int MAXIMUM_BYTES = 1024 * 1024;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    private final JsonSchema schema;
    private final String manifest;
    public TaskNodeInputSchema() throws IOException {
        byte[] bytes;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/task-node-input.schema.json"))) { bytes = stream.readAllBytes(); }
        manifest = DigestUtils.sha256Hex(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(mapper.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }
    public TaskNodeInputProtocol.Input read(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAXIMUM_BYTES) invalid();
        if (!schema.validate(mapper.readTree(bytes)).isEmpty()) invalid();
        try { return mapper.readValue(bytes, TaskNodeInputProtocol.Input.class); }
        catch (IllegalArgumentException exception) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public JsonNode tree(TaskNodeInputProtocol.Input input) { return mapper.valueToTree(input); }
    public JsonNode readTree(String json) throws IOException { return mapper.readTree(json); }
    public String canonical(Object value) throws IOException { return new String(ContentReadAuthorization.canonicalBytes(value), StandardCharsets.UTF_8); }
    public String hash(Object value) throws IOException { return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value)); }
    public byte[] bytes(TaskNodeInputProtocol.Input input) throws IOException {
        byte[] bytes = canonical(tree(input)).getBytes(StandardCharsets.UTF_8); read(bytes); return bytes;
    }
    public String manifest() { return manifest; }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
