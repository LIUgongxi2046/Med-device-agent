package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Independent N05 consumer for associated source questions; never upgrades OCR to vision. */
final class MultimodalQaPipeline {
 private MultimodalQaPipeline(){}
 static JsonNode plan(JsonNode fixed)throws Exception{
  var schema=new MultimodalQaSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(s);
  out.put("output_contract","Exactly one TEXT block per current-section question, beginning key|ANSWER|, key|NO_ANSWER| or key|CONFLICT| followed by a nonempty explanation. Cite all associated evidence. Preserve contradictions; never infer image appearance from OCR. Do not emit unrelated text.");
  out.put("native_visual_understanding",false).put("human_review_required",true);return out;
 }
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads,ArtifactProtocol.ReviewFindings findings)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!MultimodalQaSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new MultimodalQaSchema();var s=schema.read(fixed.required("selection"));var out=(ObjectNode)schema.tree(Map.of("schema_version","1.0.0","scenario_kind",MultimodalQaSchema.KIND,"human_review_required",true,"native_visual_understanding",false,"capability",s.capability(),"conclusion_authority","INDEPENDENT_MACHINE_REVIEW_NOT_PROFESSIONAL_APPROVAL"));
  out.set("specification",fixed.required("specification"));out.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));out.set("verified_section_ids",schema.tree(input.input().targetSections()));var reasons=out.putArray("unknown_reasons");var sourceRows=out.putArray("inputs");var valid=new HashSet<String>();
  var rule=input.rules().rules().stream().filter(r->r.ruleId().equals(s.reviewRule().version_id())&&r.sha256().equals(s.reviewRule().content_sha256())).toList();
  if(rule.size()!=1||!new HashSet<>(rule.get(0).content().basis_manifest()).containsAll(s.inputs().stream().map(MultimodalQaSchema.Input::source).toList()))reasons.add("APPROVED_QA_RULE_OR_SOURCE_BASIS_MISSING");
  // All authored inputs remain visible, even if a model would prefer only favorable evidence.
  for(var source:s.inputs()){
   JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(source.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
   boolean verified=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(source.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var row=sourceRows.addObject().put("key",source.key()).put("representation",source.representation()).put("verified",verified);row.set("evidence_ref",schema.tree(source.evidence()));if(found!=null)row.put("receipt_id",found.required("receipt_id").asText());if(verified)valid.add(source.key());else reasons.add("SOURCE_NOT_INDEPENDENTLY_VERIFIED:"+source.key());
  }
  for(var n:input.target().candidate().nodeManifest())if(!Set.of(ArtifactProtocol.NodeKind.ROOT,ArtifactProtocol.NodeKind.SECTION,ArtifactProtocol.NodeKind.PARAGRAPH,ArtifactProtocol.NodeKind.TEXT).contains(n.kind()))reasons.add("UNSUPPORTED_QA_OUTPUT_NODE");
  var mapped=new HashSet<String>();var answers=out.putArray("answers");
  for(var q:s.questions()){
   if(!input.input().targetSections().contains(q.sectionId()))continue;
   var row=answers.addObject().put("key",q.key()).put("question",q.question()).put("section_id",q.sectionId());row.set("input_keys",schema.tree(q.inputKeys()));var failures=row.putArray("unknown_reasons");var nodes=new ArrayList<JsonNode>();
   for(var node:input.target().body().required("nodes"))if("TEXT".equals(node.required("content").required("kind").asText())&&node.required("content").path("text").asText().startsWith(q.key()+"|")&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(node.required("node_id").asText())&&n.sectionId().equals(q.sectionId())))nodes.add(node);
   if(nodes.size()!=1){failures.add("ANSWER_MISSING_OR_AMBIGUOUS");reasons.add("QUESTION_FAILED:"+q.key());continue;}
   var node=nodes.get(0);String id=node.required("node_id").asText(),text=node.required("content").required("text").asText();mapped.add(id);row.put("node_id",id).put("answer_sha256",WireValues.sha256(text.getBytes(StandardCharsets.UTF_8)));
   String[] pieces=text.split("\\|",3);String status=pieces.length==3?pieces[1]:"INVALID";row.put("status",status);if(pieces.length==3)row.put("text",pieces[2]);if(!Set.of("ANSWER","NO_ANSWER","CONFLICT").contains(status)||pieces.length!=3||pieces[2].isBlank())failures.add("INVALID_ANSWER_FORMAT");
   var refs=new HashSet<DatasetProtocol.EvidenceRef>();for(String key:q.inputKeys()){var source=s.inputs().stream().filter(i->i.key().equals(key)).findFirst().orElseThrow();refs.add(source.evidence());if(!valid.contains(key))failures.add("QUESTION_SOURCE_UNVERIFIED:"+key);boolean linked=false;for(var link:input.target().body().required("evidence_links"))if(schema.tree(source.evidence()).equals(link.required("evidence")))for(var target:link.required("node_ids"))if(id.equals(target.asText()))linked=true;if(!linked)failures.add("QUESTION_CITATION_MISSING:"+key);}
   if("CONFLICT".equals(status)&&refs.size()<2)failures.add("CONFLICT_REQUIRES_DISTINCT_INPUTS");
   if("ANSWER".equals(status)&&input.target().claims().values().stream().noneMatch(c->whole(c.anchor(),id,text.length())&&c.evidenceLinks().stream().map(l->l.evidenceRef()).collect(java.util.stream.Collectors.toSet()).containsAll(refs)&&findings.claimChecks().stream().anyMatch(check->check.claimId().equals(c.claimId())&&check.supportCheck()==ArtifactProtocol.SupportCheck.SUPPORTED)))failures.add("INDEPENDENT_FULL_ANSWER_SUPPORT_MISSING");
   // The fixed rule must review answerability, contradictions and modality limitations, including abstentions.
   if(findings.ruleChecks().stream().noneMatch(c->c.ruleId().equals(s.reviewRule().version_id())&&c.targetNodeIds().contains(id)&&new HashSet<>(c.evidenceRefs()).containsAll(refs)&&c.result()==ArtifactProtocol.RuleResult.PASS))failures.add("INDEPENDENT_ANSWERABILITY_AND_CONFLICT_REVIEW_MISSING");
   row.put("verified",failures.isEmpty()).put("adoptable_answer",failures.isEmpty()&&"ANSWER".equals(status));if(!failures.isEmpty())reasons.add("QUESTION_FAILED:"+q.key());if(!"ANSWER".equals(status))reasons.add("QUESTION_ABSTAINED:"+q.key());
  }
  if(answers.isEmpty())reasons.add("NO_QUESTIONS_FOR_CURRENT_SECTION");
  for(var node:input.target().body().required("nodes"))if("TEXT".equals(node.required("content").required("kind").asText())&&!mapped.contains(node.required("node_id").asText()))reasons.add("UNMAPPED_QA_TEXT:"+node.required("node_id").asText());
  out.put("blocked",!reasons.isEmpty());return out;
 }
 private static boolean whole(ArtifactProtocol.Anchor anchor,String node,int length){return anchor instanceof ArtifactProtocol.TextAnchor a&&a.nodeId().equals(node)&&a.start()==0&&a.end()==length||anchor instanceof ArtifactProtocol.StructuredAnchor b&&b.nodeId().equals(node)&&b.rowId()==null&&b.fieldId()==null;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("multimodal_qa",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance,ArtifactProtocol.ReviewFindings findings)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads"),findings),provenance.required("normalization").get("multimodal_qa")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
