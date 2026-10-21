package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;

/** Explicit additive waiting-control locator. V1 Completion and its serialized
 * history are unchanged: a waiting receipt is never disguised as an artifact. */
public final class TaskRunWaitingProtocol {
    private TaskRunWaitingProtocol(){}
    public record WaitingCompletionV2(String schemaVersion,String outcome,String runId,String stepId,String inputSha256,
            long leaseGeneration,String waitingReceiptId,String waitingReceiptSha256){
        public WaitingCompletionV2{
            if(!"2.0.0".equals(schemaVersion)||!"WAITING_CONTROL".equals(outcome)||leaseGeneration<1)throw new IllegalArgumentException("WAITING_COMPLETION_V2_REQUIRED");
            for(String id:List.of(runId,stepId,waitingReceiptId))WireValues.uuid(id);WireValues.hash(inputSha256);WireValues.hash(waitingReceiptSha256);
        }
    }
}
