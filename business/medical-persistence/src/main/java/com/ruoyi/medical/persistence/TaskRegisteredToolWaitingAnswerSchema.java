package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;
/** Closed private answer-link contract, independent of AT08's immutable SDK input. */
public final class TaskRegisteredToolWaitingAnswerSchema {
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema;private final String manifest;
 public TaskRegisteredToolWaitingAnswerSchema()throws Exception{byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-registered-waiting-answer.schema.json"))){bytes=in.readAllBytes();}manifest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}
 JsonNode read(String name,byte[] bytes)throws Exception{if(bytes==null||bytes.length>32768)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return validate(name,json.readTree(bytes));}
 JsonNode validate(String name,JsonNode value){if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);return value;}
 String manifest(){return manifest;}
}
