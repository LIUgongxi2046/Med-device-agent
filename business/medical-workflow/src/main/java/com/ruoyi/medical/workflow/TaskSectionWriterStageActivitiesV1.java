package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.*;
@ActivityInterface
public interface TaskSectionWriterStageActivitiesV1 {
    @ActivityMethod(name="medical.run.section-writer.prepare.v1") InventoryV1 prepare(Reference reference);
    @ActivityMethod(name="medical.run.section-writer.inventory.v1") InventoryV1 inventory(Reference reference);
    @ActivityMethod(name="medical.run.section-writer.dispatch.v1") DispatchedV1 dispatch(Reference reference,CommandV1 command);
    @ActivityMethod(name="medical.run.section-writer.commit.v1") CommitV1 commit(Reference reference,DispatchedV1 original,CandidateReadyV1 candidate);
    @ActivityMethod(name="medical.run.section-writer.recover.v1") RecoveryV1 recover(Reference reference,CommandV1 command);
}
