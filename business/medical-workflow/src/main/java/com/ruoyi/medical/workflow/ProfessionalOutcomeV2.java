package com.ruoyi.medical.workflow;

import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunWaitingProtocol.*;

/** Closed explicit V2 result union. Neither branch weakens V1 Completion. */
public record ProfessionalOutcomeV2(String schemaVersion,String kind,Completion completion,WaitingCompletionV2 waiting){
    public ProfessionalOutcomeV2{
        if(!"2.0.0".equals(schemaVersion)||!("RESULT".equals(kind)&&completion!=null&&waiting==null||"WAITING_CONTROL".equals(kind)&&completion==null&&waiting!=null))throw new IllegalArgumentException("PROFESSIONAL_OUTCOME_V2_REQUIRED");
    }
    public void require(Dispatch dispatch){
        String run=completion==null?waiting.runId():completion.runId(),step=completion==null?waiting.stepId():completion.stepId(),input=completion==null?waiting.inputSha256():completion.inputSha256();
        long generation=completion==null?waiting.leaseGeneration():completion.leaseGeneration();
        if(!dispatch.runId().equals(run)||!dispatch.stepId().equals(step)||!dispatch.inputSha256().equals(input)||dispatch.leaseGeneration()!=generation)throw new IllegalArgumentException("PROFESSIONAL_OUTCOME_BINDING_MISMATCH");
    }
}
