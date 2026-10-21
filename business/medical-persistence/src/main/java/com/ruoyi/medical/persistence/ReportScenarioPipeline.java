package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Host deterministic arithmetic and independent N05 check of the actual saved report. */
final class ReportScenarioPipeline {
 private ReportScenarioPipeline(){}
 static JsonNode calculations(JsonNode fixed)throws Exception{
  var schema=new ReportScenarioSchema();var s=schema.read(fixed.required("selection"));var out=schema.tree(Map.of("schema_version","1.0.0"));var rows=((ObjectNode)out).putArray("calculations");
  for(var c:s.calculations()){var row=rows.addObject().put("key",c.key()).put("section_id",c.sectionId()).put("unit",c.unit()).put("operation",c.operation()).put("scale",c.scale()).put("rounding",c.rounding());row.set("inputs",schema.tree(c.inputs()));try{String value=ReportScenarioSchema.calculate(s,c).toPlainString();row.put("status","COMPUTED").put("value",value).put("required_text",c.prefix()+value+c.suffix());}catch(ArithmeticException e){row.put("status","UNKNOWN").put("reason","UNDEFINED_OR_INEXACT_DECIMAL_CALCULATION");}}
  return out;
 }
 static JsonNode produce(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads)throws Exception{
  var fixed=input.documentScenario();if(fixed==null||!ReportScenarioSchema.KIND.equals(fixed.required("selection").required("scenario_kind").asText()))return null;return produceSelected(input,reads,fixed);
 }
 static JsonNode produceSelected(TaskSourceVerifierInputAuthority.Verified input,JsonNode reads,JsonNode fixed)throws Exception{
  var schema=new ReportScenarioSchema();var s=schema.read(fixed.required("selection"));var result=(ObjectNode)calculations(fixed);result.put("scenario_kind",ReportScenarioSchema.KIND).put("human_review_required",true).put("conclusion_authority","MACHINE_REPORT_NOT_APPROVAL");result.set("specification",fixed.required("specification"));result.put("selection_sha256",WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))));var reasons=result.putArray("unknown_reasons");
  if(!s.sectionIds().containsAll(input.input().targetSections()))reasons.add("REPORT_SECTION_SCOPE_OR_ORDER_MISMATCH");
  var actualSections=input.target().candidate().nodeManifest().stream().filter(n->n.kind()==ArtifactProtocol.NodeKind.SECTION).map(ArtifactProtocol.NodeManifestItem::sectionId).toList();if(!actualSections.equals(input.input().targetSections()))reasons.add("REPORT_STRUCTURE_MISMATCH");
  var verified=new HashSet<String>();var sourceRows=result.putArray("source_checks");
  for(var item:s.inputs()){JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(item.evidence()).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}
   boolean ok=found!=null&&found.path("access").asBoolean(false)&&found.path("located").asBoolean(false)&&found.path("complete").asBoolean(false)&&WireValues.sha256(item.decimalLiteral().getBytes(StandardCharsets.UTF_8)).equals(found.path("literal_sha256").asText());
   var row=sourceRows.addObject().put("key",item.key()).put("verified",ok);row.set("evidence_ref",schema.tree(item.evidence()));if(found!=null)row.put("receipt_id",found.required("receipt_id").asText());if(ok)verified.add(item.key());else reasons.add("EXACT_DECIMAL_SOURCE_NOT_VERIFIED:"+item.key());
  }
  for(var calculation:result.required("calculations")){var c=s.calculations().stream().filter(x->x.key().equals(calculation.required("key").asText())).findFirst().orElseThrow();if(!input.input().targetSections().contains(c.sectionId()))continue;if(!"COMPUTED".equals(calculation.required("status").asText())||!verified.containsAll(c.inputs())){reasons.add("CALCULATION_INPUT_UNKNOWN:"+c.key());continue;}
   var matches=new ArrayList<String>();for(var node:input.target().body().required("nodes"))if("TEXT".equals(node.required("content").required("kind").asText())&&calculation.required("required_text").asText().equals(node.required("content").path("text").asText())&&input.target().candidate().nodeManifest().stream().anyMatch(n->n.nodeId().equals(node.required("node_id").asText())&&n.sectionId().equals(c.sectionId())))matches.add(node.required("node_id").asText());
   if(matches.size()!=1){reasons.add("REPORT_VALUE_OR_SECTION_MISMATCH:"+c.key());continue;}String node=matches.get(0);for(String key:c.inputs()){var evidence=s.inputs().stream().filter(x->x.key().equals(key)).findFirst().orElseThrow().evidence();boolean linked=false;for(var link:input.target().body().required("evidence_links"))if(schema.tree(evidence).equals(link.required("evidence")))for(var id:link.required("node_ids"))if(node.equals(id.asText()))linked=true;if(!linked)reasons.add("REPORT_CITATION_MISSING:"+c.key()+":"+key);}
  }
  result.set("verified_section_ids",schema.tree(input.input().targetSections()));result.put("coverage_mode","CURRENT_SECTION_CANDIDATE");result.put("blocked",!reasons.isEmpty());return result;
 }
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized n,JsonNode result){if(result==null)return n;var pins=(ObjectNode)n.pins();pins.set("report_scenario",result);var f=n.findings();if(result.required("blocked").asBoolean())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);}
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance)throws Exception{var expected=produce(input,provenance.required("independent_reads"));if(!Objects.equals(expected,provenance.required("normalization").get("report_scenario")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
