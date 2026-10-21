package com.ruoyi.medical.workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
@WorkflowInterface
public interface DocumentCheckWorkflow {
    @WorkflowMethod(name="medical.system.check-document.v1") DocumentCheckProtocol.Receipt run(DocumentCheckProtocol.Reference reference);
}
