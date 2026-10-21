package com.ruoyi.medical.workflow;
import java.util.*;
import com.ruoyi.medical.contracts.WireValues;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
/** New unsigned transport version; original V1/V2 signed schema bytes are unchanged. */
public final class TaskRunReuseProtocolV1 {
 private TaskRunReuseProtocolV1(){}
 public record ReusedV1(String schemaVersion,String stageKind,String runId,String stepId,String inputSha256,String originalRunId,String originalStepId,long originalGeneration,String originalReceiptId,String originalReceiptSha256,String reuseReceiptId,String reuseReceiptSha256,List<Output> outputs){
  public ReusedV1 {if(!"1.0.0".equals(schemaVersion)||!Set.of("N02","N03","N04","N05").contains(stageKind)||originalGeneration<1)invalid();for(String id:List.of(runId,stepId,originalRunId,originalStepId,originalReceiptId,reuseReceiptId))WireValues.uuid(id);for(String hash:List.of(inputSha256,originalReceiptSha256,reuseReceiptSha256))WireValues.hash(hash);if(runId.equals(originalRunId))invalid();outputs=List.copyOf(outputs);}
  public void require(Reference ref){if(!runId.equals(ref.runId())||ref.executionKind()!=ExecutionKind.TASK)invalid();}
  /** Internal scheduler projection only. Generation belongs to ORIGINAL execution,
   * never a successor lease; this is never serialized as an ordinary Step/Receipt. */
  Step projection(PlannedStep planned){if(!"N02".equals(stageKind)||!stepId.equals(planned.stepId()))invalid();return new Step(stepId,inputSha256,planned.dependsOnStepIds(),planned.required(),State.SUCCEEDED,originalGeneration,reuseReceiptId,null,outputs);}
 }
 public record AssemblyV1(String schemaVersion,String runId,String stepId,String inputSha256,String originalRunId,String originalReceiptId,String originalReceiptSha256,String reuseReceiptId,String reuseReceiptSha256,TaskCandidateAssemblyStageProtocolV1.CommitV1 original){
  public AssemblyV1{if(!"1.0.0".equals(schemaVersion)||original==null||!original.satisfied())invalid();for(String id:List.of(runId,stepId,originalRunId,originalReceiptId,reuseReceiptId))WireValues.uuid(id);for(String hash:List.of(inputSha256,originalReceiptSha256,reuseReceiptSha256))WireValues.hash(hash);if(runId.equals(originalRunId)||!originalRunId.equals(original.produced().command().prepared().runId())||!originalReceiptId.equals(original.receiptId()))invalid();}
  public void require(Reference ref){if(!runId.equals(ref.runId()))invalid();}
 }
 public record InventoryV1(String schemaVersion,String runId,List<ReusedV1> reused,AssemblyV1 assembly,FinalReceipt completed){public InventoryV1{if(!"1.0.0".equals(schemaVersion))invalid();WireValues.uuid(runId);reused=List.copyOf(reused);var seen=new HashSet<String>();for(var r:reused)if(!runId.equals(r.runId())||!seen.add(r.stepId()))invalid();if(assembly!=null&&!runId.equals(assembly.runId()))invalid();if(completed!=null&&!runId.equals(completed.runId()))invalid();}public void require(Reference ref){if(!runId.equals(ref.runId())||reused.size()>ref.maximumSteps())invalid();reused.forEach(r->r.require(ref));if(assembly!=null)assembly.require(ref);}}
 private static void invalid(){throw new IllegalArgumentException("TASK_REUSE_V1_INVALID");}
}
