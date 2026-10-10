package com.ruoyi.medical.workflow;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Objects;

public final class WorkflowProtocol {
    private WorkflowProtocol() { }
    public enum ExecutionKind { TASK, SYSTEM_JOB }
    public enum State { QUEUED, RUNNING, WAITING_INPUT, SUCCEEDED, FAILED, CANCEL_REQUESTED, CANCELLED }
    public enum CommandKind { RESOLVE_WAITING, REQUEST_CANCEL, TERMINATION_PROOF }
    public record Reference(String schemaVersion, String tenantId, String projectId, String runId, ExecutionKind executionKind,
            String inputManifestId, String inputSha256, String policyRevision, long deadlineEpochMillis, int maximumCommands,
            int maximumSteps, int maximumParallelSteps, long readTimeoutMillis, long stepTimeoutMillis, long scheduleTimeoutMillis,
            String controlTaskQueue, String professionalTaskQueue) {
        public Reference {
            if (!"1.0.0".equals(schemaVersion) || executionKind == null) invalid();
            ids(tenantId, runId, inputManifestId, policyRevision); WireValues.hash(inputSha256);
            if (projectId != null) ids(projectId);
            if (executionKind == ExecutionKind.TASK && projectId == null) invalid();
            if (deadlineEpochMillis <= 0 || maximumCommands < 1 || maximumSteps < 1 || maximumParallelSteps < 1
                    || maximumParallelSteps > maximumSteps || readTimeoutMillis < 1 || stepTimeoutMillis < 1 || scheduleTimeoutMillis < 1) invalid();
            queue(controlTaskQueue); queue(professionalTaskQueue);
        }
        public String workflowId() { return tenantId + "/" + runId; }
    }
    public record Output(String artifactId, String sha256, String schemaRef) {
        public Output { ids(artifactId); WireValues.hash(sha256); if (schemaRef == null || schemaRef.isBlank()) invalid(); }
    }
    public record Step(String stepId, String inputSha256, List<String> dependsOnStepIds, boolean required, State status,
            long leaseGeneration, String receiptId, String waitingItemId, List<Output> outputs) {
        public Step {
            ids(stepId); WireValues.hash(inputSha256); dependsOnStepIds = List.copyOf(dependsOnStepIds); dependsOnStepIds.forEach(WorkflowProtocol::ids);
            outputs = List.copyOf(outputs); Objects.requireNonNull(status);
            if (receiptId != null) ids(receiptId);
            if (waitingItemId != null) ids(waitingItemId);
            if (leaseGeneration < 0 || status != State.QUEUED && (leaseGeneration < 1 || receiptId == null)
                    || status == State.WAITING_INPUT && waitingItemId == null) invalid();
        }
    }
    public record Plan(String inputSha256, String planRevision, State runStatus, String cancellationCommandId, FinalReceipt completedReceipt, List<Step> steps) {
        public Plan {
            WireValues.hash(inputSha256); ids(planRevision); Objects.requireNonNull(runStatus); steps = List.copyOf(steps);
            if (cancellationCommandId != null) ids(cancellationCommandId);
            if (runStatus == State.CANCEL_REQUESTED && cancellationCommandId == null) invalid();
            if ((runStatus == State.SUCCEEDED || runStatus == State.FAILED || runStatus == State.CANCELLED) && completedReceipt == null) invalid();
            if (completedReceipt != null && (completedReceipt.status() != runStatus || !completedReceipt.inputSha256().equals(inputSha256))) invalid();
        }
    }
    public record Dispatch(String runId, String stepId, String actionId, String commandId, String inputSha256,
            long leaseGeneration, String stepEnvelopeArtifactId, String stepEnvelopeSha256) {
        public Dispatch {
            ids(runId, stepId, actionId, commandId, stepEnvelopeArtifactId); WireValues.hash(inputSha256); WireValues.hash(stepEnvelopeSha256);
            if (leaseGeneration < 1) invalid();
        }
    }
    public record Completion(String runId, String stepId, String inputSha256, long leaseGeneration, State status,
            String resultArtifactId, String resultSha256) {
        public Completion {
            ids(runId, stepId, resultArtifactId); WireValues.hash(inputSha256); WireValues.hash(resultSha256);
            if (leaseGeneration < 1 || status != State.SUCCEEDED && status != State.WAITING_INPUT && status != State.FAILED) invalid();
        }
    }
    public record Receipt(String stepId, String inputSha256, long leaseGeneration, State status, String receiptId,
            String waitingItemId, List<Output> outputs) {
        public Receipt {
            ids(stepId, receiptId); WireValues.hash(inputSha256); outputs = List.copyOf(outputs);
            if (waitingItemId != null) ids(waitingItemId);
            if (leaseGeneration < 1 || status == null || status == State.RUNNING || status == State.CANCEL_REQUESTED
                    || status == State.WAITING_INPUT && waitingItemId == null) invalid();
        }
    }
    public record Command(String commandId, String commandSha256, String runId, CommandKind kind, String decisionReceiptId) {
        public Command { ids(commandId, runId, decisionReceiptId); WireValues.hash(commandSha256); Objects.requireNonNull(kind); }
    }
    public record CommandReceipt(String commandId, String commandSha256, String runId, boolean cancelRequested, List<Receipt> steps) {
        public CommandReceipt { ids(commandId, runId); WireValues.hash(commandSha256); steps = List.copyOf(steps); }
    }
    public record Termination(String proofReceiptId, boolean dispatchClosed, boolean invocationsTerminal, boolean processesExited) {
        public Termination { ids(proofReceiptId); }
        public boolean complete() { return dispatchClosed && invocationsTerminal && processesExited; }
    }
    public record FinalReceipt(String runId, String inputSha256, State status, String receiptId) {
        public FinalReceipt {
            ids(runId, receiptId); WireValues.hash(inputSha256);
            if (status != State.SUCCEEDED && status != State.FAILED && status != State.CANCELLED) invalid();
        }
    }
    public record View(String runId, State status, List<Receipt> receipts, int activeSteps, int consumedCommands) { }
    private static void ids(String... values) { for (String value : values) WireValues.uuid(value); }
    private static void queue(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) invalid(); }
    private static void invalid() { throw new IllegalArgumentException("WORKFLOW_INPUT_INVALID"); }
}
