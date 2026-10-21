package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Independent additive contract. Existing publication and task request digests are unchanged. */
@Component public final class ScenarioSpecificationSchema {
 public static final int MAXIMUM_BYTES=262144;
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final PermissionSchema json; private final JsonSchema schema; private final String manifest; private final JsonSchema largeSchema; private final String largeManifest;
 public ScenarioSpecificationSchema(PermissionSchema json)throws IOException {
  this.json=json;byte[] bytes;
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/scenario-specification-v1.schema.json"))){bytes=input.readAllBytes();}
  manifest=DigestUtils.sha256Hex(bytes);
  byte[] large;try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/scenario-specification-v1.1.schema.json"))){large=input.readAllBytes();}
  largeManifest=DigestUtils.sha256Hex(large);largeSchema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(large),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
 }
 public JsonNode request(String kind,byte[] bytes)throws IOException {
  if(bytes.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return validate(kind,reader.readTree(bytes));
 }
 public JsonNode validate(String kind,JsonNode value) {
  if(value==null||!value.isObject()||!java.util.Set.of("Author","Review","Publish","Disable").contains(kind))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  String version=value.path("schema_version").asText();if(!"1.0.0".equals(version)&&!("Author".equals(kind)&&"1.1.0".equals(version)))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var definition=("1.1.0".equals(version)?largeSchema:schema).getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind));
  if(definition==null||!definition.validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return value.deepCopy();
 }
 public JsonNode read(String value)throws IOException{return reader.readTree(value);}
 public String manifest(){return manifest;}
 public String manifest(String version){if("1.0.0".equals(version))return manifest;if("1.1.0".equals(version))return largeManifest;throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
