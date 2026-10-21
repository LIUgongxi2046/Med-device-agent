package com.ruoyi.medical.workflow;

import io.temporal.common.converter.DefaultDataConverter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the real pinned Temporal payload converter, not Java compatibility constructors.
 * Synthetic legacy payloads establish serialization compatibility only, not production-history replay. */
class LegacyWorkflowPlanSerializationTest {
    private static final String HASH = "1".repeat(64);
    private static final String OUTPUT_HASH = "2".repeat(64);
    private static final String TENANT = id(1), PROJECT = id(2), RUN = id(3), INPUT = id(4), POLICY = id(5);
    private static final String PLAN = id(6), FIRST = id(7), SECOND = id(8), RECEIPT = id(9), FINAL = id(10);

    @Test void missingNewFieldsDeriveOnlyTheActualLegacyClosedGraph() {
        var original = legacy("RUNNING", List.of(
            step(FIRST, List.of(), true, "QUEUED", 0, null, null, List.of()),
            step(SECOND, List.of(FIRST), false, "QUEUED", 0, null, null, List.of())));
        var payload = DefaultDataConverter.STANDARD_INSTANCE.toPayload(original).orElseThrow();
        String json = payload.getData().toStringUtf8();
        for (String absent : List.of("plannedSteps", "graphReceiptId", "graphSha256", "requiredStages", "expansions", "unmaterializedFailures")) {
            assertFalse(json.contains('"' + absent + '"'), "Not a genuine legacy-shaped payload: " + absent);
        }
        Plan decoded = DefaultDataConverter.STANDARD_INSTANCE.fromPayload(payload, Plan.class, Plan.class);
        assertEquals(HASH, decoded.inputSha256());
        assertEquals(PLAN, decoded.planRevision());
        assertEquals(List.of(new PlannedStep(FIRST, List.of(), true), new PlannedStep(SECOND, List.of(FIRST), false)), decoded.plannedSteps());
        assertNull(decoded.graphReceiptId());
        assertNull(decoded.graphSha256());
        assertTrue(decoded.requiredStages().isEmpty());
        assertTrue(decoded.expansions().isEmpty());
        assertTrue(decoded.unmaterializedFailures().isEmpty());
        assertEquals(List.of(FIRST, SECOND), List.copyOf(WorkflowPlan.require(reference(), decoded).keySet()));
    }

    @Test void legacyWaitingReceiptAndOutputPinsAreNotRewritten() {
        String waiting = id(11), secondReceipt = id(12), artifact = id(13);
        var output = Map.<String, Object>of("artifactId", artifact, "sha256", OUTPUT_HASH, "schemaRef", "urn:test:legacy-output");
        Plan decoded = decode(legacy("WAITING_INPUT", List.of(
            step(FIRST, List.of(), true, "SUCCEEDED", 3, RECEIPT, null, List.of(output)),
            step(SECOND, List.of(FIRST), true, "WAITING_INPUT", 2, secondReceipt, waiting, List.of()))));
        var verified = WorkflowPlan.require(reference(), decoded);
        assertEquals(3, verified.get(FIRST).leaseGeneration());
        assertEquals(RECEIPT, verified.get(FIRST).receiptId());
        assertEquals(List.of(new Output(artifact, OUTPUT_HASH, "urn:test:legacy-output")), verified.get(FIRST).outputs());
        assertEquals(State.WAITING_INPUT, verified.get(SECOND).status());
        assertEquals(waiting, verified.get(SECOND).waitingItemId());
        assertEquals(secondReceipt, verified.get(SECOND).receiptId());
    }

