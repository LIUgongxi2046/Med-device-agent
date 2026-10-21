package com.ruoyi.medical.workflow;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.failure.ApplicationFailure;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunRecoveryProtocolV2.*;
import static com.ruoyi.medical.workflow.TaskRunStageProtocolV2.*;
/** Real SDK test source. The fake persistence producer is explicit; no model,
 * token, external service, or real worker configuration is enabled. */
class WorkflowStageRecoveryV2Test {
    static final String H="a".repeat(64);
    @Test void lostStageExecuteReplyQueriesOriginalAndNeverExecutesAgain(){run(false);}
    @Test void existingStageCommandAtRestartIsQueryOnly(){run(true);}
    private void run(boolean restarted){try(var env=TestWorkflowEnvironment.newInstance()){
        var ref=WorkflowOriginalRunningV2Test.reference(env.currentTimeMillis()+6000);var d=WorkflowOriginalRunningV2Test.deployment(env.getWorkflowClient().getOptions().getNamespace(),ref);var c=new Controls(ref,restarted);
        var worker=env.newWorker(ref.controlTaskQueue());worker.registerWorkflowImplementationFactory(TaskWorkflow.class,()->new TaskWorkflowWaitingV2Impl(d));worker.registerActivitiesImplementations(c);env.start();
        new WorkflowDeliveryV2(env.getWorkflowClient(),d).start(ref,id());var result=env.getWorkflowClient().newUntypedWorkflowStub(ref.workflowId()).getResult(FinalReceipt.class);
        assertEquals(State.CANCELLED,result.status());assertEquals(restarted?0:1,c.executions);assertEquals(1,c.commits);assertEquals(2,c.polls.size());assertEquals("SATISFIED",c.receipt.disposition());
        var history=env.getWorkflowClient().fetchHistory(ref.workflowId()).getEvents();
        try { assertRecoveryCadence(history,d.recoveryPollMillis(),"medical.run.recover-stage.v2"); }
        catch(AssertionError failure) { System.err.println("Stage recovery history (restarted="+restarted+"): "+history); throw failure; }
        var names=history.stream().filter(e->e.hasActivityTaskScheduledEventAttributes()).map(e->e.getActivityTaskScheduledEventAttributes().getActivityType().getName()).toList();assertTrue(names.contains("medical.run.recover-stage.v2"));assertFalse(names.contains("medical.professional.step.v2"));assertFalse(names.contains("medical.run.dispatch"));
    }}
    static void assertRecoveryCadence(List<io.temporal.api.history.v1.HistoryEvent> history,long intervalMillis,String activityName) {
        var events=new HashMap<Long,io.temporal.api.history.v1.HistoryEvent>();
        history.forEach(event->events.put(event.getEventId(),event));
        var recoveries=history.stream().filter(event->event.hasActivityTaskScheduledEventAttributes()
                && activityName.equals(event.getActivityTaskScheduledEventAttributes().getActivityType().getName())).toList();
        assertEquals(2,recoveries.size(),"Exactly two durable original recovery queries for "+activityName);
        // The rate limit starts at workflow task time BEFORE activity scheduling.
        // Activity callback arrival times include variable worker dispatch lag and cannot measure it.
        var workflowTimes=new ArrayList<Long>();
        for(var scheduled:recoveries) {
            long linkedId=scheduled.getActivityTaskScheduledEventAttributes().getWorkflowTaskCompletedEventId();
            var completed=events.get(linkedId);
            // Temporal test-server 1.40.0 computes getNextEventId()-1 BEFORE adding
            // WorkflowTaskCompleted, so its command linkage is exactly one event early.
            // Accept only that verified adjacent completion, not an arbitrary nearest task.
            if(completed==null || !completed.hasWorkflowTaskCompletedEventAttributes())completed=events.get(linkedId+1);
            assertNotNull(completed,"Missing workflow task completion for linkage "+linkedId);
            assertTrue(completed.hasWorkflowTaskCompletedEventAttributes(),"Expected linked/adjacent completion for "+linkedId);
            assertTrue(completed.getEventId()<scheduled.getEventId(),"Completion must precede its activity command");
            var attributes=completed.getWorkflowTaskCompletedEventAttributes();
            // This test-server also omits completed.startedEventId. Both events do
            // retain the exact scheduledEventId, which identifies the same task.
            long completedId=completed.getEventId();
            var starts=history.stream().filter(event->event.hasWorkflowTaskStartedEventAttributes()
                    && event.getWorkflowTaskStartedEventAttributes().getScheduledEventId()==attributes.getScheduledEventId()
                    && event.getEventId()<completedId).toList();
            assertEquals(1,starts.size(),"Unambiguous task start required for completion "+completedId);
            var started=starts.get(0);
            if(attributes.getStartedEventId()!=0)assertEquals(attributes.getStartedEventId(),started.getEventId(),"Explicit start linkage");
            var taskScheduled=events.get(attributes.getScheduledEventId());
            assertNotNull(taskScheduled,"Missing referenced workflow task schedule");
            assertTrue(taskScheduled.hasWorkflowTaskScheduledEventAttributes(),"Referenced task must actually be scheduled");
            assertTrue(taskScheduled.getEventId()<started.getEventId(),"Task scheduling must precede start");
            var timestamp=started.getEventTime();
            workflowTimes.add(Math.addExact(Math.multiplyExact(timestamp.getSeconds(),1000L),timestamp.getNanos()/1000000L));
        }
        long elapsed=workflowTimes.get(1)-workflowTimes.get(0);
        assertTrue(elapsed>=intervalMillis,"Recovery workflow task times="+workflowTimes+", elapsed="+elapsed+"ms, required="+intervalMillis+"ms, scheduled event IDs="+recoveries.stream().map(e->e.getEventId()).toList());
    }
    static final class Controls implements RunActivities,TaskRunRecoveryActivitiesV2,TaskRunStageActivitiesV2,TaskRunReuseActivitiesV1 {
        // These fixtures model original execution, with no successor-run reuse candidates.
        public TaskRunReuseProtocolV1.InventoryV1 reuseInventory(Reference reference) {
            return new TaskRunReuseProtocolV1.InventoryV1("1.0.0", reference.runId(), List.of(), null, null);
        }
        public TaskRunReuseProtocolV1.InventoryV1 reuseAssembly(Reference reference) {
            throw new AssertionError("No assembly reuse in this fixture");
        }

