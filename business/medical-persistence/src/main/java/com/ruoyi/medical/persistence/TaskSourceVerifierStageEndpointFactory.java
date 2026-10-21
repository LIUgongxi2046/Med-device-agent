package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRunActivityJournal.*;
/** Service-authenticated request-scoped adapter. Recovery cannot call a producer. */
public record TaskSourceVerifierStageEndpointFactory(TaskSourceVerifierPreparation materializer,TaskRunActivityPreparation current,
        FormalRunRecoveryAccess recovery,TaskRunLaunchRecoveryAuthority launches,TaskRunWorkflowLedger workflow,
        TaskRunWorkflowProtocolBinding protocols,TaskRunSourceVerifierProtocolBinding verifierProtocols,FormalTaskNodeAuthority nodes,TaskSourceVerifierDispatchJournal dispatches,
        TaskSourceVerifierReviewRecovery reviews,TaskSourceVerifierStageCommitService commits,WaitingWorkflowDeploymentV2 base,
        SourceVerifierWorkflowExtensionV1 extension,SectionWriterWorkflowExtensionV1 writerExtension,TaskSectionWriterStageCommitService writerStages,TaskRunSectionWriterProtocolBinding writerProtocols){
    private static final JsonMapper READER=JsonMapper.builder().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public TaskSourceVerifierStageActivitiesV1 create(HttpServletRequest http){return new TaskSourceVerifierStageActivitiesV1(){
        public InventoryV1 prepare(WorkflowProtocol.Reference ref){return ThrowingSupplier.of(()->{verify(http,ref);requireWriterReady(http,ref);materializer.materialize(http,ref);return inventory(ref);}).get();}
        public InventoryV1 inventory(WorkflowProtocol.Reference ref){return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,http,ref);return commits.inventory(a,http,ref);}).get());}
        public DispatchedV1 dispatch(WorkflowProtocol.Reference ref,CommandV1 command){return ThrowingSupplier.of(()->{
            command.require(ref);requireWriterReady(http,ref);var inventory=inventory(ref);if(!inventory.assigned().contains(command.ready()))bad();var previous=inventory.commands().stream().filter(c->c.stepId().equals(command.stepId())).findFirst();if(previous.isPresent())same(command,previous.get());
            var dispatch=dispatches.dispatch(http,ref,command.stepId(),command.commandId());return new DispatchedV1(command,dispatch);
        }).get();}
        public RecoveryV1 recover(WorkflowProtocol.Reference ref,CommandV1 command){return recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{
            command.require(ref);original(a,http,ref);var admitted=dispatches.commands(a,http,ref).stream().filter(c->c.stepId().equals(command.stepId())).findFirst();if(admitted.isPresent())same(command,admitted.get());
            var proof=dispatches.originalByCommand(a,http,ref,command.stepId(),command.commandId());if(proof.isEmpty())return new RecoveryV1("1.0.0","PENDING",command,null,null,null);
            var dispatched=new DispatchedV1(command,proof.get().dispatch());var committed=commits.recover(a,http,ref,dispatched);if(committed.isPresent())return new RecoveryV1("1.0.0","COMMITTED",command,dispatched,null,committed.get());
            var review=reviews.original(proof.get());return review.isEmpty()?new RecoveryV1("1.0.0","PENDING",command,dispatched,null,null):new RecoveryV1("1.0.0","PRODUCED",command,dispatched,READER.treeToValue(review.get(),ReviewCompletedV1.class),null);
        }).get());}
        public CommitV1 commit(WorkflowProtocol.Reference ref,DispatchedV1 original,ReviewCompletedV1 review){return ThrowingSupplier.of(()->{
            review.require(ref,original);var prior=recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{TaskSourceVerifierStageEndpointFactory.this.original(a,http,ref);return commits.recover(a,http,ref,original);}).get());if(prior.isPresent()){same(review,prior.get().review());return prior.get();}
            String actor=current.withCurrent(http,ref,c->{currentProtocol(c.admission(),ref);return c.admission().actorId();});return nodes.execute(http,ref.tenantId(),actor,ref.runId(),original.dispatch().stepId(),null,false,(w,node)->{currentProtocol(node.controlAdmission(),ref);return commits.commit(node,ref,original,review);});
        }).get();}
    };}
    private void requireWriterReady(HttpServletRequest http,WorkflowProtocol.Reference ref){recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,http,ref);writerProtocols.requireAccepted(a,ref.runId()).requireUnchanged();if(!writerStages.inventory(a,http,ref).satisfied())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);return null;}).get());}
    private void verify(HttpServletRequest http,WorkflowProtocol.Reference ref){recovery.execute(http,ref.tenantId(),ref.runId(),a->ThrowingSupplier.of(()->{original(a,http,ref);return null;}).get());}
    private void original(EnterpriseAccess.Admission a,HttpServletRequest http,WorkflowProtocol.Reference ref)throws Exception{var proof=launches.require(a,http,ref.runId(),a.recoveryProof().identity().commandId());same(ref,workflow.recover(proof).reference());if(workflow.read(proof).isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var accepted=verifierProtocols.requireAccepted(a,ref.runId());accepted.requireUnchanged();proof.requireUnchanged();}
    private void currentProtocol(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref)throws Exception{base.require(ref,base.namespace());extension.require(base,writerExtension);var bound=protocols.requireWaitingV2(a,ref.runId());same(base.canonicalSha256(),bound.protocolSha256());bound.requireUnchanged();var verifier=verifierProtocols.requireAccepted(a,ref.runId());same(extension,verifier.extension());verifier.requireUnchanged();var writer=writerProtocols.requireAccepted(a,ref.runId());same(writerExtension,writer.extension());writer.requireUnchanged();}
}
