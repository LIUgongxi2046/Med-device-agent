package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.stereotype.Component;

@Component
public final class TaskPlanPolicy {
    private final SchemaCatalogue shared;

    public TaskPlanPolicy() throws IOException, NoSuchAlgorithmException { shared = SchemaCatalogue.fromClasspath(); }

    Validation inspect(JsonNode content, String templateVersion, String profileRevision,
            List<String> templateNodeIds, Set<String> conditionIds, Set<String> completionRuleIds) throws IOException {
        WireValues.uuid(templateVersion); WireValues.uuid(profileRevision);
        var binding = TypedSchemaBindings.bind(shared, ArtifactBindings.PLAN_CANDIDATE, content);
        var candidate = binding.read();
        if (!templateVersion.equals(candidate.templateVersion()) || !profileRevision.equals(candidate.profileRevision()))
            throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
        var expected = distinct(templateNodeIds);
        if (expected.isEmpty() || !expected.equals(distinct(candidate.requirementNodeIds()))) invalid();
        var covered = new HashSet<String>();
        var sections = new LinkedHashMap<String, ArtifactProtocol.PlanSection>();
        var sectionDependencies = new LinkedHashMap<String, List<String>>();
        var owners = new ArrayList<String>();
        var completion = new ArrayList<String>();
        for (var section : candidate.sections()) {
            if (sections.putIfAbsent(section.sectionId(), section) != null) invalid();
            ProjectSchema.text(section.goal(), Integer.MAX_VALUE);
            var requirements = distinct(section.requirementNodeIds());
            if (requirements.isEmpty() || !expected.containsAll(requirements)) invalid();
            for (String requirement : requirements) if (!covered.add(requirement)) invalid();
            if (section.ownerId() == null) owners.add(section.sectionId());
            if (section.completionRuleIds().isEmpty()) completion.add(section.sectionId());
            requireRules(section.completionRuleIds(), completionRuleIds);
            sectionDependencies.put(section.sectionId(), section.dependsOnSectionIds());
        }
        if (!covered.equals(expected)) invalid();
        var questions = new LinkedHashMap<String, ArtifactProtocol.ResearchQuestion>();
        var questionDependencies = new LinkedHashMap<String, List<String>>();
        for (var question : candidate.questions()) {
            if (questions.putIfAbsent(question.questionKey(), question) != null) invalid();
            ProjectSchema.text(question.question(), Integer.MAX_VALUE);
            var section = sections.get(question.sectionId());
            if (section == null || !new HashSet<>(section.requirementNodeIds()).containsAll(distinct(question.requirementIds()))) invalid();
            if (question.requirementIds().isEmpty() || question.completionRuleIds().isEmpty()) completion.add(question.questionKey());
            requireRules(question.completionRuleIds(), completionRuleIds);
            questionDependencies.put(question.questionKey(), question.dependsOnQuestionKeys());
        }
        var pending = distinct(candidate.pendingConditionIds());
        if (!conditionIds.containsAll(pending)) invalid();
        return new Validation(candidate, binding.payload().canonicalJson(), DigestUtils.sha256Hex(binding.payload().canonicalBytes()),
                dependencyOrder(List.copyOf(sections.keySet()), sectionDependencies),
                dependencyOrder(List.copyOf(questions.keySet()), questionDependencies), owners, completion);
    }

    void requireConfirmable(Validation plan) {
        Objects.requireNonNull(plan);
        if (!plan.candidate().pendingConditionIds().isEmpty() || !plan.unassignedSectionIds().isEmpty())
            throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        if (!plan.incompleteRuleTargets().isEmpty()) throw new TenantAccessException(ErrorCode.PLAN_UNCONFIRMED);
    }

    static List<String> dependencyOrder(List<String> nodes, Map<String, List<String>> dependencies) {
        var identities = distinct(nodes);
        if (!identities.equals(dependencies.keySet())) invalid();
        var graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
        for (String node : nodes) graph.addVertex(node);
        for (var item : dependencies.entrySet()) {
            for (String dependency : distinct(item.getValue())) {
                if (!identities.contains(dependency)) invalid();
                graph.addEdge(dependency, item.getKey());
            }
        }
        if (new CycleDetector<>(graph).detectCycles()) throw new TenantAccessException(ErrorCode.PLAN_CYCLE);
        var iterator = new TopologicalOrderIterator<>(graph, Comparator.naturalOrder());
        var result = new ArrayList<String>(); iterator.forEachRemaining(result::add);
        return List.copyOf(result);
    }

    private static void requireRules(List<String> rules, Set<String> allowed) {
        if (!allowed.containsAll(distinct(rules))) invalid();
    }

    private static Set<String> distinct(List<String> values) {
        var result = new HashSet<String>();
        for (String value : values) if (value == null || value.isBlank() || !result.add(value)) invalid();
        return result;
    }

    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }

    record Validation(ArtifactProtocol.PlanCandidate candidate, String canonicalContent, String contentSha256,
            List<String> sectionOrder, List<String> questionOrder, List<String> unassignedSectionIds, List<String> incompleteRuleTargets) {
        Validation {
            sectionOrder = List.copyOf(sectionOrder); questionOrder = List.copyOf(questionOrder);
            unassignedSectionIds = List.copyOf(unassignedSectionIds); incompleteRuleTargets = List.copyOf(incompleteRuleTargets);
        }
    }
}