        final Reference ref;final String graph=id(),plan=id(),step=id();final Receipt research;final List<String> polls=new ArrayList<>();StageCommandV2 command;FieldCompletionV2 completion;StageCommitV2 receipt;int executions,commits;
        Controls(Reference ref,boolean restarted){this.ref=ref;research=new Receipt(step,H,1,State.SUCCEEDED,id(),null,List.of());if(restarted)produce(id());}
        private void produce(String id){completion=new FieldCompletionV2("2.0.0","N03","FIELD_TARGET",ref.runId(),WorkflowStageRecoveryV2Test.id(),id,WorkflowStageRecoveryV2Test.id(),H,WorkflowStageRecoveryV2Test.id(),1,WorkflowStageRecoveryV2Test.id(),H,H);command=new StageCommandV2("2.0.0","N03",ref.runId(),id,completion.stepId(),completion.inputId(),H);}
        public RunPlanV2 loadV2(Reference r){var stages=List.of("N03","N04","N05","N06").stream().map(s->new RequiredStage(s,H,null,null)).toList();var p=new Plan(H,plan,State.RUNNING,null,null,List.of(new Step(step,H,List.of(),true,State.SUCCEEDED,1,research.receiptId(),null,List.of())),List.of(new PlannedStep(step,List.of(),true)),graph,H,stages);return new RunPlanV2("2.0.0",p,List.of(),List.of(),receipt==null?List.of():List.of(receipt),command==null?List.of():List.of(command));}
        public FieldCompletionV2 executeStage(Reference r,String stage,String id){executions++;if(command!=null)throw new AssertionError("No execution retry");produce(id);throw ApplicationFailure.newNonRetryableFailure("Reply lost after durable production","TEST_LOST_REPLY");}
        public StageRecoveryV2 recoverStage(Reference r,String stage,String id){assertEquals(command.commandId(),id);polls.add(id);return polls.size()==1?new StageRecoveryV2("2.0.0","PENDING",stage,id,null,null):new StageRecoveryV2("2.0.0","PRODUCED",stage,id,completion,null);}
        public StageCommitV2 commitStage(Reference r,FieldCompletionV2 c){assertEquals(completion,c);commits++;receipt=new StageCommitV2("2.0.0","N03",H,graph,H,c,"SATISFIED",id(),H);return receipt;}
        public StepRecoveryV2 recoverStep(Reference r,Dispatch d){throw new AssertionError("No original research in flight");}public UndispatchedFailureV2 failUndispatched(Reference r,String step){throw new AssertionError("No failed dependency");}
        public Plan load(Reference r){throw new AssertionError("No V1 load");}public Dispatch dispatch(Reference r,String step,String command){throw new AssertionError("No research execution");}public Receipt commit(Reference r,Dispatch d,Completion c){throw new AssertionError();}public Receipt failure(Reference r,Dispatch d,String reason){throw new AssertionError();}public Receipt dependencyFailed(Reference r,String step){throw new AssertionError();}public CommandReceipt consume(Reference r,Command c){throw new AssertionError();}
        public Termination cancellation(Reference r,String command,String reason){return new Termination(id(),true,true,true);}public FinalReceipt finish(Reference r,State requested,String reason,List<Receipt> receipts,List<String> consumed){assertEquals(State.CANCELLED,requested,"N03 must never hide absent N04-N06 producers");assertEquals(List.of(research),receipts);return new FinalReceipt(r.runId(),H,requested,id());}
    }
    static String id(){return UUID.randomUUID().toString();}
}
