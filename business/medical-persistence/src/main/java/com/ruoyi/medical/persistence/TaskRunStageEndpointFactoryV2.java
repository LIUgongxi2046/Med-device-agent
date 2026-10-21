package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.workflow.TaskRunStageProtocolV2.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;
/** No worker singleton retains a caller request or an execution admission. */
public record TaskRunStageEndpointFactoryV2(TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,
        TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,
        FormalTaskNodeAuthority nodes,TaskStrictFieldExecutionBridge execution,TaskRunStageCommitServiceV2 commits,WaitingWorkflowDeploymentV2 deployment){
    public TaskRunStageActivitiesV2 create(HttpServletRequest http){return new TaskRunStageActivitiesV2(){
        public FieldCompletionV2 executeStage(WorkflowProtocol.Reference ref,String stage,String command){return ThrowingSupplier.of(()->{
            var saved=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,ref,http);return commits.load(a,ref).stream().filter(s->s.stageKind().equals(stage)).findFirst();}).get());if(saved.isPresent()){saved.get().completion().require(ref,stage,command);return saved.get().completion();}
            boolean reserved=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,ref,http);var commands=execution.originalCommands(a,ref);if(commands.isEmpty())return false;if(commands.size()!=1)bad();var prior=commands.get(0);same(stage,prior.stageKind());same(command,prior.commandId());return true;}).get());
            if(reserved)return execution.recover(http,ref,stage,command).orElseThrow(()->new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE));
            return execution.execute(http,ref,stage,command);
        }).get();}
        public StageRecoveryV2 recoverStage(WorkflowProtocol.Reference ref,String stage,String command){return ThrowingSupplier.of(()->{
            var saved=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,ref,http);return commits.load(a,ref).stream().filter(s->s.stageKind().equals(stage)).findFirst();}).get());if(saved.isPresent()){saved.get().completion().require(ref,stage,command);return new StageRecoveryV2("2.0.0","COMMITTED",stage,command,null,saved.get());}
            var produced=execution.recover(http,ref,stage,command);if(produced.isPresent()){produced.get().require(ref,stage,command);return new StageRecoveryV2("2.0.0","PRODUCED",stage,command,produced.get(),null);}return new StageRecoveryV2("2.0.0","PENDING",stage,command,null,null);
        }).get();}
        public StageCommitV2 commitStage(WorkflowProtocol.Reference ref,FieldCompletionV2 completion){return ThrowingSupplier.of(()->{
            completion.require(ref,"N03",completion.commandId());var saved=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,ref,http);return commits.recover(a,ref,completion);}).get());if(saved.isPresent())return saved.get();
            String actor=preparation.withCurrent(http,ref,c->{current(c.admission(),ref);return c.admission().actorId();});return nodes.execute(http,ref.tenantId(),actor,ref.runId(),completion.stepId(),null,false,(watermarks,node)->{current(node.controlAdmission(),ref);return commits.commit(node,ref,completion);});
        }).get();}
    };}
    private void original(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,HttpServletRequest http)throws Exception{var proof=launchAuthority.require(a,http,ref.runId(),a.recoveryProof().identity().commandId());same(ref,launches.recover(proof).reference());if(launches.read(proof).isEmpty())bad();current(a,ref);proof.requireUnchanged();}
    private void current(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref)throws Exception{deployment.require(ref,deployment.namespace());var p=protocols.requireWaitingV2(a,ref.runId());same(deployment.canonicalSha256(),p.protocolSha256());p.requireUnchanged();}
}
