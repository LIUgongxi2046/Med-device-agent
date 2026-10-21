package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruoyi.medical.contracts.*;
/** Explicit HTTP contract cannot smuggle a different body version. */
final class StrictFieldWireVersion {
 static com.fasterxml.jackson.databind.JsonNode response(com.fasterxml.jackson.databind.JsonNode value,String version,String path){
  if(!version.equals(value.at(path).asText()))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return value;
 }
 static void require(byte[] body,String version)throws Exception {
  var mapper=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  var value=mapper.readTree(body);if(value==null||!version.equals(value.path("schema_version").asText()))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
 }
}
