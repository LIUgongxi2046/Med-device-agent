package com.ruoyi.medical.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

@ActivityInterface
public interface ProfessionalActivity {
    @ActivityMethod(name="medical.professional.step") Completion execute(Reference reference, Dispatch dispatch);
}
