package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class TaskPlanProjection {
    private TaskPlanProjection() { }

    public record Section(String sectionId, String goal, String ownerId, ArtifactProtocol.Method method, int position) { }
    public record SectionNode(String sectionId, String nodeId, int position) { }
    public record SectionRule(String sectionId, String ruleId, int position) { }
    public record SectionDependency(String sectionId, String dependsOnSectionId, int position) { }
    public record Question(String questionId, String questionKey, String sectionId, String question, int position) { }
    public record QuestionNode(String questionId, String nodeId, int position) { }
    public record QuestionRule(String questionId, String ruleId, int position) { }
    public record QuestionDependency(String questionId, String dependsOnQuestionId, int position) { }
    public record QuestionIdentity(String taskId, String questionId, String questionKey, String keySha256) { }
    public record Basis(String tenantId, String projectId, String taskId, String taskRevision, String planRevision,
            String templateVersion, String snapshotId, String profileRevision, String contentJson, String contentHash,
            String registryHash, String schemaRef, String schemaVersion, String manifest, String createdBy,
            java.time.LocalDateTime createdAt, String classification, String status) { }

    record Projection(String templateVersion, String profileRevision, List<String> requirementNodeIds, List<String> pendingConditionIds,
            List<Section> sections, List<SectionNode> sectionNodes, List<SectionRule> sectionRules,
            List<SectionDependency> sectionDependencies, List<Question> questions, List<QuestionNode> questionNodes,
            List<QuestionRule> questionRules, List<QuestionDependency> questionDependencies) {
        Projection {
            requirementNodeIds = List.copyOf(requirementNodeIds); pendingConditionIds = List.copyOf(pendingConditionIds);
            sections = List.copyOf(sections); sectionNodes = List.copyOf(sectionNodes);
            sectionRules = List.copyOf(sectionRules); sectionDependencies = List.copyOf(sectionDependencies);
            questions = List.copyOf(questions); questionNodes = List.copyOf(questionNodes);
            questionRules = List.copyOf(questionRules); questionDependencies = List.copyOf(questionDependencies);
        }
    }

    // 问题标识由同一事务中的任务问题登记提供；内容转换不生成业务身份。
    static Projection project(TaskPlanPolicy.Validation validation, Map<String, String> questionIdentities) {
        Objects.requireNonNull(validation); Objects.requireNonNull(questionIdentities);
        var candidate = validation.candidate();
        var sections = new ArrayList<Section>();
        var sectionNodes = new ArrayList<SectionNode>();
        var sectionRules = new ArrayList<SectionRule>();
        var sectionDependencies = new ArrayList<SectionDependency>();
        var questions = new ArrayList<Question>();
        var questionNodes = new ArrayList<QuestionNode>();
        var questionRules = new ArrayList<QuestionRule>();
        var questionDependencies = new ArrayList<QuestionDependency>();
        var activeKeys = new HashSet<String>(); var activeIds = new HashSet<String>();
        for (var question : candidate.questions()) {
            String id = requireIdentity(questionIdentities, question.questionKey());
            if (!activeKeys.add(question.questionKey()) || !activeIds.add(id)) mismatch();
        }
        for (int position = 0; position < candidate.sections().size(); position++) {
            var section = candidate.sections().get(position);
            sections.add(new Section(section.sectionId(), section.goal(), section.ownerId(), section.method(), position));
            for (int index = 0; index < section.requirementNodeIds().size(); index++) {
                sectionNodes.add(new SectionNode(section.sectionId(), section.requirementNodeIds().get(index), index));
            }
            for (int index = 0; index < section.completionRuleIds().size(); index++) {
                sectionRules.add(new SectionRule(section.sectionId(), section.completionRuleIds().get(index), index));
            }
            for (int index = 0; index < section.dependsOnSectionIds().size(); index++) {
                sectionDependencies.add(new SectionDependency(section.sectionId(), section.dependsOnSectionIds().get(index), index));
            }
        }
        for (int position = 0; position < candidate.questions().size(); position++) {
            var question = candidate.questions().get(position);
            String id = requireIdentity(questionIdentities, question.questionKey());
            questions.add(new Question(id, question.questionKey(), question.sectionId(), question.question(), position));
            for (int index = 0; index < question.requirementIds().size(); index++) {
                questionNodes.add(new QuestionNode(id, question.requirementIds().get(index), index));
            }
            for (int index = 0; index < question.completionRuleIds().size(); index++) {
                questionRules.add(new QuestionRule(id, question.completionRuleIds().get(index), index));
            }
            for (int index = 0; index < question.dependsOnQuestionKeys().size(); index++) {
                String dependency = question.dependsOnQuestionKeys().get(index);
                if (!activeKeys.contains(dependency)) mismatch();
                questionDependencies.add(new QuestionDependency(id, requireIdentity(questionIdentities, dependency), index));
            }
        }
        var result = new Projection(candidate.templateVersion(), candidate.profileRevision(), candidate.requirementNodeIds(),
                candidate.pendingConditionIds(), sections, sectionNodes, sectionRules, sectionDependencies, questions,
                questionNodes, questionRules, questionDependencies);
        if (!restore(result).equals(candidate)) mismatch();
        return result;
    }

    static ArtifactProtocol.PlanCandidate restore(Projection projection) {
        Objects.requireNonNull(projection);
        var sectionRows = ordered(projection.sections(), Section::position);
        var questionRows = ordered(projection.questions(), Question::position);
        var sectionIds = new HashSet<String>(); var questionKeys = new HashSet<String>();
        var questionIds = new HashSet<String>(); var keysById = new HashMap<String, String>();
        for (var row : sectionRows) if (!sectionIds.add(row.sectionId())) mismatch();
        for (var row : questionRows) {
            WireValues.uuid(row.questionId());
            if (!questionIds.add(row.questionId()) || !questionKeys.add(row.questionKey()) || !sectionIds.contains(row.sectionId())) mismatch();
            keysById.put(row.questionId(), row.questionKey());
        }
        var sectionNodes = group(sectionIds, projection.sectionNodes(), SectionNode::sectionId);
        var sectionRules = group(sectionIds, projection.sectionRules(), SectionRule::sectionId);
        var sectionDependencies = group(sectionIds, projection.sectionDependencies(), SectionDependency::sectionId);
        var questionNodes = group(questionIds, projection.questionNodes(), QuestionNode::questionId);
        var questionRules = group(questionIds, projection.questionRules(), QuestionRule::questionId);
        var questionDependencies = group(questionIds, projection.questionDependencies(), QuestionDependency::questionId);
        var sections = new ArrayList<ArtifactProtocol.PlanSection>();
        for (var row : sectionRows) {
            var dependencies = ordered(sectionDependencies.get(row.sectionId()), SectionDependency::position)
                    .stream().map(SectionDependency::dependsOnSectionId).toList();
            if (!sectionIds.containsAll(dependencies)) mismatch();
            sections.add(new ArtifactProtocol.PlanSection(row.sectionId(),
                    ordered(sectionNodes.get(row.sectionId()), SectionNode::position).stream().map(SectionNode::nodeId).toList(),
                    row.goal(), row.ownerId(), row.method(), dependencies,
                    ordered(sectionRules.get(row.sectionId()), SectionRule::position).stream().map(SectionRule::ruleId).toList()));
        }
        var questions = new ArrayList<ArtifactProtocol.ResearchQuestion>();
        for (var row : questionRows) {
            var dependencies = new ArrayList<String>();
            for (var dependency : ordered(questionDependencies.get(row.questionId()), QuestionDependency::position)) {
                String key = keysById.get(dependency.dependsOnQuestionId());
                if (key == null) mismatch();
                dependencies.add(key);
            }
            questions.add(new ArtifactProtocol.ResearchQuestion(row.questionKey(), row.sectionId(), row.question(),
                    ordered(questionNodes.get(row.questionId()), QuestionNode::position).stream().map(QuestionNode::nodeId).toList(),
                    ordered(questionRules.get(row.questionId()), QuestionRule::position).stream().map(QuestionRule::ruleId).toList(), dependencies));
        }
        return new ArtifactProtocol.PlanCandidate(projection.templateVersion(), projection.profileRevision(),
                projection.requirementNodeIds(), sections, questions, projection.pendingConditionIds());
    }

    private static <T> List<T> ordered(List<T> rows, ToIntFunction<T> position) {
        var result = rows.stream().sorted(Comparator.comparingInt(position)).toList();
        for (int index = 0; index < result.size(); index++) if (position.applyAsInt(result.get(index)) != index) mismatch();
        return result;
    }

    private static <T> Map<String, List<T>> group(Set<String> owners, List<T> rows, Function<T, String> owner) {
        var result = new HashMap<String, List<T>>();
        for (String id : owners) result.put(id, new ArrayList<>());
        for (T row : rows) {
            var values = result.get(owner.apply(row));
            if (values == null) mismatch();
            values.add(row);
        }
        return result;
    }

    private static String requireIdentity(Map<String, String> identities, String key) {
        String id = identities.get(key);
        if (id == null) mismatch();
        WireValues.uuid(id);
        return id;
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
