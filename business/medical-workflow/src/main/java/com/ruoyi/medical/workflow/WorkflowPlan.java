package com.ruoyi.medical.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public final class WorkflowPlan {
    private WorkflowPlan() { }
    public static Map<String,WorkflowProtocol.Step> require(WorkflowProtocol.Reference reference, WorkflowProtocol.Plan plan) {
        if (!reference.inputSha256().equals(plan.inputSha256()) || plan.steps().isEmpty() || plan.steps().size() > reference.maximumSteps()) invalid();
        if (plan.completedReceipt() != null && !reference.runId().equals(plan.completedReceipt().runId())) invalid();
        var steps = new LinkedHashMap<String,WorkflowProtocol.Step>();
        var graph = new DefaultDirectedGraph<String,DefaultEdge>(DefaultEdge.class);
        for (var step : plan.steps()) {
            if (step.status() == WorkflowProtocol.State.RUNNING || step.status() == WorkflowProtocol.State.CANCEL_REQUESTED) invalid();
            if (steps.putIfAbsent(step.stepId(), step) != null) invalid(); graph.addVertex(step.stepId());
        }
        for (var step : plan.steps()) for (String dependency : step.dependsOnStepIds()) {
            if (!steps.containsKey(dependency) || dependency.equals(step.stepId()) || graph.addEdge(dependency, step.stepId()) == null) invalid();
        }
        if (new CycleDetector<>(graph).detectCycles() || plan.steps().stream().noneMatch(WorkflowProtocol.Step::required)) invalid();
        return steps;
    }
    private static void invalid() { throw new IllegalArgumentException("WORKFLOW_PLAN_INVALID"); }
}
