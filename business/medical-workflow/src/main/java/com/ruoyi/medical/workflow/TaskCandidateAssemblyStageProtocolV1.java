package com.ruoyi.medical.workflow;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
/** Closed deterministic N06 stage. A produced body is not a saved business document. */
public final class TaskCandidateAssemblyStageProtocolV1 {
 private TaskCandidateAssemblyStageProtocolV1(){}
 public record PreparedV1(String schemaVersion,String runId,String stepId,String inputSha256,String graphId,String graphSha256,String obligationSha256){
  public PreparedV1{version(schemaVersion);ids(runId,stepId,graphId);hashes(inputSha256,graphSha256,obligationSha256);}
  public void require(Reference r){if(r.executionKind()!=ExecutionKind.TASK||!runId.equals(r.runId()))bad();}
  public void require(Reference r,Plan p){require(r);if(!graphId.equals(p.graphReceiptId())||!graphSha256.equals(p.graphSha256())||p.requiredStages().stream().filter(s->"N06".equals(s.stageKind())&&obligationSha256.equals(s.obligationSha256())).count()!=1)bad();}
 }
 public record CommandV1(PreparedV1 prepared,String commandId){public CommandV1{Objects.requireNonNull(prepared);ids(commandId);}public void require(Reference r){prepared.require(r);}}
 public record ArtifactV1(String kind,String artifactId,String payloadSha256,String objectVersion,String receiptSha256){public ArtifactV1{if(!KINDS.contains(kind)||objectVersion==null||objectVersion.isBlank())bad();ids(artifactId);hashes(payloadSha256,receiptSha256);}}
 public static final List<String> KINDS=List.of("ASSEMBLY_MANIFEST","DOCUMENT_BODY","ASSEMBLY_COMPANION","CROSS_FILE_CHECK_REPORT");
 public record ProducedV1(String schemaVersion,CommandV1 command,List<ArtifactV1> artifacts,boolean ready,List<String> blockers,boolean documentSaved,boolean n07Complete){
  public ProducedV1{version(schemaVersion);Objects.requireNonNull(command);artifacts=List.copyOf(artifacts);blockers=List.copyOf(blockers);if(!artifacts.stream().map(ArtifactV1::kind).toList().equals(KINDS)||artifacts.stream().map(ArtifactV1::artifactId).distinct().count()!=4||documentSaved||n07Complete||ready!=blockers.isEmpty())bad();}
  public void require(Reference r){command.require(r);}
 }
 public record CommitV1(String schemaVersion,ProducedV1 produced,String receiptId,String receiptSha256){public CommitV1{version(schemaVersion);Objects.requireNonNull(produced);ids(receiptId);hashes(receiptSha256);}public void require(Reference r){produced.require(r);}public boolean satisfied(){return produced.ready();}}
 public record RecoveryV1(String schemaVersion,String state,CommandV1 command,ProducedV1 produced,CommitV1 commit){public RecoveryV1{version(schemaVersion);Objects.requireNonNull(command);if(!Set.of("PENDING","PRODUCED","COMMITTED").contains(state)||state.equals("PENDING")&&(produced!=null||commit!=null)||state.equals("PRODUCED")&&(produced==null||commit!=null)||state.equals("COMMITTED")&&(produced==null||commit==null)||produced!=null&&!command.equals(produced.command())||commit!=null&&!produced.equals(commit.produced()))bad();}public void require(Reference r,CommandV1 c){command.require(r);if(!command.equals(c))bad();}}
 private static void version(String v){if(!"1.0.0".equals(v))bad();}private static void ids(String...v){for(var x:v)WireValues.uuid(x);}private static void hashes(String...v){for(var x:v)WireValues.hash(x);}private static void bad(){throw new IllegalArgumentException("TASK_CANDIDATE_ASSEMBLY_STAGE_INVALID");}
}
