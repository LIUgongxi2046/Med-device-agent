package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;

/** Additive internal persistence contract. Published tool SDK schemas are not modified. */
public final class TaskRegisteredToolSchema {
    private final JsonMapper parser=JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;private final String manifest;
    public TaskRegisteredToolSchema()throws Exception {
        byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-registered-tool.schema.json"))){bytes=in.readAllBytes();}
        manifest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }
    JsonNode validate(String definition,JsonNode value){
        if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        return value;
    }
    JsonNode parse(String value)throws Exception{return parser.readTree(value);}
    String manifest(){return manifest;}
}
