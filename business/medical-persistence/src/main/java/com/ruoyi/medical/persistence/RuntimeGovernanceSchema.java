package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;import java.time.*;import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
/** Local extension, never a replacement for frozen model, source or permission contracts. */
public final class RuntimeGovernanceSchema {
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema; private final String manifest;
 public RuntimeGovernanceSchema() throws IOException {
  byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/runtime-governance.schema.json"))){bytes=in.readAllBytes();}
  schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/permission-decisions.schema.json"))){manifest=hash(Map.of("local",DigestUtils.sha256Hex(bytes),"permissions",DigestUtils.sha256Hex(in.readAllBytes())));}
 }
 public String manifest(){return manifest;}
 public JsonNode read(String value)throws IOException{return parser.readTree(value);}
 public JsonNode request(String type,byte[] bytes)throws IOException {if(bytes==null||bytes.length==0||bytes.length>65536)invalid();return validate(type,parser.readTree(bytes));}
 public JsonNode validate(String type,JsonNode node)throws IOException {
  if(!Set.of("RetentionRequest","RetentionFixed","RetentionReceipt","RetentionRecovery","ModelEligibilityBinding","ModelEnableRequest","ModelEnableFixed","ModelEnableReceipt","ModelEnableRecovery","ModelRuntimeAvailability","ConnectionResult","ConnectionStarted","ConnectionPassed").contains(type)||node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(node).isEmpty())invalid();
  if(type.equals("ModelEnableFixed"))validate("ModelEnableRequest",node.required("request"));
  if(type.equals("ModelEnableRequest"))ProjectSchema.text(node.required("reason").textValue(),2000);
  if(type.equals("RetentionFixed"))validate("RetentionRequest",node.required("request"));
  if(type.equals("RetentionRequest")){
   ProjectSchema.text(node.required("reason").textValue(),2000);
   if(!Instant.parse(node.required("effective_from").textValue()).isBefore(Instant.parse(node.required("effective_until").textValue())))invalid();
   var categories=new HashSet<String>();for(var term:node.required("terms")){String category=term.required("category").textValue();if(!categories.add(category)||!term.required("start_event").textValue().equals(category+"_CREATED"))invalid();}
   if(!categories.equals(Set.of("RUN","STREAM")))invalid();
  }return node;
 }
 public String canonical(Object object)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(object),java.nio.charset.StandardCharsets.UTF_8);}
 public String hash(Object object)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(object));}
 public static void key(String key){if(key==null||!key.matches("[!-~]{16,128}"))invalid();}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
