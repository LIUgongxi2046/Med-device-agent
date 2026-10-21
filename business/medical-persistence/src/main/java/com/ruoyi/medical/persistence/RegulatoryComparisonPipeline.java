package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Host-only typed producer and consumer on the real N05 terminal path. No model fields are used. */
public final class RegulatoryComparisonPipeline {
 private RegulatoryComparisonPipeline(){}
 public record ClauseResult(String clauseKey,String literalChange,String applicability,List<String> unknownReasons,
   PermissionProtocol.Basis rule,DatasetProtocol.EvidenceRef baseline,DatasetProtocol.EvidenceRef target,String baselineReceipt,String targetReceipt){public ClauseResult{unknownReasons=List.copyOf(unknownReasons);}}
 public record Result(String schemaVersion,String scenarioKind,PermissionProtocol.Basis specification,String selectionSha256,
   String coverageMode,String asOfDate,List<ClauseResult> clauses,boolean blocked,boolean humanReviewRequired,String conclusionAuthority){public Result{clauses=List.copyOf(clauses);}}
 static Result produce(JsonNode fixed,TaskSourceVerifierInputAuthority.Verified input,JsonNode reads,RuleExpression engine,ProjectSchema projects)throws Exception{
  if(fixed==null)return null;
  var schema=new RegulatoryComparisonSchema();var selection=schema.read(fixed.required("selection"));
  var spec=fixed.required("specification");var basis=new PermissionProtocol.Basis(spec.required("version_id").asText(),spec.required("content_sha256").asText());
  var attributes=projects.attributes(input.production().replay().content().required("project_profile").required("attributes"));
  var result=new ArrayList<ClauseResult>();
  for(var clause:selection.clauses()){
   var reasons=new ArrayList<String>();var before=read(reads,clause.baseline(),schema);var after=read(reads,clause.target(),schema);
   String change="UNKNOWN";
   if(clause.baseline()==null||clause.target()==null)reasons.add("UNPAIRED_CLAUSE_NOT_PROOF_OF_ADDITION_OR_REMOVAL");
   else if(complete(before)&&complete(after))change=before.required("literal_sha256").asText().equals(after.required("literal_sha256").asText())?"EXACT_LITERAL_UNCHANGED":"EXACT_LITERAL_CHANGED";
   else reasons.add("ORIGINAL_OR_PARSED_LOCATOR_INCOMPLETE");
   String applicability="UNKNOWN";
   if(clause.applicabilityRule()==null)reasons.add("APPROVED_APPLICABILITY_MAPPING_MISSING");
   else{
    var rules=input.rules().rules().stream().filter(r->r.ruleId().equals(clause.applicabilityRule().version_id())&&r.sha256().equals(clause.applicabilityRule().content_sha256())).toList();
    if(rules.size()!=1)reasons.add("APPROVED_APPLICABILITY_RULE_MISSING");
    else{
     var rule=rules.get(0);var roots=new HashSet<>(rule.content().basis_manifest());
     if(!roots.contains(selection.baseline().source())||!roots.contains(selection.target().source()))reasons.add("RULE_NOT_BOUND_TO_BOTH_LAW_EDITIONS");
     else{
      boolean asOf=attributes.stream().anyMatch(a->"regulatory_as_of_date".equals(a.key())&&"KNOWN".equals(a.status())&&a.value()!=null&&selection.asOfDate().equals(a.value().asText()));
      boolean jurisdiction=attributes.stream().anyMatch(a->"regulatory_jurisdiction".equals(a.key())&&"KNOWN".equals(a.status())&&a.value()!=null&&selection.target().jurisdiction().equals(a.value().asText()));
      if(!asOf||!jurisdiction)reasons.add("CONFIRMED_AS_OF_OR_JURISDICTION_FACT_MISSING");
      var evaluated=engine.evaluate(engine.compile(rule.content().expression()),attributes);
      for(String key:evaluated.unknown_input_keys())reasons.add("BUSINESS_FACT_UNKNOWN:"+key);
      if(reasons.isEmpty()&&!"UNKNOWN".equals(evaluated.status()))applicability="MACHINE_"+evaluated.status();
     }
    }
   }
   result.add(new ClauseResult(clause.clauseKey(),change,applicability,reasons,clause.applicabilityRule(),clause.baseline(),clause.target(),receipt(before),receipt(after)));
  }
  return new Result("1.0.0",RegulatoryComparisonSchema.SCENARIO_KIND,basis,WireValues.sha256(ContentReadAuthorization.canonicalBytes(fixed.required("selection"))),"SELECTED_CLAUSES",selection.asOfDate(),result,result.stream().anyMatch(r->"UNKNOWN".equals(r.applicability())||"UNKNOWN".equals(r.literalChange())),true,"MACHINE_PROPOSITION_NOT_LEGAL_APPROVAL");
 }
 static TaskSourceVerifierNormalizer.Normalized attach(TaskSourceVerifierNormalizer.Normalized normalized,Result result){
  if(result==null)return normalized;var pins=(ObjectNode)normalized.pins();pins.set("regulatory_comparison",new RegulatoryComparisonSchema().tree(result));
  var f=normalized.findings();if(result.blocked())f=new ArtifactProtocol.ReviewFindings(f.targetKind(),f.targetRevision(),f.targetSha256(),f.ruleRevision(),f.requiredClaimIds(),f.requiredRuleIds(),f.claimChecks(),f.ruleChecks(),f.issueProposals(),false);
  pins.put("coverage_complete",f.coverageComplete());return new TaskSourceVerifierNormalizer.Normalized(f,pins);
 }
 static void consume(TaskSourceVerifierInputAuthority.Verified input,JsonNode provenance,RuleExpression engine,ProjectSchema projects)throws Exception{
  var expected=produce(input.regulatoryComparison(),input,provenance.required("independent_reads"),engine,projects);
  var actual=provenance.required("normalization").get("regulatory_comparison");
  if(expected==null?actual!=null:!new RegulatoryComparisonSchema().tree(expected).equals(actual))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
 }
 private static JsonNode read(JsonNode reads,DatasetProtocol.EvidenceRef ref,RegulatoryComparisonSchema schema){if(ref==null)return null;JsonNode found=null;for(var r:reads.required("sources"))if(schema.tree(ref).equals(r.required("evidence_ref"))){if(found!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);found=r;}return found;}
 private static boolean complete(JsonNode read){return read!=null&&read.path("access").asBoolean(false)&&read.path("located").asBoolean(false)&&read.path("complete").asBoolean(false);}
 private static String receipt(JsonNode read){return read==null?null:read.required("receipt_id").asText();}
}
