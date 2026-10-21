package com.ruoyi.medical.workflow;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
/** Written but deliberately not executed before the requested integration testing gate. */
class TaskSourceVerifierStageProtocolV1Test {
 private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}private static final String H="a".repeat(64);
 private CommandV1 command(){return new CommandV1(id(1),id(2),H,id(3),List.of(id(4)));}
 private DispatchedV1 dispatched(){return new DispatchedV1(command(),new Dispatch(id(1),id(2),id(5),id(3),H,1,id(6),H));}
 private ReviewCompletedV1 review(String status,boolean coverage,List<String> issues){return new ReviewCompletedV1("1.0.0","N05",id(8),id(1),id(2),id(9),H,id(10),"CANDIDATE",id(11),id(12),H,id(13),H,id(14),H,coverage,status,false,id(15),id(16),issues);}
 private InventoryV1 inventory(ReviewCompletedV1 review,List<String> required){return new InventoryV1("1.0.0",id(1),id(17),H,H,required,List.of(command().ready()),List.of(command()),List.of(dispatched()),List.of(new CommitV1("1.0.0",dispatched(),review,id(18),H)));}
 @Test void partialAssignmentNeverPasses(){assertFalse(inventory(review("SUCCEEDED",true,List.of()),List.of(id(4),id(20))).satisfied());}
 @Test void unresolvedOrIncompleteReviewNeverPasses(){assertFalse(inventory(review("WAITING_INPUT",false,List.of()),List.of(id(4))).satisfied());assertFalse(inventory(review("WAITING_INPUT",true,List.of(id(21))),List.of(id(4))).satisfied());}
 @Test void allPassingCoverageSatisfiesOnlyVerifierInventory(){assertTrue(inventory(review("SUCCEEDED",true,List.of()),List.of(id(4))).satisfied());}
 @Test void partialReservationCannotRedispatch(){var inventory=new InventoryV1("1.0.0",id(1),id(17),H,H,List.of(id(4)),List.of(command().ready()),List.of(command()),List.of(),List.of());assertTrue(inventory.ready().isEmpty());assertFalse(inventory.satisfied());}
 @Test void resultClosedShapeAndRealSubmitAction()throws Exception{var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var receipt=review("SUCCEEDED",true,List.of());var wire=mapper.valueToTree(receipt);assertEquals(22,wire.size());assertEquals(receipt,mapper.treeToValue(wire,ReviewCompletedV1.class));assertNotEquals(receipt.actionId(),dispatched().dispatch().actionId());var changed=(com.fasterxml.jackson.databind.node.ObjectNode)wire;changed.put("professional_approval",true);assertThrows(Exception.class,()->mapper.treeToValue(changed,ReviewCompletedV1.class));}
 @Test void fakeCoverageSuccessRejected(){assertThrows(IllegalArgumentException.class,()->review("SUCCEEDED",false,List.of()));}
 @Test void noFabricatedRecovery(){assertThrows(IllegalArgumentException.class,()->new RecoveryV1("1.0.0","PRODUCED",command(),null,review("SUCCEEDED",true,List.of()),null));}
 @Test void noWriterOrOrdinaryCompletionAlias()throws Exception{var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var n=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(review("SUCCEEDED",true,List.of()));n.put("node_kind","N04");assertThrows(Exception.class,()->mapper.treeToValue(n,ReviewCompletedV1.class));n.put("node_kind","N05").put("completion",true);assertThrows(Exception.class,()->mapper.treeToValue(n,ReviewCompletedV1.class));}
}
