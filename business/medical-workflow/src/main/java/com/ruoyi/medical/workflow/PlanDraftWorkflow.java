package com.ruoyi.medical.workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
@WorkflowInterface
public interface PlanDraftWorkflow {
    @WorkflowMethod(name="medical.system.plan-draft.v1") PlanDraftProtocol.Receipt run(PlanDraftProtocol.Reference reference);
}
