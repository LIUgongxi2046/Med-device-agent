package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.ruoyi.medical.persistence.ClinicalEvaluationSchema.*;

/** N04 supplies authored content; N05 verifies the actual body and independent physical source reads. */
final class ClinicalEvaluationPipeline {
 private ClinicalEvaluationPipeline(){}
 private static final Map<String,List<String>> FIELDS=Map.ofEntries(
 Map.entry("COVER",List.of("PRODUCT_NAME","MODELS","SIGNATURE","COMPLETION_DATE")),
 Map.entry("PRODUCT",List.of("BASIC_INFORMATION","INTENDED_USE_POPULATION_SITE","CONTACT_MODE_DURATION","DISEASE_STAGE","USE_CONDITIONS_REUSE","CONTRAINDICATIONS_WARNINGS","DEVELOPMENT_BACKGROUND","PRINCIPLE","EXISTING_METHODS_EXPECTED_EFFECT")),
 Map.entry("SCOPE",List.of("EVALUATION_SCOPE","COMPONENTS","EXCLUSIONS_AND_COMBINED_USE_BASIS")),
 Map.entry("PATH",List.of("RATIONALE","PRODUCT_CHARACTERISTICS","EXISTING_EVIDENCE","ADDITIONAL_EVIDENCE")),
 Map.entry("CONCLUSION",List.of("ESSENTIAL_PRINCIPLES","CLAIMS_SUPPORT","SAFETY_CLINICAL_PERFORMANCE","BENEFIT_RISK","POST_MARKET_STUDIES","LIMITATIONS_UNRESOLVED_GAPS")),
 Map.entry("EVALUATOR",List.of("QUALIFICATIONS","EXPERIENCE","RESPONSIBILITIES")),
 Map.entry("OTHER",List.of("SUPPLEMENTARY_INFORMATION_ATTACHMENTS")),
 Map.entry("LITERATURE",List.of("SOURCE","LAWFUL_ACCESS_BASIS","SEARCH_PLAN","SEARCH_REPORT","INCLUSION_EXCLUSION","DUPLICATE_HANDLING","FULLTEXT","RELEVANCE","CONTRIBUTION","ANALYSIS_METHOD_RATIONALE","SAFETY_PERFORMANCE_RESULTS","LIMITATIONS")),
 Map.entry("TRIAL",List.of("DESIGN_BASIS","INSTITUTION","TIME","ENDPOINTS","INCLUSION_EXCLUSION","SAMPLE_SIZE","FOLLOWUP","RESULTS","ANALYSIS_EXPLANATION")),
 Map.entry("EXEMPTION",List.of("CATALOGUE_VERSION","CATALOGUE_ENTRY","CURRENT_CATALOGUE_VERIFICATION","DOMESTIC_REGISTRATION","EQUIVALENCE_DECISION","EQUIVALENCE_OUTCOME","DIFFERENCE_SAFETY_EFFECTIVENESS")));
 private static final Set<String> EXEMPTION_ITEMS=Set.of("PRINCIPLE","STRUCTURE","MATERIAL","PERFORMANCE","STERILIZATION_DISINFECTION","INTENDED_USE","METHOD_OF_USE");
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new ClinicalEvaluationSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",KIND,"human_review_required",true,"clinical_approval",false,"conclusion_authority","MACHINE_CHECK_NOT_CLINICAL_OR_REGULATORY_APPROVAL"));var reasons=out.putArray("unknown_reasons");
  requireFact(s,s.pathDecision(),"PATH_DECISION",reasons);if(!exactNature(s,s.pathDecision(),Set.of("APPROVED_DECISION")))reasons.add("PROFESSIONAL_PATH_APPROVAL_MISSING");requireBody(s,"PATH",reasons);
  if("UNKNOWN".equals(s.path()))reasons.add("CLINICAL_PATH_UNKNOWN");
  if("COMBINATION".equals(s.path())?s.combinationPaths().size()<2:!s.combinationPaths().isEmpty())reasons.add("COMBINATION_PATH_INVALID");
  try{var approval=schema.approval(s.pathDecision().text());if(!s.path().equals(approval.path())||!s.combinationPaths().equals(approval.combinationPaths())||!"APPROVED".equals(approval.outcome()))reasons.add("PROFESSIONAL_PATH_DECISION_MISMATCH");}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("PROFESSIONAL_PATH_DECISION_UNREADABLE");}
  Set<String> paths="COMBINATION".equals(s.path())?new TreeSet<>(s.combinationPaths()):Set.of(s.path());boolean exemption="EXEMPTION".equals(s.path());
  var requiredGroups=new TreeSet<>(List.of("COVER","PRODUCT","SCOPE","PATH","CONCLUSION","EVALUATOR","OTHER"));
  if(exemption)requiredGroups.add("EXEMPTION");else {if(paths.contains("EQUIVALENT_DEVICE")||paths.contains("COMPARABLE_DEVICE"))requiredGroups.add("LITERATURE");if(paths.contains("CLINICAL_TRIAL"))requiredGroups.add("TRIAL");}
  for(String group:requiredGroups)if(s.records().stream().noneMatch(r->group.equals(r.group())&&"REQUIRED".equals(r.applicability())))reasons.add("REQUIRED_CLINICAL_GROUP_MISSING:"+group);
  for(var r:s.records()){
   requireBody(s,r.key(),reasons);if("UNKNOWN".equals(r.applicability()))reasons.add("CLINICAL_APPLICABILITY_UNKNOWN:"+r.key());
   requireFact(s,r.applicabilityBasis(),"APPLICABILITY:"+r.key(),reasons);
   if(!exactNature(s,r.applicabilityBasis(),Set.of("APPROVED_DECISION")))reasons.add("PROFESSIONAL_APPLICABILITY_APPROVAL_MISSING:"+r.key());
   if("NOT_APPLICABLE".equals(r.applicability()))continue;
   for(String name:FIELDS.get(r.group())){var f=field(r,name);if(f==null)reasons.add("CLINICAL_FIELD_MISSING:"+r.key()+":"+name);else requireFact(s,f,"CLINICAL_FIELD:"+r.key()+":"+name,reasons);}
   for(var f:r.fields())requireFact(s,f.value(),"CLINICAL_EXTRA_FIELD:"+r.key()+":"+f.name(),reasons);
   switch(r.group()){
    case "CONCLUSION" -> {for(var f:r.fields())if(!exactNature(s,f.value(),Set.of("APPROVED_DECISION","TRIAL_REPORT","LITERATURE_FULLTEXT","COMPARISON_EVIDENCE")))reasons.add("UNSUPPORTED_CLINICAL_CONCLUSION:"+r.key()+":"+f.name());}
    case "COVER" -> natureField(s,r,"SIGNATURE","SIGNATURE",reasons);
    case "EVALUATOR" -> natureField(s,r,"QUALIFICATIONS","QUALIFICATION",reasons);
    case "SCOPE" -> natureField(s,r,"EXCLUSIONS_AND_COMBINED_USE_BASIS","APPROVED_DECISION",reasons);
    case "LITERATURE" -> {natureField(s,r,"SEARCH_REPORT","LITERATURE_SEARCH",reasons);natureField(s,r,"FULLTEXT","LITERATURE_FULLTEXT",reasons);natureField(s,r,"SAFETY_PERFORMANCE_RESULTS","LITERATURE_FULLTEXT",reasons);}
    case "TRIAL" -> {if(!paths.contains("CLINICAL_TRIAL"))reasons.add("TRIAL_PATH_NOT_APPROVED");for(String nature:List.of("ETHICS_APPROVAL","TRIAL_PLAN","CONSENT_SAMPLE","TRIAL_REPORT","ORIGINAL_DATABASE","ANALYSIS_CODE"))if(!hasNature(s,r.attachmentSourceKeys(),nature))reasons.add("ACTUAL_TRIAL_ATTACHMENT_MISSING:"+r.key()+":"+nature);natureField(s,r,"RESULTS","TRIAL_REPORT",reasons);}
    case "EXEMPTION" -> {if(!exemption)reasons.add("INCOMPATIBLE_EXEMPTION_RECORD");for(String name:List.of("CATALOGUE_VERSION","CATALOGUE_ENTRY"))natureField(s,r,name,"CURRENT_CATALOGUE",reasons);natureField(s,r,"CURRENT_CATALOGUE_VERIFICATION","APPROVED_DECISION",reasons);natureField(s,r,"DOMESTIC_REGISTRATION","DOMESTIC_REGISTRATION",reasons);natureField(s,r,"EQUIVALENCE_DECISION","APPROVED_DECISION",reasons);natureField(s,r,"EQUIVALENCE_OUTCOME","APPROVED_DECISION",reasons);var outcome=field(r,"EQUIVALENCE_OUTCOME");if(outcome==null||!"BASICALLY_EQUIVALENT".equals(outcome.text()))reasons.add("EXEMPTION_NOT_ESTABLISHED_CLINICAL_EVALUATION_REQUIRED");}
    default -> {}
   }
  }
  for(String group:exemption?List.of("CATALOGUE_ENTRY","DOMESTIC_DEVICE"):List.of("DEVICE")){
   if(!exemption&&!(paths.contains("EQUIVALENT_DEVICE")||paths.contains("COMPARABLE_DEVICE")))continue;
   for(String component:paths){if("CLINICAL_TRIAL".equals(component))continue;
   for(String item:new TreeSet<>(exemption?EXEMPTION_ITEMS:Set.of("TECHNICAL_CHARACTERISTICS","BIOLOGICAL_CHARACTERISTICS","INTENDED_USE")))if(s.comparisons().stream().noneMatch(c->component.equals(c.evaluationPath())&&group.equals(c.group())&&item.equals(c.item())))reasons.add("COMPARISON_ITEM_MISSING:"+component+":"+group+":"+item);}

  }
  var identities=new HashSet<String>();for(var c:s.comparisons()){
   if(!identities.add(c.evaluationPath()+":"+c.group()+":"+c.item()))reasons.add("DUPLICATE_COMPARISON_ITEM:"+c.key());
   if(!paths.contains(c.evaluationPath())||"CLINICAL_TRIAL".equals(c.evaluationPath())||exemption=="DEVICE".equals(c.group()))reasons.add("COMPARISON_PATH_CONFLICT:"+c.key());
   requireBody(s,c.key(),reasons);if("UNKNOWN".equals(c.applicability()))reasons.add("COMPARISON_APPLICABILITY_UNKNOWN:"+c.key());
   if(!exactNature(s,c.applicabilityBasis(),Set.of("APPROVED_DECISION")))reasons.add("COMPARISON_APPLICABILITY_DECISION_MISSING:"+c.key());
   if("NOT_APPLICABLE".equals(c.applicability())){requireFact(s,c.applicabilityBasis(),c.key(),reasons);continue;}
   for(var f:comparisonFacts(c))requireFact(s,f,"COMPARISON:"+c.key(),reasons);
   if(!exactNature(s,c.analysis(),Set.of("COMPARISON_EVIDENCE","TRIAL_REPORT","LITERATURE_FULLTEXT")))reasons.add("DIFFERENCE_IMPACT_EVIDENCE_MISSING:"+c.key());
   if("CATALOGUE_ENTRY".equals(c.group())&&!exactNature(s,c.comparator(),Set.of("CURRENT_CATALOGUE")))reasons.add("CATALOGUE_COMPARISON_BASIS_MISSING:"+c.key());
   if("DOMESTIC_DEVICE".equals(c.group())&&!hasNature(s,c.comparator().sourceKeys(),"DOMESTIC_REGISTRATION"))reasons.add("DOMESTIC_COMPARATOR_REGISTRATION_MISSING:"+c.key());
  }
  out.put("path",s.path());out.set("combination_paths",schema.tree(s.combinationPaths()));out.set("required_body",schema.tree(s.body()));out.set("records",schema.tree(s.records()));out.set("comparisons",schema.tree(s.comparisons()));out.put("blocked",!reasons.isEmpty());out.put("status",reasons.isEmpty()?"READY_FOR_PROFESSIONAL_REVIEW":"UNKNOWN");return out;
 }
 private static Fact field(ClinicalEvaluationSchema.Record r,String name){return r.fields().stream().filter(f->name.equals(f.name())).map(Field::value).findFirst().orElse(null);}
 private static void natureField(Selection s,ClinicalEvaluationSchema.Record r,String name,String nature,ArrayNode reasons){var f=field(r,name);if(f==null||!exactNature(s,f,Set.of(nature)))reasons.add("ACTUAL_CLINICAL_SOURCE_MISSING:"+r.key()+":"+name);}
 private static void requireFact(Selection s,Fact f,String id,ArrayNode reasons){if(f.text().isBlank()||f.sourceKeys().isEmpty()||f.sourceKeys().stream().noneMatch(k->source(s,k).literal().equals(f.text())))reasons.add("EXACT_FACT_BASIS_MISSING:"+id);}
 private static boolean exactNature(Selection s,Fact f,Set<String> kinds){return !f.text().isBlank()&&f.sourceKeys().stream().anyMatch(k->source(s,k).literal().equals(f.text())&&kinds.contains(source(s,k).nature()));}
 private static Source source(Selection s,String k){return s.sources().stream().filter(x->x.key().equals(k)).findFirst().orElseThrow();}
 private static boolean hasNature(Selection s,List<String> keys,String nature){return keys.stream().anyMatch(k->nature.equals(source(s,k).nature()));}
 private static void requireBody(Selection s,String key,ArrayNode r){if(s.body().stream().noneMatch(b->b.entityKeys().contains(key)))r.add("CLINICAL_BODY_MISSING:"+key);}
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new ClinicalEvaluationSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)plan(fixed);var reasons=(ArrayNode)result.required("unknown_reasons");result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));
  var sections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!sections.equals(input.input().targetSections())||!s.sectionIds().containsAll(sections))reasons.add("CLINICAL_SECTION_SCOPE_MISMATCH");
  var verified=new HashSet<String>();var sourceChecks=result.putArray("source_checks");
  for(var src:s.sources()){
   JsonNode found=null;for(var read:reads.required("sources"))if(schema.tree(src.evidence()).equals(read.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=read;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(src.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var check=sourceChecks.addObject().put("source_key",src.key()).put("verified",ok);check.set("evidence",schema.tree(src.evidence()));if(found!=null)check.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(src.key());else reasons.add("EXACT_SOURCE_NOT_VERIFIED:"+src.key());
  }
  var actual=new HashMap<String,JsonNode>();for(var node:input.target().body().required("nodes")){
   String id=node.required("node_id").asText();if(actual.put(id,node)!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);var manifest=input.target().candidate().nodeManifest().stream().filter(n->n.nodeId().equals(id)).findFirst().orElseThrow();
   if(Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(node.required("content").path("kind").asText())&&s.body().stream().noneMatch(b->b.nodeId().equals(id)&&b.sectionId().equals(manifest.sectionId())&&b.content().equals(node.required("content"))))reasons.add("UNAPPROVED_CLINICAL_BODY:"+id);
  }
  var checks=result.putArray("body_checks");var bodyNodeIds=new HashMap<String,String>();
  for(var b:s.body()){
   if(!input.input().targetSections().contains(b.sectionId()))continue;
   var matches=new ArrayList<String>();for(var entry:actual.entrySet())if(b.nodeId().equals(entry.getKey())&&b.content().equals(entry.getValue().required("content"))&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(entry.getKey())&&b.sectionId().equals(n.sectionId())))matches.add(entry.getKey());
   var check=checks.addObject().put("body_key",b.key()).put("exact_body",matches.size()==1);if(matches.size()!=1){reasons.add("CLINICAL_BODY_MISSING_OR_CONFLICT:"+b.key());continue;}String nodeId=matches.get(0);bodyNodeIds.put(b.key(),nodeId);check.put("node_id",nodeId);
   var required=new LinkedHashSet<>(b.sourceKeys());for(String key:b.entityKeys())required.addAll(entitySources(s,key));if(required.isEmpty())reasons.add("BODY_EVIDENCE_EMPTY:"+b.key());
   for(String key:required){if(!verified.contains(key))reasons.add("BODY_SOURCE_UNVERIFIED:"+b.key()+":"+key);boolean linked=false;for(var link:input.target().body().required("evidence_links"))if("SUPPORT".equals(link.path("relation").asText())&&schema.tree(source(s,key).evidence()).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(nodeId.equals(id.asText()))linked=true;if(!linked)reasons.add("BODY_SUPPORT_CITATION_MISSING:"+b.key()+":"+key);}
  }
  // Each approved factual value must be present in body text for its entity, not merely in provenance.
  for(var b:s.body())if(input.input().targetSections().contains(b.sectionId()))for(String key:b.entityKeys()){
   String text=String.join(" ",s.body().stream().filter(x->b.sectionId().equals(x.sectionId())&&x.entityKeys().contains(key)&&bodyNodeIds.containsKey(x.key())).map(Body::text).toList());
   for(var fact:entityFacts(s,key))if(!fact.text().isBlank()&&!text.contains(fact.text()))reasons.add("ENTITY_FACT_BODY_MISMATCH:"+key);
  }
  verifyTables(s,input,actual,bodyNodeIds,reasons);
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("blocked",!reasons.isEmpty());result.put("status",reasons.isEmpty()?"READY_FOR_PROFESSIONAL_REVIEW":"UNKNOWN");return result;
 }
 private static List<Fact> entityFacts(Selection s,String key){var values=new ArrayList<Fact>();if("PATH".equals(key))values.add(s.pathDecision());for(var r:s.records())if(r.key().equals(key)){values.add(r.applicabilityBasis());for(var f:r.fields())values.add(f.value());}for(var c:s.comparisons())if(c.key().equals(key))values.addAll(comparisonFacts(c));return values;}
 private static Set<String> entitySources(Selection s,String key){var keys=new LinkedHashSet<String>();for(var f:entityFacts(s,key))keys.addAll(f.sourceKeys());for(var r:s.records())if(r.key().equals(key))keys.addAll(r.attachmentSourceKeys());return keys;}
 private static void verifyTables(Selection s,TaskSourceVerifierInputAuthority.Verified input,Map<String,JsonNode> actual,Map<String,String> bodies,ArrayNode reasons){
  var usedRows=new HashSet<String>();var usedCells=new HashSet<String>();
  for(var c:s.comparisons()){
   var table=s.body().stream().filter(b->b.key().equals(c.tableBodyKey())).findFirst().orElseThrow();if(!input.input().targetSections().contains(table.sectionId()))continue;
   var content=table.content();if(!bodies.containsKey(table.key())||!table.entityKeys().contains(c.key())||!"TABLE".equals(content.path("kind").asText())||content.path("column_ids").size()!=6||content.path("row_ids").size()<2){reasons.add("CLINICAL_COMPARISON_TABLE_INVALID:"+c.key());continue;}
   String header=content.path("row_ids").get(0).asText();var rows=new HashSet<String>();rows.add(header);for(var other:s.comparisons())if(other.tableBodyKey().equals(table.key()))rows.add(other.rowId());
   if(header.equals(c.rowId())||!usedRows.add(table.key()+":"+c.rowId())||content.path("row_ids").size()!=rows.size())reasons.add("CLINICAL_COMPARISON_ROW_INVALID:"+c.key());for(var row:content.path("row_ids"))if(!rows.contains(row.asText()))reasons.add("CLINICAL_UNDECLARED_TABLE_ROW:"+c.key());
   List<String> titles=List.of("对比项目","DEVICE".equals(c.group())?"对比产品":"目录中医疗器械","申报产品","差异性","支持性资料概述","分析研究资料概述");
   List<String> expected=List.of(c.item(),c.comparator().text(),c.applicant().text(),c.difference().text(),c.supportingMaterials().text(),c.analysis().text());
   for(int i=0;i<6;i++){
    String column=content.path("column_ids").get(i).asText(),node=c.columnNodeIds().get(i);if(!usedCells.add(node))reasons.add("CLINICAL_COMPARISON_CELL_REUSED:"+c.key());
    if(!cell(content,actual,header,column,null,titles.get(i))||!cell(content,actual,c.rowId(),column,node,expected.get(i))||input.target().candidate().nodeManifest().stream().noneMatch(n->n.nodeId().equals(node)&&table.sectionId().equals(n.sectionId())))reasons.add("CLINICAL_SIX_COLUMN_CONTENT_MISMATCH:"+c.key()+":"+i);
   }
  }
 }
 private static boolean cell(JsonNode table,Map<String,JsonNode> actual,String row,String column,String wanted,String text){int count=0;for(var c:table.path("cells"))if(row.equals(c.path("row_id").asText())&&column.equals(c.path("column_id").asText())&&c.path("row_span").asInt()==1&&c.path("column_span").asInt()==1&&c.path("node_ids").size()==1){String id=c.path("node_ids").get(0).asText();var n=actual.get(id);if((wanted==null||wanted.equals(id))&&n!=null&&"TEXT".equals(n.path("content").path("kind").asText())&&text.equals(n.path("content").path("text").asText()))count++;}return count==1;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("clinical_evaluation",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads")),provenance.required("normalization").get("clinical_evaluation")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
