package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.Objects;
import com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2;
/** Presence requires the whole V2 producer/finish graph; current AT08 permission
 * and a real memo-verified accepted V2 launch are still mandatory per call. */
public final class TaskRegisteredToolWaitingGate {
 private final TaskRunWorkflowProtocolBinding protocols;private final TaskRegisteredToolWaitingCompletion completion;private final WaitingWorkflowDeploymentV2 deployment;
 public TaskRegisteredToolWaitingGate(TaskRunWorkflowProtocolBinding protocols,TaskRegisteredToolWaitingCompletion completion,WaitingWorkflowDeploymentV2 deployment){this.protocols=Objects.requireNonNull(protocols);this.completion=Objects.requireNonNull(completion);this.deployment=Objects.requireNonNull(deployment);}
 void require(TaskRegisteredToolAuthority.Checked current)throws Exception{
  current.requireUnchanged();if(!"AT-08".equals(current.tool()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var accepted=protocols.requireWaitingV2(current.session().admission(),current.session().runId());if(!deployment.canonicalSha256().equals(accepted.protocolSha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);accepted.requireUnchanged();
  var sdk=current.session().launch().required("sdk");if(!"0.2.1rc2".equals(sdk.required("runtime_version").textValue())||!"0.2.1rc2".equals(sdk.required("sdk_version").textValue()))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  current.requireUnchanged();
 }
}
