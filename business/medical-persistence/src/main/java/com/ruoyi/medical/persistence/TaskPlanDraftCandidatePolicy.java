package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure N01 expansion and independent structural inspection. This is NOT run admission,
 * artifact registration, an independent professional check, or permission to confirm a plan.
 * Inputs must come from the current fixed template/profile authority, not model-supplied templates.
 * Fields and attachments remain transitively pinned by templateVersion; no new public wire shape.
 */
public final class TaskPlanDraftCandidatePolicy {
    private final TaskPlanPolicy plans;
    private final PermissionSchema json;

    public TaskPlanDraftCandidatePolicy(TaskPlanPolicy plans, PermissionSchema json) {
        this.plans = Objects.requireNonNull(plans); this.json = Objects.requireNonNull(json);
    }

    record SectionProposal(String nodeId, String goal, String ownerId, ArtifactProtocol.Method method,
            List<String> dependsOnNodeIds, List<String> completionRuleIds) {
        SectionProposal { dependsOnNodeIds = List.copyOf(dependsOnNodeIds); completionRuleIds = List.copyOf(completionRuleIds); }
    }
    record QuestionProposal(String key, String nodeId, String question, List<String> completionRuleIds,
            List<String> dependsOnKeys) {
        QuestionProposal { completionRuleIds = List.copyOf(completionRuleIds); dependsOnKeys = List.copyOf(dependsOnKeys); }
    }
    record MissingItem(String kind, String targetId) { }
    record Coverage(String nodeId, String sectionId, List<String> fieldIds, List<String> attachmentIds,
            List<String> repeatIds, List<String> questionKeys) {
        Coverage { fieldIds = List.copyOf(fieldIds); attachmentIds = List.copyOf(attachmentIds);
            repeatIds = List.copyOf(repeatIds); questionKeys = List.copyOf(questionKeys); }
    }
    record Inspected(TaskPlanPolicy.Validation validation, List<Coverage> coverage, List<MissingItem> missing) {
        Inspected { coverage = List.copyOf(coverage); missing = List.copyOf(missing); }
    }

    Inspected expand(String tenantId, String runId, String taskRevision, String profileRevision,
            TemplateApplicabilityProtocol.TemplateView fixed, List<SectionProposal> proposals,
            List<QuestionProposal> questions, Set<String> allowedCompletionRules) throws IOException {
        WireValues.uuid(tenantId); WireValues.uuid(runId); WireValues.uuid(taskRevision); WireValues.uuid(profileRevision);
        var template = fixed.template(); var nodes = new LinkedHashMap<String, String>();
        for (var node : template.content().nodes()) {
            String basis = String.join("/", "task-plan-draft-v1", tenantId, runId, taskRevision,
                    template.template_version(), template.content_sha256(), profileRevision, node.node_id());
            if (nodes.putIfAbsent(node.node_id(), UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString()) != null) invalid();
        }
        var supplied = new HashMap<String, SectionProposal>();
        for (var proposal : proposals) if (supplied.putIfAbsent(proposal.nodeId(), proposal) != null) invalid();
        if (!nodes.keySet().equals(supplied.keySet())) invalid();
        var sections = new ArrayList<ArtifactProtocol.PlanSection>();
        // Template order and complete node set survive NOT_APPLICABLE and UNKNOWN conditions.
        for (var node : template.content().nodes()) {
            var proposal = supplied.get(node.node_id());
            if (proposal.method() == null) invalid();
            var dependencies = new ArrayList<String>();
            for (String dependency : proposal.dependsOnNodeIds()) {
                if (!nodes.containsKey(dependency)) invalid();
                dependencies.add(nodes.get(dependency));
            }
            sections.add(new ArtifactProtocol.PlanSection(nodes.get(node.node_id()), List.of(node.node_id()),
                    proposal.goal(), proposal.ownerId(), proposal.method(), dependencies, proposal.completionRuleIds()));
        }
        var expandedQuestions = new ArrayList<ArtifactProtocol.ResearchQuestion>();
        for (var question : questions) {
            if (!nodes.containsKey(question.nodeId())) invalid();
            expandedQuestions.add(new ArtifactProtocol.ResearchQuestion(question.key(), nodes.get(question.nodeId()),
                    question.question(), List.of(question.nodeId()), question.completionRuleIds(), question.dependsOnKeys()));
        }
        var pending = fixed.conditions().stream().filter(c -> "UNKNOWN".equals(c.status()))
                .map(TemplateApplicabilityProtocol.ConditionResult::target_id).distinct().sorted().toList();
        var candidate = new ArtifactProtocol.PlanCandidate(template.template_version(), profileRevision,
                List.copyOf(nodes.keySet()), sections, expandedQuestions, pending);
        return inspect(candidate, profileRevision, fixed, allowedCompletionRules);
    }

