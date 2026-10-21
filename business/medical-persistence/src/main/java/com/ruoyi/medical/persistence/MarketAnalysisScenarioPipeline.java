package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Actual N05 producer and saved-receipt consumer: reads, decimal arithmetic, citations and independent conclusions. */
final class MarketAnalysisScenarioPipeline {
 private MarketAnalysisScenarioPipeline(){}
 static JsonNode calculations(JsonNode fixed)throws Exception{return ReportScenarioPipeline.calculations(new MarketAnalysisScenarioSchema().arithmeticContext(fixed));}
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads,ArtifactProtocol.ReviewFindings findings)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!MarketAnalysisScenarioSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;
  var schema=new MarketAnalysisScenarioSchema();var s=schema.read(fixed.required("selection"));
  var out=(ObjectNode)ReportScenarioPipeline.produceSelected(input,reads,schema.arithmeticContext(fixed));
  out.put("scenario_kind",MarketAnalysisScenarioSchema.KIND).put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection")))).put("conclusion_authority","INDEPENDENT_MACHINE_REVIEW_NOT_MARKET_OR_PROFESSIONAL_APPROVAL").put("evidence_policy",s.evidencePolicy());out.set("scope",schema.tree(s.scope()));out.set("observations",schema.tree(s.observations()));out.set("conclusion_review_rule",schema.tree(s.conclusionReviewRule()));
  var reasons=(com.fasterxml.jackson.databind.node.ArrayNode)out.required("unknown_reasons");var contextRows=out.putArray("context_source_checks");
  var ruleEvidence=new HashSet<DatasetProtocol.EvidenceRef>();for(var i:s.inputs())ruleEvidence.add(i.evidence());for(var c:s.context())ruleEvidence.add(c.evidence());
  var bases=new HashSet<PermissionProtocol.Basis>();for(var i:s.inputs())bases.add(i.source());for(var c:s.context())bases.add(c.source());
  var rules=input.rules().rules().stream().filter(r->r.ruleId().equals(s.conclusionReviewRule().version_id())&&r.sha256().equals(s.conclusionReviewRule().content_sha256())).toList();
  if(rules.size()!=1||!new HashSet<>(rules.get(0).content().basis_manifest()).containsAll(bases))reasons.add("APPROVED_MARKET_SCOPE_AND_CONCLUSION_RULE_OR_SOURCE_BASIS_MISSING");
  for(var c:s.context()){
   JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(c.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
   boolean verified=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(c.literal().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var row=contextRows.addObject().put("key",c.key()).put("verified",verified);row.set("evidence_ref",schema.tree(c.evidence()));if(found!=null)row.put("receipt_id",found.required("receipt_id").asText());if(!verified)reasons.add("SCOPE_OR_METHOD_SOURCE_NOT_INDEPENDENTLY_VERIFIED:"+c.key());
  }
  // Exhaustive supported text blocks, not just a model-selected subset of favorable claims.
  for(var n:input.target().candidate().nodeManifest())if(!Set.of(ArtifactProtocol.NodeKind.ROOT,ArtifactProtocol.NodeKind.SECTION,ArtifactProtocol.NodeKind.PARAGRAPH,ArtifactProtocol.NodeKind.TEXT).contains(n.kind()))reasons.add("UNSUPPORTED_MARKET_NON_TEXT_LAYOUT");
  var conclusionRows=out.putArray("conclusions");var mapped=new HashSet<String>();
  for(var c:s.conclusions()){
   if(!input.input().targetSections().contains(c.sectionId()))continue;
   var matches=new ArrayList<JsonNode>();for(var n:input.target().body().required("nodes"))if("TEXT".equals(n.required("content").required("kind").asText())&&n.required("content").path("text").asText().startsWith(c.prefix())&&inSection(input,n.required("node_id").asText(),c.sectionId()))matches.add(n);
   var row=conclusionRows.addObject().put("key",c.key()).put("section_id",c.sectionId());row.set("input_keys",schema.tree(c.inputKeys()));row.set("calculation_keys",schema.tree(c.calculationKeys()));var failures=row.putArray("unknown_reasons");
   if(matches.size()!=1){failures.add("CONCLUSION_MISSING_OR_AMBIGUOUS");reasons.add("CONCLUSION_FAILED:"+c.key());continue;}
   var node=matches.get(0);String id=node.required("node_id").asText(),text=node.required("content").required("text").asText();if(!mapped.add(id))failures.add("CONCLUSION_NODE_REUSED");row.put("node_id",id).put("text_sha256",WireValues.sha256(text.getBytes(StandardCharsets.UTF_8)));if(text.substring(c.prefix().length()).isBlank())failures.add("EMPTY_CONCLUSION");
   var evidence=new HashSet<DatasetProtocol.EvidenceRef>();for(String key:c.inputKeys())evidence.add(s.inputs().stream().filter(i->i.key().equals(key)).findFirst().orElseThrow().evidence());for(var context:s.context())evidence.add(context.evidence());
   for(var ref:evidence)if(!linked(input,schema,ref,id))failures.add("CONCLUSION_CITATION_MISSING");
   var claims=input.target().claims().values().stream().filter(claim->whole(claim.anchor(),id,text.length())&&claim.evidenceLinks().stream().map(l->l.evidenceRef()).collect(java.util.stream.Collectors.toSet()).containsAll(evidence)).toList();
   if(claims.isEmpty()||claims.stream().noneMatch(claim->findings.claimChecks().stream().anyMatch(check->check.claimId().equals(claim.claimId())&&check.supportCheck()==ArtifactProtocol.SupportCheck.SUPPORTED)))failures.add("INDEPENDENT_FULL_CONCLUSION_SUPPORT_MISSING");
   for(String key:c.calculationKeys())for(var calculation:out.required("calculations"))if(key.equals(calculation.required("key").asText())&&!"COMPUTED".equals(calculation.required("status").asText()))failures.add("CONCLUSION_CALCULATION_UNKNOWN");
   if(!passed(findings,s.conclusionReviewRule().version_id(),id,ruleEvidence))failures.add("INDEPENDENT_SCOPE_LIMITATIONS_AND_CONCLUSION_RULE_NOT_PASSED");
   row.put("verified",failures.isEmpty());if(!failures.isEmpty())reasons.add("CONCLUSION_FAILED:"+c.key());
  }
  for(var node:input.target().body().required("nodes"))if("TEXT".equals(node.required("content").required("kind").asText())){
   String id=node.required("node_id").asText(),text=node.required("content").path("text").asText();boolean calculated=false;
   for(var c:out.required("calculations"))if("COMPUTED".equals(c.required("status").asText())&&text.equals(c.required("required_text").asText())&&inSection(input,id,c.required("section_id").asText()))calculated=true;
   if(!mapped.contains(id)&&!calculated)reasons.add("UNMAPPED_MARKET_TEXT:"+id);
   if(!passed(findings,s.conclusionReviewRule().version_id(),id,ruleEvidence))reasons.add("INDEPENDENT_STATISTICAL_SCOPE_RULE_NOT_PASSED:"+id);
  }
  out.put("blocked",!reasons.isEmpty());return out;
 }
 private static boolean inSection(TaskSourceVerifierInputAuthority.Verified i,String node,String section){return i.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(node)&&n.sectionId().equals(section));}
 private static boolean passed(ArtifactProtocol.ReviewFindings f,String rule,String node,Set<DatasetProtocol.EvidenceRef> evidence){return f.ruleChecks().stream().anyMatch(c->c.ruleId().equals(rule)&&c.targetNodeIds().contains(node)&&new HashSet<>(c.evidenceRefs()).containsAll(evidence)&&c.result()==ArtifactProtocol.RuleResult.PASS);}
 private static boolean linked(TaskSourceVerifierInputAuthority.Verified input,MarketAnalysisScenarioSchema schema,DatasetProtocol.EvidenceRef ref,String id){for(var link:input.target().body().required("evidence_links"))if(schema.tree(ref).equals(link.required("evidence")))for(var node:link.required("node_ids"))if(id.equals(node.asText()))return true;return false;}
 private static boolean whole(ArtifactProtocol.Anchor anchor,String node,int length){return anchor instanceof ArtifactProtocol.TextAnchor a&&a.nodeId().equals(node)&&a.start()==0&&a.end()==length||anchor instanceof ArtifactProtocol.StructuredAnchor b&&b.nodeId().equals(node)&&b.rowId()==null&&b.fieldId()==null;}
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("market_analysis_scenario",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance,ArtifactProtocol.ReviewFindings findings)throws Exception{if(!Objects.equals(produce(input,provenance.required("independent_reads"),findings),provenance.required("normalization").get("market_analysis_scenario")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
