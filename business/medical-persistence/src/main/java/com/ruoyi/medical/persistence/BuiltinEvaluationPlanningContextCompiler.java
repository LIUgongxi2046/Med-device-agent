package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruoyi.medical.contracts.*;
import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.*;
/** Bounded deterministic N01 planning implementation. Original090 blockers are preserved; no plan is confirmed. No TOOL code, template interpolation, tools, upstream inference or expected answers. */
public final class BuiltinEvaluationPlanningContextCompiler {
 public static final String IMPLEMENTATION="MEDICAL_EVALUATION_N01_PLANNING_CONTEXT_V1",VERSION="1.0.0";
 public static final String ABI="urn:medical:evaluation-planning-context-compiler:1.0.0";
 public static final String INPUT=ABI+"#/$defs/CompilationInput",PROMPT=ABI+"#/$defs/PromptPackage",PACKAGE=ABI+"#/$defs/ToolPackage";
 public static final String OUTPUT="urn:medical:simulation-execution:1.0.0#/$defs/ExactInput";
 public static final int MAXIMUM_BYTES=2*1024*1024;
 private final SchemaCatalogue catalogue;private final PermissionSchema json;
 private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public BuiltinEvaluationPlanningContextCompiler(SchemaCatalogue catalogue,PermissionSchema json){this.catalogue=Objects.requireNonNull(catalogue);this.json=Objects.requireNonNull(json);}
 public String implementationClassSha256()throws IOException{try(var in=Objects.requireNonNull(BuiltinEvaluationPlanningContextCompiler.class.getResourceAsStream("BuiltinEvaluationPlanningContextCompiler.class"))){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes());}}
 /** Pure compilation value only. This is not a registration, run permission or PreparedExactInput. */
 static final class Compilation {
  private final byte[] bytes;private final String inputHash,packageHash,promptHash,implementationHash;
  private Compilation(byte[] bytes,String inputHash,String packageHash,String promptHash,String implementationHash){this.bytes=bytes.clone();this.inputHash=inputHash;this.packageHash=packageHash;this.promptHash=promptHash;this.implementationHash=implementationHash;}
  byte[] bytes(){return bytes.clone();}String sha256(){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);}String inputSha256(){return inputHash;}String packageSha256(){return packageHash;}String promptSha256(){return promptHash;}String implementationSha256(){return implementationHash;}
 }
 Compilation compile(byte[] toolOriginal,byte[] promptOriginal,JsonNode document,JsonNode planning,FormalEvaluationContextProtocol.Node binding)throws Exception {
  if(binding==null||!"N01".equals(binding.nodeKind()))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var tool=read(toolOriginal);var prompt=read(promptOriginal);String implementation=implementationClassSha256();
  String abiHash=catalogue.schemaDigest("evaluation-planning-context-compiler");String outputHash=catalogue.schemaDigest("simulation-execution");
  catalogue.requirePublished(PACKAGE,abiHash);catalogue.validate("evaluation-planning-context-compiler",tool,"ToolPackage");
  catalogue.requirePublished(PROMPT,abiHash);catalogue.validate("evaluation-planning-context-compiler",prompt,"PromptPackage");
  requireCanonical(toolOriginal,tool);requireCanonical(promptOriginal,prompt);
  if(!IMPLEMENTATION.equals(tool.required("implementation_id").asText())||!VERSION.equals(tool.required("implementation_version").asText())||!implementation.equals(tool.required("implementation_class_sha256").asText()))bad();
  published(tool.required("input_schema"),INPUT,abiHash);published(tool.required("output_schema"),OUTPUT,outputHash);published(tool.required("prompt_package_schema"),PROMPT,abiHash);
  if(!binding.inputSchema().schemaRef().equals(INPUT)||!binding.inputSchema().schemaSha256().equals(abiHash)||!binding.outputSchema().schemaRef().equals(OUTPUT)||!binding.outputSchema().schemaSha256().equals(outputHash))bad();
  var input=json.object();input.set("document",document.deepCopy());input.set("planning",planning.deepCopy());input.set("prompt_package",prompt);catalogue.validate("evaluation-planning-context-compiler",input,"CompilationInput");
  var exact=json.object();var messages=exact.putArray("messages");for(var text:prompt.required("system_messages")){if(text.textValue().indexOf('\0')>=0)bad();messages.addObject().put("role","system").put("content",text.textValue());}
  messages.addObject().put("role","user").put("content",json.canonical(json.object().setAll(java.util.Map.of("document",document.deepCopy(),"planning",planning.deepCopy()))));exact.putArray("tools");catalogue.validate("simulation-execution",exact,"ExactInput");
  byte[] output=json.canonical(exact).getBytes(StandardCharsets.UTF_8);if(output.length==0||output.length>MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  return new Compilation(output,org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(input).getBytes(StandardCharsets.UTF_8)),org.apache.commons.codec.digest.DigestUtils.sha256Hex(toolOriginal),org.apache.commons.codec.digest.DigestUtils.sha256Hex(promptOriginal),implementation);
 }
 private JsonNode read(byte[] bytes)throws IOException{if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);try{return parser.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());}catch(CharacterCodingException|com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 private void requireCanonical(byte[] raw,JsonNode value)throws IOException{if(!Arrays.equals(raw,json.canonical(value).getBytes(StandardCharsets.UTF_8)))bad();}
 private void published(JsonNode actual,String ref,String sha)throws Exception{if(!ref.equals(actual.required("schema_ref").asText())||!sha.equals(actual.required("schema_sha256").asText()))bad();catalogue.requirePublished(ref,sha);}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
