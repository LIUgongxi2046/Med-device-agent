package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private pre-COMMIT FIELD_TARGET proof. Status flags alone can never satisfy stage N03. */
public final class TaskStrictFieldCompletionAuthority {
    private final TaskStrictFieldOutputLedger outputs;private final TaskStrictFieldOriginalResult originals;private final TaskStrictFieldDispatchReservation commands;private final JdbcTemplate db;private final TaskStrictFieldNode execution;private final TaskStrictFieldResultRegistration results;private final TaskStrictFieldSchema schema;
    public TaskStrictFieldCompletionAuthority(DataSource data,TaskStrictFieldNode execution,TaskStrictFieldResultRegistration results,TaskStrictFieldSchema schema,TaskStrictFieldOriginalResult originals,TaskStrictFieldDispatchReservation commands,TaskStrictFieldOutputLedger outputs){this.outputs=outputs;this.originals=originals;this.commands=commands;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.execution=execution;this.results=results;this.schema=schema;}
    public Original requireOriginal(EnterpriseAccess.Admission recovery,com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2 locator)throws Exception {
        var ref=originalReference(recovery,locator);Guard guard=()->{var command=commands.original(recovery,ref,locator.stepId(),locator.commandId()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));command.requireCompletion(locator);};guard.require();return new Original(originals.require(recovery,locator),guard);
    }
    public Original requireLifecycleOriginal(TaskRunLifecycleAuthority.Proof proof,com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2 locator)throws Exception {
        Guard guard=()->commands.lifecycle(proof,locator);guard.require();return new Original(originals.lifecycle(proof,locator),guard);
    }
    public Original requireConsumerOriginal(TaskNodeProductionAuthority.Verified production,com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2 locator)throws Exception{Guard guard=()->commands.consumer(production,locator);guard.require();return new Original(originals.consumer(production,locator),guard);}
    public Original requireConsumerOriginal(TaskRunResumeOriginalAuthority.Source source,com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2 locator)throws Exception{
        Guard guard=()->{source.requireOriginal(source.admission(),locator.runId(),locator.stepId());commands.consumer(source.successor(),locator,source);};guard.require();return new Original(originals.consumer(source,locator),guard);
    }
    private com.ruoyi.medical.workflow.WorkflowProtocol.Reference originalReference(EnterpriseAccess.Admission a,com.ruoyi.medical.workflow.TaskRunStageProtocolV2.FieldCompletionV2 locator)throws Exception {
        if(!a.recoveryWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);a.recoveryProof().requireActive();
        var rows=db.queryForList("SELECT reference_json FROM task_run_workflow_launch WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),locator.runId());
        if(rows.size()!=1)bad();return schema.bind(schema.read(rows.get(0).get("reference_json").toString()),com.ruoyi.medical.workflow.WorkflowProtocol.Reference.class);
    }
    @FunctionalInterface private interface Guard{void require()throws Exception;}
    public final class Original {
        private final TaskStrictFieldOriginalResult.Original result;private final Guard guard;
        private Original(TaskStrictFieldOriginalResult.Original result,Guard guard){this.result=result;this.guard=guard;}
        public void requireUnchanged()throws Exception{guard.require();result.requireUnchanged();guard.require();}
        public JsonNode receipt(){return result.receipt();}public String receiptSha256(){return result.receiptSha256();}public String resultSha256(){return result.resultSha256();}public boolean stageSatisfied(){return result.stageSatisfied();}
    }
    public Produced requireProduced(FormalTaskNodeAuthority.Verified node,String resultId)throws Exception {
        var current=execution.currentForCommit(node,resultId);var receipt=results.recover(current);if(receipt==null||!resultId.equals(receipt.required("result_id").textValue()))bad();
        var rows=db.queryForList("SELECT result_sha256,receipt_sha256 FROM task_strict_field_result WHERE tenant_id=? AND result_id=? LIMIT 2 FOR UPDATE",current.input().input().tenantId(),resultId);
        if(rows.size()!=1||!schema.hash(receipt).equals(rows.get(0).get("receipt_sha256")))bad();var output=outputs.original(current.admission(),receipt);return new Produced(node,resultId,current,output.receipt(),output.receiptSha256());
    }
    public final class Produced {
        private final FormalTaskNodeAuthority.Verified node;private final String resultId;private final TaskStrictFieldNode.Current current;private final JsonNode receipt;private final String hash;
        private Produced(FormalTaskNodeAuthority.Verified node,String resultId,TaskStrictFieldNode.Current current,JsonNode receipt,String hash){this.node=node;this.resultId=resultId;this.current=current;this.receipt=receipt.deepCopy();this.hash=hash;}
        public void requireUnchanged()throws Exception {current.requireUnchanged();var internal=results.recover(execution.currentForCommit(node,resultId));var fresh=outputs.original(current.admission(),internal);if(!receipt.equals(fresh.receipt())||!hash.equals(fresh.receiptSha256()))bad();}
        /** Mechanical strict-field satisfaction only; never a professional approval or release decision. */
        public void requireStageSatisfied()throws Exception {
            requireUnchanged();var rows=db.queryForList("SELECT result_json FROM task_strict_field_result WHERE tenant_id=? AND result_id=? LIMIT 2 FOR UPDATE",current.input().input().tenantId(),resultId);
            if(rows.size()!=1)bad();var body=schema.read(rows.get(0).get("result_json").toString());if(!resultSha256().equals(schema.hash(body)))bad();
            var evaluation=body.required("evaluation");var comparison=evaluation.required("comparison");
            if(!"CHECKED".equals(evaluation.required("disposition").asText())||!evaluation.required("waiting_entries").isEmpty()||comparison.isNull())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            var expected=new HashSet<String>();for(var e:current.input().input().targetManifest().required("target").required("entries"))expected.add(e.required("entry_id").asText());
            for(var e:comparison.required("entries"))if(!expected.remove(e.required("entry_id").asText())||!"COMPUTED".equals(e.required("outcome").asText())||!"MATCH".equals(e.required("result").asText()))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            if(!expected.isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);var output=outputs.original(current.admission(),receipt.required("result_receipt"));if(!output.content().missingRequirementIds().isEmpty()||output.content().methodCoverage().stream().anyMatch(m->m.result()!=com.ruoyi.medical.contracts.ArtifactProtocol.CoverageResult.COMPLETE))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);requireUnchanged();
        }
        public String runId(){return current.input().input().runId();}public String stepId(){return current.input().input().stepId();}public String inputId(){return current.input().inputId();}
        public String inputSha256(){return current.input().inputSha256();}public String actionId(){return current.actionId();}public long leaseGeneration(){return Long.parseLong(current.generation());}
        public String resultId(){return resultId;}public String resultSha256(){return receipt.required("result_sha256").textValue();}public String receiptSha256(){return hash;}
        public String disposition(){return receipt.required("disposition").textValue();}public JsonNode receipt(){return receipt.deepCopy();}
        public String targetKind(){return "FIELD_TARGET";}public String targetArtifactId(){return receipt.required("target_artifact_id").textValue();}
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
