package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component public final class FormalEvaluationPolicySchema {
 public static final int MAXIMUM_BYTES=1048576;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 private final JsonSchema schema;private final String hash;
 public FormalEvaluationPolicySchema()throws IOException{byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-evaluation-policy.schema.json"))){bytes=in.readAllBytes();}hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}
 public String schemaSha256(){return hash;}
 public JsonNode request(String name,byte[] bytes)throws IOException{if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_BYTES)invalid();try{String utf8=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();return validate(name,parser.readTree(utf8));}catch(java.nio.charset.CharacterCodingException|com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public JsonNode validate(String name,JsonNode node){if(!Set.of("Author","Decide").contains(name))throw new IllegalArgumentException("Unsupported policy request");if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty())invalid();if(name.equals("Author")){expertDomains(node.required("specification"));var ids=new HashSet<String>();for(var use:node.required("root_uses"))if(!ids.add(use.required("version").required("version_id").asText()))invalid();}return node;}
 static void expertDomains(JsonNode specification){
  var declared=new TreeSet<String>();for(var rule:specification.required("dimensions"))if(!declared.add(rule.required("dimension").asText()))invalid();
  var domains=specification.required("expert_domains");var actual=new TreeSet<String>();domains.fieldNames().forEachRemaining(actual::add);if(!domains.isObject()||!actual.equals(declared))invalid();
  for(var domain:domains){if(!domain.isTextual()||domain.asText().isBlank())invalid();String value=domain.asText();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))invalid();}else if(Character.isLowSurrogate(c)||Character.isISOControl(c))invalid();}}
 }
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
