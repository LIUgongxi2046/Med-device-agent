package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Additive EVAL output binding. Original role projections retain their original published schema. */
public final class FormalEvaluationBusinessSchema {
 public static final String REF="urn:medical:evaluation-business-output:1.0.0#/$defs/Output";
 public static final String REF_V2="urn:medical:evaluation-business-output:2.0.0#/$defs/Output";
 public static boolean known(String ref){return REF.equals(ref)||REF_V2.equals(ref);}
 public static String refFor(JsonNode value){return "2.0.0".equals(value.path("schema_version").asText())?REF_V2:REF;}
 public static String digest(String ref)throws Exception{if(!known(ref))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return WireValues.sha256(bytes(ref));}
 public static byte[] bytes(String ref)throws Exception{if(REF.equals(ref))return bytes();if(!REF_V2.equals(ref))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);try(var in=Objects.requireNonNull(FormalEvaluationBusinessSchema.class.getResourceAsStream("/formal-evaluation-business-output-v2.schema.json"))){return in.readAllBytes();}}
 public static byte[] bytes()throws Exception{try(var in=Objects.requireNonNull(FormalEvaluationBusinessSchema.class.getResourceAsStream("/formal-evaluation-business-output.schema.json"))){return in.readAllBytes();}}
 public static String digest()throws Exception{return WireValues.sha256(bytes());}
 public static void validateOrPublished(String ref,String digest,byte[] bytes)throws Exception{
  if(!known(ref)){SchemaCatalogue.fromClasspath().bindPublished(ref,digest,bytes);return;}
  if(!digest(ref).equals(digest))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var mapper=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  var catalogue=SchemaCatalogue.fromClasspath();var value=mapper.readTree(bytes);
  if(REF_V2.equals(ref)){
   String projectionRef=value.required("projection_schema_ref").asText(),projectionSha=value.required("projection_schema_sha256").asText();
   String definition=switch(value.required("node_kind").asText()){case "N02"->"EvidenceBundle";case "N03"->"FieldCheckResult";case "N04"->"SectionDraft";case "N05","N07"->"ReviewFindings";case "N06"->"AssemblyManifest";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};
   if(!catalogue.schemaRef("artifacts-v2",definition).equals(projectionRef)||!catalogue.schemaDigest("artifacts-v2").equals(projectionSha))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
   catalogue.bindPublished(projectionRef,projectionSha,mapper.writeValueAsBytes(value.required("projection")));
   if(!value.required("normalized").isNull())catalogue.bindPublished(projectionRef,projectionSha,mapper.writeValueAsBytes(value.required("normalized")));

  }
  var root=catalogue.compileApiSchema(mapper.readTree(bytes(ref)));
  if(!root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Output")).validate(mapper.readTree(bytes)).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
 }
}
