package com.ruoyi.medical.workflow;

import java.util.*;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Validates receipt-backed materialization data; the loader remains responsible for
 * authenticating each original producer/disposition receipt against persistent authority. */
public final class WorkflowPlan {
    private WorkflowPlan() { }
    public static Map<String,Step> require(Reference reference,Plan plan){return validate(reference,plan,false);}
    private static Map<String,Step> validate(Reference reference,Plan plan,boolean refreshing){
        if(!reference.inputSha256().equals(plan.inputSha256())||plan.plannedSteps().isEmpty()||plan.plannedSteps().size()>reference.maximumSteps())invalid();
        if(plan.completedReceipt()!=null&&!reference.runId().equals(plan.completedReceipt().runId()))invalid();
        var planned=new LinkedHashMap<String,PlannedStep>();var graph=new DefaultDirectedGraph<String,DefaultEdge>(DefaultEdge.class);
        for(var node:plan.plannedSteps()){if(planned.putIfAbsent(node.stepId(),node)!=null)invalid();graph.addVertex(node.stepId());}
        for(var node:plan.plannedSteps())for(String dependency:node.dependsOnStepIds())if(!planned.containsKey(dependency)||dependency.equals(node.stepId())||graph.addEdge(dependency,node.stepId())==null)invalid();
        if(new CycleDetector<>(graph).detectCycles()||planned.values().stream().noneMatch(PlannedStep::required))invalid();
        var steps=new LinkedHashMap<String,Step>();
        for(var step:plan.steps()){
            if((!refreshing&&step.status()==State.RUNNING)||step.status()==State.CANCEL_REQUESTED)invalid();
            var node=planned.get(step.stepId());if(node==null||node.required()!=step.required()||!node.dependsOnStepIds().equals(step.dependsOnStepIds())||steps.putIfAbsent(step.stepId(),step)!=null)invalid();
        }
        var stages=new LinkedHashMap<String,RequiredStage>();for(var stage:plan.requiredStages())if(stages.putIfAbsent(stage.stageKind(),stage)!=null)invalid();
        var expansionIds=new HashSet<String>();var expanded=new HashSet<String>();var expandedByStage=new HashMap<String,List<String>>();
        for(var expansion:plan.expansions()){
            var stage=stages.get(expansion.stageKind());if(stage==null||!stage.obligationSha256().equals(expansion.obligationSha256())||!expansionIds.add(expansion.receiptId()))invalid();
            for(var node:expansion.nodes()){
                if(!node.equals(planned.get(node.stepId()))||!expanded.add(node.stepId()))invalid();
                expandedByStage.computeIfAbsent(expansion.stageKind(),k->new ArrayList<>()).add(node.stepId());
            }
        }
        var disposed=new HashSet<String>();var dispositionReceipts=new HashSet<String>();
        for(var failure:plan.unmaterializedFailures()){
            if(!planned.containsKey(failure.stepId())||steps.containsKey(failure.stepId())||!disposed.add(failure.stepId())||!dispositionReceipts.add(failure.receiptId()))invalid();
            var cause=failedCause(steps,failure.causeReceiptId());
            if(cause==null||!ancestor(planned,failure.stepId(),cause.stepId()))invalid();
        }
        for(var stage:stages.values()){
            switch(stage.disposition()){
                case PENDING->{if(plan.runStatus()==State.SUCCEEDED)invalid();}
                case SUCCEEDED->{var assigned=expandedByStage.get(stage.stageKind());if(assigned==null||assigned.isEmpty()||assigned.stream().anyMatch(id->!steps.containsKey(id)||steps.get(id).status()!=State.SUCCEEDED))invalid();}
                case DEPENDENCY_FAILED->{if(failedCause(steps,stage.causeReceiptId())==null||plan.runStatus()==State.SUCCEEDED)invalid();}
                case CANCELLED->{if(plan.cancellationCommandId()==null||!plan.cancellationCommandId().equals(stage.causeReceiptId())||plan.runStatus()==State.SUCCEEDED)invalid();}
            }
        }
        if((!stages.isEmpty()||!plan.expansions().isEmpty()||!disposed.isEmpty()||steps.size()<planned.size())&&(plan.graphReceiptId()==null||plan.graphSha256()==null))invalid();
        if(plan.runStatus()==State.SUCCEEDED&&(steps.size()!=planned.size()||!disposed.isEmpty()||steps.values().stream().anyMatch(s->s.required()&&s.status()!=State.SUCCEEDED)))invalid();
        return steps;
    }
    private static Step failedCause(Map<String,Step> steps,String receipt){return steps.values().stream().filter(s->s.required()&&s.status()==State.FAILED&&Objects.equals(s.receiptId(),receipt)).findFirst().orElse(null);}
    private static boolean ancestor(Map<String,PlannedStep> nodes,String child,String target){var pending=new ArrayDeque<String>();pending.addAll(nodes.get(child).dependsOnStepIds());var seen=new HashSet<String>();while(!pending.isEmpty()){String at=pending.removeFirst();if(!seen.add(at))continue;if(at.equals(target))return true;pending.addAll(nodes.get(at).dependsOnStepIds());}return false;}
    /** Appends only nodes assigned by new immutable receipts under existing obligations. */
    public static Map<String,Step> refresh(Reference reference,Plan original,Map<String,Step> existing,Plan next){
        var loaded=validate(reference,next,true);
        if(!original.planRevision().equals(next.planRevision())||!Objects.equals(original.graphReceiptId(),next.graphReceiptId())||!Objects.equals(original.graphSha256(),next.graphSha256()))invalid();
        if(original.requiredStages().size()!=next.requiredStages().size())invalid();
        for(int i=0;i<original.requiredStages().size();i++){
            var old=original.requiredStages().get(i);var fresh=next.requiredStages().get(i);
            if(!old.stageKind().equals(fresh.stageKind())||!old.obligationSha256().equals(fresh.obligationSha256())||old.disposition()!=StageDisposition.PENDING&&!old.equals(fresh))invalid();
        }
        if(next.expansions().size()<original.expansions().size()||!next.expansions().subList(0,original.expansions().size()).equals(original.expansions())
            ||next.unmaterializedFailures().size()<original.unmaterializedFailures().size()||!next.unmaterializedFailures().subList(0,original.unmaterializedFailures().size()).equals(original.unmaterializedFailures()))invalid();
        var oldNodes=new LinkedHashMap<String,PlannedStep>();original.plannedSteps().forEach(n->oldNodes.put(n.stepId(),n));
        var nextNodes=new LinkedHashMap<String,PlannedStep>();next.plannedSteps().forEach(n->nextNodes.put(n.stepId(),n));
        for(var old:oldNodes.values())if(!old.equals(nextNodes.get(old.stepId())))invalid();
        var newlyAssigned=new HashSet<String>();for(var expansion:next.expansions().subList(original.expansions().size(),next.expansions().size()))for(var node:expansion.nodes())if(oldNodes.containsKey(node.stepId())||!newlyAssigned.add(node.stepId()))invalid();
        var addedIds=new HashSet<>(nextNodes.keySet());addedIds.removeAll(oldNodes.keySet());if(!addedIds.equals(newlyAssigned))invalid();
        for(var step:existing.values()){var current=loaded.get(step.stepId());if(current==null||!step.inputSha256().equals(current.inputSha256())||step.required()!=current.required()||!step.dependsOnStepIds().equals(current.dependsOnStepIds()))invalid();}
        var added=new LinkedHashMap<String,Step>();for(var step:loaded.values())if(!existing.containsKey(step.stepId())){if(step.status()==State.RUNNING)invalid();added.put(step.stepId(),step);}return added;
    }
    static boolean terminal(Plan plan,Map<String,Step> steps){
        if(plan.requiredStages().stream().anyMatch(s->s.disposition()==StageDisposition.PENDING))return false;
        var disposed=plan.unmaterializedFailures().stream().map(UnmaterializedFailure::stepId).collect(java.util.stream.Collectors.toSet());
        if(plan.plannedSteps().stream().anyMatch(n->!steps.containsKey(n.stepId())&&!disposed.contains(n.stepId())))return false;
        return steps.values().stream().allMatch(s->s.status()==State.SUCCEEDED||s.status()==State.FAILED||s.status()==State.CANCELLED);
    }
    static boolean failed(Plan plan,Map<String,Step> steps){return !plan.unmaterializedFailures().isEmpty()||plan.requiredStages().stream().anyMatch(s->s.disposition()==StageDisposition.DEPENDENCY_FAILED)||steps.values().stream().anyMatch(s->s.required()&&s.status()!=State.SUCCEEDED);}
    private static void invalid(){throw new IllegalArgumentException("WORKFLOW_PLAN_INVALID");}
}
