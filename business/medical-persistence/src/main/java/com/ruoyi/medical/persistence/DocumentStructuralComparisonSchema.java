package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class DocumentStructuralComparisonSchema {
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JsonSchema validator; private final String hash;
 final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
 public DocumentStructuralComparisonSchema()throws Exception {
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-structural-comparison-core.schema.json"))){byte[] bytes=in.readAllBytes();hash=WireValues.sha256(bytes);validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new JsonMapper().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());validator.initializeValidators();}
 }
 public String schemaSha256(){return hash;}
 public String canonical(Object v)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(v),StandardCharsets.UTF_8);}
 public String hash(Object v)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(v));}
 /** Shape and digest self-consistency only; never establishes completeness against source bodies. Use Engine.verifyAgainstInputs at trust boundaries. */
 public ObjectNode validate(JsonNode value)throws Exception {
  if(!validator.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("CoreResult")).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var r=(ObjectNode)value.deepCopy();catalogue.validate("interfaces",r.path("request"),"CompareRequest");if(!catalogue.schemaDigest("interfaces").equals(r.path("body_schema_sha256").asText()))bad();if(!hash.equals(r.path("schema_sha256").textValue())||r.path("record_count").intValue()!=r.path("records").size())bad();
  var input=r.deepCopy();for(String k:List.of("input_sha256","record_count","records","affected_node_ids","left_affected_section_ids","right_affected_section_ids","coverage"))input.remove(k);
  if(!hash(input).equals(r.path("input_sha256").textValue())||!r.path("input_sha256").equals(r.at("/coverage/selection_sha256"))||!hash(r.path("records")).equals(r.at("/coverage/delivered_sha256").textValue()))bad();
  var seen=new HashSet<String>();for(var record:r.path("records")){
   if(!seen.add(record.path("category").asText()+":"+record.path("identity").asText()))bad();
   String before=snippet(record,"before"),after=snippet(record,"after");String status=before==null?"ADDED":after==null?"REMOVED":before.equals(after)?"UNCHANGED":"CHANGED";
   if(before==null&&after==null||!status.equals(record.path("status").asText()))bad();
   if(!record.path("text_change").isNull()){if(!"NODE".equals(record.path("category").asText())||before==null||after==null||!record.path("identity").equals(record.path("text_change").path("node_id")))bad();for(String side:List.of("left","right")){var sel=record.path("text_change").path(side);String quote=sel.path("quote").asText();if(sel.path("end").longValue()-sel.path("start").longValue()!=quote.codePointCount(0,quote.length())||!WireValues.sha256(quote.getBytes(StandardCharsets.UTF_8)).equals(sel.path("quote_sha256").asText()))bad();var original=json.readTree(side.equals("left")?before:after);if(!original.at("/content/kind").asText().equals("TEXT"))bad();String text=original.at("/content/text").asText();long start=sel.path("start").longValue(),end=sel.path("end").longValue();int count=text.codePointCount(0,text.length());if(start>count||end>count||start>end||!quote.equals(text.substring(text.offsetByCodePoints(0,(int)start),text.offsetByCodePoints(0,(int)end))))bad();}}
  }return r;
 }
 private String snippet(JsonNode record,String side)throws Exception {var value=record.path(side+"_json");var hash=record.path(side+"_sha256");if(value.isNull()){if(!hash.isNull())bad();return null;}String s=value.textValue();JsonNode parsed=json.readTree(s);String definition=switch(record.path("category").asText()){case "NODE"->"Node";case "FIELD"->"FieldValue";case "EVIDENCE"->"EvidenceLink";case "TABLE_CELL"->"Cell";default->null;};if(definition!=null)catalogue.validate("interfaces",parsed,definition);if("FACT".equals(record.path("category").asText())){if(!parsed.isArray()||parsed.isEmpty())bad();for(var fact:parsed)catalogue.validate("interfaces",fact,"FactUsage");}if(!canonical(parsed).equals(s)||!WireValues.sha256(s.getBytes(StandardCharsets.UTF_8)).equals(hash.asText()))bad();return s;}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
