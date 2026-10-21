package com.ruoyi.medical.workflow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

class WorkflowMaterializationTest {
    private static final String HASH = "1".repeat(64);
    private static final String GRAPH_HASH = "2".repeat(64);
    private final Reference reference = new Reference("1.0.0", id(), id(), id(), ExecutionKind.TASK, id(), HASH, id(),
        9999999999999L, 100, 10, 2, 1000, 1000, 1000, "control", "professional");
    private final Step first = step(id(), List.of(), State.SUCCEEDED);
    private final PlannedStep pending = new PlannedStep(id(), List.of(first.stepId()), true);
    private final List<PlannedStep> graph = List.of(new PlannedStep(first.stepId(), List.of(), true), pending);
    private final Plan original = new Plan(HASH, id(), State.RUNNING, null, null, List.of(first), graph, id(), GRAPH_HASH);

    @Test void keepsGraphIdentityWithoutInventingPendingInputHashOrStep() {
        var loaded = WorkflowPlan.require(reference, original);
        assertEquals(1, loaded.size()); assertFalse(loaded.containsKey(pending.stepId()));
        assertEquals(2, original.plannedSteps().size());
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            new Plan(HASH, original.planRevision(), State.RUNNING, null, null, List.of(first), graph, null, null)));
        assertThrows(IllegalArgumentException.class, () -> new Step(pending.stepId(), null, pending.dependsOnStepIds(), true,
            State.QUEUED, 0, null, null, List.of()));
    }
    @Test void addsOnlyReceiptBackedInputWithoutReplacingSchedulerOwnedSteps() {
        var real = step(pending.stepId(), pending.dependsOnStepIds(), State.QUEUED);
        var added = WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first), next(List.of(first, real), graph));
        assertEquals(Map.of(real.stepId(), real), added);
        assertTrue(WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first), original).isEmpty());
    }
    @Test void rejectsGraphDriftDroppedStepsAndChangedMaterializedInput() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first),
            next(List.of(), graph)));
        var changed = new Step(first.stepId(), "3".repeat(64), List.of(), true, State.SUCCEEDED, 1, id(), null, List.of());
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first),
            next(List.of(changed), graph)));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first),
            next(List.of(first), List.of(graph.get(0), new PlannedStep(pending.stepId(), List.of(), true)))));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, original, Map.of(first.stepId(), first),
            new Plan(HASH, original.planRevision(), State.RUNNING, null, null, List.of(first), graph, original.graphReceiptId(), "3".repeat(64))));
    }
    @Test void rejectsPendingSuccessAndValidatesEntirePlannedGraph() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            new Plan(HASH, original.planRevision(), State.SUCCEEDED, null,
                new FinalReceipt(reference.runId(), HASH, State.SUCCEEDED, id()), List.of(first), graph, original.graphReceiptId(), GRAPH_HASH)));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            next(List.of(first), List.of(graph.get(0), new PlannedStep(pending.stepId(), List.of(id()), true)))));
        var overCapacity = new java.util.ArrayList<>(graph);
        for (int i = 0; i < 9; i++) overCapacity.add(new PlannedStep(id(), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference, next(List.of(first), overCapacity)));
    }
    @Test void schedulerCannotFinishWhilePlannedNodesAreMissingAndViewExposesGraphEvidence() throws Exception {
        var workflow = new TaskWorkflowImpl();
        set(workflow, "reference", reference); set(workflow, "plan", original);
        var field = TaskWorkflowImpl.class.getDeclaredField("steps"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var steps = (Map<String,Step>) field.get(workflow);
        steps.put(first.stepId(), first);
        var terminal = TaskWorkflowImpl.class.getDeclaredMethod("terminal"); terminal.setAccessible(true);
        assertEquals(false, terminal.invoke(workflow));
        assertEquals(List.of(pending.stepId()), workflow.view().pendingStepIds());
        assertEquals(original.graphReceiptId(), workflow.view().graphReceiptId());
        assertEquals(GRAPH_HASH, workflow.view().graphSha256());
        steps.put(pending.stepId(), step(pending.stepId(), pending.dependsOnStepIds(), State.SUCCEEDED));
        assertEquals(true, terminal.invoke(workflow));
    }
    @Test void oldPlanConstructorRetainsCompleteGraphBehavior() {
        var old = new Plan(HASH, id(), State.RUNNING, null, null, List.of(first));
        assertNull(old.graphReceiptId()); assertNull(old.graphSha256());
        assertEquals(List.of(graph.get(0)), old.plannedSteps());
        assertEquals(Map.of(first.stepId(), first), WorkflowPlan.require(reference, old));
    }
    @Test void requiredStageObligationBlocksCompletionWithoutInventedPartitionSteps() throws Exception {
        var obligation = new RequiredStage("N03", "4".repeat(64), null, null);
        var graphOnly = new Plan(HASH, original.planRevision(), State.RUNNING, null, null, List.of(first),
            List.of(graph.get(0)), original.graphReceiptId(), GRAPH_HASH, List.of(obligation));
        assertEquals(1, WorkflowPlan.require(reference, graphOnly).size());
        var workflow = new TaskWorkflowImpl(); set(workflow, "reference", reference); set(workflow, "plan", graphOnly);
        var field = TaskWorkflowImpl.class.getDeclaredField("steps"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var steps = (Map<String,Step>) field.get(workflow);
        steps.put(first.stepId(), first);
        var terminal = TaskWorkflowImpl.class.getDeclaredMethod("terminal"); terminal.setAccessible(true);
        assertEquals(false, terminal.invoke(workflow));
        assertTrue(workflow.view().pendingStepIds().isEmpty());
        assertEquals(List.of(obligation), workflow.view().pendingRequiredStages());
        var fulfilled = new RequiredStage("N03", obligation.obligationSha256(), id(), "5".repeat(64));
        var next = new Plan(HASH, graphOnly.planRevision(), State.RUNNING, null, null, List.of(first),
            graphOnly.plannedSteps(), graphOnly.graphReceiptId(), GRAPH_HASH, List.of(fulfilled));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, graphOnly, steps, next));
        // A bare fulfillment UUID cannot replace actual later-stage execution.
        set(workflow, "plan", graphOnly); assertEquals(false, terminal.invoke(workflow));
        var dropped = new Plan(HASH, graphOnly.planRevision(), State.RUNNING, null, null, List.of(first),
            graphOnly.plannedSteps(), graphOnly.graphReceiptId(), GRAPH_HASH);
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.refresh(reference, graphOnly, steps, dropped));
        assertThrows(IllegalArgumentException.class, () -> new RequiredStage("N03", HASH, id(), null));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            new Plan(HASH, graphOnly.planRevision(), State.SUCCEEDED, null,
                new FinalReceipt(reference.runId(), HASH, State.SUCCEEDED, id()), List.of(first), graphOnly.plannedSteps(),
                graphOnly.graphReceiptId(), GRAPH_HASH, List.of(obligation))));
    }
    @Test void schedulerReloadsRealNodesWithoutOverwritingExistingStateOrPollingCompleteGraph() throws Exception {
        var workflow = new TaskWorkflowImpl(); set(workflow, "reference", reference); set(workflow, "plan", original);
        var field = TaskWorkflowImpl.class.getDeclaredField("steps"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var steps = (Map<String,Step>) field.get(workflow);
        steps.put(first.stepId(), first);
        var real = step(pending.stepId(), pending.dependsOnStepIds(), State.QUEUED);
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        RunActivities control = (RunActivities) java.lang.reflect.Proxy.newProxyInstance(RunActivities.class.getClassLoader(),
            new Class<?>[]{RunActivities.class}, (proxy, method, args) -> {
                assertEquals("load", method.getName()); loads.incrementAndGet(); return next(List.of(first, real), graph);
            });
        set(workflow, "control", control);
        var refresh = TaskWorkflowImpl.class.getDeclaredMethod("refreshMaterialized"); refresh.setAccessible(true);
        refresh.invoke(workflow);
        assertEquals(real, steps.get(real.stepId())); assertSame(first, steps.get(first.stepId()));
        assertTrue(workflow.view().pendingStepIds().isEmpty());
        refresh.invoke(workflow); assertEquals(1, loads.get());
    }
    @Test void failedN02DisposesDependentInputAndLaterObligationsWithoutInventedSteps() {
        var failed=step(first.stepId(),List.of(),State.FAILED);
        var obligation=new RequiredStage("N03",HASH,id(),HASH,StageDisposition.DEPENDENCY_FAILED,failed.receiptId());
        var disposition=new UnmaterializedFailure(pending.stepId(),id(),HASH,failed.receiptId());
        var plan=new Plan(HASH,original.planRevision(),State.RUNNING,null,null,List.of(failed),graph,
            original.graphReceiptId(),GRAPH_HASH,List.of(obligation),List.of(),List.of(disposition));
        var steps=WorkflowPlan.require(reference,plan);
        assertEquals(1,steps.size());assertTrue(WorkflowPlan.terminal(plan,steps));assertTrue(WorkflowPlan.failed(plan,steps));
        var unrelated=new UnmaterializedFailure(pending.stepId(),id(),HASH,id());
        assertThrows(IllegalArgumentException.class,()->WorkflowPlan.require(reference,new Plan(HASH,plan.planRevision(),
            State.RUNNING,null,null,List.of(failed),graph,plan.graphReceiptId(),GRAPH_HASH,List.of(obligation),List.of(),List.of(unrelated))));
    }
    @Test void laterAssignmentsRequireAppendOnlyExpansionReceiptAndRealSuccess() {
        var obligation=new RequiredStage("N03",HASH,null,null);
        var before=new Plan(HASH,original.planRevision(),State.RUNNING,null,null,List.of(first),List.of(graph.get(0)),
            original.graphReceiptId(),GRAPH_HASH,List.of(obligation));
        var expansion=new Expansion("N03",HASH,id(),HASH,List.of(pending));
        var actual=step(pending.stepId(),pending.dependsOnStepIds(),State.SUCCEEDED);
        var fulfilled=new RequiredStage("N03",HASH,id(),HASH);
        var after=new Plan(HASH,before.planRevision(),State.RUNNING,null,null,List.of(first,actual),graph,
            before.graphReceiptId(),GRAPH_HASH,List.of(fulfilled),List.of(expansion),List.of());
        assertEquals(Map.of(actual.stepId(),actual),WorkflowPlan.refresh(reference,before,Map.of(first.stepId(),first),after));
        assertTrue(WorkflowPlan.terminal(after,WorkflowPlan.require(reference,after)));
        var missing=new Plan(HASH,before.planRevision(),State.RUNNING,null,null,List.of(first,actual),graph,
            before.graphReceiptId(),GRAPH_HASH,List.of(fulfilled));
        assertThrows(IllegalArgumentException.class,()->WorkflowPlan.refresh(reference,before,Map.of(first.stepId(),first),missing));
        assertThrows(IllegalArgumentException.class,()->WorkflowPlan.refresh(reference,after,Map.of(first.stepId(),first,actual.stepId(),actual),before));
    }
    @Test void cancellationMayRetainUnmaterializedNodesButCannotClaimTheirSuccess() {
        String command=id();
        var cancelled=new RequiredStage("N03",HASH,id(),HASH,StageDisposition.CANCELLED,command);
        var plan=new Plan(HASH,original.planRevision(),State.CANCEL_REQUESTED,command,null,List.of(first),graph,
            original.graphReceiptId(),GRAPH_HASH,List.of(cancelled));
        assertEquals(1,WorkflowPlan.require(reference,plan).size());
        assertFalse(WorkflowPlan.terminal(plan,Map.of(first.stepId(),first)));
        // Cancellation is completed by actual termination proof, not by fake input/step rows.
        assertThrows(IllegalArgumentException.class,()->WorkflowPlan.require(reference,new Plan(HASH,plan.planRevision(),
            State.RUNNING,null,null,List.of(first),graph,plan.graphReceiptId(),GRAPH_HASH,List.of(cancelled))));
    }
    private Plan next(List<Step> steps, List<PlannedStep> nodes) {
        return new Plan(HASH, original.planRevision(), State.RUNNING, null, null, steps, nodes, original.graphReceiptId(), GRAPH_HASH);
    }
    private static Step step(String id, List<String> dependencies, State state) {
        return new Step(id, HASH, dependencies, true, state, state == State.QUEUED ? 0 : 1,
            state == State.QUEUED ? null : id(), null, List.of());
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
