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

/** Explicit V2 worker implementation. DEFAULT_VERSION retains the exact V1
 * command sequence on replay; the old implementation and worker are untouched. */
public final class TaskWorkflowWaitingV2Impl implements TaskWorkflow {
    private final WaitingWorkflowDeploymentV2 deployment;
    private boolean waitingV2;
    // Keep DEFAULT_VERSION histories on their original command path. This marker
    // changes only cancellation delivery, never business/termination evidence.
    private static final String TERMINATION_MARKER = "task-owned-activity-cancellation-v1";
    private boolean cancellationV1;
    private boolean resumeOriginalsV1;private TaskRunReuseActivitiesV1 reuseControl,reusePreparation;private TaskRunReuseProtocolV1.AssemblyV1 assemblyReuse;private boolean assemblyReuseAttempted,assemblyReuseUncertain;
    private final Map<String,TaskRunReuseProtocolV1.ReusedV1> reused=new LinkedHashMap<>();
    private final List<io.temporal.workflow.CancellationScope> ownedScopes = new ArrayList<>();
    private SectionWriterWorkflowExtensionV1 writerExtension;
    private boolean writerV1;
    private TaskSectionWriterStageActivitiesV1 writerControl,writerDispatch;
    private TaskSectionWriterActivityV1 writerExecution;
    private TaskSectionWriterStageProtocolV1.InventoryV1 writerInventory;
    private final Map<String,WriterRunning> writerActive=new LinkedHashMap<>();
    private final Map<String,TaskSectionWriterStageProtocolV1.CommandV1> writerRecovering=new LinkedHashMap<>();
    private long nextWriterPoll;
    private boolean writerPreparationAttempted;
    private SourceVerifierWorkflowExtensionV1 verifierExtension;
    private boolean verifierV1;
    private TaskSourceVerifierStageActivitiesV1 verifierControl,verifierDispatch;
    private TaskSourceVerifierActivityV1 verifierExecution;
    private TaskSourceVerifierStageProtocolV1.InventoryV1 verifierInventory;
    private final Map<String,VerifierRunning> verifierActive=new LinkedHashMap<>();
    private final Map<String,TaskSourceVerifierStageProtocolV1.CommandV1> verifierRecovering=new LinkedHashMap<>();
    private long nextVerifierPoll;
    private boolean verifierPreparationAttempted;
    private CandidateAssemblyWorkflowExtensionV1 assemblyExtension;
    private boolean assemblyV1,assemblyRecovering;
    private TaskCandidateAssemblyStageActivitiesV1 assemblyControl,assemblyDispatch;
    private TaskCandidateAssemblyStageProtocolV1.CommandV1 assemblyCommand;
    private TaskCandidateAssemblyStageProtocolV1.CommitV1 assemblyCommit;
    private Promise<TaskCandidateAssemblyStageProtocolV1.CommitV1> assemblyActive;
    private long nextAssemblyPoll;

    private ProfessionalActivityV2 professionalV2;
    private TaskRunWaitingActivities waitingControl;
    private TaskRunRecoveryActivitiesV2 recoveryControl;
    private TaskRunStageActivitiesV2 stageExecution,stageControl;
    private final Map<String,TaskRunStageProtocolV2.StageCommandV2> stageCommands=new LinkedHashMap<>();
    private final Map<String,TaskRunStageProtocolV2.StageCommitV2> stageCommits=new LinkedHashMap<>();
    private final Map<String,String> recoveringStages=new LinkedHashMap<>();
    private final Map<String,StageRunning> activeStages=new LinkedHashMap<>();
    private final java.util.Set<String> failedActivities=new java.util.HashSet<>();
    private final Map<String,Dispatch> recovering=new LinkedHashMap<>();
    private final Map<String,TaskRunRecoveryProtocolV2.UndispatchedFailureV2> undispatched=new LinkedHashMap<>();
    private long nextRecoveryPoll;
    /** Replay entry only: new V2 execution without configured deployment fails closed. */
    public TaskWorkflowWaitingV2Impl(){deployment=null;}
    public TaskWorkflowWaitingV2Impl(WaitingWorkflowDeploymentV2 deployment){this.deployment=java.util.Objects.requireNonNull(deployment);}
    public TaskWorkflowWaitingV2Impl(WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 writerExtension){this(deployment);this.writerExtension=java.util.Objects.requireNonNull(writerExtension);writerExtension.require(deployment);}
    public TaskWorkflowWaitingV2Impl(WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 writerExtension,SourceVerifierWorkflowExtensionV1 verifierExtension){this(deployment,writerExtension);this.verifierExtension=java.util.Objects.requireNonNull(verifierExtension);verifierExtension.require(deployment,writerExtension);}
    private Reference reference;
    private Plan plan;
    private State state = State.QUEUED;
    private final ArrayDeque<Command> pendingCommands = new ArrayDeque<>();
    private final Map<String,Command> commands = new LinkedHashMap<>();
    private final Map<String,CommandReceipt> commandReceipts = new LinkedHashMap<>();
    private final List<String> consumed = new ArrayList<>();
    private final Map<String,Step> steps = new LinkedHashMap<>();
    private final Map<String,Receipt> receipts = new LinkedHashMap<>();
    private final Map<String,Running> active = new LinkedHashMap<>();
    private boolean cancelRequested;
    private boolean materializedDuringDispatch;
    private String cancellationId;
    private String cancellationReason;
    private RunActivities control;
    private ProfessionalActivity professional;

