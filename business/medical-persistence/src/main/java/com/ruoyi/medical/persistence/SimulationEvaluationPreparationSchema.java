package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;
import java.util.*;
public final class SimulationEvaluationPreparationSchema {
 static final int MAX_BYTES=1024*1024;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
  .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema; private final String manifest;
 public SimulationEvaluationPreparationSchema()throws IOException {
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-evaluation-preparation.schema.json"))){
   byte[] bytes=in.readAllBytes();manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    .getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  }
 }
 public String manifest(){return manifest;}
 JsonNode request(String type,byte[] bytes)throws IOException {return validate(type,parse(bytes));}
 JsonNode validate(String type,JsonNode node)throws IOException {
  if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(node).isEmpty())invalid();
  SimulationInvocationSchema.numbers(node);return node;
 }
 JsonNode parse(byte[] bytes)throws IOException {
  if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)invalid();
  try {var node=parser.readTree(bytes);SimulationInvocationSchema.numbers(node);return node;}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 JsonNode canonicalDocument(byte[] bytes)throws IOException {
  var node=parse(bytes);if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(node)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return node;
 }
 static void key(String key){SimulationInputArtifactSchema.key(key);}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
