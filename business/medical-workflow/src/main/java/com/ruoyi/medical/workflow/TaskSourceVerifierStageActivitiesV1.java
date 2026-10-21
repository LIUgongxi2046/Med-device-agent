package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;
@ActivityInterface
public interface TaskSourceVerifierStageActivitiesV1 {
    @ActivityMethod(name="medical.run.source-verifier.prepare.v1") InventoryV1 prepare(Reference reference);
    @ActivityMethod(name="medical.run.source-verifier.inventory.v1") InventoryV1 inventory(Reference reference);
    @ActivityMethod(name="medical.run.source-verifier.dispatch.v1") DispatchedV1 dispatch(Reference reference,CommandV1 command);
    @ActivityMethod(name="medical.run.source-verifier.commit.v1") CommitV1 commit(Reference reference,DispatchedV1 original,ReviewCompletedV1 review);
    @ActivityMethod(name="medical.run.source-verifier.recover.v1") RecoveryV1 recover(Reference reference,CommandV1 command);
}
