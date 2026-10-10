package com.ruoyi.medical.workflow;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

class WorkflowPlanTest {
    private static final String HASH = "1".repeat(64);
    @Test void preservesRequiredAndIndependentStepsAndCompletedReceipts() {
        var reference = reference(); var first = step(id(), List.of(), true, State.SUCCEEDED, id());
        var second = step(id(), List.of(first.stepId()), true, State.QUEUED, null); var optional = step(id(), List.of(), false, State.QUEUED, null);
        var plan = plan(List.of(first, second, optional));
        var verified = WorkflowPlan.require(reference, plan);
        assertEquals(List.of(first.stepId(), second.stepId(), optional.stepId()), List.copyOf(verified.keySet()));
        assertEquals(first.receiptId(), verified.get(first.stepId()).receiptId()); assertFalse(verified.get(optional.stepId()).required());
        assertEquals(reference.tenantId() + "/" + reference.runId(), reference.workflowId());
    }
    @Test void rejectsChangedInputMissingDependenciesCyclesAndRepeatedEdges() {
        var reference = reference(); var first = step(id(), List.of(), true, State.QUEUED, null);
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference, new Plan("2".repeat(64), id(), State.RUNNING, null, null, List.of(first))));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference, plan(List.of(step(id(), List.of(id()), true, State.QUEUED, null)))));
        String a = id(), b = id();
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference, plan(List.of(step(a, List.of(b), true, State.QUEUED, null), step(b, List.of(a), true, State.QUEUED, null)))));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference, plan(List.of(first, step(id(), List.of(first.stepId(), first.stepId()), true, State.QUEUED, null)))));
    }
    @Test void requiresFixedScopeCompletedReceiptsAndSeparateTerminationProofs() {
        assertThrows(IllegalArgumentException.class, () -> new Reference("1.0.0", id(), null, id(), ExecutionKind.TASK, id(), HASH, id(), 1, 100, 10, 2, 1000, 1000, 1000, "control", "professional"));
        assertThrows(IllegalArgumentException.class, () -> step(id(), List.of(), true, State.SUCCEEDED, null));
        assertFalse(new Termination(id(), true, true, false).complete()); assertFalse(new Termination(id(), true, false, true).complete());
        assertFalse(new Termination(id(), false, true, true).complete()); assertTrue(new Termination(id(), true, true, true).complete());
    }
    @Test void rejectsNonterminalFinalReceiptsAndIncompleteWaitingIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new FinalReceipt(id(), HASH, State.RUNNING, id()));
        assertThrows(IllegalArgumentException.class, () -> new FinalReceipt(id(), HASH, State.WAITING_INPUT, id()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(id(), HASH, 1, State.WAITING_INPUT, id(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(id(), HASH, 1, State.RUNNING, id(), null, List.of()));
    }
    @Test void rejectsUnreconciledActiveStepsAndCompletedOwnerMismatch() {
        var reference = reference();
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            plan(List.of(step(id(), List.of(), true, State.RUNNING, id())))));
        var complete = step(id(), List.of(), true, State.SUCCEEDED, id());
        var finalReceipt = new FinalReceipt(id(), HASH, State.SUCCEEDED, id());
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference,
            new Plan(HASH, id(), State.SUCCEEDED, null, finalReceipt, List.of(complete))));
    }
    private static Plan plan(List<Step> steps) { return new Plan(HASH, id(), State.RUNNING, null, null, steps); }
    private static Step step(String id, List<String> dependencies, boolean required, State state, String receipt) { return new Step(id, HASH, dependencies, required, state, state == State.QUEUED ? 0 : 1, receipt, null, List.of()); }
    private static Reference reference() { return new Reference("1.0.0", id(), id(), id(), ExecutionKind.TASK, id(), HASH, id(), 9999999999999L, 100, 10, 2, 1000, 1000, 1000, "control", "professional"); }
    private static String id() { return UUID.randomUUID().toString(); }
}
