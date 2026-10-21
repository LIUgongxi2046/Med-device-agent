package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import jakarta.servlet.http.HttpServletRequest;
/** Request-bound adapter; no singleton retains the inbound certificate/request. */
public record TaskRunWaitingEndpointFactory(TaskRunActivityPreparation preparation,FormalRunRecoveryAccess recovery,FormalTaskNodeAuthority nodes,TaskRunLaunchRecoveryAuthority launchAuthority,TaskRunWorkflowLedger launches,TaskRunWorkflowProtocolBinding protocols,TaskRunDispatchReservation reservations,TaskRuntimeJournal runtime,TaskRunWaitingCommitService commits,WaitingWorkflowDeploymentV2 deployment){
    public TaskRunWaitingActivities create(HttpServletRequest http){return new TaskRunWaitingActivityAdapter(http,preparation,recovery,nodes,launchAuthority,launches,protocols,reservations,runtime,commits,deployment);}
}
