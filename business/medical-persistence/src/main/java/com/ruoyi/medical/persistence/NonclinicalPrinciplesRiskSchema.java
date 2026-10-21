package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Fixed research and risk inputs. Neither source classification nor authoring confers approval. */
@Component public final class NonclinicalPrinciplesRiskSchema {
 public static final String PREFIX="nonclinical-principles-risk-v1\n", KIND="NONCLINICAL_PRINCIPLES_RISK";
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public static final Set<String> GROUPS=Set.of("MATERIAL","ELECTRICAL_MECHANICAL_EMC_RADIATION","SOFTWARE_CYBERSECURITY","BIOLOGICAL_BIOGENIC","CLEANING_STERILIZATION","ANIMAL_OTHER","LITERATURE","STABILITY","TECHNICAL_TEST_REPORT","OTHER");
 public record Source(String key,PermissionProtocol.Basis source,String rightsRevision,DatasetProtocol.EvidenceRef evidence,String literal,String nature,String documentName,String documentNumber,String documentLocation){}
 public record Fact(String text,List<String> sourceKeys){}
 public record Applicability(String group,String status,Fact rationale){}
 public record Detail(String field,Fact value){}
 public record Study(String key,String group,String version,String status,Fact purpose,Fact scope,Fact basis,Fact acceptanceCriteria,Fact samples,Fact plan,Fact equipmentConditions,Fact executionMethodTime,Fact originalData,Fact resultsStatistics,Fact deviations,Fact conclusion,Fact limitations,Fact responsibilitySignatureDate,List<String> reportSourceKeys,List<Detail> details){}
 public record Principle(String key,String clause,Fact requirement,String applicability,Fact nonApplicableReason,Fact method,List<String> objectiveSourceKeys,String status){}
 public record Control(String key,Fact measure,List<String> implementationSourceKeys,List<String> effectivenessSourceKeys,Fact effectivenessConclusion,Fact residualRisk,String warningApplicability,Fact warning){}
 public record Risk(String key,Fact hazard,Fact situation,Fact foreseeableEvents,Fact harm,Fact analysis,Fact evaluation,List<Control> controls,Fact residualRisk){}
 public record RiskSummary(Fact productLifecycleScope,Fact basis,Fact acceptanceCriteria,Fact overallResidualRisk,Fact benefitRisk,Fact conclusion,Fact responsible){}
 public record Standard(String key,String designation,String edition,String verification,String metadataSourceKey,String authorizedTextSourceKey){}
 public record Body(String key,String nodeId,String sectionId,String group,List<String> entityKeys,JsonNode content,List<String> sourceKeys){public String text(){return projection(content);}}
 /** Five columns are ordered node IDs within an actual TABLE row, not a parallel display-only table. */
 public record Clause(String clause,String requirement){}
 public record Checklist(List<Clause> clauses){}
 public record PrincipleRow(String principleKey,String tableBodyKey,String rowId,List<String> columnNodeIds){}
 public record Selection(String schemaVersion,String scenarioKind,List<String> sectionIds,List<Source> sources,String principleCatalogueSourceKey,List<Applicability> applicability,List<Study> studies,List<Principle> principles,List<Risk> risks,RiskSummary riskSummary,List<Standard> standards,List<Body> body,List<PrincipleRow> principleRows){}
 public Selection read(JsonNode n)throws Exception{try{
  var s=json.treeToValue(n,Selection.class);if(!"1.0.0".equals(s.schemaVersion())||!KIND.equals(s.scenarioKind())||s.sectionIds().isEmpty()||s.sectionIds().size()>128||s.sources().isEmpty()||s.sources().size()>512||s.studies().size()>256||s.principles().size()>512||s.risks().size()>256||s.body().size()>1024)bad();unique(s.sectionIds());s.sectionIds().forEach(WireValues::uuid);
  var keys=new HashSet<String>();for(var x:s.sources()){text(x.key());if(!keys.add(x.key())||!Set.of("BASIS","APPROVED_TEMPLATE","PLAN","ORIGINAL_DATA","ACTUAL_REPORT","IMPLEMENTATION_RECORD","EFFECTIVENESS_RECORD","APPROVED_DECISION","STANDARD_METADATA","AUTHORIZED_STANDARD_TEXT","WARNING","LITERATURE_SEARCH","LITERATURE_FULLTEXT","ATTACHMENT").contains(x.nature()))bad();WireValues.uuid(x.source().version_id());WireValues.hash(x.source().content_sha256());WireValues.uuid(x.rightsRevision());new TaskSourceReadReceiptSchema(new PermissionSchema()).evidence(tree(x.evidence()));if(!x.source().version_id().equals(x.evidence().sourceVersion()))bad();text(x.literal());text(x.documentName());text(x.documentNumber());text(x.documentLocation());}
  if(!keys.contains(s.principleCatalogueSourceKey()))bad();
  var groups=new HashSet<String>();for(var x:s.applicability()){if(!GROUPS.contains(x.group())||!groups.add(x.group()))bad();applicability(x.status());fact(x.rationale(),keys);}if(!groups.equals(GROUPS))bad();
  var entities=new HashSet<String>();for(var x:s.studies()){text(x.key());text(x.version());if(!entities.add(x.key())||!GROUPS.contains(x.group())||!Set.of("PLAN_ONLY","IN_PROGRESS","COMPLETED","UNKNOWN").contains(x.status()))bad();for(var f:studyFacts(x))fact(f,keys);refs(x.reportSourceKeys(),keys);var details=new HashSet<String>();for(var d:x.details()){text(d.field());if(!details.add(d.field()))bad();fact(d.value(),keys);}}
  var principles=new HashSet<String>();for(var x:s.principles()){text(x.key());text(x.clause());if(!entities.add(x.key())||!principles.add(x.key())||!Set.of("SUPPORTED","WAITING_EVIDENCE","UNKNOWN").contains(x.status()))bad();applicability(x.applicability());fact(x.requirement(),keys);fact(x.nonApplicableReason(),keys);fact(x.method(),keys);refs(x.objectiveSourceKeys(),keys);}
  for(var x:s.risks()){text(x.key());if(!entities.add(x.key())||x.controls().size()>256)bad();for(var f:riskFacts(x))fact(f,keys);for(var c:x.controls()){text(c.key());if(!entities.add(c.key()))bad();for(var f:controlFacts(c))fact(f,keys);refs(c.implementationSourceKeys(),keys);refs(c.effectivenessSourceKeys(),keys);applicability(c.warningApplicability());}}
  for(var f:summaryFacts(s.riskSummary()))fact(f,keys);if(!entities.add("RISK_SUMMARY"))bad();
  var standards=new HashSet<String>();for(var x:s.standards()){text(x.key());text(x.designation());text(x.edition());optional(x.authorizedTextSourceKey());if(!standards.add(x.key())||!keys.contains(x.metadataSourceKey())||!x.authorizedTextSourceKey().isEmpty()&&!keys.contains(x.authorizedTextSourceKey())||!Set.of("METADATA_ONLY","TEXT_AUTHORIZED","UNKNOWN").contains(x.verification()))bad();}
  var bodies=new HashSet<String>();var bodyNodes=new HashSet<String>();for(var b:s.body()){text(b.key());WireValues.uuid(b.nodeId());if(!bodyNodes.add(b.nodeId()))bad();if(!bodies.add(b.key())||!s.sectionIds().contains(b.sectionId())||!Set.of("STUDY","PRINCIPLES","RISK","RISK_SUMMARY","APPLICABILITY","STANDARD").contains(b.group())||b.content()==null||!b.content().isObject()||!Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(b.content().path("kind").asText()))bad();optional(b.text());if("TEXT".equals(b.content().path("kind").asText()))text(b.content().path("text").asText());refs(b.sourceKeys(),keys);unique(b.entityKeys());for(String key:b.entityKeys())if(!entities.contains(key)&&!GROUPS.contains(key)&&!standards.contains(key))bad();}
  var rows=new HashSet<String>();for(var r:s.principleRows()){if(!principles.contains(r.principleKey())||!rows.add(r.principleKey())||!bodies.contains(r.tableBodyKey())||r.columnNodeIds().size()!=5)bad();WireValues.uuid(r.rowId());unique(r.columnNodeIds());r.columnNodeIds().forEach(WireValues::uuid);}
  return s;
 }catch(IllegalArgumentException|NullPointerException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
 static List<Fact> studyFacts(Study x){return List.of(x.purpose(),x.scope(),x.basis(),x.acceptanceCriteria(),x.samples(),x.plan(),x.equipmentConditions(),x.executionMethodTime(),x.originalData(),x.resultsStatistics(),x.deviations(),x.conclusion(),x.limitations(),x.responsibilitySignatureDate());}
 static List<Fact> riskFacts(Risk x){return List.of(x.hazard(),x.situation(),x.foreseeableEvents(),x.harm(),x.analysis(),x.evaluation(),x.residualRisk());}
 static List<Fact> controlFacts(Control x){return List.of(x.measure(),x.effectivenessConclusion(),x.residualRisk(),x.warning());}
 static List<Fact> summaryFacts(RiskSummary x){return List.of(x.productLifecycleScope(),x.basis(),x.acceptanceCriteria(),x.overallResidualRisk(),x.benefitRisk(),x.conclusion(),x.responsible());}
 private static void fact(Fact f,Set<String> keys){optional(f.text());refs(f.sourceKeys(),keys);}
 public Checklist checklist(String value)throws Exception{return json.readValue(value,Checklist.class);}
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
