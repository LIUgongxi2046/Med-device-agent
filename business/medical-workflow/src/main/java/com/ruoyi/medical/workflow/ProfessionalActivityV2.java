package com.ruoyi.medical.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

@ActivityInterface
public interface ProfessionalActivityV2 {
    @ActivityMethod(name="medical.professional.step.v2")
    ProfessionalOutcomeV2 execute(Reference reference,Dispatch dispatch);
}
