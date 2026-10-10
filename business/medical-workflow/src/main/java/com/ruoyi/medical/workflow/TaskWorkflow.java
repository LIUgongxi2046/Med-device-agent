package com.ruoyi.medical.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

@WorkflowInterface
public interface TaskWorkflow {
    @WorkflowMethod(name="medical.task.run") FinalReceipt run(Reference reference);
    @SignalMethod(name="medical.task.command") void command(Command command);
    @QueryMethod(name="medical.task.view") View view();
    @QueryMethod(name="medical.task.command-receipt") CommandReceipt commandReceipt(String commandId);
}
