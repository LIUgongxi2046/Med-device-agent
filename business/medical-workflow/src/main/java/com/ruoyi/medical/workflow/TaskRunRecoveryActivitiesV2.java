package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunRecoveryProtocolV2.*;
@ActivityInterface
public interface TaskRunRecoveryActivitiesV2 {
    @ActivityMethod(name="medical.run.load.v2") RunPlanV2 loadV2(Reference reference);
    @ActivityMethod(name="medical.run.fail-undispatched.v2") UndispatchedFailureV2 failUndispatched(Reference reference,String stepId);
    /** Original query only. Never dispatch, reclaim, reopen a host, or send to a model. */
    @ActivityMethod(name="medical.run.recover-step.v2") StepRecoveryV2 recoverStep(Reference reference,Dispatch original);
}
