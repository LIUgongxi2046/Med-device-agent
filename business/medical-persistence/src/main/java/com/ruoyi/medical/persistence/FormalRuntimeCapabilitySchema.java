package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Closed local workflow contract. Published AgentReleaseManifest remains unchanged. */
@Component
public final class FormalRuntimeCapabilitySchema {
 public static final int MAXIMUM_BYTES=1048576;
 private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 private final PermissionSchema json;private final JsonSchema schema;private final String manifest;
 public FormalRuntimeCapabilitySchema(PermissionSchema json)throws IOException{
  this.json=json;byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-runtime-capability.schema.json"))){bytes=in.readAllBytes();}
  schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  var identity=json.object().put("schema_version","1.0.0").put("workflow_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)).put("permission_manifest",json.manifestSha256());
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/evaluation-acceptance-evidence.schema.json"))){identity.put("acceptance_evidence_schema_sha256",DigestUtils.sha256Hex(in.readAllBytes()));}
  manifest=hash(identity);
 }
 public JsonNode request(String definition,byte[] bytes)throws IOException{if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)invalid();return validate(definition,reader.readTree(bytes));}
 public JsonNode validate(String definition,JsonNode value){if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();return value;}
 public JsonNode read(String value)throws IOException{return reader.readTree(value);}
 public String canonical(Object value)throws IOException{return json.canonical(json.tree(value));}
 public String hash(Object value)throws IOException{return DigestUtils.sha256Hex(canonical(value));}
 public String manifest(){return manifest;}
 public String requestHash(String kind,JsonNode body)throws IOException{return hash(Map.of("schema_version","1.0.0","operation",kind,"body",body));}
 public JsonNode receipt(String operation,String resource,String kind,String hash,String bodyHash){return validate("Receipt",json.object().put("schema_version","1.0.0").put("operation_id",operation).put("resource_id",resource).put("kind",kind).put("content_sha256",hash).put("request_body_sha256",bodyHash));}
 static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
