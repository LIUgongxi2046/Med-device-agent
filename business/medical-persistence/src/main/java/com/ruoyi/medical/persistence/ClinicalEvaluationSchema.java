package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed clinical evaluation and exemption inputs. Neither source classification nor authoring confers approval. */
@Component public final class ClinicalEvaluationSchema {
 public static final String PREFIX="clinical-evaluation-v1\n", KIND="CLINICAL_EVALUATION";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public static final Set<String> PATHS=Set.of("EQUIVALENT_DEVICE","COMPARABLE_DEVICE","CLINICAL_TRIAL","COMBINATION","EXEMPTION","UNKNOWN");
 public record Source(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal,String nature,String documentName,String documentNumber,String documentLocation){}
 public record Fact(String text,List<String> sourceKeys){}
 public record Field(String name,Fact value){}
 public record Record(String key,String group,String applicability,Fact applicabilityBasis,List<Field> fields,List<String> attachmentSourceKeys){}
 public record Comparison(String key,String group,String evaluationPath,String item,String applicability,Fact applicabilityBasis,Fact applicant,Fact comparator,Fact difference,Fact supportingMaterials,Fact analysis,String tableBodyKey,String rowId,List<String> columnNodeIds){}
 public record Body(String key,String nodeId,String sectionId,String group,List<String> entityKeys,JsonNode content,List<String> sourceKeys){public String text(){return projection(content);}}
 public record PathApproval(String path,List<String> combinationPaths,String outcome){}
 public PathApproval approval(String literal)throws Exception{return json.readValue(literal,PathApproval.class);}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,List<Source> sources,String path,List<String> combinationPaths,Fact pathDecision,List<Record> records,List<Comparison> comparisons,List<Body> body){}
 public Selection read(JsonNode n)throws Exception{try{
  var s=json.treeToValue(n,Selection.class);if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||!PATHS.contains(s.path())||s.sectionIds().isEmpty()||s.sectionIds().size()>128||s.sources().size()>512||s.records().size()>512||s.comparisons().size()>512||s.body().size()>1024)bad();unique(s.sectionIds());s.sectionIds().forEach(WireValues::uuid);unique(s.combinationPaths());if(!Set.of("EQUIVALENT_DEVICE","COMPARABLE_DEVICE","CLINICAL_TRIAL").containsAll(s.combinationPaths()))bad();
  var keys=new HashSet<String>();for(var x:s.sources()){text(x.key());if(!keys.add(x.key())||!Set.of("BASIS","APPROVED_DECISION","CURRENT_CATALOGUE","DOMESTIC_REGISTRATION","COMPARISON_EVIDENCE","LITERATURE_SEARCH","LITERATURE_FULLTEXT","TRIAL_PLAN","ETHICS_APPROVAL","CONSENT_SAMPLE","TRIAL_REPORT","ORIGINAL_DATABASE","ANALYSIS_CODE","QUALIFICATION","SIGNATURE","ATTACHMENT").contains(x.nature()))bad();WireValues.uuid(x.source().version_id());WireValues.hash(x.source().content_sha256());WireValues.uuid(x.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(x.evidence()));if(!x.source().version_id().equals(x.evidence().sourceVersion()))bad();text(x.literal());text(x.documentName());text(x.documentNumber());text(x.documentLocation());}
  fact(s.pathDecision(),keys);var entities=new HashSet<String>();entities.add("PATH");
  for(var x:s.records()){text(x.key());if(!entities.add(x.key())||!Set.of("COVER","PRODUCT","SCOPE","PATH","CONCLUSION","EVALUATOR","OTHER","LITERATURE","TRIAL","EXEMPTION").contains(x.group())||x.fields().size()>64)bad();applicability(x.applicability());fact(x.applicabilityBasis(),keys);refs(x.attachmentSourceKeys(),keys);var names=new HashSet<String>();for(var f:x.fields()){text(f.name());if(!names.add(f.name()))bad();fact(f.value(),keys);}}
  for(var x:s.comparisons()){text(x.key());text(x.item());if(!PATHS.contains(x.evaluationPath()))bad();if(!entities.add(x.key())||!Set.of("DEVICE","CATALOGUE_ENTRY","DOMESTIC_DEVICE").contains(x.group()))bad();applicability(x.applicability());for(var f:comparisonFacts(x))fact(f,keys);text(x.tableBodyKey());WireValues.uuid(x.rowId());if(x.columnNodeIds().size()!=6)bad();unique(x.columnNodeIds());x.columnNodeIds().forEach(WireValues::uuid);}
  var bodies=new HashSet<String>();var nodes=new HashSet<String>();for(var b:s.body()){text(b.key());WireValues.uuid(b.nodeId());if(!bodies.add(b.key())||!nodes.add(b.nodeId())||!s.sectionIds().contains(b.sectionId())||b.content()==null||!b.content().isObject()||!Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(b.content().path("kind").asText()))bad();text(b.group());optional(b.text());refs(b.sourceKeys(),keys);refs(b.entityKeys(),entities);}
  for(var c:s.comparisons())if(!bodies.contains(c.tableBodyKey()))bad();return s;
 }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 static List<Fact> comparisonFacts(Comparison x){return List.of(x.applicabilityBasis(),x.applicant(),x.comparator(),x.difference(),x.supportingMaterials(),x.analysis());}
 private static void fact(Fact f,Set<String> keys){optional(f.text());refs(f.sourceKeys(),keys);}
 public JsonNode tree(Object v){return json.valueToTree(v);}
 public Selection definition(String d)throws Exception{if(!d.startsWith(PREFIX))bad();return read(json.readTree(d.substring(PREFIX.length())));}
 public String definition(JsonNode n)throws Exception{String d=PREFIX+new String(ContentReadAuthorization.canonicalBytes(tree(read(n))),java.nio.charset.StandardCharsets.UTF_8);if(d.length()>200000)bad();return d;}
 public void author(JsonNode a)throws Exception{String d=a.required("specification").required("INPUTS").required("definition").asText();if(!d.startsWith(PREFIX))return;var s=definition(d);var roots=new HashSet<PermissionProtocol.Basis>();for(var r:a.required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))roots.add(new PermissionProtocol.Basis(r.required("version_id").asText(),r.required("content_sha256").asText()));for(var x:s.sources())if(!roots.contains(x.source())||!a.required("project_id").asText().equals(x.evidence().projectId()))bad();}
 static String projection(JsonNode n){if(n==null)return "";return switch(n.path("kind").asText()){case "TEXT" -> n.path("text").asText();case "IMAGE" -> n.path("alt_text").asText()+" "+n.path("caption").asText();case "FORMULA" -> n.path("latex").asText()+" "+n.path("description").asText();case "TABLE" -> n.path("caption").asText();default -> "";};}
 private static void applicability(String x){if(!Set.of("REQUIRED","NOT_APPLICABLE","UNKNOWN").contains(x))bad();}
 private static void refs(List<String> x,Set<String> keys){if(x==null||x.size()>512||!keys.containsAll(x))bad();unique(x);}
 private static void unique(List<String> x){if(new HashSet<>(x).size()!=x.size())bad();}
 private static void text(String x){ProjectSchema.text(x,16000);}private static void optional(String x){if(x==null||x.length()>16000)bad();}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
