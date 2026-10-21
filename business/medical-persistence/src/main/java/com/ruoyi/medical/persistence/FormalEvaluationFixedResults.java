package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Original factual result read before human scoring. Current-use eligibility is a separate strict gate; code upgrades cannot erase old facts. */
public final class FormalEvaluationFixedResults {
 private final EnterpriseAccess access;private final EvaluationService evaluations;private final FormalAgentCandidateAuthority candidates;private final FormalEvaluationExecutionEvidence executions;private final PermissionSchema json;
 public FormalEvaluationFixedResults(EnterpriseAccess access,EvaluationService evaluations,FormalAgentCandidateAuthority candidates,FormalEvaluationExecutionEvidence executions,PermissionSchema json){this.access=access;this.evaluations=evaluations;this.candidates=candidates;this.executions=executions;this.json=json;}
 private record Input(PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,String definition,String revision){}
 public JsonNode read(ApplicationSessionIdentity.Verified actor,String context,String evaluation,String candidateId)throws Exception{
  WireValues.uuid(evaluation);WireValues.uuid(candidateId);PermissionSchema.revision(context);
  var before=access.execute(actor,a->ThrowingSupplier.of(()->input(a,context,evaluation,candidateId)).get());
  var prepared=executions.prepareFactual(actor,context,evaluation,before.candidate(),before.policy());
  return access.execute(actor,a->ThrowingSupplier.of(()->{
   var current=input(a,context,evaluation,candidateId);if(!before.equals(current))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
   var proof=executions.requireFactual(a,evaluation,current.candidate(),current.policy(),prepared);proof.requireConsumer(a,evaluation,current.candidate(),current.policy());
   var data=json.object().put("schema_version","1.0.0").put("evaluation_id",evaluation).put("definition_sha256",current.definition()).put("execution_complete",true).put("production_eligible",false).put("verification_mode","ORIGINAL_FACTS_ONLY").put("current_use_eligible",false);
   data.set("candidate",json.tree(current.candidate()));data.set("execution_policy",json.tree(current.policy()));data.set("records",json.tree(proof.records()));data.set("original_read_receipt_ids",json.tree(proof.originalReadReceiptIds()));
   var result=json.object().put("schema_version","1.0.0").put("request_id",UUID.randomUUID().toString()).put("resource_revision",current.revision());result.set("data",data);proof.requireUnchanged();return result;
  }).get());
 }
 private Input input(EnterpriseAccess.Admission a,String context,String evaluation,String candidateId)throws Exception{
  if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  var d=evaluations.requireFormalDefinitionMetadata(a,evaluation);var ref=d.definition().request().candidates().stream().filter(c->candidateId.equals(c.version_id())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
  var candidate=candidates.require(a,ref,d.row().projectId(),"EVALUATION_RESULTS");candidate.requireUnchanged();d.requireUnchanged();return new Input(ref,candidate.executionPolicy(),d.row().contentHash(),d.row().revision().toString());
 }
}