    @Test void legacyFinalReceiptRetainsOriginalRunAndInputIdentity() {
        var original = legacy("SUCCEEDED", List.of(step(FIRST, List.of(), true, "SUCCEEDED", 1, RECEIPT, null, List.of())));
        original.put("completedReceipt", Map.of("runId", RUN, "inputSha256", HASH, "status", "SUCCEEDED", "receiptId", FINAL));
        Plan decoded = decode(original);
        assertEquals(new FinalReceipt(RUN, HASH, State.SUCCEEDED, FINAL), decoded.completedReceipt());
        assertEquals(State.SUCCEEDED, WorkflowPlan.require(reference(), decoded).get(FIRST).status());
        original.put("completedReceipt", Map.of("runId", id(14), "inputSha256", HASH, "status", "SUCCEEDED", "receiptId", FINAL));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference(), decode(original)));
    }

    @Test void legacyCancellationKeepsItsOriginalCommandAndTerminationReceipt() {
        String cancellation = id(15);
        var original = legacy("CANCELLED", List.of(step(FIRST, List.of(), true, "CANCELLED", 1, RECEIPT, null, List.of())));
        original.put("cancellationCommandId", cancellation);
        original.put("completedReceipt", Map.of("runId", RUN, "inputSha256", HASH, "status", "CANCELLED", "receiptId", FINAL));
        Plan decoded = decode(original);
        assertEquals(cancellation, decoded.cancellationCommandId());
        assertEquals(State.CANCELLED, decoded.completedReceipt().status());
        assertEquals(State.CANCELLED, WorkflowPlan.require(reference(), decoded).get(FIRST).status());
        original.remove("completedReceipt");
        assertThrows(RuntimeException.class, () -> decode(original));
    }

    @Test void legacyFallbackDoesNotManufactureMissingInputHashesOrDependencies() {
        var broken = step(FIRST, List.of(), true, "QUEUED", 0, null, null, List.of());
        broken.remove("inputSha256");
        assertThrows(RuntimeException.class, () -> decode(legacy("RUNNING", List.of(broken))));
        Plan dangling = decode(legacy("RUNNING", List.of(step(FIRST, List.of(SECOND), true, "QUEUED", 0, null, null, List.of()))));
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference(), dangling));
    }

    @Test void explicitEmptyNewPlanCannotSilentlyBecomeLegacyPlan() {
        var original = legacy("RUNNING", List.of(step(FIRST, List.of(), true, "QUEUED", 0, null, null, List.of())));
        original.put("plannedSteps", List.of());
        Plan decoded = decode(original);
        assertTrue(decoded.plannedSteps().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> WorkflowPlan.require(reference(), decoded));
    }

    private static Plan decode(Map<String, Object> original) {
        var payload = DefaultDataConverter.STANDARD_INSTANCE.toPayload(original).orElseThrow();
        return DefaultDataConverter.STANDARD_INSTANCE.fromPayload(payload, Plan.class, Plan.class);
    }
    private static Map<String, Object> legacy(String status, List<Map<String, Object>> steps) {
        var value = new LinkedHashMap<String, Object>();
        value.put("inputSha256", HASH); value.put("planRevision", PLAN); value.put("runStatus", status);
        value.put("cancellationCommandId", null); value.put("completedReceipt", null); value.put("steps", steps);
        return value;
    }
    private static Map<String, Object> step(String id, List<String> dependencies, boolean required, String status,
            long generation, String receipt, String waiting, List<Map<String, Object>> outputs) {
        var value = new LinkedHashMap<String, Object>();
        value.put("stepId", id); value.put("inputSha256", HASH); value.put("dependsOnStepIds", dependencies);
        value.put("required", required); value.put("status", status); value.put("leaseGeneration", generation);
        value.put("receiptId", receipt); value.put("waitingItemId", waiting); value.put("outputs", outputs);
        return value;
    }
    private static Reference reference() {
        return new Reference("1.0.0", TENANT, PROJECT, RUN, ExecutionKind.TASK, INPUT, HASH, POLICY,
            9999999999999L, 100, 10, 2, 1000, 1000, 1000, "control", "professional");
    }
    private static String id(int suffix) { return "00000000-0000-0000-0000-" + String.format("%012d", suffix); }
}
