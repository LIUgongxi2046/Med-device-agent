package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
final class DocumentCandidateAdoptionSchema {
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 private final JsonSchema schema;
 DocumentCandidateAdoptionSchema()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-candidate-adoption.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(in),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}schema.initializeValidators();}
 JsonNode readCandidateCompare(byte[] bytes)throws Exception {if(bytes==null||bytes.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();var n=json.readTree(text);SchemaCatalogue.fromClasspath().validate("interfaces",n,"CandidateCompareRequest");return n;}
 <T>T read(byte[] bytes,String definition,Class<T> type)throws Exception{if(bytes==null||bytes.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);String text=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();var n=json.readTree(text);if(n==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return json.treeToValue(n,type);}
}
