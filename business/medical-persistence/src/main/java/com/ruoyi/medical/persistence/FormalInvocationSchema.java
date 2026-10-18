package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.core.StreamReadFeature;import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;import com.ruoyi.medical.contracts.*;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
/** Closed local protocol, independently pinned; does not mutate the frozen catalogue. */
public final class FormalInvocationSchema {
 public static final String REF="urn:medical:formal-invocation:1.0.0";
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema schema;private final String documentHash;
 public FormalInvocationSchema()throws Exception{byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/formal-invocation.schema.json"))){bytes=in.readAllBytes();}documentHash=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();}
 public JsonNode read(String definition,byte[] bytes,int maximum)throws Exception{if(bytes==null||bytes.length==0||bytes.length>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var value=json.readTree(bytes);require(definition,value);return value;}
 public JsonNode parse(String value)throws IOException{if(value==null||value.getBytes(StandardCharsets.UTF_8).length>16777216)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return json.readTree(value);}
 public void require(String definition,JsonNode value){if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 public String canonical(Object value)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}
 public String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
 public String documentSha256(){return documentHash;}public String manifestSha256(){return documentHash;}
 public FormalInvocationPricing.Schedule price(String priceVersion,JsonNode value)throws Exception{require("PriceProfile",value);return new FormalInvocationPricing.Schedule(priceVersion,value.required("currency").textValue(),BudgetSchema.money(value.required("uncached_input_per_million").textValue()),BudgetSchema.money(value.required("cache_read_per_million").textValue()),BudgetSchema.money(value.required("cache_write_per_million").textValue()),BudgetSchema.money(value.required("output_per_million").textValue()),value.required("rounding").textValue());}
}
