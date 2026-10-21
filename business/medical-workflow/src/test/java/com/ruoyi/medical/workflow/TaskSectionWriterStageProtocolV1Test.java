package com.ruoyi.medical.workflow;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
/** Authored during implementation; intentionally not executed before integration gate. */
class TaskSectionWriterStageProtocolV1Test {
    private static String id(int i){return String.format("00000000-0000-4000-8000-%012d",i);}private static final String H="a".repeat(64);
    private CommandV1 command(){return new CommandV1(id(1),id(2),H,id(3),List.of(id(4)));}
    private DispatchedV1 dispatched(){return new DispatchedV1(command(),new Dispatch(id(1),id(2),id(5),id(3),H,1,id(6),H));}
    private CandidateReadyV1 candidate(){return new CandidateReadyV1("1.0.0",id(7),"READY","1",id(8),id(1),id(2),id(9),H,id(10),H,id(11),H,id(12),id(13),id(14));}
    @Test void partialAssignmentsNeverSatisfyStage(){var d=dispatched();var c=new CommitV1("1.0.0",d,candidate(),id(15),H);var inventory=new InventoryV1("1.0.0",id(1),id(16),H,H,List.of(id(4),id(17)),List.of(command().ready()),List.of(command()),List.of(d),List.of(c));assertFalse(inventory.satisfied());}
    @Test void partialReservationIsNotReadyForNewCommand(){var inventory=new InventoryV1("1.0.0",id(1),id(16),H,H,List.of(id(4)),List.of(command().ready()),List.of(command()),List.of(),List.of());assertTrue(inventory.ready().isEmpty());assertFalse(inventory.satisfied());}
    @Test void realCompleteAssignmentSatisfiesOnlyWriterInventory(){var c=new CommitV1("1.0.0",dispatched(),candidate(),id(15),H);assertTrue(new InventoryV1("1.0.0",id(1),id(16),H,H,List.of(id(4)),List.of(command().ready()),List.of(command()),List.of(dispatched()),List.of(c)).satisfied());}
    @Test void changedOriginalCommandRejected(){var other=new CommandV1(id(1),id(2),H,id(19),List.of(id(4)));assertThrows(IllegalArgumentException.class,()->new DispatchedV1(other,dispatched().dispatch()));}
    @Test void noFabricatedProducedOrCommittedRecovery(){assertThrows(IllegalArgumentException.class,()->new RecoveryV1("1.0.0","PRODUCED",command(),null,candidate(),null));assertThrows(IllegalArgumentException.class,()->new RecoveryV1("1.0.0","COMMITTED",command(),dispatched(),null,null));}
    @Test void candidateUsesExactAt06WireNames()throws Exception{var json=new com.fasterxml.jackson.databind.ObjectMapper();var wire=json.valueToTree(candidate());assertEquals(16,wire.size());assertTrue(wire.has("candidate_id"));assertFalse(wire.has("candidateId"));assertEquals(candidate(),json.treeToValue(wire,CandidateReadyV1.class));assertNotEquals(candidate().actionId(),dispatched().dispatch().actionId());}
    @Test void candidateCannotMasqueradeAsOrdinaryCompletion(){assertThrows(IllegalArgumentException.class,()->new CandidateReadyV1("1.0.0",id(7),"SUCCEEDED","1",id(8),id(1),id(2),id(9),H,id(10),H,id(11),H,id(12),id(13),id(14)));}
    @Test void chapterCannotBeAssignedTwice(){assertThrows(IllegalArgumentException.class,()->new InventoryV1("1.0.0",id(1),id(16),H,H,List.of(id(4)),List.of(command().ready(),new ReadyStepV1(id(22),H,List.of(id(4)))),List.of(),List.of(),List.of()));}
}