    Inspected inspect(ArtifactProtocol.PlanCandidate candidate, String profileRevision,
            TemplateApplicabilityProtocol.TemplateView fixed, Set<String> allowedCompletionRules) throws IOException {
        var template = fixed.template(); var content = template.content();
        if (!"APPROVED".equals(template.status())) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if (template.content_sha256() == null || !template.content_sha256().matches("[0-9a-f]{64}")) invalid();
        var nodeIds = content.nodes().stream().map(TemplateProtocol.Node::node_id).toList();
        var conditionIds = new HashSet<String>();
        for (var condition : fixed.conditions()) {
            conditionIds.add(condition.target_id());
            if ("UNKNOWN".equals(condition.status()) && !candidate.pendingConditionIds().contains(condition.target_id()))
                throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        }
        var validation = plans.inspect(json.tree(candidate), template.template_version(), profileRevision,
                nodeIds, conditionIds, allowedCompletionRules);
        var sectionByNode = new HashMap<String, String>();
        candidate.sections().forEach(s -> s.requirementNodeIds().forEach(n -> sectionByNode.put(n, s.sectionId())));
        var fields = new HashMap<String, List<String>>(); var attachments = new HashMap<String, List<String>>();
        var repeats = new HashMap<String, List<String>>(); var fieldOwners = new HashMap<String, String>();
        var allIds = new HashSet<String>(nodeIds);
        for (var field : content.field_definitions()) {
            member(fields, allIds, sectionByNode, field.node_id(), field.field_id());
            fieldOwners.put(field.field_id(), field.node_id());
        }
        for (var attachment : content.attachment_requirements())
            member(attachments, allIds, sectionByNode, attachment.node_id(), attachment.attachment_requirement_id());
        for (var repeat : content.repeat_definitions()) {
            member(repeats, allIds, sectionByNode, repeat.node_id(), repeat.repeat_id());
            var seen = new HashSet<String>();
            for (String field : repeat.field_ids())
                if (!seen.add(field) || !repeat.node_id().equals(fieldOwners.get(field))) invalid();
        }
        var missing = new LinkedHashSet<MissingItem>();
        candidate.pendingConditionIds().forEach(id -> missing.add(new MissingItem("APPLICABILITY_UNKNOWN", id)));
        validation.unassignedSectionIds().forEach(id -> missing.add(new MissingItem("OWNER_UNASSIGNED", id)));
        validation.incompleteRuleTargets().forEach(id -> missing.add(new MissingItem("COMPLETION_RULE_MISSING", id)));
        fixed.missing_requirements().forEach(item -> missing.add(new MissingItem("TEMPLATE_REQUIREMENT_MISSING", item.requirementId())));
        var coverage = new ArrayList<Coverage>();
        for (String node : nodeIds) {
            var keys = candidate.questions().stream().filter(q -> q.requirementIds().contains(node))
                    .map(ArtifactProtocol.ResearchQuestion::questionKey).toList();
            var section = candidate.sections().stream().filter(s -> s.sectionId().equals(sectionByNode.get(node))).findFirst().orElseThrow();
            if (section.method() == ArtifactProtocol.Method.MODEL_ASSISTED && keys.isEmpty())
                missing.add(new MissingItem("RESEARCH_QUESTION_MISSING", node));
            coverage.add(new Coverage(node, sectionByNode.get(node), fields.getOrDefault(node, List.of()),
                    attachments.getOrDefault(node, List.of()), repeats.getOrDefault(node, List.of()), keys));
        }
        // Source/material missing requirements belong to the authoritative input manifest, not model prose.
        // They must be joined by the caller before this local report can become an independent check.
        return new Inspected(validation, coverage, List.copyOf(missing));
    }

    private static void member(Map<String, List<String>> target, Set<String> identities, Map<String, String> nodes,
            String node, String id) {
        WireValues.uuid(id);
        if (!nodes.containsKey(node) || !identities.add(id)) invalid();
        target.computeIfAbsent(node, ignored -> new ArrayList<>()).add(id);
    }
    private static void invalid() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
