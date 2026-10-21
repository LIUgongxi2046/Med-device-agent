package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** N04 typed plan and N05 deterministic independent real-source/body consumer. No model-issued approval. */
final class RegistrationDossierPipeline {
 private RegistrationDossierPipeline(){}
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new RegistrationDossierSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",RegistrationDossierSchema.KIND,"human_review_required",true,"legal_approval",false,"conclusion_authority","MACHINE_COMPLETENESS_NOT_REGULATORY_OR_QUALITY_APPROVAL"));
  var reasons=out.putArray("unknown_reasons");var p=s.path();var sources=new LinkedHashMap<String,RegistrationDossierSchema.Source>();s.sources().forEach(x->sources.put(x.key(),x));
  boolean first="FIRST".equals(p.applicationType().value())&&"NON_IVD".equals(p.deviceType().value());
  for(var d:List.of(p.applicationType(),p.deviceType(),p.classification(),p.classificationCode(),p.jurisdiction()))if(!exact(sources,d.sourceKey(),d.value(),"APPROVED_DECISION")||"UNKNOWN".equals(d.value()))reasons.add("APPROVED_PATH_INPUT_MISSING:"+d.sourceKey());
  if(!exact(sources,p.regulationEdition().sourceKey(),p.regulationEdition().value(),"REGULATION"))reasons.add("REGULATION_EDITION_UNVERIFIED");
  RegistrationDossierSchema.Template template=null;var templateSource=sources.get(p.templateSourceKey());
  if(!"APPROVED_TEMPLATE".equals(templateSource.nature()))reasons.add("APPROVED_DIRECTORY_TEMPLATE_MISSING");
  else try{template=schema.template(templateSource.literal());}catch(Exception e){reasons.add("DIRECTORY_TEMPLATE_UNREADABLE");}
  var nodes=new LinkedHashMap<String,RegistrationDossierSchema.TemplateNode>();var conditionCodes=new HashSet<String>();
  if(template!=null){
   if(!template.templateVersion().equals(p.templateVersion())||!template.applicationType().equals(p.applicationType().value())||!template.deviceType().equals(p.deviceType().value())||!template.regulationEdition().equals(p.regulationEdition().value()))reasons.add("DIRECTORY_TEMPLATE_PATH_MISMATCH");
   template.nodes().forEach(n->nodes.put(n.code(),n));
   if(first)for(var expected:RegistrationDossierRequirements.firstDirectory().entrySet()){var n=nodes.get(expected.getKey());if(n==null||!expected.getValue().equals(n.title()))reasons.add("OFFICIAL_DIRECTORY_NODE_MISSING_OR_RENAMED:"+expected.getKey());}
   var conditionKeys=new HashSet<String>();for(var c:template.conditions()){
    conditionKeys.add(c.key());conditionCodes.addAll(c.directoryCodes());var decision=s.conditions().stream().filter(x->x.key().equals(c.key())).findFirst().orElse(null);
    if(decision==null||"UNKNOWN".equals(decision.applicability())||decision.rationale().isBlank()||decision.sourceKeys().isEmpty()||decision.sourceKeys().stream().anyMatch(k->!"APPROVED_DECISION".equals(sources.get(k).nature()))){reasons.add("CONDITION_UNRESOLVED:"+c.key());continue;}
    for(String key:decision.sourceKeys())try{var actual=schema.parse(sources.get(key).literal());if(!c.key().equals(actual.path("condition").asText())||!decision.applicability().equals(actual.path("applicability").asText())||!decision.rationale().equals(actual.path("rationale").asText())||!schema.tree(c.directoryCodes()).equals(actual.path("directory_codes")))reasons.add("CONDITION_APPROVED_DECISION_CONFLICT:"+c.key());}catch(Exception e){reasons.add("CONDITION_DECISION_UNREADABLE:"+c.key());}
    for(String code:c.directoryCodes()){var actual=s.directory().stream().filter(x->x.code().equals(code)).findFirst().orElse(null);if(actual==null||!actual.applicability().equals(decision.applicability())||!actual.decisionSourceKeys().containsAll(decision.sourceKeys())||!actual.rationale().equals(decision.rationale()))reasons.add("CONDITION_NODE_MISMATCH:"+c.key()+":"+code);}
   }
   if(first)for(String key:RegistrationDossierRequirements.CONDITIONS)if(!conditionKeys.contains(key))reasons.add("CONDITIONAL_DIRECTORY_GROUP_MISSING:"+key);
   for(var c:s.conditions())if(!conditionKeys.contains(c.key()))reasons.add("UNDECLARED_CONDITION:"+c.key());
  }
  if(Set.of("RENEWAL","CHANGE_REGISTRATION","CHANGE_FILING").contains(p.applicationType().value())){
   if(p.originalCertificateSourceKeys().isEmpty())reasons.add("ORIGINAL_CERTIFICATE_MISSING");if(p.changeHistorySourceKeys().isEmpty())reasons.add("CHANGE_HISTORY_OR_NO_CHANGE_EVIDENCE_MISSING");
   for(String k:p.originalCertificateSourceKeys())if(!"ORIGINAL_ATTACHMENT".equals(sources.get(k).nature()))reasons.add("ORIGINAL_CERTIFICATE_NOT_ORIGINAL:"+k);
   for(String k:p.changeHistorySourceKeys())if(!Set.of("ORIGINAL_ATTACHMENT","APPROVED_DECISION").contains(sources.get(k).nature()))reasons.add("CHANGE_HISTORY_NOT_REAL:"+k);
  }
  if(!nodes.keySet().equals(new HashSet<>(s.directory().stream().map(RegistrationDossierSchema.Directory::code).toList())))reasons.add("DIRECTORY_COMPLETE_NODE_SET_MISMATCH");
  var directory=out.putArray("directory_checks");
  for(var d:s.directory()){
   int before=reasons.size();var n=nodes.get(d.code());if(n==null||!d.title().equals(n.title())||!d.parent().equals(n.parent()))reasons.add("DIRECTORY_TEMPLATE_NODE_CONFLICT:"+d.code());
   if("UNKNOWN".equals(d.applicability()))reasons.add("DIRECTORY_APPLICABILITY_UNKNOWN:"+d.code());
   if("NOT_APPLICABLE".equals(d.applicability())&&(!conditionCodes.contains(d.code())||d.rationale().isBlank()||d.decisionSourceKeys().isEmpty()))reasons.add("MANDATORY_NODE_OR_NA_AUTHORITY_INVALID:"+d.code());
   if("REQUIRED".equals(d.applicability())){
    if(d.page().isBlank())reasons.add("DIRECTORY_PAGE_MISSING:"+d.code());
    if(s.body().stream().noneMatch(b->b.directoryCode().equals(d.code())))reasons.add("DIRECTORY_BODY_MISSING:"+d.code());
    if((n!=null&&n.requiresDocument()||first&&d.code().contains(".")&&!d.title().equals("章节目录"))&&d.documentKeys().isEmpty())reasons.add("FIXED_SUBDOCUMENT_MISSING:"+d.code());
    var requiredFields=new LinkedHashSet<String>();if(n!=null)requiredFields.addAll(n.requiredFieldKeys());if(first)requiredFields.addAll(RegistrationDossierRequirements.FIELDS.getOrDefault(d.code(),List.of()));
    for(String name:requiredFields){var f=s.fields().stream().filter(x->x.directoryCode().equals(d.code())&&x.key().equals(name)).findFirst().orElse(null);if(f==null||!fieldOriginal(schema,sources,f))reasons.add("FIELD_OR_ORIGINAL_MISSING:"+d.code()+":"+name);else if(s.body().stream().noneMatch(b->b.directoryCode().equals(d.code())&&b.fieldKeys().contains(name)&&RegistrationDossierSchema.contains(b.content(),f.value())))reasons.add("BODY_FIELD_NOT_COVERED:"+d.code()+":"+name);}
   }
   var row=directory.addObject().put("code",d.code()).put("responsible",d.responsible()).put("applicability",d.applicability()).put("metadata_complete",before==reasons.size()).put("human_review_status","REQUIRED");row.set("fixed_documents",schema.tree(d.documentKeys()));
  }
  var documents=out.putArray("document_checks");
  for(var d:s.documents()){
   int before=reasons.size();var src=sources.get(d.sourceKey());var n=nodes.get(d.directoryCode());
   if(!"REGISTERED_DOCUMENT".equals(src.nature())||!"APPROVED".equals(d.status()))reasons.add("SUBDOCUMENT_NOT_REAL_APPROVED_VERSION:"+d.key());
   if(expired(d.validThrough(),s.asOfDate()))reasons.add("DOCUMENT_EXPIRED_AT_FIXED_DATE:"+d.key());
   if(d.approvalSourceKey().isEmpty()||!"APPROVED_DECISION".equals(sources.get(d.approvalSourceKey()).nature()))reasons.add("DOCUMENT_APPROVAL_EVIDENCE_MISSING:"+d.key());
   else try{var a=schema.parse(sources.get(d.approvalSourceKey()).literal());if(!src.source().version_id().equals(a.path("version_id").asText())||!src.source().content_sha256().equals(a.path("content_sha256").asText())||!src.evidence().projectId().equals(a.path("project_id").asText())||!d.subject().equals(a.path("subject").asText())||!d.number().equals(a.path("number").asText())||!d.version().equals(a.path("version").asText())||!d.validThrough().equals(a.path("valid_through").asText())||!"APPROVED".equals(a.path("status").asText()))reasons.add("DOCUMENT_APPROVAL_VERSION_SUBJECT_PROJECT_CONFLICT:"+d.key());}catch(Exception e){reasons.add("DOCUMENT_APPROVAL_UNREADABLE:"+d.key());}
   if(n!=null&&n.requiresRecord()&&d.recordSourceKeys().isEmpty())reasons.add("ACTUAL_RECORD_MISSING:"+d.key());if(n!=null&&n.requiresAttachment()&&d.attachmentSourceKeys().isEmpty())reasons.add("ORIGINAL_ATTACHMENT_MISSING:"+d.key());
   if("6.10".equals(d.directoryCode())&&d.attachmentSourceKeys().isEmpty())reasons.add("QUALITY_INSPECTION_ORIGINAL_ATTACHMENTS_MISSING:"+d.key());
   for(String k:d.recordSourceKeys())if(!"ORIGINAL_RECORD".equals(sources.get(k).nature()))reasons.add("DRAFT_IS_NOT_EXECUTED_RECORD:"+d.key()+":"+k);
   for(String k:d.attachmentSourceKeys())if(!"ORIGINAL_ATTACHMENT".equals(sources.get(k).nature()))reasons.add("ATTACHMENT_NOT_ORIGINAL:"+d.key()+":"+k);
   var evidence=new LinkedHashSet<>(d.recordSourceKeys());evidence.addAll(d.attachmentSourceKeys());for(String k:evidence)checkUsage(schema,s,sources,d,k,reasons);
   if(RegistrationDossierRequirements.procedure(d.directoryCode())){
    var proc=s.procedures().stream().filter(x->x.documentKey().equals(d.key())).findFirst().orElse(null);
    if(proc==null||proc.scope().isBlank()||proc.responsibility().isBlank()||proc.workflow().isBlank()||proc.recordSourceKeys().isEmpty())reasons.add("QUALITY_PROCEDURE_SCOPE_RESPONSIBILITY_PROCESS_RECORD_MISSING:"+d.key());
    else{if(!src.literal().contains(proc.scope())||!src.literal().contains(proc.responsibility())||!src.literal().contains(proc.workflow()))reasons.add("QUALITY_PROCEDURE_ORIGINAL_CONFLICT:"+d.key());for(String k:proc.recordSourceKeys())if(!d.recordSourceKeys().contains(k))reasons.add("QUALITY_RECORD_REFERENCE_CONFLICT:"+d.key()+":"+k);}
   }
   if(s.body().stream().noneMatch(b->b.directoryCode().equals(d.directoryCode())&&b.documentKeys().contains(d.key())))reasons.add("BODY_FIXED_DOCUMENT_NOT_COVERED:"+d.key());
   documents.addObject().put("document_key",d.key()).put("version_id",src.source().version_id()).put("content_sha256",src.source().content_sha256()).put("metadata_complete",before==reasons.size());
  }
  for(var f:s.fields())if(!fieldOriginal(schema,sources,f))reasons.add("FIELD_UNSUPPORTED_OR_DRAFT:"+f.directoryCode()+":"+f.key());
  out.set("fixed_path",schema.tree(p));out.set("directory",schema.tree(s.directory()));out.set("required_body",schema.tree(s.body()));out.set("fields",schema.tree(s.fields()));out.set("procedures",schema.tree(s.procedures()));out.set("fixed_documents",schema.tree(s.documents()));out.set("evidence_usage",schema.tree(s.evidenceUsage()));out.put("as_of_date",s.asOfDate());out.put("blocked",!reasons.isEmpty());return out;
 }
 private static boolean fieldOriginal(RegistrationDossierSchema schema,Map<String,RegistrationDossierSchema.Source> sources,RegistrationDossierSchema.Field f){try{var src=sources.get(f.sourceKey());return src!=null&&!"DRAFT".equals(src.nature())&&(f.value().isTextual()?!f.value().asText().isBlank()&&f.value().asText().equals(src.literal()):(!f.value().isContainerNode()||!f.value().isEmpty())&&f.value().equals(schema.parse(src.literal())));}catch(Exception e){return false;}}
 private static boolean exact(Map<String,RegistrationDossierSchema.Source> sources,String key,String value,String nature){var s=sources.get(key);return s!=null&&nature.equals(s.nature())&&value.equals(s.literal());}
 private static boolean expired(String until,String asOf){return !until.isEmpty()&&java.time.LocalDate.parse(until).isBefore(java.time.LocalDate.parse(asOf));}
 private static void checkUsage(RegistrationDossierSchema schema,RegistrationDossierSchema.Selection s,Map<String,RegistrationDossierSchema.Source> sources,RegistrationDossierSchema.Document d,String key,ArrayNode reasons){
  var u=s.evidenceUsage().stream().filter(x->x.documentKey().equals(d.key())&&x.sourceKey().equals(key)).findFirst().orElse(null);
  if(u==null){reasons.add("RECORD_ATTACHMENT_APPLICABILITY_MISSING:"+d.key()+":"+key);return;}
  if(expired(u.validThrough(),s.asOfDate())||java.time.LocalDate.parse(u.recordedAt()).isAfter(java.time.LocalDate.parse(s.asOfDate())))reasons.add("RECORD_ATTACHMENT_DATE_INVALID:"+d.key()+":"+key);
  try{var source=sources.get(u.approvalSourceKey());var a=schema.parse(source.literal());var original=sources.get(key);if(!"APPROVED_DECISION".equals(source.nature())||!"APPLICABLE".equals(a.path("status").asText())||!original.source().version_id().equals(a.path("source_version_id").asText())||!original.source().content_sha256().equals(a.path("content_sha256").asText())||!sources.get(d.sourceKey()).source().version_id().equals(a.path("document_version_id").asText())||!original.evidence().projectId().equals(a.path("project_id").asText())||!u.subject().equals(a.path("subject").asText())||!u.recordedAt().equals(a.path("recorded_at").asText())||!u.validThrough().equals(a.path("valid_through").asText()))reasons.add("RECORD_ATTACHMENT_PROJECT_VERSION_APPLICABILITY_CONFLICT:"+d.key()+":"+key);}catch(Exception e){reasons.add("RECORD_ATTACHMENT_APPLICABILITY_UNREADABLE:"+d.key()+":"+key);}
 }
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!RegistrationDossierSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new RegistrationDossierSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)plan(fixed);result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));var reasons=(ArrayNode)result.required("unknown_reasons");
  var actualSections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!actualSections.equals(input.input().targetSections())||!s.sectionIds().containsAll(actualSections))reasons.add("DOSSIER_SECTION_SCOPE_MISMATCH");
  var sources=new HashMap<String,RegistrationDossierSchema.Source>();s.sources().forEach(x->sources.put(x.key(),x));var verified=new HashSet<String>();var sourceChecks=result.putArray("source_checks");
  for(var source:s.sources()){
   JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(source.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(source.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var check=sourceChecks.addObject().put("source_key",source.key()).put("verified",ok);check.set("fixed_version",schema.tree(source.source()));check.set("evidence",schema.tree(source.evidence()));if(found!=null)check.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(source.key());else reasons.add("ORIGINAL_SOURCE_NOT_INDEPENDENTLY_VERIFIED:"+source.key());
  }
  var directory=new HashMap<String,RegistrationDossierSchema.Directory>();s.directory().forEach(d->directory.put(d.code(),d));
  for(var node:input.target().body().required("nodes")){
   String id=node.required("node_id").asText();var manifest=input.target().candidate().nodeManifest().stream().filter(n->n.nodeId().equals(id)).findFirst().orElseThrow();
   if(("TEXT".equals(node.required("content").path("kind").asText())||!Set.of(ArtifactProtocol.NodeKind.ROOT,ArtifactProtocol.NodeKind.SECTION,ArtifactProtocol.NodeKind.PARAGRAPH).contains(manifest.kind()))&&s.body().stream().noneMatch(b->b.nodeId().equals(id)&&directory.get(b.directoryCode()).sectionId().equals(manifest.sectionId())&&b.content().equals(node.required("content"))))reasons.add("UNAPPROVED_DOSSIER_BODY_NODE:"+id);
  }
  var checks=result.putArray("body_checks");
  for(var b:s.body()){
   var d=directory.get(b.directoryCode());if(!input.input().targetSections().contains(d.sectionId()))continue;
   var matches=new ArrayList<String>();for(var node:input.target().body().required("nodes"))if(b.nodeId().equals(node.required("node_id").asText())&&b.content().equals(node.required("content"))&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(node.required("node_id").asText())&&n.sectionId().equals(d.sectionId())))matches.add(node.required("node_id").asText());
   var check=checks.addObject().put("body_key",b.key()).put("directory_code",d.code()).put("section_id",d.sectionId()).put("exact_structured_body",matches.size()==1);if(matches.size()!=1){reasons.add("DOSSIER_BODY_MISSING_OR_CONFLICT:"+b.key());continue;}String node=matches.get(0);check.put("node_id",node);
   var required=new LinkedHashSet<>(b.sourceKeys());required.addAll(d.decisionSourceKeys());
   for(var f:s.fields())if(f.directoryCode().equals(d.code())&&b.fieldKeys().contains(f.key())){if(!f.sourceKey().isEmpty())required.add(f.sourceKey());if(!RegistrationDossierSchema.contains(b.content(),f.value()))reasons.add("DOSSIER_BODY_FIELD_CONFLICT:"+b.key()+":"+f.key());}
   for(var doc:s.documents())if(b.documentKeys().contains(doc.key())){
    required.add(doc.sourceKey());if(!doc.approvalSourceKey().isEmpty())required.add(doc.approvalSourceKey());required.addAll(doc.recordSourceKeys());required.addAll(doc.attachmentSourceKeys());
    String text=RegistrationDossierSchema.projection(b.content());if(!text.contains(doc.version())||!text.contains(doc.subject())||!doc.number().isEmpty()&&!text.contains(doc.number()))reasons.add("BODY_FIXED_DOCUMENT_IDENTITY_CONFLICT:"+b.key()+":"+doc.key());
    for(var usage:s.evidenceUsage())if(usage.documentKey().equals(doc.key()))required.add(usage.approvalSourceKey());
    for(var proc:s.procedures())if(proc.documentKey().equals(doc.key())){required.addAll(proc.recordSourceKeys());if(!text.contains(proc.scope())||!text.contains(proc.responsibility())||!text.contains(proc.workflow()))reasons.add("BODY_PROCEDURE_CONTENT_CONFLICT:"+b.key()+":"+doc.key());}
   }
   for(String k:required){if(!verified.contains(k))reasons.add("BODY_EVIDENCE_UNVERIFIED:"+b.key()+":"+k);var ref=sources.get(k).evidence();boolean linked=false;for(var link:input.target().body().required("evidence_links"))if(schema.tree(ref).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(node.equals(id.asText()))linked=true;if(!linked)reasons.add("BODY_ORIGINAL_CITATION_MISSING:"+b.key()+":"+k);}
  }
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("directory_complete",!has(reasons,"DIRECTORY","CONDITION","APPROVED_PATH","REGULATION","MANDATORY"));result.put("text_complete",!has(reasons,"BODY","FIELD"));result.put("evidence_complete",!has(reasons,"SOURCE","EVIDENCE","DOCUMENT","RECORD","ATTACHMENT","PROCEDURE","CITATION","CERTIFICATE"));result.put("human_review_complete",false);result.put("blocked",!reasons.isEmpty());return result;
 }
 private static boolean has(ArrayNode reasons,String... words){for(var r:reasons)for(String w:words)if(r.asText().contains(w))return true;return false;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("registration_dossier",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads")),provenance.required("normalization").get("registration_dossier")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
