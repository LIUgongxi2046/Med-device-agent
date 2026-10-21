package com.ruoyi.medical.workflow;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Explicit V2 in-flight inventory. Dispatch locators are never terminal receipts. */
public final class TaskRunRecoveryProtocolV2 {
    private TaskRunRecoveryProtocolV2(){}
    public record OriginalRunningV2(Dispatch dispatch,List<String> dependsOnStepIds,boolean required){
        public OriginalRunningV2{Objects.requireNonNull(dispatch);dependsOnStepIds=List.copyOf(dependsOnStepIds);dependsOnStepIds.forEach(com.ruoyi.medical.contracts.WireValues::uuid);}
        Step internalStep(){return new Step(dispatch.stepId(),dispatch.inputSha256(),dependsOnStepIds,required,State.RUNNING,dispatch.leaseGeneration(),dispatch.commandId(),null,List.of());}
    }
    public record UndispatchedFailureV2(String schemaVersion,String kind,String runId,String stepId,String graphId,String graphSha256,
            String inputId,String inputSha256,long leaseGeneration,String receiptId,String receiptSha256,String causeReceiptId){
        public UndispatchedFailureV2{version(schemaVersion);for(String id:List.of(runId,stepId,graphId,receiptId,causeReceiptId))com.ruoyi.medical.contracts.WireValues.uuid(id);com.ruoyi.medical.contracts.WireValues.hash(graphSha256);com.ruoyi.medical.contracts.WireValues.hash(receiptSha256);
            if(!Set.of("INPUT_READY","INPUT_UNMATERIALIZED").contains(kind)||leaseGeneration!=0)invalid();boolean ready=kind.equals("INPUT_READY");if(ready!=(inputId!=null)||ready!=(inputSha256!=null))invalid();if(ready){com.ruoyi.medical.contracts.WireValues.uuid(inputId);com.ruoyi.medical.contracts.WireValues.hash(inputSha256);}}
    }
    public record RunPlanV2(String schemaVersion,Plan settledPlan,List<OriginalRunningV2> inFlight,List<UndispatchedFailureV2> undispatchedFailures,List<TaskRunStageProtocolV2.StageCommitV2> stageCommits,List<TaskRunStageProtocolV2.StageCommandV2> stageCommands){
        public RunPlanV2(String schemaVersion,Plan settledPlan,List<OriginalRunningV2> inFlight){this(schemaVersion,settledPlan,inFlight,List.of(),List.of(),List.of());}
        public RunPlanV2(String schemaVersion,Plan settledPlan,List<OriginalRunningV2> inFlight,List<UndispatchedFailureV2> undispatchedFailures){this(schemaVersion,settledPlan,inFlight,undispatchedFailures,List.of(),List.of());}
        public RunPlanV2(String schemaVersion,Plan settledPlan,List<OriginalRunningV2> inFlight,List<UndispatchedFailureV2> undispatchedFailures,List<TaskRunStageProtocolV2.StageCommitV2> stageCommits){this(schemaVersion,settledPlan,inFlight,undispatchedFailures,stageCommits,List.of());}
        public RunPlanV2{version(schemaVersion);Objects.requireNonNull(settledPlan);inFlight=List.copyOf(inFlight);undispatchedFailures=List.copyOf(undispatchedFailures);stageCommits=List.copyOf(stageCommits);stageCommands=List.copyOf(stageCommands);}
        /** Validate the complete inventory before any activity can execute. */
        public Map<String,Step> require(Reference reference){
            if((long)settledPlan.plannedSteps().size()+stageCommands.size()>reference.maximumSteps())invalid();
            var commandKinds=new HashMap<String,TaskRunStageProtocolV2.StageCommandV2>();var commandIds=new HashSet<String>();for(var command:stageCommands){command.require(reference);if(commandKinds.put(command.stageKind(),command)!=null||!commandIds.add(command.commandId())||settledPlan.requiredStages().stream().noneMatch(s->s.stageKind().equals(command.stageKind())&&s.disposition()==StageDisposition.PENDING)||settledPlan.plannedSteps().stream().anyMatch(n->n.stepId().equals(command.stepId())))invalid();}
            var stageKinds=new HashSet<String>();var stageSteps=new HashSet<String>();for(var stage:stageCommits){stage.require(reference,settledPlan);var originalCommand=commandKinds.get(stage.stageKind());if(originalCommand==null)invalid();originalCommand.require(stage.completion());if(!stageKinds.add(stage.stageKind())||!stageSteps.add(stage.completion().stepId())||settledPlan.plannedSteps().stream().anyMatch(n->n.stepId().equals(stage.completion().stepId())))invalid();}
            Plan structural=settledPlan;if(settledPlan.runStatus()==State.SUCCEEDED&&!stageCommits.isEmpty()){
                if(!inFlight.isEmpty()||!undispatchedFailures.isEmpty()||!settledPlan.unmaterializedFailures().isEmpty()||settledPlan.completedReceipt()==null||settledPlan.completedReceipt().status()!=State.SUCCEEDED||settledPlan.steps().size()!=settledPlan.plannedSteps().size()||settledPlan.steps().stream().anyMatch(s->s.required()&&s.status()!=State.SUCCEEDED)||!stagesSatisfied(settledPlan,stageCommits))invalid();
                // V2 owns the typed obligation proof. This structural-only copy is
                // never serialized, returned as V1, or used to manufacture fulfillment.
                structural=new Plan(settledPlan.inputSha256(),settledPlan.planRevision(),State.RUNNING,null,null,settledPlan.steps(),settledPlan.plannedSteps(),settledPlan.graphReceiptId(),settledPlan.graphSha256(),settledPlan.requiredStages(),settledPlan.expansions(),settledPlan.unmaterializedFailures());
            }
            var result=new LinkedHashMap<>(WorkflowPlan.require(reference,structural));var nodes=new HashMap<String,PlannedStep>();settledPlan.plannedSteps().forEach(n->nodes.put(n.stepId(),n));
            if(inFlight.size()>reference.maximumParallelSteps()||settledPlan.completedReceipt()!=null&&!inFlight.isEmpty())invalid();var disposed=new HashSet<String>();settledPlan.unmaterializedFailures().forEach(f->disposed.add(f.stepId()));
            for(var running:inFlight){var d=running.dispatch();var node=nodes.get(d.stepId());if(!reference.runId().equals(d.runId())||node==null||node.required()!=running.required()||!node.dependsOnStepIds().equals(running.dependsOnStepIds())||disposed.contains(d.stepId())||result.putIfAbsent(d.stepId(),running.internalStep())!=null)invalid();}
            for(var failure:undispatchedFailures){var node=nodes.get(failure.stepId());if(node==null||!reference.runId().equals(failure.runId())||!Objects.equals(settledPlan.graphReceiptId(),failure.graphId())||!Objects.equals(settledPlan.graphSha256(),failure.graphSha256())||result.containsKey(failure.stepId())||!disposed.add(failure.stepId()))invalid();
                var cause=settledPlan.steps().stream().filter(v->v.status()==State.FAILED&&v.required()&&Objects.equals(v.receiptId(),failure.causeReceiptId())).findFirst().orElse(null);if(cause==null||!ancestor(nodes,failure.stepId(),cause.stepId()))invalid();}
            return Collections.unmodifiableMap(result);
        }
        /** Only for the existing graph refresher, which already distinguishes RUNNING.
         * This value never crosses the V1 load wire or creates a Receipt. */
        Plan combined(Reference reference){require(reference);var values=new ArrayList<>(settledPlan.steps());inFlight.forEach(v->values.add(v.internalStep()));return new Plan(settledPlan.inputSha256(),settledPlan.planRevision(),settledPlan.runStatus(),settledPlan.cancellationCommandId(),settledPlan.completedReceipt(),values,settledPlan.plannedSteps(),settledPlan.graphReceiptId(),settledPlan.graphSha256(),settledPlan.requiredStages(),settledPlan.expansions(),settledPlan.unmaterializedFailures());}
    }
    public static boolean stagesSatisfied(Plan plan,List<TaskRunStageProtocolV2.StageCommitV2> typed){return plan.requiredStages().stream().allMatch(s->s.disposition()==StageDisposition.SUCCEEDED||s.disposition()==StageDisposition.PENDING&&typed.stream().anyMatch(c->c.stageKind().equals(s.stageKind())&&c.obligationSha256().equals(s.obligationSha256())&&c.satisfied()));}
    public record StepRecoveryV2(String schemaVersion,String state,Dispatch dispatch,Receipt receipt,ProfessionalOutcomeV2 outcome){
        public StepRecoveryV2{version(schemaVersion);Objects.requireNonNull(dispatch);if(!Set.of("PENDING","COMMITTED","PRODUCED").contains(state))invalid();if((receipt!=null)!=state.equals("COMMITTED")||(outcome!=null)!=state.equals("PRODUCED"))invalid();if(receipt!=null){if(!dispatch.stepId().equals(receipt.stepId())||!dispatch.inputSha256().equals(receipt.inputSha256())||dispatch.leaseGeneration()!=receipt.leaseGeneration()||!Set.of(State.SUCCEEDED,State.WAITING_INPUT,State.FAILED,State.CANCELLED).contains(receipt.status()))invalid();}if(outcome!=null)outcome.require(dispatch);}
        public void require(Dispatch original){if(!dispatch.equals(original))invalid();}
    }
    private static boolean ancestor(Map<String,PlannedStep> nodes,String child,String target){var pending=new ArrayDeque<>(nodes.get(child).dependsOnStepIds());var seen=new HashSet<String>();while(!pending.isEmpty()){String id=pending.removeFirst();if(!seen.add(id))continue;if(id.equals(target))return true;var node=nodes.get(id);if(node==null)invalid();pending.addAll(node.dependsOnStepIds());}return false;}
    private static void version(String v){if(!"2.0.0".equals(v))invalid();}private static void invalid(){throw new IllegalArgumentException("TASK_RECOVERY_V2_INVALID");}
}
