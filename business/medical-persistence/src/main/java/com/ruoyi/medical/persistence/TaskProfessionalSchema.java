package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Explicit v2 intent dispatch. Kind alone never selects the professional branch. */
public final class TaskProfessionalSchema {
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final TaskInvocationContextSchema invocation;private final JsonSchema schema;private final String digest,evidenceDigest;
 public TaskProfessionalSchema()throws Exception {invocation=new TaskInvocationContextSchema();byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-research-step.schema.json"))){bytes=in.readAllBytes();}digest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/agent-design/artifacts.schema.json"))){evidenceDigest=WireValues.sha256(in.readAllBytes());}}
 public String ref(String kind){return switch(kind){case "TASK_EXACT_INPUT"->TaskInvocationContextSchema.EXACT_INPUT_REF;case "STEP_ENVELOPE"->TaskProfessionalRuntimeService.ENVELOPE_REF;case "STEP_RESULT"->TaskResearchCompletionAuthority.RESULT_REF;case "SESSION_EXPORT"->TaskResearchCompletionAuthority.EXPORT_REF;case "EVIDENCE_BUNDLE"->"https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/EvidenceBundle";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};}
 public String manifest(String kind){ref(kind);return kind.equals("TASK_EXACT_INPUT")?invocation.sha256():kind.equals("EVIDENCE_BUNDLE")?evidenceDigest:digest;}
 public void requireDeclaration(String kind,String ref,String digest)throws Exception {
  if("EVIDENCE_BUNDLE".equals(kind)) {
   var p=SchemaCatalogue.fromClasspath().requirePublished(ref,digest);
   if(!"EvidenceBundle".equals(p.definition())||(!"artifacts".equals(p.name())&&!"artifacts-v2".equals(p.name())))invalid();
  } else if(!ref(kind).equals(ref)||!manifest(kind).equals(digest))invalid();
 }
 public JsonNode read(String kind,byte[] bytes,String declaredRef,String declaredDigest)throws Exception {
  requireDeclaration(kind,declaredRef,declaredDigest);
  if(!"EVIDENCE_BUNDLE".equals(kind))return read(kind,bytes);
  if(bytes==null||bytes.length==0||bytes.length>67108864)invalid();var value=parser.readTree(bytes);
  ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,value,declaredRef,declaredDigest).read();return value;
 }
 public JsonNode parse(String value)throws Exception{return parser.readTree(value);}
 public JsonNode read(String kind,byte[] bytes)throws Exception {
  if(bytes==null||bytes.length==0||bytes.length>67108864)invalid();var value=parser.readTree(bytes);ref(kind);
  if(kind.equals("TASK_EXACT_INPUT"))return invocation.readExactInput(bytes);
  if(kind.equals("EVIDENCE_BUNDLE")){TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,value).read();return value;}
  String definition=switch(kind){case "STEP_ENVELOPE"->"StepEnvelope";case "STEP_RESULT"->"StepResult";case "SESSION_EXPORT"->"SessionExport";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};
  if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();
  if(kind.equals("SESSION_EXPORT")){var paths=new HashSet<String>();for(var file:value.required("files")){String path=file.required("path").textValue();if(path.startsWith("/")||path.contains("..")||path.contains("\\")||!paths.add(path))invalid();byte[] content;try{content=Base64.getDecoder().decode(file.required("content_base64").textValue());}catch(IllegalArgumentException e){invalid();return null;}if(content.length>16777216||!Integer.toString(content.length).equals(file.required("size_bytes").textValue())||!WireValues.sha256(content).equals(file.required("sha256").textValue()))invalid();}if(!paths.contains("runtime-inventory.json"))invalid();}
  return value;
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
