package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import java.util.*;
/** Dedicated deterministic evidence. No session or model receipt shape exists. */
public final class FormalEvaluationDeterministicSchema {
 public static final String URI="urn:medical:evaluation-deterministic-runtime:1.0.0";
 public static String ref(String kind){return "BUSINESS_OUTPUT".equals(kind)?FormalEvaluationBusinessSchema.REF:URI+"#/$defs/"+name(kind);}
 public static String ref(String kind,com.fasterxml.jackson.databind.JsonNode approvedOutput){return "BUSINESS_OUTPUT".equals(kind)?FormalEvaluationBusinessSchema.refFor(approvedOutput):ref(kind);}
 public static String digest(String kind,String declaredRef)throws Exception{return "BUSINESS_OUTPUT".equals(kind)?FormalEvaluationBusinessSchema.digest(declaredRef):digest(kind);}
 public static void validateDeclared(String kind,String declaredRef,String declaredSha,byte[] bytes)throws Exception {
  if("BUSINESS_OUTPUT".equals(kind)){if(!FormalEvaluationBusinessSchema.known(declaredRef))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);FormalEvaluationBusinessSchema.validateOrPublished(declaredRef,declaredSha,bytes);return;}
  if(!ref(kind).equals(declaredRef)||!digest(kind).equals(declaredSha))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);validate(kind,bytes);
 }
 private static String name(String kind){return switch(kind){case "STEP_ENVELOPE"->"Envelope";case "STEP_RESULT"->"Result";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};}
 public static byte[] bytes()throws Exception{try(var in=Objects.requireNonNull(FormalEvaluationDeterministicSchema.class.getResourceAsStream("/formal-evaluation-deterministic-runtime.schema.json"))){return in.readAllBytes();}}
 public static String digest(String kind)throws Exception{return "BUSINESS_OUTPUT".equals(kind)?FormalEvaluationBusinessSchema.digest():WireValues.sha256(bytes());}
 public static void validate(String kind,byte[] bytes)throws Exception{
  if("BUSINESS_OUTPUT".equals(kind)){FormalEvaluationBusinessSchema.validateOrPublished(ref(kind),digest(kind),bytes);return;}
  var mapper=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
  var schema=SchemaCatalogue.fromClasspath().compileApiSchema(mapper.readTree(bytes()));
  if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name(kind))).validate(mapper.readTree(bytes)).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
 }
}
