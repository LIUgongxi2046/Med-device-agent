package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Typed deterministic stage facts. FIELD_TARGET is not an artifact Output, and
 * BLOCKED is not a fabricated human waiting item or a fulfilled obligation. */
public final class TaskRunStageProtocolV2 {
    private TaskRunStageProtocolV2(){}
    /** Original admitted command, not a claim/lease receipt. Partial reservations
     * remain query-only and cannot be relabelled READY for another command. */
    public record StageCommandV2(String schemaVersion,String stageKind,String runId,String commandId,String stepId,String inputId,String inputSha256){
        public StageCommandV2{version(schemaVersion);if(!"N03".equals(stageKind))invalid();for(String id:List.of(runId,commandId,stepId,inputId))WireValues.uuid(id);WireValues.hash(inputSha256);}
        public void require(Reference ref){if(ref.executionKind()!=ExecutionKind.TASK||!ref.runId().equals(runId))invalid();}
        public void require(FieldCompletionV2 c){if(!stageKind.equals(c.stageKind())||!runId.equals(c.runId())||!commandId.equals(c.commandId())||!stepId.equals(c.stepId())||!inputId.equals(c.inputId())||!inputSha256.equals(c.inputSha256()))invalid();}
    }
    public record StageRecoveryV2(String schemaVersion,String state,String stageKind,String commandId,FieldCompletionV2 completion,StageCommitV2 receipt){
        public StageRecoveryV2{version(schemaVersion);if(!"N03".equals(stageKind)||!Set.of("PENDING","PRODUCED","COMMITTED").contains(state))invalid();WireValues.uuid(commandId);if((completion!=null)!=state.equals("PRODUCED")||(receipt!=null)!=state.equals("COMMITTED"))invalid();var c=completion!=null?completion:receipt==null?null:receipt.completion();if(c!=null&&(!stageKind.equals(c.stageKind())||!commandId.equals(c.commandId())))invalid();}
        public void require(Reference ref,String stage,String command){if(!stageKind.equals(stage)||!commandId.equals(command))invalid();if(completion!=null)completion.require(ref,stage,command);if(receipt!=null)receipt.completion().require(ref,stage,command);}
    }
    public record FieldCompletionV2(String schemaVersion,String stageKind,String targetKind,String runId,String stepId,String commandId,
            String inputId,String inputSha256,String actionId,long leaseGeneration,String resultId,String resultSha256,String producerReceiptSha256){
        public FieldCompletionV2{version(schemaVersion);if(!"N03".equals(stageKind)||!"FIELD_TARGET".equals(targetKind)||leaseGeneration<1)invalid();
            for(String id:List.of(runId,stepId,commandId,inputId,actionId,resultId))WireValues.uuid(id);for(String hash:List.of(inputSha256,resultSha256,producerReceiptSha256))WireValues.hash(hash);}
        public void require(Reference reference,String stage,String command){if(reference.executionKind()!=ExecutionKind.TASK||!runId.equals(reference.runId())||!stageKind.equals(stage)||!commandId.equals(command))invalid();}
    }
    public record StageCommitV2(String schemaVersion,String stageKind,String obligationSha256,String graphId,String graphSha256,
            FieldCompletionV2 completion,String disposition,String receiptId,String receiptSha256){
        public StageCommitV2{version(schemaVersion);Objects.requireNonNull(completion);if(!stageKind.equals(completion.stageKind())||!Set.of("SATISFIED","BLOCKED").contains(disposition))invalid();
            WireValues.uuid(graphId);WireValues.uuid(receiptId);for(String hash:List.of(obligationSha256,graphSha256,receiptSha256))WireValues.hash(hash);}
        public void require(Reference ref,Plan plan){completion.require(ref,stageKind,completion.commandId());if(!Objects.equals(graphId,plan.graphReceiptId())||!Objects.equals(graphSha256,plan.graphSha256()))invalid();
            var obligations=plan.requiredStages().stream().filter(s->stageKind.equals(s.stageKind())).toList();if(obligations.size()!=1||!obligationSha256.equals(obligations.get(0).obligationSha256())||obligations.get(0).disposition()!=StageDisposition.PENDING||plan.expansions().stream().anyMatch(e->stageKind.equals(e.stageKind())))invalid();}
        public boolean satisfied(){return "SATISFIED".equals(disposition);}
    }
    private static void version(String value){if(!"2.0.0".equals(value))invalid();}
    private static void invalid(){throw new IllegalArgumentException("TASK_STAGE_V2_INVALID");}
}
