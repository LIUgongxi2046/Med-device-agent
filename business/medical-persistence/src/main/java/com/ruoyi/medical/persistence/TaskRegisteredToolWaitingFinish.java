package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
import java.util.*;
import com.ruoyi.medical.workflow.WaitingWorkflowDeploymentV2;

/** Explicit V2 union. No caller-supplied waiting ID or pretend result artifact. */
public final class TaskRegisteredToolWaitingFinish {
 private final WaitingWorkflowDeploymentV2 deployment;private final TaskProfessionalHostAccess hosts;private final TaskRunWorkflowProtocolBinding protocols;
 private final TaskRegisteredToolReceipts tools;private final ProfessionalRuntimeObservations observations;private final TaskProfessionalSupervisor supervisors;
 private final TaskRegisteredToolWaitingCompletion waiting;private final TaskResearchFinishOrchestrator results;private final PermissionSchema json;
 public TaskRegisteredToolWaitingFinish(TaskProfessionalHostAccess hosts,TaskRunWorkflowProtocolBinding protocols,TaskRegisteredToolReceipts tools,
        ProfessionalRuntimeObservations observations,TaskProfessionalSupervisor supervisors,TaskRegisteredToolWaitingCompletion waiting,TaskResearchFinishOrchestrator results,PermissionSchema json,WaitingWorkflowDeploymentV2 deployment){this.deployment=Objects.requireNonNull(deployment);this.hosts=hosts;this.protocols=protocols;this.tools=tools;this.observations=observations;this.supervisors=supervisors;this.waiting=waiting;this.results=results;this.json=json;}
 void requireProtocol(TaskProfessionalHostAccess.Handle handle,String run)throws Exception{hosts.execute(handle,a->ThrowingSupplier.of(()->{var accepted=protocols.requireWaitingV2(a,run);if(!deployment.canonicalSha256().equals(accepted.protocolSha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);accepted.requireUnchanged();return null;}).get());}
 JsonNode finish(HttpServletRequest http,TaskProfessionalHostAccess.Handle handle,JsonNode request)throws Exception{
  ProfessionalRuntimeObservations.validate(request,"HOST_FINISH");String id=request.required("session_id").textValue();
  boolean hasQuestion=hosts.execute(handle,a->ThrowingSupplier.of(()->{var s=hosts.session(handle,a,id);protocols.requireWaitingV2(a,s.runId()).requireUnchanged();if(!FormalTaskNodeAuthority.sameEvidence(s.binding(),request.required("binding")))bad();return tools.waitingRecord(s)!=null;}).get());
  if(!hasQuestion){var value=json.object().put("schemaVersion","2.0.0").put("kind","RESULT").putNull("waiting");value.set("completion",results.finish(http,handle,request));return value;}
  observations.finishObservation(http,request);
  var snapshot=hosts.execute(handle,a->ThrowingSupplier.of(()->supervisors.snapshotClosure(hosts.session(handle,a,id))).get());var inspection=supervisors.inspect(snapshot);
  hosts.execute(handle,a->ThrowingSupplier.of(()->{supervisors.close(hosts.session(handle,a,id),inspection).requireUnchanged();return null;}).get());
  return hosts.execute(handle,a->ThrowingSupplier.of(()->{var session=hosts.session(handle,a,id);var closure=supervisors.requireClosed(session,inspection);var produced=waiting.produce(session,closure);if(produced==null)bad();
   var value=json.object().put("schemaVersion","2.0.0").put("kind","WAITING_CONTROL").putNull("completion");
   // Temporal camelCase wire is explicit; PermissionSchema uses snake_case internally.
   value.putObject("waiting").put("schemaVersion",produced.schemaVersion()).put("outcome",produced.outcome()).put("runId",produced.runId()).put("stepId",produced.stepId()).put("inputSha256",produced.inputSha256()).put("leaseGeneration",produced.leaseGeneration()).put("waitingReceiptId",produced.waitingReceiptId()).put("waitingReceiptSha256",produced.waitingReceiptSha256());return value;
  }).get());
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
