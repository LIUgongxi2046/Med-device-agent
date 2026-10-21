package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
@ActivityInterface
public interface TaskSourceVerifierActivityV1 {
    @ActivityMethod(name="medical.task.source-verifier.execute.v1")
    TaskSourceVerifierStageProtocolV1.ReviewCompletedV1 execute(Reference reference,Dispatch dispatch);
}
