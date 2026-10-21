package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunStageProtocolV2.*;
@ActivityInterface
public interface TaskRunStageActivitiesV2 {
    @ActivityMethod(name="medical.run.execute-stage.v2") FieldCompletionV2 executeStage(Reference reference,String stageKind,String commandId);
    @ActivityMethod(name="medical.run.recover-stage.v2") StageRecoveryV2 recoverStage(Reference reference,String stageKind,String commandId);
    @ActivityMethod(name="medical.run.commit-stage.v2") StageCommitV2 commitStage(Reference reference,FieldCompletionV2 completion);
}
