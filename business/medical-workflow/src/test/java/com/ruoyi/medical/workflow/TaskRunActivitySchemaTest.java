package com.ruoyi.medical.workflow;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunActivitySchema.Operation;

class TaskRunActivitySchemaTest {
    final TaskRunActivitySchema schema=new TaskRunActivitySchema();
    final Reference reference=new Reference("1.0.0",id(1),id(2),id(3),ExecutionKind.TASK,id(4),"a".repeat(64),id(5),9999999999999L,8,8,2,1000,2000,3000,"control","professional");
    final Dispatch dispatch=new Dispatch(id(3),id(6),id(7),id(8),"b".repeat(64),1,id(9),"c".repeat(64));
    final Completion completion=new Completion(id(3),id(6),"b".repeat(64),1,State.SUCCEEDED,id(10),"d".repeat(64));
    @Test void closedThreeOperationInputsRoundTripWithoutActorPermissionsOrToken()throws Exception{
        for(var value:List.of(schema.request(Operation.LOAD,reference),schema.request(Operation.DISPATCH,reference,"step_id",id(6),"command_id",id(8)),schema.request(Operation.COMMIT,reference,"dispatch",dispatch,"completion",completion))){
            var parsed=schema.readRequest(schema.encode(value));assertEquals(reference,parsed.reference());assertArrayEquals(schema.encode(value),schema.encode(parsed.fields()));
            value.put("actor_id",id(11));assertThrows(IllegalArgumentException.class,()->schema.readRequest(schema.encode(value)));
        }
    }
    @Test void duplicateTrailingMalformedUtf8AndWrongNestedScalarRejected()throws Exception{
        var load=schema.request(Operation.LOAD,reference);String raw=new String(schema.encode(load),java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(Exception.class,()->schema.readRequest((raw+" {}").getBytes()));assertThrows(Exception.class,()->schema.readRequest(raw.replace("\"operation\":\"LOAD\"","\"operation\":\"LOAD\",\"operation\":\"LOAD\"").getBytes()));
        assertThrows(Exception.class,()->schema.readRequest(new byte[]{(byte)0xc3,0x28}));
        ((com.fasterxml.jackson.databind.node.ObjectNode)load.required("reference")).put("maximum_steps","8");assertThrows(Exception.class,()->schema.request(load));
    }
    @Test void referenceQueuesAndCommandBoundsNotCoerced()throws Exception{
        var load=schema.request(Operation.LOAD,reference);((com.fasterxml.jackson.databind.node.ObjectNode)load.required("reference")).put("control_task_queue","wrong/queue");assertThrows(Exception.class,()->schema.request(load));
        assertThrows(Exception.class,()->schema.request(Operation.FINISH,reference,"requested",State.SUCCEEDED,"reason_code",null,"receipts",List.of(),"command_ids",List.of(id(8),id(8))));
    }
    @Test void replyMustMatchExactOperationRunAndNestedRecord()throws Exception{
        var request=schema.request(schema.request(Operation.DISPATCH,reference,"step_id",id(6),"command_id",id(8)));var reply=schema.response(request,dispatch);
        assertEquals(dispatch,schema.readResponse(schema.encode(reply),Operation.DISPATCH,reference,Dispatch.class));reply.put("run_id",id(99));assertThrows(Exception.class,()->schema.readResponse(schema.encode(reply),Operation.DISPATCH,reference,Dispatch.class));
        reply.put("run_id",reference.runId());((com.fasterxml.jackson.databind.node.ObjectNode)reply.required("result")).put("lease_generation","1");assertThrows(Exception.class,()->schema.readResponse(schema.encode(reply),Operation.DISPATCH,reference,Dispatch.class));
    }
    @Test void planKeepsPendingLaterStagesAndOnlyRealMaterializedInputs()throws Exception{
        var planned=new PlannedStep(id(6),List.of(),true);var step=new Step(id(6),"b".repeat(64),List.of(),true,State.QUEUED,0,null,null,List.of());
        var stages=List.of("N03","N04","N05","N06").stream().map(s->new RequiredStage(s,"f".repeat(64),null,null)).toList();
        var plan=new Plan(reference.inputSha256(),id(12),State.RUNNING,null,null,List.of(step),List.of(planned),id(13),"e".repeat(64),stages);
        var request=schema.request(schema.request(Operation.LOAD,reference));assertEquals(plan,schema.readResponse(schema.encode(schema.response(request,plan)),Operation.LOAD,reference,Plan.class));
    }
    @Test void httpClientRejectsUnpinnedOriginsBeforeAnyTlsOrNetwork()throws Exception{
        for(String origin:List.of("http://localhost","https://localhost/other","https://user@localhost","https://localhost?x=1","https://localhost#x"))assertThrows(IllegalArgumentException.class,()->new TaskRunActivitiesHttpClient(URI.create(origin),()->{fail("must not request TLS");return null;},Duration.ofSeconds(1),4096,schema));
    }
    @Test void eachAttemptGetsFreshTlsAndDenialHasNoTransportFallback(){
        var count=new AtomicInteger();var client=new TaskRunActivitiesHttpClient(URI.create("https://activities.invalid"),()->{count.incrementAndGet();throw new SecurityException("fixture denial before network");},Duration.ofSeconds(1),4096,schema);
        assertThrows(SecurityException.class,()->client.load(reference));assertThrows(SecurityException.class,()->client.load(reference));assertEquals(2,count.get());
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
