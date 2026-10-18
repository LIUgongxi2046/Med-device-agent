package com.ruoyi.medical.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

public final class TaskWorkflowImpl implements TaskWorkflow {
    private Reference reference;
    private State state = State.QUEUED;
    private final ArrayDeque<Command> pendingCommands = new ArrayDeque<>();
    private final Map<String,Command> commands = new LinkedHashMap<>();
    private final Map<String,CommandReceipt> commandReceipts = new LinkedHashMap<>();
    private final List<String> consumed = new ArrayList<>();
    private final Map<String,Step> steps = new LinkedHashMap<>();
    private final Map<String,Receipt> receipts = new LinkedHashMap<>();
    private final Map<String,Running> active = new LinkedHashMap<>();
    private boolean cancelRequested;
    private String cancellationId;
    private String cancellationReason;
    private RunActivities control;
    private ProfessionalActivity professional;

    @Override public FinalReceipt run(Reference input) {
        reference = input;
        if (!reference.workflowId().equals(Workflow.getInfo().getWorkflowId())) invalid("WORKFLOW_OWNER_MISMATCH");
        control = Workflow.newActivityStub(RunActivities.class, options(reference.controlTaskQueue(), reference.readTimeoutMillis(), 3));
        professional = Workflow.newActivityStub(ProfessionalActivity.class, options(reference.professionalTaskQueue(), reference.stepTimeoutMillis(), 1)
            .toBuilder().setHeartbeatTimeout(Duration.ofSeconds(45)).build());
        var plan = control.load(reference); steps.putAll(WorkflowPlan.require(reference, plan));
        if (plan.completedReceipt() != null) {
            var complete = plan.completedReceipt(); if (!reference.runId().equals(complete.runId())) invalid("RUN_FINAL_RECEIPT_MISMATCH");
            state = complete.status(); return complete;
        }
        for (var step : steps.values()) if (step.receiptId() != null) receipts.put(step.stepId(),
            new Receipt(step.stepId(), step.inputSha256(), step.leaseGeneration(), step.status(), step.receiptId(), step.waitingItemId(), step.outputs()));
        if (plan.runStatus() == State.CANCEL_REQUESTED) {
            cancelRequested = true; cancellationId = plan.cancellationCommandId(); cancellationReason = "RUN_CANCEL_REQUESTED";
        }
        state = State.RUNNING;
        while (true) {
            consumeCommands(); harvest();
            if (!cancelRequested && Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) {
                cancelRequested = true; cancellationId = Workflow.randomUUID().toString(); cancellationReason = "DEADLINE_EXCEEDED";
            }
            if (cancelRequested) {
                state = State.CANCEL_REQUESTED;
                var proof = control.cancellation(reference, cancellationId, cancellationReason);
                if (proof.complete() && active.isEmpty()) return finish(State.CANCELLED, cancellationReason);
                // 缺少外部终态或进程退出证明时，只接收持久Signal或既有Activity结果。
                Workflow.await(() -> !pendingCommands.isEmpty() || completed()); continue;
            }
            dispatchReady();
            if (cancelRequested || !pendingCommands.isEmpty()) continue;
            if (active.isEmpty() && terminal()) {
                boolean failed = steps.values().stream().anyMatch(step -> step.required() && step.status() != State.SUCCEEDED);
                return finish(failed ? State.FAILED : State.SUCCEEDED, failed ? "REQUIRED_STEP_FAILED" : "ALL_REQUIRED_STEPS_COMPLETE");
            }
            state = active.isEmpty() ? State.WAITING_INPUT : State.RUNNING;
            long remaining = reference.deadlineEpochMillis() - Workflow.currentTimeMillis();
            if (remaining > 0) Workflow.await(Duration.ofMillis(remaining), () -> !pendingCommands.isEmpty() || completed());
        }
    }
    private ActivityOptions options(String queue, long timeout, int attempts) {
        return ActivityOptions.newBuilder().setTaskQueue(queue).setStartToCloseTimeout(Duration.ofMillis(timeout))
            .setScheduleToStartTimeout(Duration.ofMillis(reference.scheduleTimeoutMillis()))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(attempts).build()).build();
    }
    private void consumeCommands() {
        while (!pendingCommands.isEmpty()) {
            var command = pendingCommands.removeFirst(); var receipt = control.consume(reference, command);
            if (!command.commandId().equals(receipt.commandId()) || !command.commandSha256().equals(receipt.commandSha256())
                    || !reference.runId().equals(receipt.runId())) invalid("COMMAND_RECEIPT_MISMATCH");
            for (var step : receipt.steps()) accept(step, command.kind() == CommandKind.RESOLVE_WAITING);
            consumed.add(command.commandId());
            commandReceipts.put(command.commandId(), receipt);
            if (receipt.cancelRequested() && !cancelRequested) { cancelRequested = true; cancellationId = command.commandId(); cancellationReason = "RUN_CANCEL_REQUESTED"; }
        }
    }
    private void dispatchReady() {
        for (var original : List.copyOf(steps.values())) {
            var step = steps.get(original.stepId());
            if (step.status() != State.QUEUED || active.size() >= reference.maximumParallelSteps()) continue;
            if (step.dependsOnStepIds().stream().anyMatch(id -> steps.get(id).status() == State.FAILED || steps.get(id).status() == State.CANCELLED)) {
                accept(control.dependencyFailed(reference, step.stepId())); continue;
            }
            if (step.dependsOnStepIds().stream().anyMatch(id -> steps.get(id).status() != State.SUCCEEDED)) continue;
            if (!pendingCommands.isEmpty() || Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) return;
            var dispatch = control.dispatch(reference, step.stepId(), Workflow.randomUUID().toString());
            if (!reference.runId().equals(dispatch.runId()) || !step.stepId().equals(dispatch.stepId())
                    || !step.inputSha256().equals(dispatch.inputSha256())) invalid("STEP_DISPATCH_MISMATCH");
            steps.put(step.stepId(), new Step(step.stepId(), step.inputSha256(), step.dependsOnStepIds(), step.required(), State.RUNNING,
                dispatch.leaseGeneration(), dispatch.commandId(), null, List.of()));
            consumeCommands();
            if (cancelRequested || Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) return;
            active.put(step.stepId(), new Running(dispatch, Async.function(() -> {
                var completion = professional.execute(reference, dispatch);
                if (!dispatch.runId().equals(completion.runId()) || !dispatch.stepId().equals(completion.stepId())
                        || !dispatch.inputSha256().equals(completion.inputSha256()) || dispatch.leaseGeneration() != completion.leaseGeneration()) invalid("STEP_RESULT_MISMATCH");
                return control.commit(reference, dispatch, completion);
            })));
        }
    }
    private void harvest() {
        for (var entry : List.copyOf(active.entrySet())) {
            var running = entry.getValue(); if (!running.promise().isCompleted()) continue;
            var result = running.promise().getFailure() == null ? running.promise().get()
                : control.failure(reference, running.dispatch(), "ACTIVITY_FAILURE");
            if (result.leaseGeneration() != running.dispatch().leaseGeneration()) invalid("STEP_LEASE_MISMATCH");
            accept(result); active.remove(entry.getKey());
        }
    }
    private void accept(Receipt receipt) { accept(receipt, false); }
    private void accept(Receipt receipt, boolean resolvedWaiting) {
        var step = steps.get(receipt.stepId());
        if (step == null || !step.inputSha256().equals(receipt.inputSha256())) invalid("STEP_RECEIPT_MISMATCH");
        if (receipt.leaseGeneration() < step.leaseGeneration()) invalid("STEP_LEASE_MISMATCH");
        if (step.status() == State.WAITING_INPUT && resolvedWaiting && receipt.status() == State.QUEUED) {
            if (!WaitingStepTransition.valid(step, receipts.get(step.stepId()), receipt)) invalid("WAITING_RESOLUTION_MISMATCH");
            // Only the durable consumed resolution may queue a fresh attempt. The
            // original waiting receipt remains immutable in persistent history.
            steps.put(step.stepId(), new Step(step.stepId(),step.inputSha256(),step.dependsOnStepIds(),step.required(),
                State.QUEUED,step.leaseGeneration(),null,null,List.of()));
            receipts.remove(step.stepId()); return;
        }
        if (step.status() == State.WAITING_INPUT || step.status() == State.SUCCEEDED || step.status() == State.FAILED || step.status() == State.CANCELLED) {
            if (!receipt.equals(receipts.get(step.stepId()))) invalid("IMMUTABLE_STEP_RECEIPT_MISMATCH");
            return;
        }
        steps.put(step.stepId(), new Step(step.stepId(), step.inputSha256(), step.dependsOnStepIds(), step.required(), receipt.status(),
            receipt.leaseGeneration(), receipt.receiptId(), receipt.waitingItemId(), receipt.outputs()));
        receipts.put(step.stepId(), receipt);
    }
    private boolean completed() { return active.values().stream().anyMatch(value -> value.promise().isCompleted()); }
    private boolean terminal() { return steps.values().stream().allMatch(step -> step.status() == State.SUCCEEDED || step.status() == State.FAILED || step.status() == State.CANCELLED); }
    private FinalReceipt finish(State requested, String reason) {
        var result = control.finish(reference, requested, reason, List.copyOf(receipts.values()), List.copyOf(consumed));
        if (!reference.runId().equals(result.runId()) || !reference.inputSha256().equals(result.inputSha256())
                || result.status() != requested) invalid("RUN_FINAL_RECEIPT_MISMATCH");
        state = result.status(); return result;
    }
    @Override public void command(Command command) {
        if (reference == null || !reference.runId().equals(command.runId())) invalid("COMMAND_OWNER_MISMATCH");
        var previous = commands.get(command.commandId());
        if (previous != null) { if (!previous.equals(command)) invalid("COMMAND_INPUT_MISMATCH"); return; }
        if (commands.size() >= reference.maximumCommands()) invalid("COMMAND_CAPACITY_EXCEEDED");
        commands.put(command.commandId(), command); pendingCommands.addLast(command);
    }
    @Override public View view() { return new View(reference == null ? null : reference.runId(), state, List.copyOf(receipts.values()), active.size(), consumed.size()); }
    @Override public CommandReceipt commandReceipt(String commandId) {
        com.ruoyi.medical.contracts.WireValues.uuid(commandId); return commandReceipts.get(commandId);
    }
    private static void invalid(String code) { throw ApplicationFailure.newNonRetryableFailure(code, code); }
    private record Running(Dispatch dispatch, Promise<Receipt> promise) { }
}
