package com.ruoyi.medical.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunWaitingProtocol.*;

/** Additive explicitly registered V2 activity. Not part of the V1 worker/contract. */
@ActivityInterface
public interface TaskRunWaitingActivities {
    @ActivityMethod(name="medical.run.commit-waiting.v2")
    Receipt commitWaiting(Reference reference,Dispatch dispatch,WaitingCompletionV2 completion);
}
