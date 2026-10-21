package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.ruoyi.medical.persistence.NonclinicalPrinciplesRiskSchema.*;

/** N04 supplies authored content; N05 verifies the actual body and independent physical source reads. */
final class NonclinicalPrinciplesRiskPipeline {
 private NonclinicalPrinciplesRiskPipeline(){}
 private static final Map<String,List<String>> DETAILS=Map.of(
  "MATERIAL",List.of("MATERIAL_CHARACTERIZATION","PERFORMANCE_BASIS","DESIGN_INPUT","CLINICAL_SIGNIFICANCE","INTERFACE_COMPATIBILITY_APPLICABILITY"),
  "ELECTRICAL_MECHANICAL_EMC_RADIATION",List.of("SAFETY_REQUIREMENTS","TEST_CONDITIONS","MODEL_COVERAGE","LIMITATIONS"),
  "SOFTWARE_CYBERSECURITY",List.of("SOFTWARE_VERSION","REQUIREMENTS","IMPLEMENTATION_CORE_FUNCTIONS","RISK_TRACEABILITY","VERIFICATION_DEFECTS","VULNERABILITY_ASSESSMENT","AI_OFF_THE_SHELF_INTEROPERABILITY_CLOUD_APPLICABILITY"),
  "BIOLOGICAL_BIOGENIC",List.of("CONTACT_MATERIAL_SITE_DURATION","RESIDUES","EVALUATION_STRATEGY","TEST_OR_EXEMPTION_BASIS","BIOGENIC_PROCESS_INACTIVATION_IMMUNE_RISK"),
  "CLEANING_STERILIZATION",List.of("METHOD_PARAMETERS","PROCESS_VALIDATION","RESIDUES","MATERIAL_TOLERANCE","REUSE_LIMIT","PACKAGING_IFU_ALIGNMENT"),
  "ANIMAL_OTHER",List.of("NECESSITY_DECISION","EXECUTION_LIMITATIONS"),
  "LITERATURE",List.of("SEARCH_SCOPE_PROCESS","ACTUAL_SEARCH_RESULTS","FULLTEXT_TRANSLATION_SUPPORT"),
  "STABILITY",List.of("SHELF_LIFE","USE_TRANSPORT_PACKAGING","TIMEPOINTS_SAMPLES","EVALUATION_INDICATORS","LABEL_STORAGE_ALIGNMENT"),
  "TECHNICAL_TEST_REPORT",List.of("FIXED_TECHNICAL_REQUIREMENT","TEST_ENTITY","SAMPLE_MODEL","INDICATOR_COVERAGE","REPORT_DATE_ATTACHMENTS"),
  "OTHER",List.of("PRODUCT_PATH_APPLICABILITY","SUPPORTING_EVIDENCE"));
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new NonclinicalPrinciplesRiskSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",KIND,"human_review_required",true,"conclusion_authority","MACHINE_CHECK_NOT_RESEARCH_OR_REGULATORY_APPROVAL"));var reasons=out.putArray("unknown_reasons");
  for(var a:s.applicability()){
   requireFact(s,a.rationale(),"APPLICABILITY:"+a.group(),reasons);if("UNKNOWN".equals(a.status()))reasons.add("APPLICABILITY_UNKNOWN:"+a.group());
   if(!hasNature(s,a.rationale().sourceKeys(),"APPROVED_DECISION"))reasons.add("APPLICABILITY_DECISION_MISSING:"+a.group());
   if("REQUIRED".equals(a.status())&&s.studies().stream().noneMatch(x->a.group().equals(x.group())))reasons.add("REQUIRED_RESEARCH_MISSING:"+a.group());
   requireBody(s,"APPLICABILITY",a.group(),reasons);
  }
  for(var x:s.studies()){
   requireBody(s,"STUDY",x.key(),reasons);
   if(s.applicability().stream().noneMatch(a->a.group().equals(x.group())&&"REQUIRED".equals(a.status())))reasons.add("STUDY_APPLICABILITY_UNRESOLVED:"+x.key());
   for(var f:List.of(x.purpose(),x.scope(),x.basis(),x.acceptanceCriteria(),x.samples(),x.plan()))requireFact(s,f,"STUDY_PLAN:"+x.key(),reasons);
   if(!hasNature(s,x.plan().sourceKeys(),"PLAN"))reasons.add("STUDY_PLAN_SOURCE_MISSING:"+x.key());
   if(!"COMPLETED".equals(x.status())){
    reasons.add("RESEARCH_NOT_COMPLETED:"+x.key());
    if("PLAN_ONLY".equals(x.status())&&(!x.originalData().text().isBlank()||!x.resultsStatistics().text().isBlank()||!x.conclusion().text().isBlank()||!x.reportSourceKeys().isEmpty()))reasons.add("PLAN_CANNOT_DECLARE_ACTUAL_RESULTS:"+x.key());
   }else{
    for(var f:studyFacts(x))requireFact(s,f,"STUDY_RESULT:"+x.key(),reasons);
    if(!exactNature(s,x.originalData(),Set.of("ORIGINAL_DATA"))||!allNature(s,x.reportSourceKeys(),Set.of("ACTUAL_REPORT"))||!exactNature(s,x.resultsStatistics(),Set.of("ACTUAL_REPORT"))||!exactNature(s,x.conclusion(),Set.of("ACTUAL_REPORT")))reasons.add("ACTUAL_DATA_REPORT_CONCLUSION_MISSING:"+x.key());
   }
   for(String field:DETAILS.get(x.group())){var d=x.details().stream().filter(v->field.equals(v.field())).findFirst().orElse(null);if(d==null)reasons.add("SPECIALIZED_RESEARCH_FIELD_MISSING:"+x.key()+":"+field);else requireFact(s,d.value(),"SPECIALIZED_RESEARCH:"+x.key()+":"+field,reasons);}
   if("LITERATURE".equals(x.group())&&x.details().stream().filter(d->"ACTUAL_SEARCH_RESULTS".equals(d.field())).noneMatch(d->hasNature(s,d.value().sourceKeys(),"LITERATURE_SEARCH")))reasons.add("ACTUAL_LITERATURE_SEARCH_MISSING:"+x.key());
   if("ANIMAL_OTHER".equals(x.group())&&x.details().stream().filter(d->"NECESSITY_DECISION".equals(d.field())).noneMatch(d->hasNature(s,d.value().sourceKeys(),"APPROVED_DECISION")))reasons.add("ANIMAL_NECESSITY_NOT_CONFIRMED:"+x.key());
  }
  if(s.principles().isEmpty())reasons.add("PRINCIPLES_CHECKLIST_MISSING");
  var catalogue=source(s,s.principleCatalogueSourceKey());
  if(!"APPROVED_TEMPLATE".equals(catalogue.nature()))reasons.add("PRINCIPLES_APPROVED_CATALOGUE_MISSING");
  try{var checklist=schema.checklist(catalogue.literal());var clauses=new HashSet<String>();if(checklist.clauses()==null||checklist.clauses().isEmpty())throw new IllegalArgumentException();for(var clause:checklist.clauses()){if(clause.clause()==null||clause.clause().isBlank()||clause.requirement()==null||clause.requirement().isBlank()||!clauses.add(clause.clause())||s.principles().stream().filter(p->clause.clause().equals(p.clause())&&clause.requirement().equals(p.requirement().text())).count()!=1)reasons.add("PRINCIPLE_CATALOGUE_COVERAGE_MISMATCH");}if(s.principles().size()!=clauses.size())reasons.add("PRINCIPLE_CATALOGUE_SIZE_MISMATCH");}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("PRINCIPLE_CATALOGUE_UNREADABLE");}
  var columns=out.putArray("principle_column_titles");for(String title:List.of("条款号","要求","适用","证明符合性采用的方法","为符合性提供客观证据的文件"))columns.add(title);
  for(var p:s.principles()){
   requireFact(s,p.requirement(),"PRINCIPLE_REQUIREMENT:"+p.key(),reasons);requireBody(s,"PRINCIPLES",p.key(),reasons);
   if("UNKNOWN".equals(p.applicability()))reasons.add("PRINCIPLE_APPLICABILITY_UNKNOWN:"+p.key());
   else if("NOT_APPLICABLE".equals(p.applicability())){requireFact(s,p.nonApplicableReason(),"PRINCIPLE_NOT_APPLICABLE:"+p.key(),reasons);if(!hasNature(s,p.nonApplicableReason().sourceKeys(),"APPROVED_DECISION"))reasons.add("PRINCIPLE_APPLICABILITY_BASIS_MISSING:"+p.key());}
   else {requireFact(s,p.method(),"PRINCIPLE_METHOD:"+p.key(),reasons);if(!allNature(s,p.objectiveSourceKeys(),Set.of("ACTUAL_REPORT","ORIGINAL_DATA","IMPLEMENTATION_RECORD","EFFECTIVENESS_RECORD","LITERATURE_FULLTEXT","ATTACHMENT","WARNING")))reasons.add("PRINCIPLE_OBJECTIVE_EVIDENCE_MISSING:"+p.key());}
   if(!"SUPPORTED".equals(p.status()))reasons.add("PRINCIPLE_SUPPORT_UNRESOLVED:"+p.key());
   if(s.principleRows().stream().noneMatch(r->p.key().equals(r.principleKey())))reasons.add("PRINCIPLE_FIVE_COLUMN_ROW_MISSING:"+p.key());
  }
  if(s.risks().isEmpty())reasons.add("RISK_ANALYSIS_MISSING");
  for(var r:s.risks()){
   requireBody(s,"RISK",r.key(),reasons);for(var f:riskFacts(r))requireFact(s,f,"RISK:"+r.key(),reasons);
   if(r.controls().isEmpty())reasons.add("RISK_CONTROL_MISSING:"+r.key());
   for(var c:r.controls()){
    requireBody(s,"RISK",c.key(),reasons);for(var f:List.of(c.measure(),c.effectivenessConclusion(),c.residualRisk()))requireFact(s,f,"CONTROL:"+c.key(),reasons);
    if(!allNature(s,c.implementationSourceKeys(),Set.of("IMPLEMENTATION_RECORD","ACTUAL_REPORT")))reasons.add("CONTROL_IMPLEMENTATION_MISSING:"+c.key());
    if(!allNature(s,c.effectivenessSourceKeys(),Set.of("EFFECTIVENESS_RECORD","ACTUAL_REPORT"))||!exactNature(s,c.effectivenessConclusion(),Set.of("EFFECTIVENESS_RECORD","ACTUAL_REPORT")))reasons.add("CONTROL_EFFECTIVENESS_MISSING:"+c.key());
    if("UNKNOWN".equals(c.warningApplicability()))reasons.add("WARNING_APPLICABILITY_UNKNOWN:"+c.key());
    requireFact(s,c.warning(),"WARNING_OR_NONAPPLICABILITY:"+c.key(),reasons);
    if("REQUIRED".equals(c.warningApplicability())&&!hasNature(s,c.warning().sourceKeys(),"WARNING"))reasons.add("IFU_WARNING_MISSING:"+c.key());
    if("NOT_APPLICABLE".equals(c.warningApplicability())&&!hasNature(s,c.warning().sourceKeys(),"APPROVED_DECISION"))reasons.add("WARNING_NONAPPLICABILITY_BASIS_MISSING:"+c.key());
   }
  }
  requireBody(s,"RISK_SUMMARY","RISK_SUMMARY",reasons);for(var f:summaryFacts(s.riskSummary()))requireFact(s,f,"RISK_SUMMARY",reasons);
  for(var st:s.standards()){
   requireBody(s,"STANDARD",st.key(),reasons);
   if(!"TEXT_AUTHORIZED".equals(st.verification())||st.authorizedTextSourceKey().isBlank()||!hasNature(s,List.of(st.authorizedTextSourceKey()),"AUTHORIZED_STANDARD_TEXT"))reasons.add("STANDARD_TEXT_UNVERIFIED:"+st.key());
  }
  out.set("required_body",schema.tree(s.body()));out.set("studies",schema.tree(s.studies()));out.set("principles",schema.tree(s.principles()));out.set("risks",schema.tree(s.risks()));out.set("risk_summary",schema.tree(s.riskSummary()));out.set("applicability",schema.tree(s.applicability()));out.set("standards",schema.tree(s.standards()));out.put("blocked",!reasons.isEmpty());return out;
 }
 private static void requireFact(Selection s,Fact f,String id,ArrayNode reasons){if(f.text().isBlank()||f.sourceKeys().isEmpty()||f.sourceKeys().stream().noneMatch(k->source(s,k).literal().equals(f.text())))reasons.add("EXACT_FACT_BASIS_MISSING:"+id);}
 private static boolean exactNature(Selection s,Fact f,Set<String> kinds){return !f.text().isBlank()&&f.sourceKeys().stream().anyMatch(k->source(s,k).literal().equals(f.text())&&kinds.contains(source(s,k).nature()));}
 private static Source source(Selection s,String k){return s.sources().stream().filter(x->x.key().equals(k)).findFirst().orElseThrow();}
 private static boolean hasNature(Selection s,List<String> keys,String nature){return keys.stream().anyMatch(k->s.sources().stream().anyMatch(x->x.key().equals(k)&&nature.equals(x.nature())));}
 private static boolean allNature(Selection s,List<String> keys,Set<String> n){return !keys.isEmpty()&&keys.stream().allMatch(k->n.contains(source(s,k).nature()));}
 private static void requireBody(Selection s,String group,String key,ArrayNode r){if(s.body().stream().noneMatch(b->group.equals(b.group())&&b.entityKeys().contains(key)))r.add("SPECIALIZED_BODY_MISSING:"+group+":"+key);}
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new NonclinicalPrinciplesRiskSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)plan(fixed);var reasons=(ArrayNode)result.required("unknown_reasons");result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));
  var sections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!sections.equals(input.input().targetSections())||!s.sectionIds().containsAll(sections))reasons.add("NONCLINICAL_SECTION_SCOPE_MISMATCH");
  var verified=new HashSet<String>();var sourceChecks=result.putArray("source_checks");
  for(var src:s.sources()){
   JsonNode found=null;for(var read:reads.required("sources"))if(schema.tree(src.evidence()).equals(read.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=read;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(src.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var check=sourceChecks.addObject().put("source_key",src.key()).put("verified",ok);check.set("evidence",schema.tree(src.evidence()));if(found!=null)check.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(src.key());else reasons.add("EXACT_SOURCE_NOT_VERIFIED:"+src.key());
  }
  var actual=new HashMap<String,JsonNode>();for(var node:input.target().body().required("nodes")){
   String id=node.required("node_id").asText();if(actual.put(id,node)!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);var manifest=input.target().candidate().nodeManifest().stream().filter(n->n.nodeId().equals(id)).findFirst().orElseThrow();
   if(Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(node.required("content").path("kind").asText())&&s.body().stream().noneMatch(b->b.nodeId().equals(id)&&b.sectionId().equals(manifest.sectionId())&&b.content().equals(node.required("content"))))reasons.add("UNAPPROVED_NONCLINICAL_BODY:"+id);
  }
  var checks=result.putArray("body_checks");var bodyNodeIds=new HashMap<String,String>();
  for(var b:s.body()){
   if(!input.input().targetSections().contains(b.sectionId()))continue;
   var matches=new ArrayList<String>();for(var entry:actual.entrySet())if(b.nodeId().equals(entry.getKey())&&b.content().equals(entry.getValue().required("content"))&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(entry.getKey())&&b.sectionId().equals(n.sectionId())))matches.add(entry.getKey());
   var check=checks.addObject().put("body_key",b.key()).put("exact_body",matches.size()==1);if(matches.size()!=1){reasons.add("NONCLINICAL_BODY_MISSING_OR_CONFLICT:"+b.key());continue;}String nodeId=matches.get(0);bodyNodeIds.put(b.key(),nodeId);check.put("node_id",nodeId);
   var required=new LinkedHashSet<>(b.sourceKeys());for(String key:b.entityKeys())required.addAll(entitySources(s,key));if(required.isEmpty())reasons.add("BODY_EVIDENCE_EMPTY:"+b.key());
   for(String key:required){if(!verified.contains(key))reasons.add("BODY_SOURCE_UNVERIFIED:"+b.key()+":"+key);boolean linked=false;for(var link:input.target().body().required("evidence_links"))if("SUPPORT".equals(link.path("relation").asText())&&schema.tree(source(s,key).evidence()).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(nodeId.equals(id.asText()))linked=true;if(!linked)reasons.add("BODY_SUPPORT_CITATION_MISSING:"+b.key()+":"+key);}
  }
  // Each approved factual value must be present in body text for its entity, not merely in provenance.
  for(var b:s.body())if(input.input().targetSections().contains(b.sectionId()))for(String key:b.entityKeys()){
   String text=String.join(" ",s.body().stream().filter(x->b.sectionId().equals(x.sectionId())&&x.entityKeys().contains(key)&&bodyNodeIds.containsKey(x.key())).map(Body::text).toList());
   for(var fact:entityFacts(s,key))if(!fact.text().isBlank()&&!text.contains(fact.text()))reasons.add("ENTITY_FACT_BODY_MISMATCH:"+key);
  }
  for(var st:s.standards()){String text=String.join(" ",s.body().stream().filter(b->input.input().targetSections().contains(b.sectionId())&&b.entityKeys().contains(st.key())&&bodyNodeIds.containsKey(b.key())).map(Body::text).toList());if(s.body().stream().anyMatch(b->input.input().targetSections().contains(b.sectionId())&&b.entityKeys().contains(st.key()))&&(!text.contains(st.designation())||!text.contains(st.edition())))reasons.add("STANDARD_DESIGNATION_EDITION_BODY_MISMATCH:"+st.key());}
  for(var p:s.principles()){
   var binding=s.principleRows().stream().filter(r->p.key().equals(r.principleKey())).findFirst().orElse(null);if(binding==null)continue;var table=s.body().stream().filter(b->b.key().equals(binding.tableBodyKey())).findFirst().orElseThrow();if(!input.input().targetSections().contains(table.sectionId()))continue;
   var c=table.content();if(!bodyNodeIds.containsKey(table.key())||!"PRINCIPLES".equals(table.group())||!table.entityKeys().contains(p.key())||!"TABLE".equals(c.path("kind").asText())||c.path("column_ids").size()!=5||!contains(c.path("row_ids"),binding.rowId())){reasons.add("PRINCIPLE_TABLE_SHAPE_MISMATCH:"+p.key());continue;}
   var permittedRows=new HashSet<String>();String header=c.required("row_ids").get(0).asText();permittedRows.add(header);for(var row:s.principleRows())if(row.tableBodyKey().equals(table.key()))permittedRows.add(row.rowId());
   if(header.equals(binding.rowId())||c.required("row_ids").size()!=permittedRows.size())reasons.add("PRINCIPLE_UNDECLARED_OR_HEADER_ROW:"+p.key());
   for(var row:c.required("row_ids"))if(!permittedRows.contains(row.asText()))reasons.add("PRINCIPLE_UNDECLARED_ROW:"+p.key());
   List<String> titles=List.of("条款号","要求","适用","证明符合性采用的方法","为符合性提供客观证据的文件");
   for(int i=0;i<5;i++){String column=c.required("column_ids").get(i).asText();int count=0;for(var cell:c.path("cells"))if(header.equals(cell.path("row_id").asText())&&column.equals(cell.path("column_id").asText())&&cell.path("row_span").asInt()==1&&cell.path("column_span").asInt()==1&&cell.path("node_ids").size()==1){var headerNode=actual.get(cell.path("node_ids").get(0).asText());if(headerNode!=null&&titles.get(i).equals(headerNode.required("content").path("text").asText()))count++;}if(count!=1)reasons.add("PRINCIPLE_OFFICIAL_HEADER_MISMATCH:"+p.key()+":"+i);}
   List<String> expected=List.of(p.clause(),p.requirement().text(),"NOT_APPLICABLE".equals(p.applicability())?"不适用："+p.nonApplicableReason().text():"REQUIRED".equals(p.applicability())?"适用":"待确认",p.method().text().isBlank()?("NOT_APPLICABLE".equals(p.applicability())?"不适用":"待补证"):p.method().text(),objectiveLocations(s,p));
   for(int i=0;i<5;i++){
    String node=binding.columnNodeIds().get(i),column=c.required("column_ids").get(i).asText();int count=0;for(var cell:c.path("cells"))if(binding.rowId().equals(cell.path("row_id").asText())&&column.equals(cell.path("column_id").asText())&&cell.path("row_span").asInt()==1&&cell.path("column_span").asInt()==1&&cell.path("node_ids").size()==1&&node.equals(cell.path("node_ids").get(0).asText()))count++;
    JsonNode actualNode=actual.get(node);if(count!=1||actualNode==null||!"TEXT".equals(actualNode.required("content").path("kind").asText())||!expected.get(i).equals(actualNode.required("content").path("text").asText())||input.target().candidate().nodeManifest().stream().noneMatch(n->n.nodeId().equals(node)&&table.sectionId().equals(n.sectionId())))reasons.add("PRINCIPLE_FIVE_COLUMN_CONTENT_MISMATCH:"+p.key()+":"+i);
   }
  }
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("blocked",!reasons.isEmpty());return result;
 }
 private static boolean contains(JsonNode n,String value){for(var x:n)if(value.equals(x.asText()))return true;return false;}
 private static String objectiveLocations(Selection s,Principle p){if(p.objectiveSourceKeys().isEmpty())return "NOT_APPLICABLE".equals(p.applicability())?"不适用":"待补证";return String.join("；",p.objectiveSourceKeys().stream().map(k->{var x=source(s,k);return x.documentName()+" ["+x.documentNumber()+"] "+x.documentLocation();}).toList());}
 private static List<Fact> entityFacts(Selection s,String key){
  var values=new ArrayList<Fact>();for(var x:s.studies())if(x.key().equals(key)){values.addAll(studyFacts(x));for(var d:x.details())values.add(d.value());}
  for(var x:s.principles())if(x.key().equals(key)){values.add(x.requirement());values.add(x.method());values.add(x.nonApplicableReason());}
  for(var x:s.risks()){if(x.key().equals(key))values.addAll(riskFacts(x));for(var c:x.controls())if(c.key().equals(key))values.addAll(controlFacts(c));}
  if("RISK_SUMMARY".equals(key))values.addAll(summaryFacts(s.riskSummary()));for(var a:s.applicability())if(a.group().equals(key))values.add(a.rationale());return values;
 }
 private static Set<String> entitySources(Selection s,String key){var keys=new LinkedHashSet<String>();for(var f:entityFacts(s,key))keys.addAll(f.sourceKeys());for(var x:s.studies())if(x.key().equals(key))keys.addAll(x.reportSourceKeys());for(var p:s.principles())if(p.key().equals(key)){keys.addAll(p.objectiveSourceKeys());keys.add(s.principleCatalogueSourceKey());}for(var r:s.risks())for(var c:r.controls())if(c.key().equals(key)){keys.addAll(c.implementationSourceKeys());keys.addAll(c.effectivenessSourceKeys());}for(var st:s.standards())if(st.key().equals(key)){keys.add(st.metadataSourceKey());if(!st.authorizedTextSourceKey().isEmpty())keys.add(st.authorizedTextSourceKey());}return keys;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("nonclinical_principles_risk",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads")),provenance.required("normalization").get("nonclinical_principles_risk")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
