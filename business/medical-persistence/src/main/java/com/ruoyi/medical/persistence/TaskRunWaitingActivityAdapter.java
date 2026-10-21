package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.workflow.TaskRunWaitingProtocol.WaitingCompletionV2;
import com.ruoyi.medical.workflow.WorkflowProtocol.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Original receipt retry first, fresh current authority only for a new local commit. */
final class TaskRunWaitingActivityAdapter implements TaskRunWaitingActivities {
    private final HttpServletRequest http;private final TaskRunActivityPreparation preparation;private final FormalRunRecoveryAccess recovery;private final FormalTaskNodeAuthority nodes;
    private final TaskRunLaunchRecoveryAuthority launchAuthority;private final TaskRunWorkflowLedger launches;private final TaskRunWorkflowProtocolBinding protocols;private final TaskRunDispatchReservation reservations;private final TaskRuntimeJournal runtime;private final TaskRunWaitingCommitService commits;private final WaitingWorkflowDeploymentV2 deployment;
    TaskRunWaitingActivityAdapter(HttpServletRequest http,TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,FormalTaskNodeAuthority nodes,TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,TaskRunDispatchReservation reservations,TaskRuntimeJournal runtime,TaskRunWaitingCommitService commits,WaitingWorkflowDeploymentV2 deployment){this.http=http;this.preparation=preparation;this.recovery=recovery;this.nodes=nodes;this.launchAuthority=launchAuthority;this.launches=launches;this.protocols=protocols;this.reservations=reservations;this.runtime=runtime;this.commits=commits;this.deployment=deployment;}
    public Receipt commitWaiting(Reference reference,Dispatch dispatch,WaitingCompletionV2 completion){return ThrowingSupplier.of(()->{
        if(reference.executionKind()!=ExecutionKind.TASK||!reference.runId().equals(dispatch.runId()))deny();
        var original=recovery.execute(http,reference.tenantId(),reference.runId(),a->ThrowingSupplier.of(()->{
            var p=launchAuthority.require(a,http,reference.runId(),a.recoveryProof().identity().commandId());same(reference,launches.recover(p).reference());var protocol=protocols.requireWaitingV2(a,reference.runId());
            var reserved=reservations.original(a,reference,dispatch.stepId(),dispatch.commandId()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));reserved.requireDispatch(dispatch);same(dispatch,runtime.requireHistoricalStep(a,dispatch));
            var prior=commits.loadCommitted(a,dispatch.runId(),dispatch.stepId());protocol.requireUnchanged();return prior.isEmpty()?Optional.<Receipt>empty():Optional.of(commits.recover(a,dispatch,completion));
        }).get());if(original.isPresent())return original.get();
        String actor=preparation.withCurrent(http,reference,c->{deployment.require(reference,deployment.namespace());var p=protocols.requireWaitingV2(c.admission(),reference.runId());same(deployment.canonicalSha256(),p.protocolSha256());p.requireUnchanged();return c.admission().actorId();});
        return nodes.execute(http,reference.tenantId(),actor,reference.runId(),dispatch.stepId(),null,false,(w,node)->{var p=protocols.requireWaitingV2(node.controlAdmission(),reference.runId());same(deployment.canonicalSha256(),p.protocolSha256());p.requireUnchanged();return commits.commit(node,dispatch,completion);});
    }).get();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
