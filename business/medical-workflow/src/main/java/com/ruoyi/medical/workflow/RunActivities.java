package com.ruoyi.medical.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

@ActivityInterface
public interface RunActivities {
    @ActivityMethod(name="medical.run.load") Plan load(Reference reference);
    @ActivityMethod(name="medical.run.dispatch") Dispatch dispatch(Reference reference, String stepId, String commandId);
    @ActivityMethod(name="medical.run.commit") Receipt commit(Reference reference, Dispatch dispatch, Completion completion);
    @ActivityMethod(name="medical.run.failure") Receipt failure(Reference reference, Dispatch dispatch, String reasonCode);
    @ActivityMethod(name="medical.run.dependency-failed") Receipt dependencyFailed(Reference reference, String stepId);
    @ActivityMethod(name="medical.run.command") CommandReceipt consume(Reference reference, Command command);
    @ActivityMethod(name="medical.run.cancel") Termination cancellation(Reference reference, String commandId, String reasonCode);
    @ActivityMethod(name="medical.run.finish") FinalReceipt finish(Reference reference, State requested, String reasonCode, List<Receipt> receipts, List<String> commandIds);
}
