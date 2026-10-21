package com.ruoyi.medical.workflow;
import io.temporal.activity.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
@ActivityInterface
public interface TaskSectionWriterActivityV1 {
    @ActivityMethod(name="medical.task.section-writer.execute.v1")
    TaskSectionWriterStageProtocolV1.CandidateReadyV1 execute(Reference reference,Dispatch dispatch);
}
