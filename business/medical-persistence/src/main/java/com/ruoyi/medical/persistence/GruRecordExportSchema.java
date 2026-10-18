package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;

/** Closed local USER record export contract; never a model execution or generic artifact reader. */
public final class GruRecordExportSchema {
 public static final int MAX_REQUEST_BYTES=256*1024, MAX_CONTENT_BYTES=32*1024*1024, MAX_MESSAGES=500;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
  .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema;
 private final String manifest; private final String documentSha256;
 public GruRecordExportSchema() throws Exception {
  var shared=SchemaCatalogue.fromClasspath();
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/gru-record-export.schema.json"))){
   byte[] bytes=in.readAllBytes(); documentSha256=DigestUtils.sha256Hex(bytes);
   schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
   manifest=hash(Map.of("local",DigestUtils.sha256Hex(bytes),"interfaces",shared.schemaDigest("interfaces"),"resource-details",shared.schemaDigest("resource-details")));
  }
 }
 public String manifest(){return manifest;}
 public String schemaDocumentSha256(){return documentSha256;}
 public String ref(String type){return "urn:medical:gru-record-export:1.0.0#/$defs/"+type;}
 public JsonNode read(String value)throws IOException{return parser.readTree(value);}
 public JsonNode request(byte[] bytes)throws IOException{return request("Request",bytes);}
 public JsonNode request(String type,byte[] bytes)throws IOException {
  if(!"Request".equals(type)||bytes==null||bytes.length==0||bytes.length>MAX_REQUEST_BYTES)invalid();
  try{return validate(type,parser.readTree(bytes));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 public JsonNode response(String type,JsonNode value)throws IOException{return validate(type,value);}
 public JsonNode validate(String type,JsonNode value)throws IOException {
  if(!Set.of("Basis","Request","Document","Receipt","View","Recovery").contains(type)||value==null)invalid();
  if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(value).isEmpty())invalid();
  SimulationInvocationSchema.numbers(value);
  switch(type){
   case "Request" -> {if(ContentReadAuthorization.canonicalBytes(value).length>MAX_REQUEST_BYTES)invalid();validate("Basis",value.required("basis"));ProjectSchema.text(value.required("reason").textValue(),2000);}
   case "Basis","Document" -> {
    manifest(value);
    if("Basis".equals(type)&&!value.required("tenant_id").equals(value.required("current_context").required("tenant_id")))invalid();
    if("Document".equals(type)){
     if(ContentReadAuthorization.canonicalBytes(value).length>MAX_CONTENT_BYTES)invalid();
     var messages=value.required("messages");var items=value.required("message_manifest");
     if(messages.size()!=items.size())invalid();
     for(int i=0;i<messages.size();i++){
      var message=messages.get(i);var item=items.get(i);
      for(String key:List.of("message_id","sequence","content_sha256"))if(!message.required(key).equals(item.required(key)))invalid();
      text(message.required("text").textValue());
     }
    }
   }
   case "Receipt" -> {if(Long.parseLong(value.required("size_bytes").textValue())>MAX_CONTENT_BYTES)invalid();}
   case "View" -> validate("Receipt",value.required("receipt"));
   case "Recovery" -> {
    key(value.required("idempotency_key").textValue());
    if("COMPLETED".equals(value.required("status").textValue())){
     var receipt=validate("Receipt",value.required("receipt"));
     for(String key:List.of("conversation_id","request_body_sha256"))if(!value.required(key).equals(receipt.required(key)))invalid();
    }
   }
   default -> invalid();
  }
  return value;
 }
 private void manifest(JsonNode value)throws IOException {
  var items=value.required("message_manifest");int count=value.required("message_count").intValue();
  if(items.size()!=count||!Integer.toString(count+1).equals(value.required("conversation_revision").textValue()))invalid();
  var ids=new HashSet<String>();
  for(int i=0;i<count;i++)if(!Integer.toString(i+2).equals(items.get(i).required("sequence").textValue())||!ids.add(items.get(i).required("message_id").textValue()))invalid();
  if(!hash(items).equals(value.required("message_manifest_sha256").textValue()))invalid();
 }
 public String canonical(Object value)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}
 public String hash(Object value)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}
 public String requestHash(String route,JsonNode request,String context,String session)throws IOException{return hash(Map.of("method","POST","route",route,"request",request,"context_revision",context,"session_id",session,"manifest",manifest));}
 public static void key(String value){if(value==null||!value.matches("[!-~]{16,128}"))invalid();}
 private static void text(String value){
  if(value==null||value.isBlank())invalid();
  for(int i=0;i<value.length();i++){
   char c=value.charAt(i);
   if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))invalid();}
   else if(Character.isLowSurrogate(c)||(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))invalid();
  }
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
