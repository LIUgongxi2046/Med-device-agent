package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.Set;

/** Separate pre-plan wire contract. A confirmed TASK Plan is intentionally not representable. */
public final class PlanDraftProtocol {
    private PlanDraftProtocol() { }
    public record Reference(String schemaVersion,String executionKind,String jobType,String nodeKind,String tenantId,
            String projectId,String runId,String stepId,String taskId,String taskRevision,String inputArtifactId,
            String inputSha256,String inputReceiptSha256,String admissionSha256,String policyRevision,
            String controlTaskQueue,String professionalTaskQueue,long activityTimeoutMillis,long heartbeatTimeoutMillis) {
        public Reference {
            if(!"1.0.0".equals(schemaVersion)||!"SYSTEM_JOB".equals(executionKind)||!"PLAN_DRAFT".equals(jobType)||!"N01".equals(nodeKind))invalid();
            for(String id:new String[]{tenantId,projectId,runId,stepId,taskId,taskRevision,inputArtifactId,policyRevision})WireValues.uuid(id);
            for(String hash:new String[]{inputSha256,inputReceiptSha256,admissionSha256})WireValues.hash(hash);
            for(String queue:new String[]{controlTaskQueue,professionalTaskQueue})if(queue==null||!queue.matches("[A-Za-z0-9_.-]{1,128}"))invalid();
            if(activityTimeoutMillis<1||activityTimeoutMillis>86400000||heartbeatTimeoutMillis<1||heartbeatTimeoutMillis>activityTimeoutMillis)invalid();
        }
        public String workflowId(){return tenantId+"/plan-draft/"+runId;}
    }
    /** Data returned only after the activity's independent persistence consumer verifies it.
     * SAVED_PENDING_REVIEW never means a plan has been confirmed. */
    public record Receipt(String schemaVersion,String runId,String stepId,String inputSha256,String receiptId,
            String receiptSha256,String state,String candidateArtifactId,String waitingItemId) {
        public Receipt {
            if(!"1.0.0".equals(schemaVersion)||!Set.of("SAVED_PENDING_REVIEW","WAITING_INPUT","FAILED","CANCELLED").contains(state))invalid();
            for(String id:new String[]{runId,stepId,receiptId})WireValues.uuid(id);WireValues.hash(inputSha256);WireValues.hash(receiptSha256);
            if(candidateArtifactId!=null)WireValues.uuid(candidateArtifactId);if(waitingItemId!=null)WireValues.uuid(waitingItemId);
            if("SAVED_PENDING_REVIEW".equals(state)&&candidateArtifactId==null||"WAITING_INPUT".equals(state)&&waitingItemId==null)invalid();
        }
    }
    private static void invalid(){throw new IllegalArgumentException("PLAN_DRAFT_CONTRACT_INVALID");}
}
