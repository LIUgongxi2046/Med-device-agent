package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.WorkflowDelivery;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Physical Temporal boundary. Existing attempts are always query-only; NOT_FOUND is unresolved,
 * never permission to replace the start. No SQL transaction spans a Temporal call. */
public final class TaskRunWorkflowDelivery {
    private final Boundary transport;
    public TaskRunWorkflowDelivery(WorkflowDelivery delivery){
        Objects.requireNonNull(delivery);
        this.transport=new Boundary(){
            public String namespace(){return delivery.namespace();}
            public WorkflowDelivery.Acceptance start(Reference r,String c){return delivery.start(r,c);}
            public WorkflowDelivery.Acceptance recover(Reference r,String c){return delivery.recover(r,c);}
        };
    }
    /** Explicit test boundary only; production uses the actual WorkflowDelivery constructor. */
    TaskRunWorkflowDelivery(Boundary transport){this.transport=Objects.requireNonNull(transport);}
    public Received deliver(TaskRunWorkflowLedger.StartPermit permit){
        outside();var attempt=Objects.requireNonNull(permit).attempt();var intent=attempt.intent();namespace(intent);
        WorkflowDelivery.Acceptance result;
        if(attempt.claim()){
            result=transport.start(intent.reference(),intent.commandId());
        }else throw new IllegalStateException("CURRENT_RECOVERY_AUTHORITY_REQUIRED");
        return received(intent,result);
    }
    public Received recover(TaskRunWorkflowLedger.RecoveryPermit permit){
        outside();var intent=Objects.requireNonNull(permit).claim();namespace(intent);return received(intent,transport.recover(intent.reference(),intent.commandId()));
    }
    private void namespace(TaskRunWorkflowLedger.Intent intent){if(!intent.namespace().equals(transport.namespace()))throw new IllegalArgumentException("WORKFLOW_NAMESPACE_MISMATCH");}
    private static Received received(TaskRunWorkflowLedger.Intent intent,WorkflowDelivery.Acceptance result){
        if(result==null||!intent.reference().workflowId().equals(result.workflowId())||!intent.reference().inputSha256().equals(result.inputSha256()))
            throw new IllegalArgumentException("WORKFLOW_ACKNOWLEDGEMENT_MISMATCH");
        return new Received(intent,result);
    }
    /** Typed bridge only: V2 Received has a private constructor and is issued only
     * after the real SDK checks the immutable deployment, memo and start command. */
    static Received fromV2(TaskRunWorkflowDeliveryV2.Received received){
        Objects.requireNonNull(received);
        if(!received.binding().deployment().canonicalSha256().equals(received.protocolSha256()))
            throw new IllegalArgumentException("WORKFLOW_PROTOCOL_ACKNOWLEDGEMENT_MISMATCH");
        return received(received.binding().intent(),received.acceptance());
    }
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("WORKFLOW_IO_INSIDE_SQL_TRANSACTION");}
    interface Boundary {String namespace();WorkflowDelivery.Acceptance start(Reference reference,String command);WorkflowDelivery.Acceptance recover(Reference reference,String command);}
    public static final class Received {
        private final TaskRunWorkflowLedger.Intent intent;private final WorkflowDelivery.Acceptance acceptance;
        private Received(TaskRunWorkflowLedger.Intent intent,WorkflowDelivery.Acceptance acceptance){this.intent=intent;this.acceptance=acceptance;}
        TaskRunWorkflowLedger.Intent intent(){return intent;}WorkflowDelivery.Acceptance acceptance(){return acceptance;}
    }
}
