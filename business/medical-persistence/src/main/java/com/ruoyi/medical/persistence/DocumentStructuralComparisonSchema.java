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
 private final JsonSchema validator,validatorV2; private final String hash,hashV2;private final boolean candidate;
 final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
 public DocumentStructuralComparisonSchema()throws Exception {this(false);}
 DocumentStructuralComparisonSchema(boolean candidate)throws Exception {this.candidate=candidate;
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream(candidate?"/candidate-structural-comparison-core.schema.json":"/document-structural-comparison-core.schema.json"))){byte[] bytes=in.readAllBytes();hash=WireValues.sha256(bytes);validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new JsonMapper().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());validator.initializeValidators();}
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream(candidate?"/candidate-structural-comparison-core-v2.schema.json":"/document-structural-comparison-core-v2.schema.json"))){byte[] bytes=in.readAllBytes();hashV2=WireValues.sha256(bytes);validatorV2=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(new JsonMapper().readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());validatorV2.initializeValidators();}
 }
 boolean candidate(){return candidate;}
 public String schemaSha256(){return hash;}
 String schemaSha256(boolean v2){return v2?hashV2:hash;}
 String schemaSha256(JsonNode core){return schemaSha256(versioned(core));}
 String schemaRef(JsonNode core){return "urn:medical:"+(candidate?"candidate":"document")+"-structural-comparison-core:"+(versioned(core)?"2.0.0":"1.0.0")+"#/$defs/CoreResult";}
 private boolean versioned(JsonNode core){String v=core.path("schema_version").asText();if(!Set.of("1.0.0","2.0.0").contains(v))bad();return "2.0.0".equals(v);}
 private String bodySchema(JsonNode core,String side){if(!versioned(core))return "interfaces";var p=catalogue.requirePublished(core.path(side+"_body_schema_ref").asText(),core.path(side+"_body_schema_sha256").asText());if(!Set.of("interfaces","document-body-v2").contains(p.name())||!"DocumentBody".equals(p.definition()))bad();return p.name();}
 public String canonical(Object v)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(v),StandardCharsets.UTF_8);}
 public String hash(Object v)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(v));}
 /** Shape and digest self-consistency only; never establishes completeness against source bodies. Use Engine.verifyAgainstInputs at trust boundaries. */
 public ObjectNode validate(JsonNode value)throws Exception {
  if(!(versioned(value)?validatorV2:validator).getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("CoreResult")).validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var r=(ObjectNode)value.deepCopy();catalogue.validate("interfaces",r.path("request"),candidate?"CandidateCompareRequest":"CompareRequest");if(!versioned(r)&&!catalogue.schemaDigest("interfaces").equals(r.path("body_schema_sha256").asText()))bad();bodySchema(r,"left");bodySchema(r,"right");if(!schemaSha256(r).equals(r.path("schema_sha256").textValue())||r.path("record_count").intValue()!=r.path("records").size())bad();
  var input=r.deepCopy();for(String k:List.of("input_sha256","record_count","records","affected_node_ids","left_affected_section_ids","right_affected_section_ids","coverage"))input.remove(k);
  if(!hash(input).equals(r.path("input_sha256").textValue())||!r.path("input_sha256").equals(r.at("/coverage/selection_sha256"))||!hash(r.path("records")).equals(r.at("/coverage/delivered_sha256").textValue()))bad();
  var seen=new HashSet<String>();for(var record:r.path("records")){
   if(!seen.add(record.path("category").asText()+":"+record.path("identity").asText()))bad();
   String before=snippet(record,"before",bodySchema(r,"left")),after=snippet(record,"after",bodySchema(r,"right"));String status=before==null?"ADDED":after==null?"REMOVED":before.equals(after)?"UNCHANGED":"CHANGED";
   if(before==null&&after==null||!status.equals(record.path("status").asText()))bad();
   if(!record.path("text_change").isNull()){if(!"NODE".equals(record.path("category").asText())||before==null||after==null||!record.path("identity").equals(record.path("text_change").path("node_id")))bad();for(String side:List.of("left","right")){var sel=record.path("text_change").path(side);String quote=sel.path("quote").asText();if(sel.path("end").longValue()-sel.path("start").longValue()!=quote.codePointCount(0,quote.length())||!WireValues.sha256(quote.getBytes(StandardCharsets.UTF_8)).equals(sel.path("quote_sha256").asText()))bad();var original=json.readTree(side.equals("left")?before:after);if(!original.at("/content/kind").asText().equals("TEXT"))bad();String text=original.at("/content/text").asText();long start=sel.path("start").longValue(),end=sel.path("end").longValue();int count=text.codePointCount(0,text.length());if(start>count||end>count||start>end||!quote.equals(text.substring(text.offsetByCodePoints(0,(int)start),text.offsetByCodePoints(0,(int)end))))bad();}}
  }return r;
 }
 private String snippet(JsonNode record,String side,String publication)throws Exception {var value=record.path(side+"_json");var hash=record.path(side+"_sha256");if(value.isNull()){if(!hash.isNull())bad();return null;}String s=value.textValue();JsonNode parsed=json.readTree(s);String definition=switch(record.path("category").asText()){case "NODE"->"Node";case "FIELD"->"FieldValue";case "EVIDENCE"->"EvidenceLink";case "TABLE_CELL"->"Cell";default->null;};if(definition!=null)catalogue.validate(publication,parsed,definition);if("FACT".equals(record.path("category").asText())){if(!parsed.isArray()||parsed.isEmpty())bad();for(var fact:parsed)catalogue.validate(publication,fact,"FactUsage");}if(!canonical(parsed).equals(s)||!WireValues.sha256(s.getBytes(StandardCharsets.UTF_8)).equals(hash.asText()))bad();return s;}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
