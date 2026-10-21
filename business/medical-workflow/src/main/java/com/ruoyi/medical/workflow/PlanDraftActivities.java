package com.ruoyi.medical.workflow;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
@ActivityInterface
public interface PlanDraftActivities {
    /** The producer rechecks actual admission, launcher receipt, lease, host, model and AT-10
     * journals. A retry may read the original receipt, never regenerate an unknown result. */
    @ActivityMethod(name="medical.system.plan-draft.execute.v1") PlanDraftProtocol.Receipt execute(PlanDraftProtocol.Reference reference);
}
