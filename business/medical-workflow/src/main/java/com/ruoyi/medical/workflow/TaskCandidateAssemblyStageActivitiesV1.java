package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskCandidateAssemblyStageProtocolV1.*;
@ActivityInterface public interface TaskCandidateAssemblyStageActivitiesV1 {
 @ActivityMethod(name="medical.run.candidate-assembly.prepare.v1") PreparedV1 prepare(Reference reference);
 @ActivityMethod(name="medical.run.candidate-assembly.dispatch.v1") ProducedV1 dispatch(Reference reference,CommandV1 command);
 @ActivityMethod(name="medical.run.candidate-assembly.commit.v1") CommitV1 commit(Reference reference,ProducedV1 produced);
 @ActivityMethod(name="medical.run.candidate-assembly.recover.v1") RecoveryV1 recover(Reference reference,CommandV1 command);
}
