package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;

/** Independent closed recovery projection. It does not alter frozen request or journal hashes. */
public final class FormalPublicationDisableRecoverySchema {
 public static final int MAXIMUM_BYTES=2*FormalAgentPublicationSchema.MAXIMUM_BYTES;
 private final FormalAgentPublicationSchema original;
 private final JsonSchema schema;
 private final JsonMapper reader=JsonMapper.builder()
  .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
          com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
          com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public FormalPublicationDisableRecoverySchema(FormalAgentPublicationSchema original)throws IOException{
  this.original=original;
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-agent-publication-disable-recovery.schema.json"))){
   schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  }
  schema.initializeValidators();
 }
 public JsonNode recovery(byte[] bytes)throws IOException{
  if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)invalid();
  var value=definition("Recovery",reader.readTree(bytes));
  SimulationInputArtifactSchema.key(value.required("original_key").asText());
  original.validate("Disable",value.required("request"));
  return value;
 }
 public JsonNode receipt(JsonNode value){return definition("RecoveredDisable",value);}
 private JsonNode definition(String name,JsonNode value){
  if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())invalid();
  return value;
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
