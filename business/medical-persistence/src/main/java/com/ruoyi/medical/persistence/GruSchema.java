package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
/** Local record-only write contract; all shared value definitions remain frozen. */
public final class GruSchema {
 static final int MAX_BYTES=65536,MAX_RECOVERY_BYTES=128*1024,MAX_MESSAGES=500;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema; private final SchemaCatalogue shared; private final String manifest;
 public GruSchema()throws Exception {
  shared=SchemaCatalogue.fromClasspath();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/gru-records.schema.json"))){
   byte[] bytes=in.readAllBytes();schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
   manifest=DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(Map.of("local",DigestUtils.sha256Hex(bytes),"interfaces",shared.schemaDigest("interfaces"),"resource-details",shared.schemaDigest("resource-details"))));
  }
 }
 String manifest(){return manifest;}
 JsonNode read(String value)throws IOException{return parser.readTree(value);}
 JsonNode request(String type,byte[] bytes)throws IOException {
  if(bytes==null||bytes.length==0||bytes.length>("RecoveryRequest".equals(type)?MAX_RECOVERY_BYTES:MAX_BYTES))invalid();
  try {return validate(type,parser.readTree(bytes));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 JsonNode response(String type,JsonNode value){
  if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(value).isEmpty())invalid();
  if(value.has("scope")){shared.validate("interfaces",value.required("scope"),"SourceScope");if(value.required("scope").required("network_enabled").booleanValue())invalid();}
  if("Read".equals(type)){
   if(value.required("messages").size()!=value.required("message_records").size())invalid();
   var seen=new HashSet<String>();for(int i=0;i<value.required("messages").size();i++){
    var item=message(value.required("messages").get(i));var record=value.required("message_records").get(i);
    if(!item.required("message_id").equals(record.required("message_id"))||!seen.add(item.required("message_id").textValue()))invalid();
    for(String key:List.of("conversation_id","project_id","document_revision","scope"))if(!item.required(key).equals(value.required(key)))invalid();
   }
  }
  if("List".equals(type))for(var item:value.required("items"))response("View",item);
  if("Recovery".equals(type)){
   var expected=PermissionSchema.revision(value.required("expected_revision").textValue());var current=PermissionSchema.revision(value.required("current_revision").textValue());
   String status=value.required("status").textValue();
   if("NOT_APPLIED_STALE".equals(status)&&current.compareTo(expected)<=0)invalid();
   if("UNKNOWN".equals(status)&&current.compareTo(expected)>0)invalid();
   if("FOUND".equals(status)){
    var receipt=value.required("receipt");response("Receipt",receipt);
    if(receipt.required("message_id").isNull()||!receipt.required("conversation_id").equals(value.required("conversation_id"))||!receipt.required("request_body_sha256").equals(value.required("request_body_sha256")))invalid();
   }
  }
  return value;
 }
 JsonNode validate(String type,JsonNode value)throws IOException {
  if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)).validate(value).isEmpty())invalid();
  if("RecoveryRequest".equals(type)){
   if(ContentReadAuthorization.canonicalBytes(value).length>MAX_RECOVERY_BYTES)invalid();
   SimulationInputArtifactSchema.key(value.required("idempotency_key").textValue());
   var original=validate("Append",value.required("request"));
   if(ContentReadAuthorization.canonicalBytes(original).length>MAX_BYTES)invalid();return value;
  }
  shared.validate("interfaces",value.required("scope"),"SourceScope");
  if(value.required("scope").required("network_enabled").booleanValue())throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  if(value.has("selection")&&!value.required("selection").isNull())shared.validate("interfaces",value.required("selection"),"Selection");
  try {SimulationInvocationSchema.numbers(value);}catch(IOException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
  ProjectSchema.text(value.required("reason").textValue(),2000);
  if("Append".equals(type)){PermissionSchema.revision(value.required("expected_revision").textValue());messageText(value.required("text").textValue());}
  return value;
 }
 CommonProtocol.SourceScope scope(JsonNode value)throws IOException{return TypedSchemaBindings.bind(shared,CommonBindings.SOURCE_SCOPE,value).read();}
 CommonProtocol.Selection selection(JsonNode value)throws IOException{return value.isNull()?null:TypedSchemaBindings.bind(shared,CommonBindings.SELECTION,value).read();}
 JsonNode message(JsonNode value){shared.validate("resource-details",value,"Message");if(!"USER".equals(value.required("sender_kind").textValue())||!value.required("run_id").isNull()||!value.required("candidate_ids").isEmpty())invalid();return value;}
 String canonical(Object value)throws IOException{
  // Frozen Selection is nullable; JSON null has exactly these canonical bytes. Do not invent an object or change the shared object/array canonicalizer.
  if(value==null||value instanceof JsonNode node&&node.isNull())return "null";
  return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);
 }
 String hash(Object value)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}
 String requestHash(String route,JsonNode request,String context,String session)throws IOException{return hash(Map.of("method","POST","route",route,"request",request,"context_revision",context,"session_id",session,"manifest",manifest));}
 void selection(String document,InterfaceProtocol.DocumentBody body,JsonNode value)throws IOException{if(!value.isNull())DocumentSelectionSemantics.verify(shared,document,body,selection(value));}
 private static void messageText(String value){
  if(value==null||value.isBlank())invalid();
  for(int i=0;i<value.length();i++){char c=value.charAt(i);
   if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))invalid();}
   else if(Character.isLowSurrogate(c)||(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))invalid();
  }
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
