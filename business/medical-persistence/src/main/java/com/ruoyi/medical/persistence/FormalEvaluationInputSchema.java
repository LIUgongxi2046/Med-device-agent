package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Describes actual human-provided input bytes. Validation creates no approval or production eligibility. */
@Component public final class FormalEvaluationInputSchema {
 public static final String REF="urn:medical:formal-evaluation-input:1.0.0#/$defs/Document";
 public static final String ARTIFACT_KIND="FORMAL_EVALUATION_INPUT";
 public static final int MAXIMUM_BYTES=2*1024*1024;
 private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 private final PermissionSchema json;private final JsonSchema document;private final String schemaHash;
 public FormalEvaluationInputSchema(PermissionSchema json)throws IOException{
  this.json=json;byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-input.schema.json"))){bytes=in.readAllBytes();}
  schemaHash=DigestUtils.sha256Hex(bytes);var root=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  root.initializeValidators();document=root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Document"));
 }
 public String schemaSha256(){return schemaHash;}
 public JsonNode document(byte[] raw)throws IOException{
  if(raw==null||raw.length==0||raw.length>MAXIMUM_BYTES)invalid();JsonNode node;
  try{String utf8=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();node=reader.readTree(utf8);}catch(java.nio.charset.CharacterCodingException|com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
  validate(node);if(!Arrays.equals(raw,json.canonical(node).getBytes(StandardCharsets.UTF_8)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return node;
 }
 public JsonNode validate(JsonNode node)throws IOException{
  if(node==null||!document.validate(node).isEmpty())invalid();unicode(node);ProjectSchema.text(node.required("document_type").asText(),64);
  var ids=new HashSet<String>();for(var section:node.required("sections")){
   if(!ids.add(section.required("section_id").asText()))invalid();String text=section.required("text").asText();if(text.isBlank()||text.indexOf('\0')>=0)invalid();
  }
  if(json.canonical(node).getBytes(StandardCharsets.UTF_8).length>MAXIMUM_BYTES)invalid();return node;
 }
 private static void unicode(JsonNode node){
  if(node.isTextual()){String text=node.textValue();for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))invalid();}else if(Character.isLowSurrogate(c))invalid();}}
  else if(node.isContainerNode())node.forEach(FormalEvaluationInputSchema::unicode);
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
