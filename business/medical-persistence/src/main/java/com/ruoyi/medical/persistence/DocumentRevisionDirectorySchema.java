package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Local closed metadata directory contract. Validation does not verify historical body bytes. */
public final class DocumentRevisionDirectorySchema {
 public static final String REF="urn:medical:document-revision-directory:1.0.0#/$defs/";
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
   .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema validator;
 private final String schemaDocumentSha256,manifest;
 public DocumentRevisionDirectorySchema()throws Exception{
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-revision-directory.schema.json"))){
   byte[] bytes=in.readAllBytes();schemaDocumentSha256=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
   validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());validator.initializeValidators();
   manifest=hash(Map.of("schema_version","1.0.0","document-revision-directory.schema.json",schemaDocumentSha256));
  }
 }
 public String ref(String definition){if(!Set.of("Entry","Page","View").contains(definition))invalid();return REF+definition;}
 public String manifest(){return manifest;}
 public String schemaDocumentSha256(){return schemaDocumentSha256;}
 public String canonical(Object value)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}
 public String hash(Object value)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}
 public JsonNode read(String value)throws IOException{return json.readTree(value);}
 public ObjectNode parse(String definition,byte[] bytes,int maximumBytes)throws IOException{
  if(maximumBytes<1)throw new IllegalArgumentException("DOCUMENT_DIRECTORY_CAPACITY_REQUIRED");
  if(bytes==null||bytes.length==0)invalid();if(bytes.length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  try{return validate(definition,json.readTree(bytes));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 public ObjectNode validate(String definition,JsonNode value){
  ref(definition);if(value==null||!value.isObject()||!validator.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
  var result=(ObjectNode)value.deepCopy();
  if("Entry".equals(definition)){entry(result);return result;}
  WireValues.uuid(result.required("document_id").textValue());
  if(PermissionSchema.revision(result.required("document_head_revision").textValue()).signum()<=0)invalid();
  boolean complete=result.required("complete").booleanValue();if(complete!=result.required("next_cursor").isNull())invalid();
  String current=result.required("current_document_revision").isNull()?null:result.required("current_document_revision").textValue();
  if(current!=null)WireValues.uuid(current);
  var ids=new HashSet<String>();Instant previous=null;String previousId=null;
  for(JsonNode item:result.required("items")){
   entry(item);String revision=item.required("document_revision").textValue();
   if(!ids.add(revision)||item.required("is_current").booleanValue()!=Objects.equals(current,revision))invalid();
   String expected="/api/documents/"+result.required("document_id").textValue()+"/revisions/"+revision;
   if(!expected.equals(item.required("read_url").textValue()))invalid();
   Instant at=Instant.parse(item.required("created_at").textValue());
   if(previous!=null&&(at.isAfter(previous)||(at.equals(previous)&&revision.compareTo(previousId)>=0)))invalid();
   previous=at;previousId=revision;
  }
  return result;
 }
 public ObjectNode validate(JsonNode value){return validate("Page",value);}
 public ObjectNode page(JsonNode value){return validate("Page",value);}
 private void entry(JsonNode e){
  WireValues.uuid(e.required("document_revision").textValue());WireValues.uuid(e.required("created_by").textValue());WireValues.hash(e.required("body_sha256").textValue());
  try{Instant.parse(e.required("created_at").textValue());}catch(RuntimeException ex){invalid();}
  for(String name:List.of("check_ids","review_ids","signature_ids")){
   String previous=null;for(JsonNode id:e.required(name)){String current=id.textValue();WireValues.uuid(current);if(previous!=null&&previous.compareTo(current)>=0)invalid();previous=current;}
  }
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
