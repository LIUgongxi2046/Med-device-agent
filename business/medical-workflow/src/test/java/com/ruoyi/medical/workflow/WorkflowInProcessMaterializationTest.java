package com.ruoyi.medical.workflow;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

/** SDK in-process simulation with explicitly doubled activities. Not deployment/persistence acceptance. */
class WorkflowInProcessMaterializationTest {
    static final String H="1".repeat(64), G="2".repeat(64);
    @Test void actualWorkflowAddsLaterNodeOnlyAfterFirstReceipt(){exercise(false,false);}
    @Test void actualWorkflowFailsWithUnmaterializedDependentAndLaterStage(){exercise(true,false);}
    @Test void actualWorkflowCancelsWithoutInventingLaterInputs(){exercise(false,true);}
    @Test void legacyClosedGraphDoesNotScheduleLazyReloads(){exercise(false,false,true);}
    private void exercise(boolean fail,boolean cancel){exercise(fail,cancel,false);}
    private void exercise(boolean fail,boolean cancel,boolean legacy){
        try(var env=TestWorkflowEnvironment.newInstance()){
            var reference=new Reference("1.0.0",id(),id(),id(),ExecutionKind.TASK,id(),H,id(),
                env.currentTimeMillis()+300000,100,10,1,10000,10000,10000,"test-control","test-professional");
            var control=new Activities(reference,fail,cancel,legacy);
            var worker=env.newWorker("test-control");worker.registerWorkflowImplementationTypes(TaskWorkflowImpl.class);worker.registerActivitiesImplementations(control);
            var professional=env.newWorker("test-professional");professional.registerActivitiesImplementations((ProfessionalActivity)(r,d)->{
                assertFalse(cancel,"Cancelled runs must not invoke professional work");
                control.calls.add(d.stepId());
                return new Completion(r.runId(),d.stepId(),d.inputSha256(),d.leaseGeneration(),fail?State.FAILED:State.SUCCEEDED,id(),H);
            });
            env.start();
            var workflow=env.getWorkflowClient().newWorkflowStub(TaskWorkflow.class,WorkflowOptions.newBuilder()
                .setWorkflowId(reference.workflowId()).setTaskQueue("test-control").build());
            var result=workflow.run(reference);
            assertEquals(cancel?State.CANCELLED:fail?State.FAILED:State.SUCCEEDED,result.status());
            assertEquals(cancel?0:fail?1:2,control.calls.size());
            assertEquals(cancel?0:fail?1:2,control.committed.size());
            if(!fail&&!cancel)assertEquals(List.of(control.first,control.later),control.calls);
            assertTrue(control.finished);
            if(legacy)assertEquals(1,control.loads.get(),"Closed graph must not add control.load commands");
        }
    }
    static final class Activities implements RunActivities {
        final Reference reference;final boolean fail,cancel,legacy;final String first=id(),later=id(),plan=id(),graph=id(),command=id(),expansion=id(),stageReceipt=id(),failureReceipt=id();
        final Map<String,Receipt> committed=new ConcurrentHashMap<>();final List<String> calls=Collections.synchronizedList(new ArrayList<>());volatile boolean finished;
        final java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger();
        Activities(Reference r,boolean fail,boolean cancel,boolean legacy){reference=r;this.fail=fail;this.cancel=cancel;this.legacy=legacy;}
        public Plan load(Reference r){
            loads.incrementAndGet();
            if(legacy)return new Plan(H,plan,State.RUNNING,null,null,List.of(step(first,List.of()),step(later,List.of(first))));
            var firstNode=new PlannedStep(first,List.of(),true);var laterNode=new PlannedStep(later,List.of(first),true);
            var nodes=new ArrayList<PlannedStep>();nodes.add(firstNode);if(fail||committed.containsKey(first))nodes.add(laterNode);
            var steps=new ArrayList<Step>();steps.add(step(first,List.of()));if(!fail&&committed.containsKey(first))steps.add(step(later,List.of(first)));
            RequiredStage stage=new RequiredStage("N03",G,null,null);var expansions=new ArrayList<Expansion>();var failures=new ArrayList<UnmaterializedFailure>();
            if(fail&&committed.containsKey(first)){
                String cause=committed.get(first).receiptId();stage=new RequiredStage("N03",G,stageReceipt,H,StageDisposition.DEPENDENCY_FAILED,cause);
                failures.add(new UnmaterializedFailure(later,failureReceipt,H,cause));
            }else if(!fail&&committed.containsKey(first)){
                expansions.add(new Expansion("N03",G,expansion,H,List.of(laterNode)));
                if(committed.containsKey(later))stage=new RequiredStage("N03",G,stageReceipt,H);
            }
            return new Plan(H,plan,cancel?State.CANCEL_REQUESTED:State.RUNNING,cancel?command:null,null,steps,nodes,graph,G,List.of(stage),expansions,failures);
        }
        private Step step(String id,List<String> dependencies){var receipt=committed.get(id);return receipt==null?new Step(id,H,dependencies,true,State.QUEUED,0,null,null,List.of()):new Step(id,H,dependencies,true,receipt.status(),1,receipt.receiptId(),null,List.of());}
        public Dispatch dispatch(Reference r,String step,String command){if(step.equals(later))assertTrue(committed.containsKey(first));return new Dispatch(r.runId(),step,id(),command,H,1,id(),H);}
        public Receipt commit(Reference r,Dispatch d,Completion c){var receipt=new Receipt(d.stepId(),H,1,c.status(),id(),null,List.of());committed.put(d.stepId(),receipt);return receipt;}
        public Receipt failure(Reference r,Dispatch d,String reason){throw new AssertionError(reason);}
        public Receipt dependencyFailed(Reference r,String step){throw new AssertionError("Unmaterialized dependent must not get a fabricated step");}
        public CommandReceipt consume(Reference r,Command c){throw new AssertionError("No signal in this simulation");}
        public Termination cancellation(Reference r,String c,String reason){assertTrue(cancel);assertEquals(command,c);return new Termination(id(),true,true,true);}
        public FinalReceipt finish(Reference r,State requested,String reason,List<Receipt> receipts,List<String> commands){finished=true;assertEquals(committed.size(),receipts.size());return new FinalReceipt(r.runId(),H,requested,id());}
    }
    static String id(){return UUID.randomUUID().toString();}
}
