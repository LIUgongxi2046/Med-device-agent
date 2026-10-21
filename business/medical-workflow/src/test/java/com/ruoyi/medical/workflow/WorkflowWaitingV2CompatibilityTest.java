package com.ruoyi.medical.workflow;

import io.temporal.client.*;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.testing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunWaitingProtocol.*;

/** Real in-process SDK histories and WorkflowReplayer; activities are explicit test
 * doubles. This proves history routing, not deployed DB/TLS/provider acceptance. */
class WorkflowWaitingV2CompatibilityTest {
    static final String H="1".repeat(64);
    @Test void actualV1SdkHistoryReplaysWithDefaultBranchAndUnchangedActivityNames()throws Exception{
        io.temporal.internal.common.WorkflowExecutionHistory history;
        try(var env=TestWorkflowEnvironment.newInstance()){
            var r=reference(env.currentTimeMillis()+300000);var activities=new WorkflowInProcessMaterializationTest.Activities(r,false,false,true);
            var worker=env.newWorker(r.controlTaskQueue());worker.registerWorkflowImplementationTypes(TaskWorkflowImpl.class);worker.registerActivitiesImplementations(activities);
            env.newWorker(r.professionalTaskQueue()).registerActivitiesImplementations((ProfessionalActivity)(ref,d)->new Completion(ref.runId(),d.stepId(),d.inputSha256(),d.leaseGeneration(),State.SUCCEEDED,id(),WorkflowInProcessMaterializationTest.H));
            env.start();var workflow=env.getWorkflowClient().newWorkflowStub(TaskWorkflow.class,WorkflowOptions.newBuilder().setWorkflowId(r.workflowId()).setTaskQueue(r.controlTaskQueue()).build());assertEquals(State.SUCCEEDED,workflow.run(r).status());
            history=env.getWorkflowClient().fetchHistory(r.workflowId());
            assertTrue(history.getEvents().stream().filter(e->e.hasActivityTaskScheduledEventAttributes()).anyMatch(e->e.getActivityTaskScheduledEventAttributes().getActivityType().getName().equals("medical.professional.step")));
            assertFalse(history.getEvents().stream().filter(e->e.hasActivityTaskScheduledEventAttributes()).anyMatch(e->e.getActivityTaskScheduledEventAttributes().getActivityType().getName().endsWith(".v2")));
        }
        WorkflowReplayer.replayWorkflowExecution(history,TaskWorkflowWaitingV2Impl.class);
    }
    @Test void actualV2StartMemoRoutesWaitingWithoutResultArtifactAndRecoversQueryOnly()throws Exception{
        try(var env=TestWorkflowEnvironment.newInstance()){
            var r=reference(env.currentTimeMillis()+10000);var deployment=deployment(env.getWorkflowClient().getOptions().getNamespace(),r);var control=new WaitingControl(r);
            var worker=env.newWorker(r.controlTaskQueue());worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(deployment));worker.registerActivitiesImplementations(control,(TaskRunWaitingActivities)control::waiting);
            env.newWorker(r.professionalTaskQueue()).registerActivitiesImplementations((ProfessionalActivityV2)(ref,d)->new ProfessionalOutcomeV2("2.0.0","WAITING_CONTROL",null,new WaitingCompletionV2("2.0.0","WAITING_CONTROL",ref.runId(),d.stepId(),d.inputSha256(),d.leaseGeneration(),id(),H)));
            env.start();String command=id();var delivery=new WorkflowDeliveryV2(env.getWorkflowClient(),deployment);var accepted=delivery.start(r,command);
            var result=env.getWorkflowClient().newUntypedWorkflowStub(r.workflowId()).getResult(FinalReceipt.class);assertEquals(State.CANCELLED,result.status());assertEquals(1,control.waiting.get());assertEquals(0,control.commits.get());
            assertEquals(accepted,delivery.recover(r,command));assertThrows(IllegalArgumentException.class,()->delivery.recover(r,id()));
            var history=env.getWorkflowClient().fetchHistory(r.workflowId());var started=history.getEvents().get(0).getWorkflowExecutionStartedEventAttributes();
            assertEquals(deployment.canonicalSha256(),env.getWorkflowClient().getOptions().getDataConverter().fromPayload(started.getMemo().getFieldsOrThrow(WaitingWorkflowDeploymentV2.HASH_MEMO),String.class,String.class));
            var names=history.getEvents().stream().filter(e->e.hasActivityTaskScheduledEventAttributes()).map(e->e.getActivityTaskScheduledEventAttributes().getActivityType().getName()).toList();assertTrue(names.contains("medical.professional.step.v2"));assertTrue(names.contains("medical.run.commit-waiting.v2"));assertFalse(names.contains("medical.professional.step"));
        }
    }
    @Test void v1CompletionConverterBytesAndConstructorRemainExact(){
        var value=new Completion(fixed(1),fixed(2),H,1,State.WAITING_INPUT,fixed(3),H);String raw=DefaultDataConverter.STANDARD_INSTANCE.toPayload(value).orElseThrow().getData().toStringUtf8();
        assertEquals("{\"runId\":\""+fixed(1)+"\",\"stepId\":\""+fixed(2)+"\",\"inputSha256\":\""+H+"\",\"leaseGeneration\":1,\"status\":\"WAITING_INPUT\",\"resultArtifactId\":\""+fixed(3)+"\",\"resultSha256\":\""+H+"\"}",raw);
        assertThrows(RuntimeException.class,()->new Completion(fixed(1),fixed(2),H,1,State.WAITING_INPUT,null,null));
        assertThrows(IllegalArgumentException.class,()->new ProfessionalOutcomeV2("2.0.0","WAITING_CONTROL",value,null));
    }
    @Test void v2CannotBeSentThroughTheV1ActivitySchema()throws Exception{
        var schema=new TaskRunActivitySchema();var r=reference(9999999999999L);var d=new Dispatch(r.runId(),id(),id(),id(),H,1,id(),H);var w=new WaitingCompletionV2("2.0.0","WAITING_CONTROL",r.runId(),d.stepId(),H,1,id(),H);
        assertThrows(Exception.class,()->schema.request(TaskRunActivitySchema.Operation.COMMIT,r,"dispatch",d,"completion",w));
        var waiting=new TaskRunWaitingSchema();assertEquals(w,waiting.read(waiting.request(r,d,w)).completion());
    }
    static final class WaitingControl implements RunActivities,TaskRunRecoveryActivitiesV2,TaskRunReuseActivitiesV1 {
        // These fixtures model original execution, with no successor-run reuse candidates.
        public TaskRunReuseProtocolV1.InventoryV1 reuseInventory(Reference reference) {
            return new TaskRunReuseProtocolV1.InventoryV1("1.0.0", reference.runId(), List.of(), null, null);
        }
        public TaskRunReuseProtocolV1.InventoryV1 reuseAssembly(Reference reference) {
            throw new AssertionError("No assembly reuse in this fixture");
        }

        final Reference r;final String step=id(),plan=id(),graph=id(),waitingItem=id();volatile Receipt receipt;final AtomicInteger waiting=new AtomicInteger(),commits=new AtomicInteger();
        WaitingControl(Reference r){this.r=r;}
        public Plan load(Reference reference){var s=receipt==null?new Step(step,H,List.of(),true,State.QUEUED,0,null,null,List.of()):new Step(step,H,List.of(),true,State.WAITING_INPUT,1,receipt.receiptId(),waitingItem,List.of());return new Plan(H,plan,State.RUNNING,null,null,List.of(s),List.of(new PlannedStep(step,List.of(),true)),graph,H,List.of(new RequiredStage("N03",H,null,null)));}
        public TaskRunRecoveryProtocolV2.RunPlanV2 loadV2(Reference r){return new TaskRunRecoveryProtocolV2.RunPlanV2("2.0.0",load(r),List.of());}
        public TaskRunRecoveryProtocolV2.UndispatchedFailureV2 failUndispatched(Reference r,String step){throw new AssertionError("Unexpected undispatched mutation");}
        public TaskRunRecoveryProtocolV2.StepRecoveryV2 recoverStep(Reference r,Dispatch d){throw new AssertionError("No original running fixture");}
        public Dispatch dispatch(Reference r,String step,String command){return new Dispatch(r.runId(),step,id(),command,H,1,id(),H);}
        Receipt waiting(Reference r,Dispatch d,WaitingCompletionV2 c){assertEquals("WAITING_CONTROL",c.outcome());waiting.incrementAndGet();return receipt=new Receipt(step,H,1,State.WAITING_INPUT,id(),waitingItem,List.of());}
        public Receipt commit(Reference r,Dispatch d,Completion c){commits.incrementAndGet();throw new AssertionError("V1 commit must not receive waiting control");}
        public Receipt failure(Reference r,Dispatch d,String reason){throw new AssertionError(reason);}public Receipt dependencyFailed(Reference r,String step){throw new AssertionError("no dependency failure");}
        public CommandReceipt consume(Reference r,Command c){throw new AssertionError("no signal");}
        public Termination cancellation(Reference r,String command,String reason){return new Termination(id(),true,true,true);}
        public FinalReceipt finish(Reference r,State requested,String reason,List<Receipt> receipts,List<String> commands){assertEquals(State.CANCELLED,requested);assertEquals(List.of(receipt),receipts);return new FinalReceipt(r.runId(),H,requested,id());}
    }
    static WaitingWorkflowDeploymentV2 deployment(String namespace,Reference r){return new WaitingWorkflowDeploymentV2("2.0.0",id(),namespace,r.controlTaskQueue(),r.professionalTaskQueue(),"medical.task.run","medical.professional.step.v2","medical.run.commit-waiting.v2",WaitingWorkflowDeploymentV2.MARKER,"medical.run.load.v2","medical.run.recover-step.v2",1000,"medical.run.fail-undispatched.v2","medical.run.execute-stage.v2","medical.run.commit-stage.v2","medical.run.recover-stage.v2");}
    static Reference reference(long deadline){return new Reference("1.0.0",id(),id(),id(),ExecutionKind.TASK,id(),H,id(),deadline,10,10,1,10000,10000,10000,"waiting-v2-control","waiting-v2-professional");}
    static String id(){return UUID.randomUUID().toString();}static String fixed(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
