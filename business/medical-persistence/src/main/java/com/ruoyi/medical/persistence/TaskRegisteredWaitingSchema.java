package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;
/** Closed additive no-output waiting producer contract, never the public SDK schema. */
final class TaskRegisteredWaitingSchema {
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema;private final String manifest;
 TaskRegisteredWaitingSchema()throws Exception{byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-registered-waiting-v2.schema.json"))){bytes=in.readAllBytes();}manifest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}
 JsonNode validate(JsonNode value){if(value==null||value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);if(!schema.validate(value).isEmpty())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);return value;}
 JsonNode parse(String value)throws Exception{if(value==null||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return json.readTree(value);}String manifest(){return manifest;}
}
