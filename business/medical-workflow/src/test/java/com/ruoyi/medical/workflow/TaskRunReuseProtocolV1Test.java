package com.ruoyi.medical.workflow;
import java.util.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunReuseProtocolV1.*;
/** Deferred source tests: intentionally not executed during implementation. */
class TaskRunReuseProtocolV1Test {
 private static final String H="a".repeat(64);
 private static String id(){return UUID.randomUUID().toString();}
 private final Reference ref=new Reference("1.0.0",id(),id(),id(),ExecutionKind.TASK,id(),H,id(),9999999999999L,10,10,1,1000,1000,1000,"reuse-control","reuse-professional");
 private ReusedV1 reused(String kind,String run,String step,long generation){return new ReusedV1("1.0.0",kind,run,step,H,id(),id(),generation,id(),H,id(),H,List.of());}
 @Test void originalGenerationDoesNotGrantAnOrdinarySuccessorLease(){String step=id();var original=reused("N02",ref.runId(),step,7);var projection=original.projection(new PlannedStep(step,List.of(),true));assertEquals(State.SUCCEEDED,projection.status());assertEquals(7,projection.leaseGeneration());assertEquals(original.reuseReceiptId(),projection.receiptId());assertThrows(IllegalArgumentException.class,()->reused("N02",ref.runId(),step,0));assertThrows(IllegalArgumentException.class,()->reused("N03",ref.runId(),step,7).projection(new PlannedStep(step,List.of(),true)));}
 @Test void inventoryBindsCurrentRunAndRejectsDuplicates(){var value=reused("N04",ref.runId(),id(),1);new InventoryV1("1.0.0",ref.runId(),List.of(value),null,null).require(ref);assertThrows(IllegalArgumentException.class,()->new InventoryV1("1.0.0",ref.runId(),List.of(value,value),null,null));assertThrows(IllegalArgumentException.class,()->new InventoryV1("1.0.0",id(),List.of(value),null,null));assertThrows(IllegalArgumentException.class,()->reused("N06",ref.runId(),id(),1));}
 @Test void additiveWireIsClosedAndStrict()throws Exception{var wire=new TaskRunReuseSchemaV1();assertEquals("INVENTORY",wire.request(wire.request(ref,"INVENTORY")).operation());var mapper=new JsonMapper();var request=(ObjectNode)mapper.readTree(wire.request(ref,"ASSEMBLY_PREPARE"));request.put("actor_id",id());assertThrows(Exception.class,()->wire.request(mapper.writeValueAsBytes(request)));assertThrows(Exception.class,()->wire.request(("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\",\"operation\":\"INVENTORY\",\"reference\":null}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 @Test void inventoryRoundTripPreservesOriginalPins()throws Exception{var wire=new TaskRunReuseSchemaV1();var value=new InventoryV1("1.0.0",ref.runId(),List.of(reused("N03",ref.runId(),id(),2)),null,null);assertEquals(value,wire.response(new JsonMapper().writeValueAsBytes(wire.response(value,ref)),ref));}
}
