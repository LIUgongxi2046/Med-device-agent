package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.*;
import io.temporal.client.WorkflowClient;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Only this physical boundary can produce a V2 acceptance proof. Recovery uses
 * the immutable original deployment and never invokes start. */
public final class TaskRunWorkflowDeliveryV2 {
    private final WorkflowClient client;
    public TaskRunWorkflowDeliveryV2(WorkflowClient client){this.client=Objects.requireNonNull(client);}
    Received deliver(TaskRunWorkflowProtocolBinding.StartPermit permit){outside();var b=permit.binding();var attempt=permit.original().attempt();same(b,attempt.intent());if(!attempt.claim())throw new IllegalStateException("ORIGINAL_RECOVERY_REQUIRED");var transport=new WorkflowDeliveryV2(client,b.deployment(),b.extension(),b.verifier(),b.assembly());return received(b,transport.start(b.intent().reference(),b.intent().commandId()),transport.protocolSha256());}
    Received recover(TaskRunWorkflowProtocolBinding.RecoveryPermit permit){outside();var b=permit.binding();same(b,permit.original().claim());var transport=new WorkflowDeliveryV2(client,b.deployment(),b.extension(),b.verifier(),b.assembly());return received(b,transport.recover(b.intent().reference(),b.intent().commandId()),transport.protocolSha256());}
    private static void same(TaskRunWorkflowProtocolBinding.Bound b,TaskRunWorkflowLedger.Intent i){if(!b.intent().content().equals(i.content()))throw new IllegalArgumentException("V2_ORIGINAL_INTENT_MISMATCH");}
    private static Received received(TaskRunWorkflowProtocolBinding.Bound b,WorkflowDelivery.Acceptance a,String hash){if(a==null||!b.intent().reference().workflowId().equals(a.workflowId())||!b.intent().reference().inputSha256().equals(a.inputSha256())||!b.deployment().canonicalSha256().equals(hash))throw new IllegalArgumentException("V2_ACCEPTANCE_MISMATCH");return new Received(b,a,hash);}
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("WORKFLOW_IO_INSIDE_SQL_TRANSACTION");}
    static final class Received{private final TaskRunWorkflowProtocolBinding.Bound binding;private final WorkflowDelivery.Acceptance acceptance;private final String protocolSha256;private Received(TaskRunWorkflowProtocolBinding.Bound binding,WorkflowDelivery.Acceptance acceptance,String hash){this.binding=binding;this.acceptance=acceptance;this.protocolSha256=hash;}TaskRunWorkflowProtocolBinding.Bound binding(){return binding;}WorkflowDelivery.Acceptance acceptance(){return acceptance;}String protocolSha256(){return protocolSha256;}}
}