    public TaskWorkflowWaitingV2Impl(WaitingWorkflowDeploymentV2 deployment,SectionWriterWorkflowExtensionV1 writer,SourceVerifierWorkflowExtensionV1 verifier,CandidateAssemblyWorkflowExtensionV1 assembly){this(deployment,writer,verifier);this.assemblyExtension=java.util.Objects.requireNonNull(assembly);assembly.require(deployment,verifier);}
    @Override public FinalReceipt run(Reference input) {
        try { return runUntilComplete(input); }
        catch (RecoveredCompletion completed) { return completed.receipt; }
    }
    // Internal deterministic unwind, caught at the workflow boundary before any
    // further activity can be scheduled after an exact durable final receipt.
    private static final class RecoveredCompletion extends RuntimeException {
        private final FinalReceipt receipt;
        private RecoveredCompletion(FinalReceipt receipt) { super(null, null, false, false); this.receipt = receipt; }
    }
    private FinalReceipt runUntilComplete(Reference input) {
        reference = input;
        if (!reference.workflowId().equals(Workflow.getInfo().getWorkflowId())) invalid("WORKFLOW_OWNER_MISMATCH");
        waitingV2 = Workflow.getVersion(WaitingWorkflowDeploymentV2.MARKER, Workflow.DEFAULT_VERSION, 2) == 2;
        cancellationV1 = waitingV2 && Workflow.getVersion(TERMINATION_MARKER, Workflow.DEFAULT_VERSION, 1) == 1;
        resumeOriginalsV1=waitingV2&&Workflow.getVersion("task-original-success-reuse-v1",Workflow.DEFAULT_VERSION,1)==1;
        if(waitingV2){
            if(deployment==null)invalid("WAITING_V2_DEPLOYMENT_REQUIRED");
            deployment.require(reference,Workflow.getInfo().getNamespace());
            deployment.requireMemo(Workflow.getMemo(WaitingWorkflowDeploymentV2.CONFIG_MEMO,String.class,String.class),
                Workflow.getMemo(WaitingWorkflowDeploymentV2.HASH_MEMO,String.class,String.class),
                Workflow.getMemo(WaitingWorkflowDeploymentV2.COMMAND_MEMO,String.class,String.class));
            if(!deployment.controlTaskQueue().equals(Workflow.getInfo().getTaskQueue()))invalid("WAITING_V2_QUEUE_MISMATCH");
            professionalV2=Workflow.newActivityStub(ProfessionalActivityV2.class,options(reference.professionalTaskQueue(),reference.stepTimeoutMillis(),1)
                .toBuilder().setHeartbeatTimeout(Duration.ofSeconds(45)).build());
            waitingControl=Workflow.newActivityStub(TaskRunWaitingActivities.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));
            recoveryControl=Workflow.newActivityStub(TaskRunRecoveryActivitiesV2.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));
            if(resumeOriginalsV1){reuseControl=Workflow.newActivityStub(TaskRunReuseActivitiesV1.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));reusePreparation=Workflow.newActivityStub(TaskRunReuseActivitiesV1.class,options(reference.controlTaskQueue(),reference.stepTimeoutMillis(),1));}
            stageExecution=Workflow.newActivityStub(TaskRunStageActivitiesV2.class,options(reference.controlTaskQueue(),reference.stepTimeoutMillis(),1));
            stageControl=Workflow.newActivityStub(TaskRunStageActivitiesV2.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));
        }
        if(waitingV2&&writerExtension!=null){
            writerV1=Workflow.getVersion(SectionWriterWorkflowExtensionV1.MARKER,Workflow.DEFAULT_VERSION,1)==1;
            if(writerV1){writerExtension.requireMemo(Workflow.getMemo(SectionWriterWorkflowExtensionV1.CONFIG_MEMO,String.class,String.class),Workflow.getMemo(SectionWriterWorkflowExtensionV1.HASH_MEMO,String.class,String.class));
                writerControl=Workflow.newActivityStub(TaskSectionWriterStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));
                writerDispatch=Workflow.newActivityStub(TaskSectionWriterStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.stepTimeoutMillis(),1));
                writerExecution=Workflow.newActivityStub(TaskSectionWriterActivityV1.class,options(writerExtension.professionalTaskQueue(),reference.stepTimeoutMillis(),1).toBuilder().setHeartbeatTimeout(Duration.ofSeconds(45)).build());}
        }
        if(writerV1&&verifierExtension!=null){
            verifierV1=Workflow.getVersion(SourceVerifierWorkflowExtensionV1.MARKER,Workflow.DEFAULT_VERSION,1)==1;
            if(verifierV1){verifierExtension.requireMemo(Workflow.getMemo(SourceVerifierWorkflowExtensionV1.CONFIG_MEMO,String.class,String.class),Workflow.getMemo(SourceVerifierWorkflowExtensionV1.HASH_MEMO,String.class,String.class));
                verifierControl=Workflow.newActivityStub(TaskSourceVerifierStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));
                verifierDispatch=Workflow.newActivityStub(TaskSourceVerifierStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.stepTimeoutMillis(),1));
                verifierExecution=Workflow.newActivityStub(TaskSourceVerifierActivityV1.class,options(verifierExtension.professionalTaskQueue(),reference.stepTimeoutMillis(),1).toBuilder().setHeartbeatTimeout(Duration.ofSeconds(45)).build());}
        }
        if(verifierV1&&assemblyExtension!=null){assemblyV1=Workflow.getVersion(CandidateAssemblyWorkflowExtensionV1.MARKER,Workflow.DEFAULT_VERSION,1)==1;if(assemblyV1){assemblyExtension.requireMemo(Workflow.getMemo(CandidateAssemblyWorkflowExtensionV1.CONFIG_MEMO,String.class,String.class),Workflow.getMemo(CandidateAssemblyWorkflowExtensionV1.HASH_MEMO,String.class,String.class));assemblyControl=Workflow.newActivityStub(TaskCandidateAssemblyStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.readTimeoutMillis(),3));assemblyDispatch=Workflow.newActivityStub(TaskCandidateAssemblyStageActivitiesV1.class,options(reference.controlTaskQueue(),reference.stepTimeoutMillis(),1));}}
        control = Workflow.newActivityStub(RunActivities.class, options(reference.controlTaskQueue(), reference.readTimeoutMillis(), 3));
        professional = Workflow.newActivityStub(ProfessionalActivity.class, options(reference.professionalTaskQueue(), reference.stepTimeoutMillis(), 1)
            .toBuilder().setHeartbeatTimeout(Duration.ofSeconds(45)).build());
        if(resumeOriginalsV1){var original=refreshReuse();if(original.completed()!=null){state=original.completed().status();return original.completed();}}
        if(waitingV2){TaskRunRecoveryProtocolV2.RunPlanV2 loaded;try{loaded=recoveryControl.loadV2(reference);}catch(io.temporal.failure.ActivityFailure failure){if(resumeOriginalsV1){var original=refreshReuse();if(original.completed()!=null){state=original.completed().status();return original.completed();}}throw failure;}steps.putAll(loaded.require(reference));plan=loaded.combined(reference);loaded.undispatchedFailures().forEach(f->undispatched.put(f.stepId(),f));for(var running:loaded.inFlight())recovering.put(running.dispatch().stepId(),running.dispatch());acceptStageInventory(loaded);}
        else {plan = control.load(reference); steps.putAll(WorkflowPlan.require(reference, plan));}
        if(resumeOriginalsV1){refreshReuse();var augmented=withReuse(plan);steps.putAll(WorkflowPlan.refresh(reference,plan,steps,augmented));plan=augmented;}
        if (plan.completedReceipt() != null) {
            var complete = plan.completedReceipt(); if (!reference.runId().equals(complete.runId())) invalid("RUN_FINAL_RECEIPT_MISMATCH");
            state = complete.status(); return complete;
        }
        for (var step : steps.values()) if (step.receiptId() != null && !reused.containsKey(step.stepId()) && (!waitingV2||step.status()!=State.RUNNING)) receipts.put(step.stepId(),
            new Receipt(step.stepId(), step.inputSha256(), step.leaseGeneration(), step.status(), step.receiptId(), step.waitingItemId(), step.outputs()));
        if (plan.runStatus() == State.CANCEL_REQUESTED) {
            cancelRequested = true; cancellationId = plan.cancellationCommandId(); cancellationReason = "RUN_CANCEL_REQUESTED";
        }
        state = State.RUNNING;
        while (true) {
            consumeCommands(); harvest(); if(waitingV2)harvestStages(); if(writerV1)harvestWriters(); if(verifierV1)harvestVerifiers();if(assemblyV1)harvestAssembly();
            if(waitingV2&&!cancelRequested&&Workflow.currentTimeMillis()<reference.deadlineEpochMillis())pollRecovered();
            if (!cancelRequested && Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) {
                cancelRequested = true; cancellationId = Workflow.randomUUID().toString(); cancellationReason = "DEADLINE_EXCEEDED";
            }
            if (cancelRequested) {
                state = State.CANCEL_REQUESTED;
                var proof = control.cancellation(reference, cancellationId, cancellationReason);
                // Close the durable dispatch barrier FIRST. Temporal cancellation is
                // a stop request only; independent process/provider facts still gate finish.
                if (cancellationV1 && proof.dispatchClosed()) {
                    for (var scope : ownedScopes) scope.cancel();
                    ownedScopes.clear();
                }
                if (proof.complete() && active.isEmpty() && activeStages.isEmpty() && writerActive.isEmpty() && verifierActive.isEmpty() && assemblyActive==null) {if(waitingV2){recovering.clear();recoveringStages.clear();}return finish(State.CANCELLED, cancellationReason);}
                // 缺少外部终态或进程退出证明时，只接收持久Signal或既有Activity结果。
                if(waitingV2)Workflow.await(Duration.ofMillis(deployment.recoveryPollMillis()),() -> !pendingCommands.isEmpty() || completed());else Workflow.await(() -> !pendingCommands.isEmpty() || completed()); continue;
            }
            materializedDuringDispatch = false;
            dispatchReady();
            if(waitingV2&&!cancelRequested&&pendingCommands.isEmpty())dispatchStage();
            if(writerV1&&!cancelRequested&&pendingCommands.isEmpty())advanceWriters();
            if(verifierV1&&!cancelRequested&&pendingCommands.isEmpty())advanceVerifiers();
            if(assemblyV1&&!cancelRequested&&pendingCommands.isEmpty())advanceAssembly();
            if (cancelRequested || !pendingCommands.isEmpty() || materializedDuringDispatch) continue;
            if (active.isEmpty() && activeStages.isEmpty() && terminal()) {
                boolean failed = WorkflowPlan.failed(plan,steps);
                return finish(failed ? State.FAILED : State.SUCCEEDED, failed ? "REQUIRED_STEP_FAILED" : "ALL_REQUIRED_STEPS_COMPLETE");
            }
            state = active.isEmpty() && recovering.isEmpty() && activeStages.isEmpty() && recoveringStages.isEmpty() && writerActive.isEmpty() && writerRecovering.isEmpty() && verifierActive.isEmpty() && verifierRecovering.isEmpty() && assemblyActive==null && !assemblyRecovering ? State.WAITING_INPUT : State.RUNNING;
            long remaining = reference.deadlineEpochMillis() - Workflow.currentTimeMillis();
            if(waitingV2&&(!recovering.isEmpty()||!recoveringStages.isEmpty()))remaining=Math.min(remaining,Math.max(1,nextRecoveryPoll-Workflow.currentTimeMillis()));
            if(writerV1)remaining=Math.min(remaining,Math.max(1,nextWriterPoll-Workflow.currentTimeMillis()));
            if(assemblyV1&&verifierInventory!=null&&verifiersSatisfied()&&assemblyCommit==null&&assemblyReuse==null)remaining=Math.min(remaining,Math.max(1,nextAssemblyPoll-Workflow.currentTimeMillis()));
            if(verifierV1&&writerInventory!=null&&writersSatisfied())remaining=Math.min(remaining,Math.max(1,nextVerifierPoll-Workflow.currentTimeMillis()));
            if (remaining > 0) Workflow.await(Duration.ofMillis(remaining), () -> !pendingCommands.isEmpty() || completed());
        }
    }
    private <T> Promise<T> cancellable(io.temporal.workflow.Functions.Func<T> action) {
        if (!cancellationV1) return Async.function(action);
        var result = Workflow.<T>newPromise();
        var scope = Workflow.newCancellationScope(() -> result.completeFrom(Async.function(action)));
        ownedScopes.add(scope);
        scope.run();
        return result;
    }
    private ActivityOptions options(String queue, long timeout, int attempts) {
        var builder=ActivityOptions.newBuilder().setTaskQueue(queue).setStartToCloseTimeout(Duration.ofMillis(timeout))
            .setScheduleToStartTimeout(Duration.ofMillis(reference.scheduleTimeoutMillis()))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(attempts).build());
        if(cancellationV1)builder.setCancellationType(io.temporal.activity.ActivityCancellationType.WAIT_CANCELLATION_COMPLETED);
        return builder.build();
    }
    private void consumeCommands() {
        while (!pendingCommands.isEmpty()) {
            var command = pendingCommands.removeFirst(); var receipt = control.consume(reference, command);
            if (!command.commandId().equals(receipt.commandId()) || !command.commandSha256().equals(receipt.commandSha256())
                    || !reference.runId().equals(receipt.runId())) invalid("COMMAND_RECEIPT_MISMATCH");
            if (receipt.steps().stream().anyMatch(step -> !steps.containsKey(step.stepId()))) refreshMaterialized();
            for (var step : receipt.steps()) accept(step, command.kind() == CommandKind.RESOLVE_WAITING);
            refreshMaterialized();
            consumed.add(command.commandId());
            commandReceipts.put(command.commandId(), receipt);
            if (receipt.cancelRequested() && !cancelRequested) { cancelRequested = true; cancellationId = command.commandId(); cancellationReason = "RUN_CANCEL_REQUESTED"; }
        }
    }
    private void dispatchReady() {
        if(waitingV2){for(var node:List.copyOf(plan.plannedSteps())){if(!steps.containsKey(node.stepId())&&!undispatched.containsKey(node.stepId())&&hasFailedAncestor(node.stepId())){
            if(!pendingCommands.isEmpty()||Workflow.currentTimeMillis()>=reference.deadlineEpochMillis())return;
            var result=recoveryControl.failUndispatched(reference,node.stepId());if(!"INPUT_UNMATERIALIZED".equals(result.kind())||!node.stepId().equals(result.stepId()))invalid("UNDISPATCHED_FACT_MISMATCH");refreshMaterialized();}}}
        for (var original : List.copyOf(steps.values())) {
            var step = steps.get(original.stepId());
            if (step.status() != State.QUEUED || active.size()+recovering.size() >= reference.maximumParallelSteps()) continue;
            if (step.dependsOnStepIds().stream().anyMatch(id -> steps.containsKey(id) && (steps.get(id).status() == State.FAILED || steps.get(id).status() == State.CANCELLED))) {
                if(waitingV2&&step.leaseGeneration()==0){var failure=recoveryControl.failUndispatched(reference,step.stepId());if(!"INPUT_READY".equals(failure.kind())||!step.stepId().equals(failure.stepId())||!step.inputSha256().equals(failure.inputSha256()))invalid("UNDISPATCHED_FACT_MISMATCH");}
                else accept(control.dependencyFailed(reference, step.stepId())); refreshMaterialized(); continue;
            }
            if (step.dependsOnStepIds().stream().anyMatch(id -> !steps.containsKey(id) || steps.get(id).status() != State.SUCCEEDED)) continue;
            if (!pendingCommands.isEmpty() || Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) return;
            var dispatch = control.dispatch(reference, step.stepId(), Workflow.randomUUID().toString());
            if (!reference.runId().equals(dispatch.runId()) || !step.stepId().equals(dispatch.stepId())
                    || !step.inputSha256().equals(dispatch.inputSha256())) invalid("STEP_DISPATCH_MISMATCH");
            steps.put(step.stepId(), new Step(step.stepId(), step.inputSha256(), step.dependsOnStepIds(), step.required(), State.RUNNING,
                dispatch.leaseGeneration(), dispatch.commandId(), null, List.of()));
            consumeCommands();
            if (cancelRequested || Workflow.currentTimeMillis() >= reference.deadlineEpochMillis()) return;
            active.put(step.stepId(), new Running(dispatch, cancellable(() -> {
                if(waitingV2){
                    var outcome=professionalV2.execute(reference,dispatch);outcome.require(dispatch);
                    return "WAITING_CONTROL".equals(outcome.kind())
                        ?waitingControl.commitWaiting(reference,dispatch,outcome.waiting())
                        :control.commit(reference,dispatch,outcome.completion());
                }
                var completion = professional.execute(reference, dispatch);
                if (!dispatch.runId().equals(completion.runId()) || !dispatch.stepId().equals(completion.stepId())
                        || !dispatch.inputSha256().equals(completion.inputSha256()) || dispatch.leaseGeneration() != completion.leaseGeneration()) invalid("STEP_RESULT_MISMATCH");
                return control.commit(reference, dispatch, completion);
            })));
        }
    }
    private void harvest() {
        boolean harvested = false;
        for (var entry : List.copyOf(active.entrySet())) {
            var running = entry.getValue(); if (!running.promise().isCompleted()) continue;
            if(waitingV2&&running.promise().getFailure()!=null){
                // The execute/finish reply may have been lost after durable production.
                // A query of the exact original dispatch precedes any failure write;
                // neither this branch nor recovery can reschedule professional work.
                recovering.put(entry.getKey(),running.dispatch());failedActivities.add(entry.getKey());active.remove(entry.getKey());continue;
            }
            var result = running.promise().getFailure() == null ? running.promise().get()
                : control.failure(reference, running.dispatch(), "ACTIVITY_FAILURE");
            if (result.leaseGeneration() != running.dispatch().leaseGeneration()) invalid("STEP_LEASE_MISMATCH");
            accept(result); active.remove(entry.getKey()); harvested = true;
        }
        if (harvested) refreshMaterialized();
    }
    private void refreshMaterialized() {
        if (!waitingV2 && steps.size() == plan.plannedSteps().size() && plan.requiredStages().stream().allMatch(stage -> stage.fulfillmentReceiptId() != null)) return;
        Plan refreshed;
        if(waitingV2){TaskRunRecoveryProtocolV2.RunPlanV2 loaded;try{loaded=recoveryControl.loadV2(reference);}catch(io.temporal.failure.ActivityFailure failure){if(resumeOriginalsV1){var original=refreshReuse();if(original.completed()!=null){state=original.completed().status();throw new RecoveredCompletion(original.completed());}}throw failure;}loaded.require(reference);refreshed=loaded.combined(reference);acceptStageInventory(loaded);
            var current=new LinkedHashMap<String,TaskRunRecoveryProtocolV2.UndispatchedFailureV2>();loaded.undispatchedFailures().forEach(f->current.put(f.stepId(),f));for(var prior:undispatched.values())if(!prior.equals(current.get(prior.stepId())))invalid("DISPOSITION_CHANGED");
            for(var failure:current.values()){var old=steps.get(failure.stepId());if(old!=null){if(!"INPUT_READY".equals(failure.kind())||old.status()!=State.QUEUED||old.leaseGeneration()!=0||!old.inputSha256().equals(failure.inputSha256())||active.containsKey(old.stepId())||recovering.containsKey(old.stepId()))invalid("DISPOSITION_CHANGED");steps.remove(old.stepId());}}undispatched.clear();undispatched.putAll(current);
            for(var running:loaded.inFlight()){var d=running.dispatch();var old=steps.get(d.stepId());if(old!=null&&old.status()!=State.QUEUED&&old.status()!=State.RUNNING)invalid("TERMINAL_DISPATCH_REOPENED");if(active.containsKey(d.stepId())){if(!active.get(d.stepId()).dispatch().equals(d))invalid("ORIGINAL_DISPATCH_CHANGED");}else{var prior=recovering.putIfAbsent(d.stepId(),d);if(prior!=null&&!prior.equals(d))invalid("ORIGINAL_DISPATCH_CHANGED");}}}
        else refreshed=control.load(reference);
        if(resumeOriginalsV1){refreshReuse();refreshed=withReuse(refreshed);}
        var added = WorkflowPlan.refresh(reference, plan, steps, refreshed);
        plan = refreshed;
        steps.putAll(added);
        materializedDuringDispatch |= !added.isEmpty();
        for (var step : added.values()) if (step.receiptId() != null && !reused.containsKey(step.stepId()) && (!waitingV2||step.status()!=State.RUNNING)) receipts.put(step.stepId(),
            new Receipt(step.stepId(), step.inputSha256(), step.leaseGeneration(), step.status(), step.receiptId(), step.waitingItemId(), step.outputs()));
    }
    private TaskRunReuseProtocolV1.InventoryV1 refreshReuse(){
        var found=reuseControl.reuseInventory(reference);found.require(reference);if(assemblyReuse!=null&&!assemblyReuse.equals(found.assembly()))invalid("ASSEMBLY_REUSE_CHANGED");if(found.assembly()!=null&&(assemblyCommand!=null||assemblyActive!=null||assemblyCommit!=null))invalid("ASSEMBLY_REUSE_EXECUTION_CONFLICT");assemblyReuse=found.assembly();var current=new LinkedHashMap<String,TaskRunReuseProtocolV1.ReusedV1>();for(var value:found.reused()){if(active.containsKey(value.stepId())||recovering.containsKey(value.stepId())||receipts.containsKey(value.stepId()))invalid("REUSE_EXECUTION_CONFLICT");current.put(value.stepId(),value);}for(var old:reused.values())if(!old.equals(current.get(old.stepId())))invalid("REUSE_ORIGINAL_CHANGED");reused.clear();reused.putAll(current);return found;
    }
    private Plan withReuse(Plan actual){
        var values=new ArrayList<>(actual.steps());var present=new java.util.HashSet<String>();values.forEach(s->present.add(s.stepId()));for(var value:reused.values())if("N02".equals(value.stageKind())){var planned=actual.plannedSteps().stream().filter(s->s.stepId().equals(value.stepId())).findFirst().orElseThrow(()->new IllegalArgumentException("REUSE_GRAPH_MISMATCH"));if(!present.add(value.stepId()))invalid("REUSE_ORDINARY_STEP_CONFLICT");values.add(value.projection(planned));}
        return new Plan(actual.inputSha256(),actual.planRevision(),actual.runStatus(),actual.cancellationCommandId(),actual.completedReceipt(),values,actual.plannedSteps(),actual.graphReceiptId(),actual.graphSha256(),actual.requiredStages(),actual.expansions(),actual.unmaterializedFailures());
    }
    private boolean fieldSatisfied(){return stageCommits.containsKey("N03")&&stageCommits.get("N03").satisfied()||reused.values().stream().anyMatch(r->"N03".equals(r.stageKind()));}
    private boolean writersSatisfied(){return writerInventory!=null&&!writerInventory.assigned().isEmpty()&&writerInventory.assigned().stream().mapToInt(s->s.chapterIds().size()).sum()==writerInventory.requiredChapterIds().size()&&writerInventory.assigned().stream().allMatch(s->writerInventory.commits().stream().anyMatch(c->c.original().command().stepId().equals(s.stepId()))||reusedStage("N04",s.stepId(),s.inputSha256()));}
    private boolean verifiersSatisfied(){return verifierInventory!=null&&!verifierInventory.assigned().isEmpty()&&verifierInventory.assigned().stream().mapToInt(s->s.chapterIds().size()).sum()==verifierInventory.requiredChapterIds().size()&&verifierInventory.assigned().stream().allMatch(s->verifierInventory.commits().stream().anyMatch(c->c.review().passed()&&c.original().command().stepId().equals(s.stepId()))||reusedStage("N05",s.stepId(),s.inputSha256()));}
    private boolean reusedStage(String kind,String step,String input){var value=reused.get(step);return value!=null&&kind.equals(value.stageKind())&&input.equals(value.inputSha256());}
    private void pollRecovered(){
        if(recovering.isEmpty()&&recoveringStages.isEmpty()||Workflow.currentTimeMillis()<nextRecoveryPoll)return;
        nextRecoveryPoll=Math.addExact(Workflow.currentTimeMillis(),deployment.recoveryPollMillis());boolean changed=false;
        for(var entry:List.copyOf(recovering.entrySet())){
            if(Workflow.currentTimeMillis()>=reference.deadlineEpochMillis()||cancelRequested||!pendingCommands.isEmpty())break;
            var d=entry.getValue();var result=recoveryControl.recoverStep(reference,d);result.require(d);
            if("PENDING".equals(result.state())){
                if(failedActivities.contains(entry.getKey()))try{var failure=control.failure(reference,d,"ACTIVITY_FAILURE");accept(failure);recovering.remove(entry.getKey());failedActivities.remove(entry.getKey());changed=true;}catch(io.temporal.failure.ActivityFailure notYetProved){/* No terminal fact: remain original-query-only. */}
                continue;
            }
            Receipt receipt=result.receipt();if("PRODUCED".equals(result.state())){var outcome=result.outcome();receipt="WAITING_CONTROL".equals(outcome.kind())?waitingControl.commitWaiting(reference,d,outcome.waiting()):control.commit(reference,d,outcome.completion());}
            accept(receipt);recovering.remove(entry.getKey());failedActivities.remove(entry.getKey());changed=true;
        }
        for(var entry:List.copyOf(recoveringStages.entrySet())){
            if(Workflow.currentTimeMillis()>=reference.deadlineEpochMillis()||cancelRequested||!pendingCommands.isEmpty())break;
            var result=stageControl.recoverStage(reference,entry.getKey(),entry.getValue());result.require(reference,entry.getKey(),entry.getValue());
            if("PENDING".equals(result.state()))continue;
            var receipt=result.receipt();if("PRODUCED".equals(result.state()))receipt=stageControl.commitStage(reference,result.completion());
            acceptStage(receipt,entry.getKey(),entry.getValue());recoveringStages.remove(entry.getKey());changed=true;
        }
        if(changed)refreshMaterialized();
    }
    private void acceptStageInventory(TaskRunRecoveryProtocolV2.RunPlanV2 loaded){
        loaded.require(reference);var originals=new LinkedHashMap<String,TaskRunStageProtocolV2.StageCommandV2>();loaded.stageCommands().forEach(c->originals.put(c.stageKind(),c));
        for(var prior:stageCommands.values())if(!prior.equals(originals.get(prior.stageKind())))invalid("STAGE_COMMAND_CHANGED");
        for(var command:originals.values()){
            var active=activeStages.get(command.stageKind());if(active!=null&&!active.commandId().equals(command.commandId()))invalid("STAGE_COMMAND_CHANGED");
            var recovery=recoveringStages.get(command.stageKind());if(recovery!=null&&!recovery.equals(command.commandId()))invalid("STAGE_COMMAND_CHANGED");
            stageCommands.put(command.stageKind(),command);
        }
        var commits=new LinkedHashMap<String,TaskRunStageProtocolV2.StageCommitV2>();loaded.stageCommits().forEach(c->commits.put(c.stageKind(),c));
        for(var prior:stageCommits.values())if(!prior.equals(commits.get(prior.stageKind())))invalid("STAGE_COMMIT_CHANGED");
        for(var commit:commits.values()){stageCommands.get(commit.stageKind()).require(commit.completion());stageCommits.put(commit.stageKind(),commit);recoveringStages.remove(commit.stageKind());}
        for(var command:stageCommands.values())if(!stageCommits.containsKey(command.stageKind())&&!activeStages.containsKey(command.stageKind()))recoveringStages.putIfAbsent(command.stageKind(),command.commandId());
    }
    private void dispatchStage(){
        if(resumeOriginalsV1){refreshReuse();if(reused.values().stream().anyMatch(r->"N03".equals(r.stageKind())))return;}
        if(!active.isEmpty()||!recovering.isEmpty()||!activeStages.isEmpty()||!recoveringStages.isEmpty()||!undispatched.isEmpty()||Workflow.currentTimeMillis()>=reference.deadlineEpochMillis())return;
        if(plan.plannedSteps().stream().anyMatch(n->!steps.containsKey(n.stepId())||steps.get(n.stepId()).status()!=State.SUCCEEDED))return;
        var obligation=plan.requiredStages().stream().filter(s->"N03".equals(s.stageKind())&&s.disposition()==StageDisposition.PENDING).findFirst();
        // N04 is a separately versioned branch; N05/N06 remain unfulfilled here. Keep obligations visible
        // and unfulfilled rather than inventing an artifact or overall success.
        if(obligation.isEmpty()||stageCommits.containsKey("N03")||stageCommands.containsKey("N03")||plan.plannedSteps().size()+stageCommands.size()>=reference.maximumSteps())return;
        String command=Workflow.randomUUID().toString();activeStages.put("N03",new StageRunning(command,cancellable(()->{
            var completion=stageExecution.executeStage(reference,"N03",command);completion.require(reference,"N03",command);return stageControl.commitStage(reference,completion);
        })));
    }
    private void harvestStages(){
        boolean changed=false;for(var entry:List.copyOf(activeStages.entrySet())){
            var running=entry.getValue();if(!running.promise().isCompleted())continue;
            if(running.promise().getFailure()!=null)recoveringStages.put(entry.getKey(),running.commandId());
            else{acceptStage(running.promise().get(),entry.getKey(),running.commandId());changed=true;}
            activeStages.remove(entry.getKey());
        }
        if(changed)refreshMaterialized();
    }
    private void acceptStage(TaskRunStageProtocolV2.StageCommitV2 receipt,String stage,String command){
        receipt.completion().require(reference,stage,command);receipt.require(reference,plan);var prior=stageCommits.putIfAbsent(stage,receipt);if(prior!=null&&!prior.equals(receipt))invalid("STAGE_COMMIT_CHANGED");
        var original=stageCommands.get(stage);if(original!=null)original.require(receipt.completion());
    }
    private boolean stagesSatisfied(){
        return plan.requiredStages().stream().allMatch(s->s.disposition()==StageDisposition.SUCCEEDED
            || "N03".equals(s.stageKind())&&reused.values().stream().anyMatch(r->"N03".equals(r.stageKind()))
            || writerV1&&"N04".equals(s.stageKind())&&writerInventory!=null&&writersSatisfied()
            || assemblyV1&&"N06".equals(s.stageKind())&&(assemblyReuse!=null||assemblyCommit!=null&&assemblyCommit.satisfied())
            || verifierV1&&"N05".equals(s.stageKind())&&verifierInventory!=null&&verifiersSatisfied()
            || stageCommits.values().stream().anyMatch(c->c.stageKind().equals(s.stageKind())&&c.obligationSha256().equals(s.obligationSha256())&&c.satisfied()));
    }
    private void advanceAssembly(){
        if(assemblyReuse!=null||assemblyCommit!=null||assemblyActive!=null||verifierInventory==null||!verifiersSatisfied()||Workflow.currentTimeMillis()<nextAssemblyPoll||Workflow.currentTimeMillis()>=reference.deadlineEpochMillis()||plan.requiredStages().stream().noneMatch(s->"N06".equals(s.stageKind())))return;
        nextAssemblyPoll=Math.addExact(Workflow.currentTimeMillis(),deployment.recoveryPollMillis());
        if(assemblyReuseUncertain){refreshReuse();return;}
        if(resumeOriginalsV1&&assemblyCommand==null&&!assemblyReuseAttempted){assemblyReuseAttempted=true;try{reusePreparation.reuseAssembly(reference);}catch(io.temporal.failure.ActivityFailure uncertain){assemblyReuseUncertain=true;/* Unknown completion never grants a replacement assembly dispatch. */}refreshReuse();if(assemblyReuse!=null||assemblyReuseUncertain)return;}
        if(assemblyCommand==null){try{var prepared=assemblyControl.prepare(reference);prepared.require(reference,plan);assemblyCommand=new TaskCandidateAssemblyStageProtocolV1.CommandV1(prepared,Workflow.randomUUID().toString());}catch(io.temporal.failure.ActivityFailure unavailable){return;}}
        var command=assemblyCommand;
        if(assemblyRecovering){try{var found=assemblyControl.recover(reference,command);found.require(reference,command);if(found.commit()!=null){acceptAssembly(found.commit());assemblyRecovering=false;}else if(found.produced()!=null){assemblyActive=cancellable(()->assemblyDispatch.commit(reference,found.produced()));assemblyRecovering=false;}}catch(io.temporal.failure.ActivityFailure unavailable){/* Original lookup only; never redispatch an ambiguous write. */}return;}
        assemblyActive=cancellable(()->{var produced=assemblyDispatch.dispatch(reference,command);produced.require(reference);if(!command.equals(produced.command()))invalid("ASSEMBLY_COMMAND_MISMATCH");return assemblyDispatch.commit(reference,produced);});
    }
    private void harvestAssembly(){if(assemblyActive==null||!assemblyActive.isCompleted())return;if(assemblyActive.getFailure()!=null)assemblyRecovering=true;else acceptAssembly(assemblyActive.get());assemblyActive=null;}
    private void acceptAssembly(TaskCandidateAssemblyStageProtocolV1.CommitV1 value){value.require(reference);value.produced().command().prepared().require(reference,plan);if(!value.produced().command().equals(assemblyCommand)||assemblyCommit!=null&&!assemblyCommit.equals(value))invalid("ASSEMBLY_ORIGINAL_CHANGED");assemblyCommit=value;}
    private void advanceWriters(){
        if(Workflow.currentTimeMillis()>=reference.deadlineEpochMillis())return;
        if(Workflow.currentTimeMillis()>=nextWriterPoll){
            nextWriterPoll=Math.addExact(Workflow.currentTimeMillis(),deployment.recoveryPollMillis());
            var loaded=writerControl.inventory(reference);loaded.require(reference,plan);
            if(!writerPreparationAttempted&&fieldSatisfied()&&loaded.assigned().size()<loaded.requiredChapterIds().size()){writerPreparationAttempted=true;try{loaded=writerDispatch.prepare(reference);loaded.require(reference,plan);}catch(io.temporal.failure.ActivityFailure uncertain){/* Query inventory only after an ambiguous producer outcome. */}}
            if(writerInventory!=null){
                if(!loaded.requiredChapterIds().equals(writerInventory.requiredChapterIds()))invalid("WRITER_ASSIGNMENT_SCOPE_CHANGED");
                if(!loaded.assigned().containsAll(writerInventory.assigned())||!loaded.commands().containsAll(writerInventory.commands())||!loaded.dispatched().containsAll(writerInventory.dispatched())||!loaded.commits().containsAll(writerInventory.commits()))invalid("WRITER_ORIGINAL_CHANGED");
                if(writersSatisfied()&&!loaded.equals(writerInventory))invalid("WRITER_COMPLETED_ASSIGNMENT_CHANGED");
            }
            writerInventory=loaded;
            var done=new java.util.HashSet<String>();loaded.commits().forEach(c->done.add(c.original().command().stepId()));
            for(var c:loaded.commands())if(!done.contains(c.stepId())&&!writerActive.containsKey(c.stepId()))writerRecovering.putIfAbsent(c.stepId(),c);
            for(var e:List.copyOf(writerRecovering.entrySet())){
                if(done.contains(e.getKey())){writerRecovering.remove(e.getKey());continue;}
                var result=writerControl.recover(reference,e.getValue());result.require(reference,e.getValue());
                if("PENDING".equals(result.state()))continue;
                var receipt=result.receipt();if("PRODUCED".equals(result.state()))receipt=writerControl.commit(reference,result.original(),result.candidate());
                receipt.require(reference);if(!receipt.original().command().equals(e.getValue()))invalid("WRITER_COMMAND_CHANGED");writerRecovering.remove(e.getKey());nextWriterPoll=0;
            }
        }
        if(writerInventory==null||!active.isEmpty()||!recovering.isEmpty()||!activeStages.isEmpty()||!recoveringStages.isEmpty()||!undispatched.isEmpty())return;
        if(plan.plannedSteps().stream().anyMatch(n->!steps.containsKey(n.stepId())||steps.get(n.stepId()).status()!=State.SUCCEEDED)||!fieldSatisfied())return;
        if(resumeOriginalsV1)refreshReuse();
        for(var ready:writerInventory.ready()){
            if(reused.containsKey(ready.stepId()))continue;
            if(writerActive.size()+writerRecovering.size()>=reference.maximumParallelSteps()||!pendingCommands.isEmpty())return;
            if(writerActive.containsKey(ready.stepId())||writerRecovering.containsKey(ready.stepId()))continue;
            var command=new TaskSectionWriterStageProtocolV1.CommandV1(reference.runId(),ready.stepId(),ready.inputSha256(),Workflow.randomUUID().toString(),ready.chapterIds());
            writerActive.put(ready.stepId(),new WriterRunning(command,cancellable(()->{
                var original=writerDispatch.dispatch(reference,command);if(!command.equals(original.command()))invalid("WRITER_DISPATCH_CHANGED");
                if(cancelRequested||Workflow.currentTimeMillis()>=reference.deadlineEpochMillis())invalid("WRITER_DISPATCH_CLOSED");
                var candidate=writerExecution.execute(reference,original.dispatch());candidate.require(reference,original);
                return writerControl.commit(reference,original,candidate);
            })));
        }
    }
    private void harvestWriters(){for(var entry:List.copyOf(writerActive.entrySet())){var running=entry.getValue();if(!running.promise().isCompleted())continue;
        if(running.promise().getFailure()!=null)writerRecovering.put(entry.getKey(),running.command());
        else {var receipt=running.promise().get();receipt.require(reference);if(!receipt.original().command().equals(running.command()))invalid("WRITER_COMMAND_CHANGED");writerRecovering.put(entry.getKey(),running.command());}
        writerActive.remove(entry.getKey());nextWriterPoll=0;
    }}
    private record WriterRunning(TaskSectionWriterStageProtocolV1.CommandV1 command,Promise<TaskSectionWriterStageProtocolV1.CommitV1> promise){}
    private void advanceVerifiers(){
        if(Workflow.currentTimeMillis()>=reference.deadlineEpochMillis()||!writerV1||writerInventory==null||!writersSatisfied())return;
        if(Workflow.currentTimeMillis()>=nextVerifierPoll){
            nextVerifierPoll=Math.addExact(Workflow.currentTimeMillis(),deployment.recoveryPollMillis());
            var loaded=verifierControl.inventory(reference);loaded.require(reference,plan);
            if(!verifierPreparationAttempted&&writersSatisfied()&&loaded.assigned().size()<loaded.requiredChapterIds().size()){verifierPreparationAttempted=true;try{loaded=verifierDispatch.prepare(reference);loaded.require(reference,plan);}catch(io.temporal.failure.ActivityFailure uncertain){/* Query inventory only after an ambiguous producer outcome. */}}
            if(verifierInventory!=null){
                if(!loaded.requiredChapterIds().equals(verifierInventory.requiredChapterIds()))invalid("VERIFIER_ASSIGNMENT_SCOPE_CHANGED");
                if(!loaded.assigned().containsAll(verifierInventory.assigned())||!loaded.commands().containsAll(verifierInventory.commands())||!loaded.dispatched().containsAll(verifierInventory.dispatched())||!loaded.commits().containsAll(verifierInventory.commits()))invalid("VERIFIER_ORIGINAL_CHANGED");
                if(verifiersSatisfied()&&!loaded.equals(verifierInventory))invalid("VERIFIER_COMPLETED_ASSIGNMENT_CHANGED");
            }
            if((long)plan.plannedSteps().size()+writerInventory.assigned().size()+loaded.assigned().size()>reference.maximumSteps())invalid("VERIFIER_STEP_CAPACITY");
            verifierInventory=loaded;
            var done=new java.util.HashSet<String>();loaded.commits().forEach(c->done.add(c.original().command().stepId()));
            for(var c:loaded.commands())if(!done.contains(c.stepId())&&!verifierActive.containsKey(c.stepId()))verifierRecovering.putIfAbsent(c.stepId(),c);
            for(var e:List.copyOf(verifierRecovering.entrySet())){
                if(done.contains(e.getKey())){verifierRecovering.remove(e.getKey());continue;}
                var result=verifierControl.recover(reference,e.getValue());result.require(reference,e.getValue());
                if("PENDING".equals(result.state()))continue;
                var receipt=result.receipt();if("PRODUCED".equals(result.state()))receipt=verifierControl.commit(reference,result.original(),result.review());
                receipt.require(reference);if(!receipt.original().command().equals(e.getValue()))invalid("VERIFIER_COMMAND_CHANGED");verifierRecovering.remove(e.getKey());nextVerifierPoll=0;
            }
        }
        if(verifierInventory==null||!active.isEmpty()||!recovering.isEmpty()||!activeStages.isEmpty()||!recoveringStages.isEmpty()||!undispatched.isEmpty())return;
        if(plan.plannedSteps().stream().anyMatch(n->!steps.containsKey(n.stepId())||steps.get(n.stepId()).status()!=State.SUCCEEDED)||!fieldSatisfied())return;
        if(resumeOriginalsV1)refreshReuse();
        for(var ready:verifierInventory.ready()){
            if(reused.containsKey(ready.stepId()))continue;
            if(verifierActive.size()+verifierRecovering.size()+writerActive.size()+writerRecovering.size()>=reference.maximumParallelSteps()||!pendingCommands.isEmpty())return;
            if(verifierActive.containsKey(ready.stepId())||verifierRecovering.containsKey(ready.stepId()))continue;
            var command=new TaskSourceVerifierStageProtocolV1.CommandV1(reference.runId(),ready.stepId(),ready.inputSha256(),Workflow.randomUUID().toString(),ready.chapterIds());
            verifierActive.put(ready.stepId(),new VerifierRunning(command,cancellable(()->{
                var original=verifierDispatch.dispatch(reference,command);if(!command.equals(original.command()))invalid("VERIFIER_DISPATCH_CHANGED");
                if(cancelRequested||Workflow.currentTimeMillis()>=reference.deadlineEpochMillis())invalid("VERIFIER_DISPATCH_CLOSED");
                var review=verifierExecution.execute(reference,original.dispatch());review.require(reference,original);
                return verifierControl.commit(reference,original,review);
            })));
        }
    }
    private void harvestVerifiers(){for(var entry:List.copyOf(verifierActive.entrySet())){var running=entry.getValue();if(!running.promise().isCompleted())continue;
        if(running.promise().getFailure()!=null)verifierRecovering.put(entry.getKey(),running.command());
        else {var receipt=running.promise().get();receipt.require(reference);if(!receipt.original().command().equals(running.command()))invalid("VERIFIER_COMMAND_CHANGED");verifierRecovering.put(entry.getKey(),running.command());}
        verifierActive.remove(entry.getKey());nextVerifierPoll=0;
    }}
    private record VerifierRunning(TaskSourceVerifierStageProtocolV1.CommandV1 command,Promise<TaskSourceVerifierStageProtocolV1.CommitV1> promise){}
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
    private boolean completed() { return assemblyActive!=null&&assemblyActive.isCompleted() || verifierActive.values().stream().anyMatch(v->v.promise().isCompleted()) || writerActive.values().stream().anyMatch(v->v.promise().isCompleted()) || active.values().stream().anyMatch(value -> value.promise().isCompleted()) || activeStages.values().stream().anyMatch(value -> value.promise().isCompleted()); }
    private boolean hasFailedAncestor(String step){var graph=new LinkedHashMap<String,PlannedStep>();plan.plannedSteps().forEach(n->graph.put(n.stepId(),n));var todo=new ArrayDeque<>(graph.get(step).dependsOnStepIds());var seen=new java.util.HashSet<String>();while(!todo.isEmpty()){String id=todo.removeFirst();if(!seen.add(id))continue;var s=steps.get(id);if(s!=null&&s.status()==State.FAILED&&s.required())return true;var n=graph.get(id);if(n==null)invalid("GRAPH_NODE_MISSING");todo.addAll(n.dependsOnStepIds());}return false;}
    private boolean terminal() {
        if(assemblyV1&&(assemblyActive!=null||assemblyRecovering))return false;
        if(verifierV1&&(!verifierActive.isEmpty()||!verifierRecovering.isEmpty()))return false;
        if(writerV1&&(!writerActive.isEmpty()||!writerRecovering.isEmpty()))return false;
        if(waitingV2&&(!recovering.isEmpty()||!activeStages.isEmpty()||!recoveringStages.isEmpty()))return false;
        if(waitingV2&&steps.values().stream().anyMatch(s->s.required()&&s.status()==State.FAILED))return plan.plannedSteps().stream().allMatch(n->undispatched.containsKey(n.stepId())||steps.containsKey(n.stepId())&&java.util.Set.of(State.SUCCEEDED,State.FAILED,State.CANCELLED).contains(steps.get(n.stepId()).status()));
        if(waitingV2)return plan.plannedSteps().stream().allMatch(n->steps.containsKey(n.stepId()))&&steps.values().stream().allMatch(s->s.status()==State.SUCCEEDED)&&stagesSatisfied();
        return WorkflowPlan.terminal(plan,steps);
    }
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
    @Override public View view() { return new View(reference == null ? null : reference.runId(), state, List.copyOf(receipts.values()), (assemblyActive!=null||assemblyRecovering?1:0)+active.size()+recovering.size()+activeStages.size()+recoveringStages.size()+writerActive.size()+writerRecovering.size()+verifierActive.size()+verifierRecovering.size(), consumed.size(),
        plan == null ? List.of() : plan.plannedSteps().stream().map(PlannedStep::stepId).filter(id -> !steps.containsKey(id)).toList(),
        plan == null ? null : plan.graphReceiptId(), plan == null ? null : plan.graphSha256(),
        plan == null ? List.of() : plan.requiredStages().stream().filter(stage -> stage.fulfillmentReceiptId() == null && !("N03".equals(stage.stageKind())&&reused.values().stream().anyMatch(r->"N03".equals(r.stageKind()))) && !(assemblyV1&&"N06".equals(stage.stageKind())&&(assemblyReuse!=null||assemblyCommit!=null&&assemblyCommit.satisfied())) && !(writerV1&&"N04".equals(stage.stageKind())&&writerInventory!=null&&writersSatisfied()) && !(verifierV1&&"N05".equals(stage.stageKind())&&verifierInventory!=null&&verifiersSatisfied()) && (!waitingV2 || !stageCommits.containsKey(stage.stageKind()) || !stageCommits.get(stage.stageKind()).satisfied())).toList()); }
    @Override public CommandReceipt commandReceipt(String commandId) {
        com.ruoyi.medical.contracts.WireValues.uuid(commandId); return commandReceipts.get(commandId);
    }
    private static void invalid(String code) { throw ApplicationFailure.newNonRetryableFailure(code, code); }
    private record Running(Dispatch dispatch, Promise<Receipt> promise) { }
    private record StageRunning(String commandId,Promise<TaskRunStageProtocolV2.StageCommitV2> promise){}
}
