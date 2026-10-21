package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;
/** Service-authenticated request-scoped adapter. Recovery cannot call a producer. */
public record TaskSectionWriterStageEndpointFactory(TaskSectionWriterPreparation materializer,TaskRunActivityPreparation current,
        FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,
        TaskRunWorkflowProtocolBinding protocols,TaskRunSectionWriterProtocolBinding writerProtocols,FormalTaskNodeAuthority nodes,TaskSectionWriterDispatchJournal dispatches,
        TaskSectionWriterCandidateRecovery candidates,TaskSectionWriterStageCommitService commits,WaitingWorkflowDeploymentV2 base,
        SectionWriterWorkflowExtensionV1 extension){
    private static final JsonMapper READER=JsonMapper.builder().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public TaskSectionWriterStageActivitiesV1 create(HttpServletRequest http){return new TaskSectionWriterStageActivitiesV1(){
        public InventoryV1 prepare(WorkflowProtocol.Reference ref){return ThrowingSupplier.of(()->{verify(http,ref);materializer.materialize(http,ref);return inventory(ref);}).get();}
        public InventoryV1 inventory(WorkflowProtocol.Reference ref){return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,http,ref);return commits.inventory(a,http,ref);}).get());}
        public DispatchedV1 dispatch(WorkflowProtocol.Reference ref,CommandV1 command){return ThrowingSupplier.of(()->{
            command.require(ref);var inventory=inventory(ref);if(!inventory.assigned().contains(command.ready()))bad();var previous=inventory.commands().stream().filter(c->c.stepId().equals(command.stepId())).findFirst();if(previous.isPresent())same(command,previous.get());
            var dispatch=dispatches.dispatch(http,ref,command.stepId(),command.commandId());return new DispatchedV1(command,dispatch);
        }).get();}
        public RecoveryV1 recover(WorkflowProtocol.Reference ref,CommandV1 command){return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{
            command.require(ref);original(a,http,ref);var admitted=dispatches.commands(a,http,ref).stream().filter(c->c.stepId().equals(command.stepId())).findFirst();if(admitted.isPresent())same(command,admitted.get());
            var proof=dispatches.originalByCommand(a,http,ref,command.stepId(),command.commandId());if(proof.isEmpty())return new RecoveryV1("1.0.0","PENDING",command,null,null,null);
            var dispatched=new DispatchedV1(command,proof.get().dispatch());var committed=commits.recover(a,http,ref,dispatched);if(committed.isPresent())return new RecoveryV1("1.0.0","COMMITTED",command,dispatched,null,committed.get());
            var candidate=candidates.original(proof.get());return candidate.isEmpty()?new RecoveryV1("1.0.0","PENDING",command,dispatched,null,null):new RecoveryV1("1.0.0","PRODUCED",command,dispatched,READER.treeToValue(candidate.get(),CandidateReadyV1.class),null);
        }).get());}
        public CommitV1 commit(WorkflowProtocol.Reference ref,DispatchedV1 original,CandidateReadyV1 candidate){return ThrowingSupplier.of(()->{
            candidate.require(ref,original);var prior=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{TaskSectionWriterStageEndpointFactory.this.original(a,http,ref);return commits.recover(a,http,ref,original);}).get());if(prior.isPresent()){same(candidate,prior.get().candidate());return prior.get();}
            String actor=current.withCurrent(http,ref,c->{currentProtocol(c.admission(),ref);return c.admission().actorId();});return nodes.execute(http,ref.tenantId(),actor,ref.runId(),original.dispatch().stepId(),null,false,(w,node)->{currentProtocol(node.controlAdmission(),ref);return commits.commit(node,ref,original,candidate);});
        }).get();}
    };}
    private void verify(HttpServletRequest http,WorkflowProtocol.Reference ref){recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,http,ref);return null;}).get());}
    private void original(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception{var proof=launches.require(a,http,ref.runId(),a.recoveryProof().identity().commandId());same(ref,workflow.recover(proof).reference());if(workflow.read(proof).isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var accepted=writerProtocols.requireAccepted(a,ref.runId());accepted.requireUnchanged();proof.requireUnchanged();}
    private void currentProtocol(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref)throws Exception{var bound=protocols.requireWaitingV2(a,ref.runId());bound.requireUnchanged();var writer=writerProtocols.requireAccepted(a,ref.runId());writer.requireUnchanged();}
}
