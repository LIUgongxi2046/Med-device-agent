package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** Dedicated evaluation wire data. No TASK authority, approval or success can be constructed here. */
public final class FormalEvaluationProtocol {
    private FormalEvaluationProtocol() {}
    public record Step(String stepId,String nodeKind,String nodeSha256) {
        public Step {WireValues.uuid(stepId);WireValues.hash(nodeSha256);if(nodeKind==null||!nodeKind.matches("N0[0-9]"))bad();}
    }
    public record Reference(String schemaVersion,String executionKind,String jobType,String tenantId,String projectId,
            String executionId,String evaluationId,String runId,String sampleId,int repeatIndex,String inputArtifactId,
            String inputSha256,String admissionSha256,String candidateVersion,String candidateSha256,String policyVersion,
            String policySha256,List<Step> steps,String controlTaskQueue,String professionalTaskQueue,
            long activityTimeoutMillis,long heartbeatTimeoutMillis,long recoveryPollMillis) {
        public Reference {
            if(!"1.0.0".equals(schemaVersion)||!"SYSTEM_JOB".equals(executionKind)||!"EVALUATION_VERIFICATION".equals(jobType))bad();
            for(String id:List.of(tenantId,projectId,executionId,evaluationId,runId,sampleId,inputArtifactId,candidateVersion,policyVersion))WireValues.uuid(id);
            for(String h:List.of(inputSha256,admissionSha256,candidateSha256,policySha256))WireValues.hash(h);
            steps=List.copyOf(steps);if(repeatIndex<0||repeatIndex>=100||steps.isEmpty()||steps.size()>10)bad();
            var ids=new HashSet<String>();var kinds=new HashSet<String>();for(var s:steps)if(!ids.add(s.stepId())||!kinds.add(s.nodeKind()))bad();
            for(String queue:List.of(controlTaskQueue,professionalTaskQueue))if(!queue.matches("[A-Za-z0-9_.-]{1,128}"))bad();
            if(controlTaskQueue.equals(professionalTaskQueue)||activityTimeoutMillis<1||activityTimeoutMillis>86400000||heartbeatTimeoutMillis<1||heartbeatTimeoutMillis>activityTimeoutMillis||recoveryPollMillis<100||recoveryPollMillis>60000)bad();
        }
        public String workflowId(){return tenantId+"/"+runId;}
    }
    /** Original private backend result registration; a host's returned final answer is not this receipt. */
    public record NodeReceipt(String schemaVersion,String runId,String stepId,String nodeSha256,String state,String receiptId,String receiptSha256) {
        public NodeReceipt {
            if(!"1.0.0".equals(schemaVersion)||!Set.of("SUCCEEDED","UNKNOWN","WAITING_INPUT","FAILED","CANCELLED").contains(state))bad();
            for(String id:List.of(runId,stepId,receiptId))WireValues.uuid(id);WireValues.hash(nodeSha256);WireValues.hash(receiptSha256);
        }
        public void require(Reference r,Step s){if(!r.runId().equals(runId)||!s.stepId().equals(stepId)||!s.nodeSha256().equals(nodeSha256))bad();}
    }
    public record CellReceipt(String schemaVersion,String runId,String admissionSha256,String state,String receiptId,String receiptSha256) {
        public CellReceipt {if(!"1.0.0".equals(schemaVersion)||!Set.of("SUCCEEDED","UNKNOWN","WAITING_INPUT","FAILED","CANCELLED").contains(state))bad();WireValues.uuid(runId);WireValues.uuid(receiptId);WireValues.hash(admissionSha256);WireValues.hash(receiptSha256);}
    }
    private static void bad(){throw new IllegalArgumentException("FORMAL_EVALUATION_EXECUTION_CONTRACT_INVALID");}
}
