package com.ruoyi.medical.workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
/** A dedicated single N01 activity; there is no TASK graph or fabricated plan revision. */
public final class PlanDraftWorkflowImpl implements PlanDraftWorkflow {
    @Override public PlanDraftProtocol.Receipt run(PlanDraftProtocol.Reference reference){
        var activities=Workflow.newActivityStub(PlanDraftActivities.class,ActivityOptions.newBuilder()
                .setTaskQueue(reference.professionalTaskQueue()).setStartToCloseTimeout(Duration.ofMillis(reference.activityTimeoutMillis()))
                .setHeartbeatTimeout(Duration.ofMillis(reference.heartbeatTimeoutMillis()))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build()).build());
        var receipt=activities.execute(reference);
        if(receipt==null||!reference.runId().equals(receipt.runId())||!reference.stepId().equals(receipt.stepId())||!reference.inputSha256().equals(receipt.inputSha256()))
            throw new IllegalArgumentException("PLAN_DRAFT_RECEIPT_MISMATCH");
        return receipt;
    }
}
