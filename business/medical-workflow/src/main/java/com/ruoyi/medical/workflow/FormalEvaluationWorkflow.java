package com.ruoyi.medical.workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
@WorkflowInterface
public interface FormalEvaluationWorkflow {
    @WorkflowMethod(name="medical.system.formal-evaluation.v1") FormalEvaluationProtocol.CellReceipt run(FormalEvaluationProtocol.Reference reference);
}
