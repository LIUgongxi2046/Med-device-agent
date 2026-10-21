package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Source-pinned IFU and independent label. Authoring is not regulatory approval. */
@Component public final class InstructionsLabelSchema {
 public static final String PREFIX="instructions-label-v1\n", KIND="INSTRUCTIONS_LABEL";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public static final Set<String> IFU_GROUPS=Set.of("IFU-01","IFU-02","IFU-03","IFU-04","IFU-05","IFU-06","IFU-07","IFU-08","IFU-09","IFU-10","IFU-11","IFU-12","IFU-13","IFU-14");
 public static final Set<String> LABEL_GROUPS=Set.of("IDENTITY","REGISTRANT_AGENT","REGISTRATION","MANUFACTURER_LICENSE","DATES_LIFETIME","POWER","SYMBOLS","WARNINGS","STORAGE_OPERATION","ENVIRONMENT","RADIATION");
 public static final Set<String> WARNING_GROUPS=Set.of("USER_POPULATION","HAZARDS_LIMITATIONS","ACCIDENT_HANDLING","MONITORING_ASSESSMENT_CONTROL","SINGLE_USE_STERILITY_PACKAGING","COMBINED_USE","INTERFERENCE","ADVERSE_EVENT_COMPONENTS","DISPOSAL","OTHER_WARNING");
 public static final Set<String> REUSE_GROUPS=Set.of("CLEANING","DISINFECTION","PACKAGING","STERILIZATION","REUSE_LIMIT");
 public record Source(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal,String nature){}
 public record Fact(String text,List<String> sourceKeys){}
 public record Field(String key,String group,String applicability,Fact decision,Fact value){}
 public record Document(String kind,String version,String stage,String productVersion,String layout,String ruleSourceKey,Fact versionRecord,List<Field> fields){}
 /** This contract must be present verbatim in an approved rules source; it is never generated as law. */
 public record Decision(String subject,String status,String productVersion,String outcome){}
 public record PackagingRecord(String version,String productVersion,boolean minimumSalesUnit){}
 public record TranslationRecord(String originalSourceVersion,String originalSha256,String chineseVersion,String productVersion,String outcome){}
 public record VersionRecord(String documentKind,String version,String productVersion,String stage,String packagingVersion){}
 public record Rule(String group,List<String> fieldKeys,boolean mandatory,boolean minimumContent,boolean deferrableInApplication){}
 public record Rules(String documentKind,String stage,String productVersion,String layout,List<Rule> rules,Fact seeInstructions,Fact layoutBasis){}
 public record ProductFact(String key,Fact value,String productVersion){}
 public record Comparison(String key,String documentKind,String fieldKey,String productFactKey,String context,String productContext,String unit,String productUnit,String decision,Fact decisionBasis){}
 public record Body(String key,String nodeId,String sectionId,String documentKind,List<String> entityKeys,JsonNode content,List<String> sourceKeys){public String text(){return NonclinicalPrinciplesRiskSchema.projection(content);}}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,List<Source> sources,List<Document> documents,List<ProductFact> productFacts,List<Comparison> comparisons,String importStatus,Fact importDecision,Fact originalInstructions,Fact translationVerification,Fact packagingVersion,List<Body> body){}
 public Selection read(JsonNode n)throws Exception{try{
  var s=json.treeToValue(n,Selection.class);if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||s.sectionIds().isEmpty()||s.sectionIds().size()>128||s.sources().size()>512||s.documents().size()!=2||s.body().size()>1024||s.productFacts().size()>512||s.comparisons().size()>1024)bad();unique(s.sectionIds());s.sectionIds().forEach(WireValues::uuid);
  var keys=new HashSet<String>();for(var x:s.sources()){text(x.key());if(!keys.add(x.key())||!Set.of("APPROVED_RULES","APPROVED_DECISION","APPROVED_PRODUCT_FACT","REGISTRATION_PROOF","APPROVED_TECHNICAL_REQUIREMENTS","BATCH_RECORD","VERSION_RECORD","RISK_WARNING","VALIDATION_REPORT","ORIGINAL_INSTRUCTIONS","TRANSLATION_VERIFICATION","PACKAGING","BASIS").contains(x.nature()))bad();WireValues.uuid(x.source().version_id());WireValues.hash(x.source().content_sha256());WireValues.uuid(x.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(x.evidence()));if(!x.source().version_id().equals(x.evidence().sourceVersion()))bad();text(x.literal());}
  var kinds=new HashSet<String>();var entities=new HashSet<String>();for(var d:s.documents()){if(!Set.of("IFU","LABEL").contains(d.kind())||!kinds.add(d.kind())||!Set.of("APPLICATION_DRAFT","APPROVED_MARKET","REVISION").contains(d.stage())||!Set.of("FULL","SPACE_LIMITED").contains(d.layout())||"IFU".equals(d.kind())&&!"FULL".equals(d.layout())||d.ruleSourceKey()==null||!d.ruleSourceKey().isEmpty()&&!keys.contains(d.ruleSourceKey()))bad();text(d.version());text(d.productVersion());fact(d.versionRecord(),keys);entities.add(d.kind());var fields=new HashSet<String>();for(var f:d.fields()){text(f.key());text(f.group());if(!fields.add(f.key())||!entities.add(d.kind()+":"+f.key()))bad();if(!Set.of("REQUIRED","NOT_APPLICABLE","UNKNOWN","PENDING_REGISTRATION").contains(f.applicability()))bad();fact(f.decision(),keys);fact(f.value(),keys);}}
  var products=new HashSet<String>();for(var f:s.productFacts()){text(f.key());text(f.productVersion());if(!products.add(f.key()))bad();fact(f.value(),keys);}
  var comparisons=new HashSet<String>();for(var c:s.comparisons()){text(c.key());if(!comparisons.add(c.key())||!entities.contains(c.documentKind()+":"+c.fieldKey())||!products.contains(c.productFactKey())||!Set.of("EQUAL","CONDITION_DIFFERENT","CONFLICT","UNKNOWN").contains(c.decision()))bad();optional(c.context());optional(c.productContext());optional(c.unit());optional(c.productUnit());fact(c.decisionBasis(),keys);}
  if(!Set.of("DOMESTIC","IMPORTED","UNKNOWN").contains(s.importStatus()))bad();for(var f:List.of(s.importDecision(),s.originalInstructions(),s.translationVerification(),s.packagingVersion()))fact(f,keys);
  var bodies=new HashSet<String>();var nodes=new HashSet<String>();for(var b:s.body()){text(b.key());WireValues.uuid(b.nodeId());if(!nodes.add(b.nodeId())||!bodies.add(b.key())||!s.sectionIds().contains(b.sectionId())||!kinds.contains(b.documentKind())||!b.content().isObject()||!Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(b.content().path("kind").asText()))bad();refs(b.sourceKeys(),keys);if(b.entityKeys().isEmpty())bad();unique(b.entityKeys());for(String e:b.entityKeys())if(!entities.contains(e)||!e.equals(b.documentKind())&&!e.startsWith(b.documentKind()+":"))bad();}
  return s;
 }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 public Rules rules(String s)throws Exception{var r=json.readValue(s,Rules.class);if(r.rules()==null||r.rules().size()>256||r.layoutBasis()==null||r.seeInstructions()==null)bad();for(var rule:r.rules()){if(rule==null||rule.group()==null||rule.fieldKeys()==null||rule.fieldKeys().isEmpty())bad();for(String key:rule.fieldKeys())text(key);}return r;}
 public Decision decision(String s)throws Exception{return json.readValue(s,Decision.class);}
 public VersionRecord version(String s)throws Exception{return json.readValue(s,VersionRecord.class);}
 public PackagingRecord packaging(String s)throws Exception{return json.readValue(s,PackagingRecord.class);}
 public TranslationRecord translation(String s)throws Exception{return json.readValue(s,TranslationRecord.class);}
 public JsonNode tree(Object v){return json.valueToTree(v);}
 public Selection definition(String d)throws Exception{if(!d.startsWith(PREFIX))bad();return read(json.readTree(d.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String d=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(d.length()>200000)bad();return d;}
 public void author(JsonNode a)throws Exception{String d=a.required("specification").required("INPUTS").required("definition").asText();if(!d.startsWith(PREFIX))return;var s=definition(d);var roots=new HashSet<PermissionProtocol.Basis>();for(var r:a.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))roots.add(new PermissionProtocol.Basis(r.required("version_id").asText(),r.required("content_sha256").asText()));for(var x:s.sources())if(!roots.contains(x.source())||!a.required("project_id").asText().equals(x.evidence().projectId()))bad();}
 private static void fact(Fact f,Set<String> keys){optional(f.text());refs(f.sourceKeys(),keys);}
 private static void refs(List<String> x,Set<String> keys){if(x==null||x.size()>512||!keys.containsAll(x))bad();unique(x);}
 private static void unique(List<String> x){if(new HashSet<>(x).size()!=x.size())bad();}
 private static void text(String x){ProjectSchema.text(x,16000);}private static void optional(String x){if(x==null||x.length()>16000)bad();}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
