package com.ruoyi.medical.workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
/** A dedicated saved-document N07 activity. Never reopens the original TASK graph. */
public final class DocumentCheckWorkflowImpl implements DocumentCheckWorkflow {
    @Override public DocumentCheckProtocol.Receipt run(DocumentCheckProtocol.Reference reference){
        var activities=Workflow.newActivityStub(DocumentCheckActivities.class,ActivityOptions.newBuilder()
                .setTaskQueue(reference.professionalTaskQueue()).setStartToCloseTimeout(Duration.ofMillis(reference.activityTimeoutMillis()))
                .setHeartbeatTimeout(Duration.ofMillis(reference.heartbeatTimeoutMillis()))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build()).build());
        var receipt=activities.execute(reference);
        if(receipt==null||!reference.runId().equals(receipt.runId())||!reference.stepId().equals(receipt.stepId())||!reference.inputSha256().equals(receipt.inputSha256())||!reference.documentId().equals(receipt.documentId())||!reference.documentRevision().equals(receipt.documentRevision())||!reference.bodySha256().equals(receipt.bodySha256())||!reference.baselineSha256().equals(receipt.baselineSha256()))
            throw new IllegalArgumentException("CHECK_DOCUMENT_RECEIPT_MISMATCH");
        return receipt;
    }
}
