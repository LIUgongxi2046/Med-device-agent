package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.ruoyi.medical.persistence.InstructionsLabelSchema.*;

/** Source-derived requirements only. Unknown prerequisites stay visible and block completion. */
final class InstructionsLabelPipeline {
 private InstructionsLabelPipeline(){}
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new InstructionsLabelSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",KIND,"human_review_required",true,"regulatory_approval",false));var reasons=out.putArray("unknown_reasons");
  var ifu=s.documents().stream().filter(d->"IFU".equals(d.kind())).findFirst().orElseThrow();var label=s.documents().stream().filter(d->"LABEL".equals(d.kind())).findFirst().orElseThrow();
  if(!ifu.productVersion().equals(label.productVersion())||!ifu.stage().equals(label.stage()))reasons.add("IFU_LABEL_PRODUCT_VERSION_OR_STAGE_CONFLICT");
  requireNature(s,s.packagingVersion(),Set.of("PACKAGING"),"PACKAGING_VERSION",reasons);
  String packagingVersion="";try{var packaging=schema.packaging(s.packagingVersion().text());packagingVersion=packaging.version();if(!ifu.productVersion().equals(packaging.productVersion())||!packaging.minimumSalesUnit())reasons.add("MINIMUM_SALES_UNIT_PACKAGING_CONFLICT");}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("PACKAGING_VERSION_RECORD_UNREADABLE");}
  requireNature(s,s.importDecision(),Set.of("APPROVED_DECISION"),"IMPORT_APPLICABILITY",reasons);
  decision(s,s.importDecision(),"IMPORT",s.importStatus(),ifu.productVersion(),reasons);
  if("UNKNOWN".equals(s.importStatus()))reasons.add("IMPORT_STATUS_UNKNOWN");
  if("IMPORTED".equals(s.importStatus())){requireNature(s,s.originalInstructions(),Set.of("ORIGINAL_INSTRUCTIONS"),"ORIGINAL_INSTRUCTIONS",reasons);requireNature(s,s.translationVerification(),Set.of("TRANSLATION_VERIFICATION"),"TRANSLATION_VERIFICATION",reasons);}
  if("IMPORTED".equals(s.importStatus()))try{var verification=schema.translation(s.translationVerification().text());if(!ifu.version().equals(verification.chineseVersion())||!ifu.productVersion().equals(verification.productVersion())||!"VERIFIED".equals(verification.outcome())||s.originalInstructions().sourceKeys().stream().noneMatch(k->{var original=source(s,k);return "ORIGINAL_INSTRUCTIONS".equals(original.nature())&&original.literal().equals(s.originalInstructions().text())&&original.source().version_id().equals(verification.originalSourceVersion())&&original.source().content_sha256().equals(verification.originalSha256());}))reasons.add("TRANSLATION_VERSION_OR_SOURCE_CONFLICT");}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("TRANSLATION_VERIFICATION_UNREADABLE");}
  for(var d:s.documents()){
   requireNature(s,d.versionRecord(),Set.of("VERSION_RECORD"),"DOCUMENT_VERSION:"+d.kind(),reasons);try{var version=schema.version(d.versionRecord().text());if(!d.kind().equals(version.documentKind())||!d.version().equals(version.version())||!d.productVersion().equals(version.productVersion())||!d.stage().equals(version.stage())||!Objects.equals(packagingVersion,version.packagingVersion()))reasons.add("DOCUMENT_VERSION_RECORD_MISMATCH:"+d.kind());}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("DOCUMENT_VERSION_RECORD_UNREADABLE:"+d.kind());}
   if(s.body().stream().noneMatch(b->d.kind().equals(b.documentKind())))reasons.add("INDEPENDENT_DOCUMENT_BODY_MISSING:"+d.kind());
   if(d.ruleSourceKey().isEmpty()){reasons.add("APPROVED_RULES_MISSING:"+d.kind());continue;}var src=source(s,d.ruleSourceKey());if(!"APPROVED_RULES".equals(src.nature()))reasons.add("APPROVED_RULES_MISSING:"+d.kind());
   Rules rules;try{rules=schema.rules(src.literal());if(!d.kind().equals(rules.documentKind())||!d.stage().equals(rules.stage())||!d.productVersion().equals(rules.productVersion())||!d.layout().equals(rules.layout())){reasons.add("RULE_SCOPE_CONFLICT:"+d.kind());continue;}}catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException|NullPointerException e){reasons.add("APPROVED_RULES_UNREADABLE:"+d.kind());continue;}
   var groups=new HashSet<String>();var ruleFields=new HashSet<String>();var requiredGroups=new HashSet<>("IFU".equals(d.kind())?IFU_GROUPS:LABEL_GROUPS);if("IFU".equals(d.kind())){requiredGroups.addAll(WARNING_GROUPS);requiredGroups.addAll(REUSE_GROUPS);}
   if(rules.rules()==null){reasons.add("RULE_CATALOGUE_MISSING:"+d.kind());continue;}
   for(var rule:rules.rules()){
    if(rule==null||rule.group()==null||rule.fieldKeys()==null||rule.fieldKeys().isEmpty()||!groups.add(rule.group())){reasons.add("RULE_GROUP_INVALID:"+d.kind());continue;}
    for(String key:rule.fieldKeys()){
     if(key==null||!ruleFields.add(key)){reasons.add("RULE_FIELD_DUPLICATE:"+d.kind());continue;}
     var f=d.fields().stream().filter(x->key.equals(x.key())&&rule.group().equals(x.group())).findFirst().orElse(null);if(f==null){reasons.add("RULE_FIELD_MISSING:"+d.kind()+":"+key);continue;}
     String id=d.kind()+":"+key;requireNature(s,f.decision(),Set.of("APPROVED_DECISION"),"APPLICABILITY:"+id,reasons);decision(s,f.decision(),id,f.applicability(),d.productVersion(),reasons);
     if("UNKNOWN".equals(f.applicability()))reasons.add("FIELD_APPLICABILITY_UNKNOWN:"+id);
     if(rule.mandatory()&&"NOT_APPLICABLE".equals(f.applicability()))reasons.add("MANDATORY_FIELD_EXCLUDED:"+id);
     if("PENDING_REGISTRATION".equals(f.applicability())){
      if(!"APPLICATION_DRAFT".equals(d.stage())||!rule.deferrableInApplication()||!Set.of("IFU-04","IFU-05","REGISTRATION").contains(f.group())||!f.value().text().isBlank())reasons.add("REGISTRATION_DEFERRAL_NOT_ALLOWED:"+id);
     }else if("REQUIRED".equals(f.applicability())){
      requireFact(s,f.value(),id,reasons);
      if(Set.of("IFU-04","REGISTRATION").contains(f.group()))requireNature(s,f.value(),Set.of("REGISTRATION_PROOF"),id,reasons);
      if("IFU-05".equals(f.group()))requireNature(s,f.value(),Set.of("APPROVED_TECHNICAL_REQUIREMENTS"),id,reasons);
      if(Set.of("IFU-07","WARNINGS").contains(f.group())||WARNING_GROUPS.contains(f.group()))requireNature(s,f.value(),Set.of("RISK_WARNING","APPROVED_PRODUCT_FACT","APPROVED_DECISION"),id,reasons);
      if(Set.of("IFU-10","DATES_LIFETIME").contains(f.group()))requireNature(s,f.value(),"APPLICATION_DRAFT".equals(d.stage())?Set.of("APPROVED_RULES","VALIDATION_REPORT","BATCH_RECORD"):Set.of("BATCH_RECORD","VALIDATION_REPORT"),id,reasons);
      if("IFU-13".equals(f.group()))requireNature(s,f.value(),Set.of("VERSION_RECORD"),id,reasons);
     }
     boolean mustShow=!"NOT_APPLICABLE".equals(f.applicability())&&(!"SPACE_LIMITED".equals(d.layout())||rule.minimumContent());
     if(mustShow&&s.body().stream().noneMatch(b->b.documentKind().equals(d.kind())&&b.entityKeys().contains(id)))reasons.add("REQUIRED_DOCUMENT_FIELD_BODY_MISSING:"+id);
    }
   }
   if(!groups.containsAll(requiredGroups))reasons.add("RULE_CATALOGUE_INCOMPLETE:"+d.kind());
   for(var f:d.fields())if(!ruleFields.contains(f.key()))reasons.add("UNAPPROVED_FIELD:"+d.kind()+":"+f.key());
   requireFact(s,rules.layoutBasis(),"LAYOUT_BASIS:"+d.kind(),reasons);
   if("SPACE_LIMITED".equals(d.layout())){
    if(rules.rules().stream().noneMatch(Rule::minimumContent))reasons.add("LABEL_MINIMUM_CONTENT_NOT_APPROVED");
    requireFact(s,rules.seeInstructions(),"LABEL_SEE_INSTRUCTIONS",reasons);
    if(rules.seeInstructions()==null||rules.seeInstructions().text().isBlank()||s.body().stream().filter(b->"LABEL".equals(b.documentKind())).noneMatch(b->b.text().contains(rules.seeInstructions().text())))reasons.add("LABEL_SEE_INSTRUCTIONS_BODY_MISSING");
   }
  }
  // Documents have independent node/section scopes; neither can masquerade as the other.
  var ifuSections=new HashSet<String>();for(var b:s.body())if("IFU".equals(b.documentKind()))ifuSections.add(b.sectionId());for(var b:s.body())if("LABEL".equals(b.documentKind())&&ifuSections.contains(b.sectionId()))reasons.add("IFU_LABEL_SECTION_NOT_INDEPENDENT");
  var checkedFields=new HashSet<String>();for(var c:s.comparisons()){
   var d=s.documents().stream().filter(x->x.kind().equals(c.documentKind())).findFirst().orElseThrow();var f=d.fields().stream().filter(x->x.key().equals(c.fieldKey())).findFirst().orElseThrow();var p=s.productFacts().stream().filter(x->x.key().equals(c.productFactKey())).findFirst().orElseThrow();
   requireNature(s,p.value(),Set.of("APPROVED_PRODUCT_FACT","REGISTRATION_PROOF","APPROVED_TECHNICAL_REQUIREMENTS","VERSION_RECORD","RISK_WARNING","VALIDATION_REPORT","BATCH_RECORD","PACKAGING"),"PRODUCT_FACT:"+p.key(),reasons);requireNature(s,c.decisionBasis(),Set.of("APPROVED_DECISION"),"CONSISTENCY_DECISION:"+c.key(),reasons);
   decision(s,c.decisionBasis(),c.key(),c.decision(),d.productVersion(),reasons);
   if(!d.productVersion().equals(p.productVersion()))reasons.add("PRODUCT_FACT_VERSION_CONFLICT:"+c.key());
   if(!"EQUAL".equals(c.decision()))reasons.add("CONSISTENCY_UNRESOLVED:"+c.key());
   if(!f.value().text().equals(p.value().text())||!c.unit().equals(c.productUnit())||!c.context().equals(c.productContext()))reasons.add("PRODUCT_FACT_OR_CONTEXT_CONFLICT:"+c.key());
   checkedFields.add(d.kind()+":"+f.key());
  }
  for(var d:s.documents())for(var f:d.fields())if("REQUIRED".equals(f.applicability())&&!checkedFields.contains(d.kind()+":"+f.key()))reasons.add("PRODUCT_FACT_COMPARISON_MISSING:"+d.kind()+":"+f.key());
  out.set("documents",schema.tree(s.documents()));out.set("comparisons",schema.tree(s.comparisons()));out.set("required_body",schema.tree(s.body()));out.put("blocked",!reasons.isEmpty());out.put("status",reasons.isEmpty()?"READY_FOR_PROFESSIONAL_REVIEW":"UNKNOWN");return out;
 }
 private static void decision(Selection s,Fact fact,String subject,String status,String version,ArrayNode reasons){try{var d=new InstructionsLabelSchema().decision(fact.text());if(!subject.equals(d.subject())||!status.equals(d.status())||!version.equals(d.productVersion())||!"APPROVED".equals(d.outcome()))reasons.add("DECISION_SCOPE_MISMATCH:"+subject);}catch(Exception e){reasons.add("DECISION_UNREADABLE:"+subject);}}
 private static Source source(Selection s,String k){return s.sources().stream().filter(x->x.key().equals(k)).findFirst().orElseThrow();}
 private static boolean exact(Selection s,Fact f,Set<String> natures){return f!=null&&f.text()!=null&&!f.text().isBlank()&&f.sourceKeys()!=null&&f.sourceKeys().stream().anyMatch(k->s.sources().stream().anyMatch(x->x.key().equals(k)&&x.literal().equals(f.text())&&(natures==null||natures.contains(x.nature()))));}
 private static void requireFact(Selection s,Fact f,String id,ArrayNode reasons){if(!exact(s,f,null))reasons.add("EXACT_FACT_BASIS_MISSING:"+id);}
 private static void requireNature(Selection s,Fact f,Set<String> natures,String id,ArrayNode reasons){if(!exact(s,f,natures))reasons.add("ACTUAL_APPROVED_SOURCE_MISSING:"+id);}
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new InstructionsLabelSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)plan(fixed);var reasons=(ArrayNode)result.required("unknown_reasons");result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));
  var sections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!sections.equals(input.input().targetSections())||!s.sectionIds().containsAll(sections))reasons.add("INSTRUCTIONS_LABEL_SECTION_SCOPE_MISMATCH");
  var verified=new HashSet<String>();var sourceChecks=result.putArray("source_checks");
  for(var src:s.sources()){
   JsonNode found=null;for(var read:reads.required("sources"))if(schema.tree(src.evidence()).equals(read.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=read;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(src.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var check=sourceChecks.addObject().put("source_key",src.key()).put("verified",ok);check.set("evidence",schema.tree(src.evidence()));if(found!=null)check.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(src.key());else reasons.add("EXACT_SOURCE_NOT_VERIFIED:"+src.key());
  }
  var actual=new HashMap<String,JsonNode>();for(var node:input.target().body().required("nodes")){
   String id=node.required("node_id").asText();if(actual.put(id,node)!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);var manifest=input.target().candidate().nodeManifest().stream().filter(n->n.nodeId().equals(id)).findFirst().orElseThrow();
   if(Set.of("TEXT","TABLE","IMAGE","FORMULA").contains(node.required("content").path("kind").asText())&&s.body().stream().noneMatch(b->b.nodeId().equals(id)&&b.sectionId().equals(manifest.sectionId())&&b.content().equals(node.required("content"))))reasons.add("UNAPPROVED_INSTRUCTIONS_LABEL_BODY:"+id);
  }
  var checks=result.putArray("body_checks");var bodyNodeIds=new HashMap<String,String>();
  for(var b:s.body()){
   if(!input.input().targetSections().contains(b.sectionId()))continue;
   var matches=new ArrayList<String>();for(var entry:actual.entrySet())if(b.nodeId().equals(entry.getKey())&&b.content().equals(entry.getValue().required("content"))&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(entry.getKey())&&b.sectionId().equals(n.sectionId())))matches.add(entry.getKey());
   var check=checks.addObject().put("body_key",b.key()).put("exact_body",matches.size()==1);if(matches.size()!=1){reasons.add("INSTRUCTIONS_LABEL_BODY_MISSING_OR_CONFLICT:"+b.key());continue;}String nodeId=matches.get(0);bodyNodeIds.put(b.key(),nodeId);check.put("node_id",nodeId);
   var required=new LinkedHashSet<>(b.sourceKeys());for(String key:b.entityKeys())required.addAll(entitySources(s,key));if(required.isEmpty())reasons.add("BODY_EVIDENCE_EMPTY:"+b.key());
   for(String key:required){if(!verified.contains(key))reasons.add("BODY_SOURCE_UNVERIFIED:"+b.key()+":"+key);boolean linked=false;for(var link:input.target().body().required("evidence_links"))if("SUPPORT".equals(link.path("relation").asText())&&schema.tree(source(s,key).evidence()).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(nodeId.equals(id.asText()))linked=true;if(!linked)reasons.add("BODY_SUPPORT_CITATION_MISSING:"+b.key()+":"+key);}
  }
  // Each approved factual value must be present in body text for its entity, not merely in provenance.
  for(var b:s.body())if(input.input().targetSections().contains(b.sectionId()))for(String key:b.entityKeys()){
   String text=String.join(" ",s.body().stream().filter(x->b.sectionId().equals(x.sectionId())&&x.entityKeys().contains(key)&&bodyNodeIds.containsKey(x.key())).map(Body::text).toList());
   for(var fact:entityFacts(s,key))if(!fact.text().isBlank()&&!text.contains(fact.text()))reasons.add("ENTITY_FACT_BODY_MISMATCH:"+key);
  }
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("blocked",!reasons.isEmpty());result.put("status",reasons.isEmpty()?"READY_FOR_PROFESSIONAL_REVIEW":"UNKNOWN");return result;
 }
 private static List<Fact> entityFacts(Selection s,String key){var facts=new ArrayList<Fact>();for(var d:s.documents()){if(d.kind().equals(key))continue;for(var field:d.fields())if((d.kind()+":"+field.key()).equals(key)&&"REQUIRED".equals(field.applicability()))facts.add(field.value());}return facts;}
 private static Set<String> entitySources(Selection s,String key){var keys=new LinkedHashSet<String>();for(var f:entityFacts(s,key))keys.addAll(f.sourceKeys());for(var d:s.documents())if(key.equals(d.kind())||key.startsWith(d.kind()+":")){if(!d.ruleSourceKey().isEmpty())keys.add(d.ruleSourceKey());keys.addAll(d.versionRecord().sourceKeys());for(var field:d.fields())if((d.kind()+":"+field.key()).equals(key))keys.addAll(field.decision().sourceKeys());}return keys;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("instructions_label",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads")),provenance.required("normalization").get("instructions_label")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
