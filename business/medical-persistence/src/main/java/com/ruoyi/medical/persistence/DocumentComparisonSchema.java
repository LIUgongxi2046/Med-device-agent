package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Original published BX-05 request/receipt plus exact local complete difference profile. */
public final class DocumentComparisonSchema{
 static final String CORE_REF="urn:medical:document-structural-comparison-core:1.0.0#/$defs/CoreResult";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 private final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();final DocumentStructuralComparisonSchema core=new DocumentStructuralComparisonSchema();private final String manifest;private final com.networknt.schema.JsonSchema views,viewsV2;
 public DocumentComparisonSchema()throws Exception{
 byte[] raw;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-comparison-views.schema.json"))){raw=in.readAllBytes();}
 var doc=json.readTree(raw);try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-structural-comparison-core.schema.json"))){if(!doc.at("/$defs/CoreResult").equals(json.readTree(in).at("/$defs/CoreResult")))throw new IllegalStateException("COMPARISON_CORE_SCHEMA_DRIFT");}
 views=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(doc,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());views.initializeValidators();
 try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-comparison-views-v2.schema.json"))){var v2=json.readTree(in);try(var coreIn=Objects.requireNonNull(getClass().getResourceAsStream("/document-structural-comparison-core-v2.schema.json"))){if(!v2.at("/$defs/CoreResult").equals(json.readTree(coreIn).at("/$defs/CoreResult")))throw new IllegalStateException("COMPARISON_CORE_SCHEMA_DRIFT");}viewsV2=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(v2,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());viewsV2.initializeValidators();}
 manifest=hash(Map.of("schema_version","1.0.0","core_schema",core.schemaSha256(),"interfaces_schema",catalogue.schemaDigest("interfaces"),"views_schema",WireValues.sha256(raw)));}
 InterfaceProtocol.CompareRequest request(byte[] raw,int maximum)throws Exception{if(raw.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var value=json.readTree(raw);catalogue.validate("interfaces",value,"CompareRequest");return json.treeToValue(value,InterfaceProtocol.CompareRequest.class);}
 static void key(String key){if(key==null||key.length()<16||key.length()>128||!key.matches("[\\x21-\\x7e]+"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 JsonNode receipt(JsonNode node){catalogue.validate("interfaces",node,"Comparison");return node.deepCopy();}
 JsonNode view(String definition,JsonNode node)throws Exception{
 if(!(("Read".equals(definition)&&"2.0.0".equals(node.at("/result/schema_version").asText()))?viewsV2:views).getSubSchema(new com.networknt.schema.JsonNodePath(com.networknt.schema.PathType.JSON_POINTER).append("$defs").append(definition)).validate(node).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
 if("Read".equals(definition)){
 var comparison=receipt(node.required("comparison"));var result=core.validate(node.required("result"));
 if(!comparison.required("left_id").equals(result.at("/request/left_revision"))||!comparison.required("right_revision").equals(result.at("/request/right_revision"))||!comparison.required("affected_node_ids").equals(result.required("affected_node_ids"))||!comparison.at("/differences/sha256").asText().equals(hash(result))||!comparison.at("/differences/schema_ref").asText().equals(core.schemaRef(result))||!comparison.at("/differences/coverage").equals(result.required("coverage"))||node.required("left_is_current").booleanValue()!=comparison.required("left_id").equals(node.required("current_document_revision"))||node.required("right_is_current").booleanValue()!=comparison.required("right_revision").equals(node.required("current_document_revision")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
 }else if("Recovery".equals(definition)&&!node.required("comparison").isNull()){receipt(node.required("comparison"));if(!node.required("comparison_id").equals(node.at("/comparison/comparison_id")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 return node;
 }
 JsonNode read(String value)throws Exception{return json.readTree(value);}
 <T>T decode(String value,Class<T> type)throws Exception{return json.treeToValue(read(value),type);}
 JsonNode tree(Object value)throws Exception{return read(canonical(value));}
 String canonical(Object value)throws Exception{return core.canonical(value);}
 String hash(Object value)throws Exception{return core.hash(value);}
 byte[] bytes(Object value)throws Exception{return canonical(value).getBytes(StandardCharsets.UTF_8);}
 String manifest(){return manifest;}
}
