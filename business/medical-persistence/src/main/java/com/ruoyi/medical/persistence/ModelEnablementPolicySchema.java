package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Closed local wire/storage contract. No fixture/evaluation/agent qualification is created here. */
@Component public final class ModelEnablementPolicySchema {
 public static final int MAXIMUM_REQUEST_BYTES=8192;
 private final PermissionSchema json;private final JsonSchema root;private final String hash;
 private final JsonMapper reader=JsonMapper.builder().addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public ModelEnablementPolicySchema(PermissionSchema json)throws IOException{
  this.json=json;byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/model-enablement-policy-preparation.schema.json"))){bytes=in.readAllBytes();}
  hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);root=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());root.initializeValidators();
 }
 public String manifest(){return hash;}
 public JsonNode request(byte[] raw)throws IOException{if(raw==null||raw.length==0||raw.length>MAXIMUM_REQUEST_BYTES)invalid();return validate("Register",parse(raw));}
 public JsonNode parse(byte[] raw)throws IOException{try{return reader.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString());}catch(CharacterCodingException|com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public JsonNode validate(String name,JsonNode value){var def=root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));if(def==null||value==null||!def.validate(value).isEmpty())invalid();return value;}
 public String canonical(JsonNode value)throws IOException{return json.canonical(value);}
 public String hash(JsonNode value)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(value).getBytes(StandardCharsets.UTF_8));}
 public JsonNode tree(Object value){return reader.valueToTree(value);}
 public <T>T bind(JsonNode value,Class<T> type)throws IOException{return reader.treeToValue(value,type);}
 public static void key(String value){if(value==null||!value.matches("[A-Za-z0-9._:-]{16,128}"))invalid();}
 public static void purpose(String value){if(value==null||!value.matches("[A-Z][A-Z0-9_]{0,63}"))invalid();}
 public String operationHash(String tenant,String actor,JsonNode request)throws IOException{WireValues.uuid(tenant);WireValues.uuid(actor);validate("Register",request);var identity=json.object().put("operation",ModelEnablementPolicyPreparation.OPERATION).put("tenant_id",tenant).put("actor_id",actor).put("workflow_schema_sha256",manifest());identity.set("request",request);return hash(identity);}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
