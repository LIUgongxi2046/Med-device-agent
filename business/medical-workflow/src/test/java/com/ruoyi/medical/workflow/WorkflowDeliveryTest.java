package com.ruoyi.medical.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

class WorkflowDeliveryTest {
    @Test void preservesActualTemporalInputAndRejectsChangedRepeatedDelivery() {
        var service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget("127.0.0.1:17233")
            .setRpcTimeout(Duration.ofSeconds(5)).build());
        var client = WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder().setNamespace("medical-local").build());
        var delivery = new WorkflowDelivery(client); String commandId = id();
        var reference = new Reference("1.0.0", id(), id(), id(), ExecutionKind.TASK, id(), "1".repeat(64), id(),
            System.currentTimeMillis() + 60000, 100, 10, 2, 1000, 1000, 1000, "simulation-control-" + id(), "simulation-professional-" + id());
        var first = delivery.start(reference, commandId);
        try {
            assertEquals(reference.workflowId(), first.workflowId());
            assertEquals(first, delivery.start(reference, commandId));
            var changed = new Reference(reference.schemaVersion(), reference.tenantId(), reference.projectId(), reference.runId(), reference.executionKind(),
                reference.inputManifestId(), "2".repeat(64), reference.policyRevision(), reference.deadlineEpochMillis(), reference.maximumCommands(),
                reference.maximumSteps(), reference.maximumParallelSteps(), reference.readTimeoutMillis(), reference.stepTimeoutMillis(),
                reference.scheduleTimeoutMillis(), reference.controlTaskQueue(), reference.professionalTaskQueue());
            assertThrows(IllegalArgumentException.class, () -> delivery.start(changed, commandId));
            assertThrows(IllegalArgumentException.class, () -> delivery.signal(changed, first.temporalRunId(),
                new Command(id(), "3".repeat(64), reference.runId(), CommandKind.REQUEST_CANCEL, id())));
            var signal = new Command(id(), "3".repeat(64), reference.runId(), CommandKind.REQUEST_CANCEL, id());
            assertEquals(first, delivery.signal(reference, first.temporalRunId(), signal));
            var history = client.fetchHistory(first.workflowId(), first.temporalRunId());
            assertEquals(1, history.getEvents().stream().filter(event -> event.hasWorkflowExecutionStartedEventAttributes()).count());
            assertEquals(1, history.getEvents().stream().filter(event -> event.hasWorkflowExecutionSignaledEventAttributes()).count());
            var sent = history.getEvents().stream().filter(event -> event.hasWorkflowExecutionSignaledEventAttributes()).findFirst().orElseThrow()
                .getWorkflowExecutionSignaledEventAttributes();
            assertEquals("medical.task.command", sent.getSignalName());
            assertEquals(signal, client.getOptions().getDataConverter().fromPayload(sent.getInput().getPayloads(0), Command.class, Command.class));
        } finally {
            client.newUntypedWorkflowStub(first.workflowId(), java.util.Optional.of(first.temporalRunId()), java.util.Optional.of("medical.task.run"))
                .terminate("SIMULATION_TRANSPORT_CHECK_COMPLETE");
            service.shutdown();
        }
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
