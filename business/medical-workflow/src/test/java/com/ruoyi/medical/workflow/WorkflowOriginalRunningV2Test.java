package com.ruoyi.medical.workflow;
import io.temporal.client.*;
import io.temporal.testing.TestWorkflowEnvironment;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunRecoveryProtocolV2.*;

/** Real SDK scheduling with explicit persistence/producer doubles. No provider,
 * lease, service identity, or deployment activation is performed. */
class WorkflowOriginalRunningV2Test {
    static final String H="a".repeat(64);
    @Test void runningRestartOnlyPollsOriginalWithoutDispatchProfessionalOrPrematureSuccess(){
        try(var env=TestWorkflowEnvironment.newInstance()){
            var r=reference(env.currentTimeMillis()+5000);var d=deployment(env.getWorkflowClient().getOptions().getNamespace(),r);var control=new Controls(r,env::currentTimeMillis);var paid=new AtomicInteger();
            var worker=env.newWorker(r.controlTaskQueue());worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(d));worker.registerActivitiesImplementations(control);
            env.newWorker(r.professionalTaskQueue()).registerActivitiesImplementations((ProfessionalActivityV2)(ref,dispatch)->{paid.incrementAndGet();throw new AssertionError("Original recovery must not execute professional activity");});env.start();
            var accepted=new WorkflowDeliveryV2(env.getWorkflowClient(),d).start(r,id());var result=env.getWorkflowClient().newUntypedWorkflowStub(r.workflowId()).getResult(FinalReceipt.class);
            assertNotNull(accepted.temporalRunId());assertEquals(State.CANCELLED,result.status());assertEquals(0,paid.get());assertEquals(0,control.dispatches.get());assertEquals(2,control.polls.size());
            var history=env.getWorkflowClient().fetchHistory(r.workflowId()).getEvents();
            try { WorkflowStageRecoveryV2Test.assertRecoveryCadence(history,d.recoveryPollMillis(),"medical.run.recover-step.v2"); }
            catch(AssertionError failure) { System.err.println("Original recovery history: "+history); throw failure; }
            var names=history.stream().filter(e->e.hasActivityTaskScheduledEventAttributes()).map(e->e.getActivityTaskScheduledEventAttributes().getActivityType().getName()).toList();assertTrue(names.contains("medical.run.load.v2"));assertTrue(names.contains("medical.run.recover-step.v2"));assertFalse(names.contains("medical.run.dispatch"));assertFalse(names.contains("medical.professional.step.v2"));assertFalse(names.contains("medical.run.load"));
        }
    }
    @Test void terminalOriginalRecoveredDuringRefreshReturnsWithoutAnotherDispatch() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var r = reference(env.currentTimeMillis() + 10000);
            var d = deployment(env.getWorkflowClient().getOptions().getNamespace(), r);
            var finalReceipt = new FinalReceipt(r.runId(), H, State.SUCCEEDED, id());
            class CompletedControls extends Controls implements TaskRunReuseActivitiesV1 {
                int loads; boolean complete; final AtomicInteger finishes = new AtomicInteger();
                CompletedControls(Reference input) { super(input, env::currentTimeMillis); }
                @Override public RunPlanV2 loadV2(Reference ignored) {
                    if (++loads > 1) { complete = true; throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure("already finalized", "ALREADY_FINALIZED"); }
                    return super.loadV2(ignored);
                }
                public TaskRunReuseProtocolV1.InventoryV1 reuseInventory(Reference ignored) {
                    return new TaskRunReuseProtocolV1.InventoryV1("1.0.0", r.runId(), List.of(), null, complete ? finalReceipt : null);
                }
                public TaskRunReuseProtocolV1.InventoryV1 reuseAssembly(Reference ignored) { throw new AssertionError("No assembly dispatch after final receipt"); }
                @Override public FinalReceipt finish(Reference ignored, State requested, String reason, List<Receipt> receipts, List<String> commands) {
                    finishes.incrementAndGet(); throw new AssertionError("Recovered completion must not be rewritten");
                }
            }
            var control = new CompletedControls(r); var paid = new AtomicInteger();
            var worker = env.newWorker(r.controlTaskQueue());
            worker.registerWorkflowImplementationFactory(TaskWorkflow.class, () -> new TaskWorkflowWaitingV2Impl(d));
            worker.registerActivitiesImplementations(control);
            env.newWorker(r.professionalTaskQueue()).registerActivitiesImplementations((ProfessionalActivityV2)(ref, dispatch) -> { paid.incrementAndGet(); throw new AssertionError("No paid work"); });
            env.start(); new WorkflowDeliveryV2(env.getWorkflowClient(), d).start(r, id());
            assertEquals(finalReceipt, env.getWorkflowClient().newUntypedWorkflowStub(r.workflowId()).getResult(FinalReceipt.class));
            assertEquals(2, control.loads); assertEquals(0, paid.get()); assertEquals(0, control.dispatches.get()); assertEquals(0, control.finishes.get());
        }
    }
    @Test void combinedInventoryCannotDuplicateHideOrMisbindInflight(){var r=reference(9999999999999L);var c=new Controls(r,()->0L);var plan=c.loadV2(r);assertEquals(State.RUNNING,plan.require(r).get(c.step).status());assertTrue(plan.settledPlan().steps().isEmpty());assertFalse(WorkflowPlan.terminal(plan.combined(r),plan.require(r)));assertThrows(IllegalArgumentException.class,()->new RunPlanV2("2.0.0",plan.settledPlan(),List.of(plan.inFlight().get(0),plan.inFlight().get(0))).require(r));var wrong=new OriginalRunningV2(c.original,List.of(id()),true);assertThrows(IllegalArgumentException.class,()->new RunPlanV2("2.0.0",plan.settledPlan(),List.of(wrong)).require(r));}
    @Test void schemaKeepsPendingDistinctFromOutcomeAndRejectsUnknownNestedFields()throws Exception{var r=reference(9999999999999L);var c=new Controls(r,()->0L);var schema=new TaskRunRecoverySchemaV2();var request=new TaskRunRecoverySchemaV2.Request("RECOVER",r,c.original);var pending=new StepRecoveryV2("2.0.0","PENDING",c.original,null,null);var value=schema.response(request,pending);var mapper=new com.fasterxml.jackson.databind.ObjectMapper();assertEquals(pending,schema.response(mapper.writeValueAsBytes(value),request));((com.fasterxml.jackson.databind.node.ObjectNode)value.required("result").required("dispatch")).put("token","forbidden");assertThrows(Exception.class,()->schema.response(mapper.writeValueAsBytes(value),request));assertThrows(IllegalArgumentException.class,()->new StepRecoveryV2("2.0.0","COMMITTED",c.original,null,null));}
    static class Controls implements RunActivities,TaskRunRecoveryActivitiesV2,TaskRunWaitingActivities,TaskRunStageActivitiesV2,TaskRunReuseActivitiesV1 {
        // These fixtures model original execution, with no successor-run reuse candidates.
        public TaskRunReuseProtocolV1.InventoryV1 reuseInventory(Reference reference) {
            return new TaskRunReuseProtocolV1.InventoryV1("1.0.0", reference.runId(), List.of(), null, null);
        }
        public TaskRunReuseProtocolV1.InventoryV1 reuseAssembly(Reference reference) {
            throw new AssertionError("No assembly reuse in this fixture");
        }

        final Reference r;final LongSupplier clock;final String step=id(),plan=id(),graph=id();final Dispatch original;final List<Long> polls=new ArrayList<>();final AtomicInteger dispatches=new AtomicInteger();Receipt receipt;TaskRunStageProtocolV2.StageCommandV2 stageCommand;TaskRunStageProtocolV2.StageCommitV2 stageReceipt;
        Controls(Reference r,LongSupplier clock){this.r=r;this.clock=clock;original=new Dispatch(r.runId(),step,id(),id(),H,1,id(),H);}
        public RunPlanV2 loadV2(Reference ignored){var stages=List.of("N03","N04","N05","N06").stream().map(s->new RequiredStage(s,H,null,null)).toList();var settled=receipt==null?List.<Step>of():List.of(new Step(step,H,List.of(),true,receipt.status(),1,receipt.receiptId(),null,List.of()));var base=new Plan(H,plan,State.RUNNING,null,null,settled,List.of(new PlannedStep(step,List.of(),true)),graph,H,stages);return new RunPlanV2("2.0.0",base,receipt==null?List.of(new OriginalRunningV2(original,List.of(),true)):List.of(),List.of(),stageReceipt==null?List.of():List.of(stageReceipt),stageCommand==null?List.of():List.of(stageCommand));}
        public TaskRunStageProtocolV2.FieldCompletionV2 executeStage(Reference ref,String kind,String command){var c=new TaskRunStageProtocolV2.FieldCompletionV2("2.0.0","N03","FIELD_TARGET",r.runId(),id(),command,id(),H,id(),1,id(),H,H);stageCommand=new TaskRunStageProtocolV2.StageCommandV2("2.0.0","N03",r.runId(),command,c.stepId(),c.inputId(),H);return c;}
        public TaskRunStageProtocolV2.StageCommitV2 commitStage(Reference ref,TaskRunStageProtocolV2.FieldCompletionV2 c){stageReceipt=new TaskRunStageProtocolV2.StageCommitV2("2.0.0","N03",H,graph,H,c,"BLOCKED",id(),H);return stageReceipt;}
        public TaskRunStageProtocolV2.StageRecoveryV2 recoverStage(Reference ref,String kind,String command){throw new AssertionError("No lost stage response in this fixture");}
        public UndispatchedFailureV2 failUndispatched(Reference r,String step){throw new AssertionError("Unexpected undispatched mutation");}
        public StepRecoveryV2 recoverStep(Reference ignored,Dispatch d){assertEquals(original,d);polls.add(clock.getAsLong());if(polls.size()==1)return new StepRecoveryV2("2.0.0","PENDING",d,null,null);receipt=new Receipt(step,H,1,State.SUCCEEDED,id(),null,List.of());return new StepRecoveryV2("2.0.0","COMMITTED",d,receipt,null);}
        public Plan load(Reference r){throw new AssertionError("No V1 load on V2 history");}public Dispatch dispatch(Reference r,String step,String command){dispatches.incrementAndGet();throw new AssertionError("No new dispatch");}
        public Receipt commit(Reference r,Dispatch d,Completion c){throw new AssertionError("Already committed original");}public Receipt commitWaiting(Reference r,Dispatch d,TaskRunWaitingProtocol.WaitingCompletionV2 c){throw new AssertionError("No waiting original");}
        public Receipt failure(Reference r,Dispatch d,String reason){throw new AssertionError(reason);}public Receipt dependencyFailed(Reference r,String step){throw new AssertionError("No dependency failure");}public CommandReceipt consume(Reference r,Command c){throw new AssertionError("No signal");}
        public Termination cancellation(Reference r,String command,String reason){return new Termination(id(),true,true,true);}public FinalReceipt finish(Reference r,State requested,String reason,List<Receipt> receipts,List<String> commands){assertEquals(State.CANCELLED,requested);assertEquals(List.of(receipt),receipts);return new FinalReceipt(r.runId(),H,requested,id());}
    }
    static WaitingWorkflowDeploymentV2 deployment(String namespace,Reference r){return new WaitingWorkflowDeploymentV2("2.0.0",id(),namespace,r.controlTaskQueue(),r.professionalTaskQueue(),"medical.task.run","medical.professional.step.v2","medical.run.commit-waiting.v2",WaitingWorkflowDeploymentV2.MARKER,"medical.run.load.v2","medical.run.recover-step.v2",1000,"medical.run.fail-undispatched.v2","medical.run.execute-stage.v2","medical.run.commit-stage.v2","medical.run.recover-stage.v2");}
    static Reference reference(long deadline){return new Reference("1.0.0",id(),id(),id(),ExecutionKind.TASK,id(),H,id(),deadline,10,10,1,10000,10000,10000,"original-v2-control","original-v2-professional");}static String id(){return UUID.randomUUID().toString();}
}
