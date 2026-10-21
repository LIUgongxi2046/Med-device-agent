package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Set;

/** Independent saved-document execution. The original TASK relation is evidence, not its execution identity. */
public final class DocumentCheckProtocol {
    private DocumentCheckProtocol() { }
    public record Reference(String schemaVersion,String executionKind,String jobType,String nodeKind,
            String tenantId,String projectId,String runId,String stepId,String intentId,
            String documentId,String documentRevision,String bodySha256,String baselineSha256,
            String inputSha256,String admissionSha256,
            String policyRevision,String controlTaskQueue,String professionalTaskQueue,
            long activityTimeoutMillis,long heartbeatTimeoutMillis) {
        public Reference {
            if(!"1.0.0".equals(schemaVersion)||!"SYSTEM_JOB".equals(executionKind)||!"CHECK_DOCUMENT".equals(jobType)||!"N07".equals(nodeKind))invalid();
            for(String id:new String[]{tenantId,projectId,runId,stepId,intentId,documentId,documentRevision,policyRevision})WireValues.uuid(id);
            for(String hash:new String[]{bodySha256,baselineSha256,inputSha256,admissionSha256})WireValues.hash(hash);
            for(String queue:new String[]{controlTaskQueue,professionalTaskQueue})if(queue==null||!queue.matches("[A-Za-z0-9_.-]{1,128}"))invalid();
            if(controlTaskQueue.equals(professionalTaskQueue)||activityTimeoutMillis<1||activityTimeoutMillis>86400000||heartbeatTimeoutMillis<1||heartbeatTimeoutMillis>activityTimeoutMillis)invalid();
        }
        public String workflowId(){return tenantId+"/check-document/"+runId;}
    }
    /** Only an independently registered actual-body check can finish the job. No professional approval is implied. */
    public record Receipt(String schemaVersion,String runId,String stepId,String inputSha256,
            String receiptId,String receiptSha256,String state,String documentId,String documentRevision,
            String bodySha256,String baselineSha256,List<String> checkIds,boolean professionalApproval) {
        public Receipt {
            if(!"1.0.0".equals(schemaVersion)||!Set.of("SUCCEEDED","WAITING_INPUT","FAILED","CANCELLED").contains(state)||professionalApproval)invalid();
            for(String id:new String[]{runId,stepId,receiptId,documentId,documentRevision})WireValues.uuid(id);
            for(String hash:new String[]{inputSha256,receiptSha256,bodySha256,baselineSha256})WireValues.hash(hash);
            checkIds=List.copyOf(checkIds);
            if(checkIds.size()>512||checkIds.size()!=Set.copyOf(checkIds).size()||"SUCCEEDED".equals(state)&&checkIds.isEmpty())invalid();
            checkIds.forEach(WireValues::uuid);
        }
    }
    private static void invalid(){throw new IllegalArgumentException("CHECK_DOCUMENT_CONTRACT_INVALID");}
}
