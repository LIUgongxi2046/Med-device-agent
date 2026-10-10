package com.ruoyi.medical.workflow;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.util.Objects;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

public final class WorkflowDelivery {
    private final WorkflowClient client;
    public WorkflowDelivery(WorkflowClient client) { this.client = Objects.requireNonNull(client); }

    public Acceptance start(Reference reference, String commandId) {
        com.ruoyi.medical.contracts.WireValues.uuid(commandId);
        var options = WorkflowOptions.newBuilder().setWorkflowId(reference.workflowId()).setTaskQueue(reference.controlTaskQueue())
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
            .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
            .setRequestId(commandId).build();
        var workflow = client.newWorkflowStub(TaskWorkflow.class, options);
        var execution = WorkflowClient.start(workflow::run, reference);
        requireInput(reference, execution);
        return acceptance(reference, execution);
    }

    public Acceptance signal(Reference reference, String temporalRunId, Command command) {
        com.ruoyi.medical.contracts.WireValues.uuid(temporalRunId);
        if (!reference.runId().equals(command.runId())) invalid();
        var execution = WorkflowExecution.newBuilder().setWorkflowId(reference.workflowId()).setRunId(temporalRunId).build();
        requireInput(reference, execution);
        client.newUntypedWorkflowStub(execution, java.util.Optional.of("medical.task.run")).signal("medical.task.command", command);
        // 这里只登记Temporal接收；C04仍须重读独立消费者回执。
        return acceptance(reference, execution);
    }

    public CommandReceipt consumed(Reference reference, String temporalRunId, String commandId) {
        com.ruoyi.medical.contracts.WireValues.uuid(temporalRunId); com.ruoyi.medical.contracts.WireValues.uuid(commandId);
        var execution = WorkflowExecution.newBuilder().setWorkflowId(reference.workflowId()).setRunId(temporalRunId).build();
        requireInput(reference, execution);
        return client.newUntypedWorkflowStub(execution, java.util.Optional.of("medical.task.run"))
            .query("medical.task.command-receipt", CommandReceipt.class, commandId);
    }

    private void requireInput(Reference reference, WorkflowExecution execution) {
        var request = GetWorkflowExecutionHistoryRequest.newBuilder().setNamespace(client.getOptions().getNamespace())
            .setExecution(execution).setMaximumPageSize(1).build();
        var history = client.getWorkflowServiceStubs().blockingStub().getWorkflowExecutionHistory(request).getHistory();
        if (history.getEventsCount() < 1 || !history.getEvents(0).hasWorkflowExecutionStartedEventAttributes()) invalid();
        var started = history.getEvents(0).getWorkflowExecutionStartedEventAttributes();
        if (!started.getWorkflowType().getName().equals("medical.task.run") || !started.hasInput()
                || started.getInput().getPayloadsCount() != 1) invalid();
        var original = client.getOptions().getDataConverter().fromPayload(started.getInput().getPayloads(0), Reference.class, Reference.class);
        if (!reference.equals(original)) invalid();
    }
    private static Acceptance acceptance(Reference reference, WorkflowExecution execution) {
        return new Acceptance(reference.workflowId(), execution.getRunId(), reference.inputSha256());
    }
    public record Acceptance(String workflowId, String temporalRunId, String inputSha256) {
        public Acceptance { com.ruoyi.medical.contracts.WireValues.uuid(temporalRunId); com.ruoyi.medical.contracts.WireValues.hash(inputSha256); }
    }
    private static void invalid() { throw new IllegalArgumentException("WORKFLOW_DELIVERY_INPUT_MISMATCH"); }
}
