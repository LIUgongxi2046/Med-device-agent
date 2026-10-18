package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;import java.time.*;import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
/** Local extension, never a replacement for frozen model, source or permission contracts. */
@org.springframework.stereotype.Component public final class FormalCapabilityCancellationSchema {
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final AgentPublicationSchema original;private final JsonSchema schema; private final String manifest;
 public FormalCapabilityCancellationSchema(AgentPublicationSchema original) throws IOException {
  this.original=original;
  byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-capability-cancellation.schema.json"))){bytes=in.readAllBytes();}
  schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/permission-decisions.schema.json"))){manifest=hash(Map.of("local",DigestUtils.sha256Hex(bytes),"permissions",DigestUtils.sha256Hex(in.readAllBytes())));}
 }
 public String manifest(){return manifest;}
 public JsonNode read(String value)throws IOException{return parser.readTree(value);}
 public JsonNode request(String type,byte[] bytes)throws IOException {if(bytes==null||bytes.length==0||bytes.length>(type.equals("CancelRequest")?2097152:65536))invalid();return validate(type,parser.readTree(bytes));}
 public JsonNode validate(String type,JsonNode node)throws IOException {
  if(!Set.of("CancelRequest","CancelReceipt","Cancellation","CancelRecovery","Receipt").contains(type)||node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(node).isEmpty())invalid();
  if(type.equals("CancelRequest")){if(ContentReadAuthorization.canonicalBytes(node.required("original_request")).length>AgentPublicationSchema.MAXIMUM_BYTES)invalid();original.validate("FormalCapability",node.required("original_request"));SimulationInputArtifactSchema.key(node.required("original_key").asText());}
  if(type.equals("Cancellation"))validate("CancelRequest",node.required("request"));
  return node;
 }
 public String canonical(Object object)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(object),java.nio.charset.StandardCharsets.UTF_8);}
 public String hash(Object object)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(object));}
 public static void key(String key){if(key==null||!key.matches("[!-~]{16,128}"))invalid();}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
