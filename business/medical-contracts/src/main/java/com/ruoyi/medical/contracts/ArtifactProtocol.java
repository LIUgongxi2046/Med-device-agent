package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import static com.ruoyi.medical.contracts.CommonProtocol.*;

public final class ArtifactProtocol {
    private ArtifactProtocol() { }
    public enum ExecutionKind { TASK, SYSTEM_JOB }
    public enum Method { DETERMINISTIC, MODEL_ASSISTED, HUMAN_PROVIDED }
    public enum Relation { SUPPORT, CONFLICT, BACKGROUND }
    public enum QuestionStatus { VERIFIED, INSUFFICIENT, CONFLICT }
    public enum ValueKind { TEXT, LONG_TEXT, DATE, DECIMAL, ENUM, MULTISELECT, RELATION, TABLE, IMAGE, FORMULA, FILE, EMPTY }
    public enum ComparisonResult { MATCH, CONFLICT, MISSING, UNKNOWN_CONDITION, NOT_APPLICABLE }
    public enum CoverageResult { COMPLETE, INCOMPLETE, UNKNOWN_CONDITION }
    public enum ClaimKind { PRODUCT_FACT, REGULATORY_REQUIREMENT, METHOD_REQUIREMENT, STUDY_RESULT, TRANSITION }
    public enum NodeKind { ROOT, SECTION, PARAGRAPH, TEXT, TABLE, ROW, CELL, IMAGE, FORMULA, LIST, ITEM, ATTACHMENT }
    public enum AccessCheck { PASS, FAIL }
    public enum LocatorCheck { PASS, FAIL, BLOCKED }
    public enum SupportCheck { SUPPORTED, CONTRADICTED, INSUFFICIENT, NOT_APPLICABLE }
    public enum RuleResult { PASS, FAIL, BLOCKED, NOT_APPLICABLE }
    public enum TargetKind { CANDIDATE, DOCUMENT }
    public enum VersionKind { CANDIDATE, DOCUMENT }
    public enum AssemblyKind { CANDIDATE_SET, DOCUMENT }

    public record ProfessionalArtifact(String schemaVersion, ExecutionKind executionKind, String tenantId, String projectId,
                                       String runId, String stepId, String inputManifestSha256, String planRevision,
                                       String snapshotId, List<String> targetSectionIds, String dependencyAccessSetId,
                                       String contentSha256, ArtifactOutput output) {
        public ProfessionalArtifact {
            constant(schemaVersion, "1.0.0"); ids(tenantId, runId, stepId, dependencyAccessSetId);
            nullableIds(projectId, planRevision, snapshotId); targetSectionIds = copyIds(targetSectionIds);
            if (executionKind == null) invalid();
            if (executionKind == ExecutionKind.TASK) ids(projectId, planRevision, snapshotId);
        }
    }
    public record ObjectRef(String objectKey, String objectVersion, String sha256, long sizeBytes, String mediaType) { }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "artifact_kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = PlanOutput.class, name = "PLAN_CANDIDATE"), @JsonSubTypes.Type(value = EvidenceOutput.class, name = "EVIDENCE_BUNDLE"),
        @JsonSubTypes.Type(value = FieldOutput.class, name = "FIELD_CHECK_RESULT"), @JsonSubTypes.Type(value = SectionOutput.class, name = "SECTION_CANDIDATE"),
        @JsonSubTypes.Type(value = ClaimOutput.class, name = "CLAIM_RECORD"), @JsonSubTypes.Type(value = ReviewOutput.class, name = "REVIEW_FINDINGS"),
        @JsonSubTypes.Type(value = AssemblyOutput.class, name = "ASSEMBLY_MANIFEST"), @JsonSubTypes.Type(value = HandoffOutput.class, name = "HANDOFF_RECORD")})
    public sealed interface ArtifactOutput permits PlanOutput, EvidenceOutput, FieldOutput, SectionOutput, ClaimOutput, ReviewOutput, AssemblyOutput, HandoffOutput { String artifactKind(); }
    public record PlanOutput(String artifactKind, PlanCandidate content) implements ArtifactOutput { public PlanOutput { constant(artifactKind, "PLAN_CANDIDATE"); } }
    public record EvidenceOutput(String artifactKind, EvidenceBundle content) implements ArtifactOutput { public EvidenceOutput { constant(artifactKind, "EVIDENCE_BUNDLE"); } }
    public record FieldOutput(String artifactKind, FieldCheckResult content) implements ArtifactOutput { public FieldOutput { constant(artifactKind, "FIELD_CHECK_RESULT"); } }
    public record SectionOutput(String artifactKind, SectionCandidate content) implements ArtifactOutput { public SectionOutput { constant(artifactKind, "SECTION_CANDIDATE"); } }
    public record ClaimOutput(String artifactKind, ClaimRecord content) implements ArtifactOutput { public ClaimOutput { constant(artifactKind, "CLAIM_RECORD"); } }
    public record ReviewOutput(String artifactKind, ReviewFindings content) implements ArtifactOutput { public ReviewOutput { constant(artifactKind, "REVIEW_FINDINGS"); } }
    public record AssemblyOutput(String artifactKind, AssemblyManifest content) implements ArtifactOutput { public AssemblyOutput { constant(artifactKind, "ASSEMBLY_MANIFEST"); } }
    public record HandoffOutput(String artifactKind, HandoffRecord content) implements ArtifactOutput { public HandoffOutput { constant(artifactKind, "HANDOFF_RECORD"); } }

    public record PlanCandidate(String templateVersion, String profileRevision, List<String> requirementNodeIds, List<PlanSection> sections,
                                 List<ResearchQuestion> questions, List<String> pendingConditionIds) {
        public PlanCandidate { ids(templateVersion, profileRevision); requirementNodeIds = copyIds(requirementNodeIds); sections = List.copyOf(sections); questions = List.copyOf(questions); pendingConditionIds = copyIds(pendingConditionIds); }
    }
    public record PlanSection(String sectionId, List<String> requirementNodeIds, String goal, String ownerId, Method method,
                               List<String> dependsOnSectionIds, List<String> completionRuleIds) {
        public PlanSection { ids(sectionId); nullableIds(ownerId); requirementNodeIds = copyIds(requirementNodeIds); dependsOnSectionIds = copyIds(dependsOnSectionIds); completionRuleIds = copyIds(completionRuleIds); }
    }
    public record ResearchQuestion(String questionKey, String sectionId, String question, List<String> requirementIds,
                                   List<String> completionRuleIds, List<String> dependsOnQuestionKeys) {
        public ResearchQuestion { ids(sectionId); requirementIds = copyIds(requirementIds); completionRuleIds = copyIds(completionRuleIds); dependsOnQuestionKeys = List.copyOf(dependsOnQuestionKeys); }
    }
    public record EvidenceLink(DatasetProtocol.EvidenceRef evidenceRef, Relation relation) { }
    public record EvidenceBundle(List<String> retrievalIds, List<QuestionCoverage> questionCoverage, List<DatasetProtocol.EvidenceRef> selectedRefs,
                                  List<DatasetProtocol.EvidenceRef> conflictRefs, List<String> missingRequirementIds, List<String> readReceiptIds) {
        public EvidenceBundle { retrievalIds = copyIds(retrievalIds); questionCoverage = List.copyOf(questionCoverage); selectedRefs = List.copyOf(selectedRefs); conflictRefs = List.copyOf(conflictRefs); missingRequirementIds = copyIds(missingRequirementIds); readReceiptIds = copyIds(readReceiptIds); }
    }
    public record QuestionCoverage(String questionId, QuestionStatus status, List<EvidenceLink> evidenceLinks, List<String> completionCheckIds, List<String> missingRequirementIds) {
        public QuestionCoverage { ids(questionId); evidenceLinks = List.copyOf(evidenceLinks); completionCheckIds = copyIds(completionCheckIds); missingRequirementIds = copyIds(missingRequirementIds); }
    }
    public record FieldValue(ValueKind valueKind, String rawText, String decimalValue, Long precision, String unit,
                              List<String> selectedIds, ObjectRef objectRef) {
        public FieldValue {
            selectedIds = copyIds(selectedIds);
            if (decimalValue != null) WireValues.decimal(decimalValue, 38, 12);
            if (valueKind == null) invalid();
            if (valueKind == ValueKind.DECIMAL && (rawText == null || rawText.isEmpty() || decimalValue == null || precision == null || precision < 0)) invalid();
        }
    }
    public record FieldCheckResult(String targetArtifactId, String ruleRevision, List<FieldComparison> entries,
                                   List<MethodCoverage> methodCoverage, List<String> missingRequirementIds) {
        public FieldCheckResult { ids(targetArtifactId, ruleRevision); entries = List.copyOf(entries); methodCoverage = List.copyOf(methodCoverage); missingRequirementIds = copyIds(missingRequirementIds); }
    }
    public record FieldComparison(String fieldId, String rowId, List<String> modelIds, List<String> conditionIds, FieldValue sourceValue,
                                   FieldValue candidateValue, String comparisonRuleId, String conversionRuleId, ComparisonResult result,
                                   List<DatasetProtocol.EvidenceRef> evidenceRefs) {
        public FieldComparison { ids(fieldId, comparisonRuleId); nullableIds(rowId, conversionRuleId); modelIds = copyIds(modelIds); conditionIds = copyIds(conditionIds); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    public record MethodCoverage(String indicatorId, String modelId, String conditionId, List<String> methodIds,
                                 List<DatasetProtocol.EvidenceRef> evidenceRefs, CoverageResult result) {
        public MethodCoverage { ids(indicatorId, modelId, conditionId); methodIds = copyIds(methodIds); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind", visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = TextAnchor.class, name = "TEXT"), @JsonSubTypes.Type(value = StructuredAnchor.class, name = "STRUCTURED")})
    public sealed interface Anchor permits TextAnchor, StructuredAnchor { String kind(); }
    public record TextAnchor(String kind, String nodeId, long start, long end, String textSha256) implements Anchor {
        public TextAnchor { constant(kind, "TEXT"); ids(nodeId); }
    }
    public record StructuredAnchor(String kind, String nodeId, String rowId, String fieldId, String contentSha256) implements Anchor {
        public StructuredAnchor { constant(kind, "STRUCTURED"); ids(nodeId); nullableIds(rowId, fieldId); }
    }
    public record ClaimRecord(String claimId, Anchor anchor, ClaimKind claimKind, List<EvidenceLink> evidenceLinks) {
        public ClaimRecord { ids(claimId); evidenceLinks = List.copyOf(evidenceLinks); if (claimKind == null || (claimKind != ClaimKind.TRANSITION && evidenceLinks.isEmpty())) invalid(); }
    }
    public record NodeManifestItem(String nodeId, String parentId, String sectionId, NodeKind kind, String fieldId, String rowId, String contentSha256) {
        public NodeManifestItem { ids(nodeId, sectionId); nullableIds(parentId, fieldId, rowId); }
    }
    public record FieldAssignment(String fieldId, String rowId, String nodeId, String factVersion, FieldValue value, List<DatasetProtocol.EvidenceRef> evidenceRefs) {
        public FieldAssignment { ids(fieldId, nodeId); nullableIds(rowId, factVersion); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    public record AttachmentBinding(String requirementId, String nodeId, String sourceVersion, List<DatasetProtocol.EvidenceRef> evidenceRefs) {
        public AttachmentBinding { ids(requirementId, nodeId, sourceVersion); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    public record IssueProposal(String issueKey, String code, String severityCode, String ruleId, Anchor anchor, List<String> claimIds,
                                String message, List<DatasetProtocol.EvidenceRef> evidenceRefs) {
        public IssueProposal { ids(ruleId); claimIds = copyIds(claimIds); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    public record SectionDraft(String bodySchemaRevision, String bodyJson, List<ClaimRecord> claimRecords, List<FieldAssignment> fieldAssignments,
                               List<AttachmentBinding> attachmentBindings, List<IssueProposal> issueProposals) {
        public SectionDraft { ids(bodySchemaRevision); claimRecords = List.copyOf(claimRecords); fieldAssignments = List.copyOf(fieldAssignments); attachmentBindings = List.copyOf(attachmentBindings); issueProposals = List.copyOf(issueProposals); }
    }
    public record SectionCandidate(String baseDocumentRevision, List<String> targetSections, ObjectRef bodyObjectRef, String bodySchemaRevision,
                                   List<NodeManifestItem> nodeManifest, List<ClaimRecord> claimRecords, List<FieldAssignment> fieldAssignments,
                                   List<AttachmentBinding> attachmentBindings, List<IssueProposal> issueProposals) {
        public SectionCandidate { ids(baseDocumentRevision, bodySchemaRevision); targetSections = copyIds(targetSections); nodeManifest = List.copyOf(nodeManifest); claimRecords = List.copyOf(claimRecords); fieldAssignments = List.copyOf(fieldAssignments); attachmentBindings = List.copyOf(attachmentBindings); issueProposals = List.copyOf(issueProposals); }
    }
    public record ClaimCheck(String claimId, AccessCheck accessCheck, LocatorCheck locatorCheck, SupportCheck supportCheck,
                             List<String> ruleIds, List<String> readReceiptIds, String rationale) {
        public ClaimCheck { ids(claimId); ruleIds = copyIds(ruleIds); readReceiptIds = copyIds(readReceiptIds); }
    }
    public record RuleCheck(String ruleId, List<String> targetNodeIds, RuleResult result, List<DatasetProtocol.EvidenceRef> evidenceRefs, String rationale) {
        public RuleCheck { ids(ruleId); targetNodeIds = copyIds(targetNodeIds); evidenceRefs = List.copyOf(evidenceRefs); }
    }
    public record ReviewFindings(TargetKind targetKind, String targetRevision, String targetSha256, String ruleRevision,
                                 List<String> requiredClaimIds, List<String> requiredRuleIds, List<ClaimCheck> claimChecks,
                                 List<RuleCheck> ruleChecks, List<IssueProposal> issueProposals, boolean coverageComplete) {
        public ReviewFindings { ids(targetRevision, ruleRevision); requiredClaimIds = copyIds(requiredClaimIds); requiredRuleIds = copyIds(requiredRuleIds); claimChecks = List.copyOf(claimChecks); ruleChecks = List.copyOf(ruleChecks); issueProposals = List.copyOf(issueProposals); }
    }
    public record SectionVersion(String sectionId, VersionKind versionKind, String versionId, long ordinal, String contentSha256) {
        public SectionVersion { ids(sectionId, versionId); }
    }
    public record AssemblyManifest(AssemblyKind targetKind, String documentRevision, List<SectionVersion> sections,
                                   List<String> requirementNodeIds, List<String> factVersions, List<DatasetProtocol.EvidenceRef> evidenceRefs,
                                   String ruleRevision, List<String> checkIds, List<String> reviewIds, List<String> unresolvedIssueIds, String formatProfileRevision) {
        public AssemblyManifest {
            nullableIds(documentRevision); ids(ruleRevision, formatProfileRevision); sections = List.copyOf(sections);
            requirementNodeIds = copyIds(requirementNodeIds); factVersions = copyIds(factVersions); evidenceRefs = List.copyOf(evidenceRefs);
            checkIds = copyIds(checkIds); reviewIds = copyIds(reviewIds); unresolvedIssueIds = copyIds(unresolvedIssueIds);
            if (targetKind == null) invalid();
            if (targetKind == AssemblyKind.DOCUMENT) {
                ids(documentRevision);
                for (SectionVersion section : sections) if (section.versionKind() != VersionKind.DOCUMENT) invalid();
            }
        }
    }
    public record FailedAction(String actionId, String errorCode, String receiptId) { public FailedAction { ids(actionId, receiptId); } }
    public record RemainingLimits(long wallTimeSeconds, long modelRequests, long toolCalls, long researchRounds, long revisionRounds,
                                   long totalTokens, String costAmount, String currency) {
        public RemainingLimits { WireValues.decimal(costAmount, 24, 8); }
    }
    public record HandoffRecord(List<String> completedActionRefs, List<String> artifactRefs, List<DatasetProtocol.EvidenceRef> evidenceRefs,
                                List<String> unresolvedItemIds, List<String> pendingDecisionRefs, List<FailedAction> failedActionRefs,
                                List<String> inflightInvocationIds, String budgetRevision, RemainingLimits remainingLimits, String createdAt) {
        public HandoffRecord { completedActionRefs = copyIds(completedActionRefs); artifactRefs = copyIds(artifactRefs); evidenceRefs = List.copyOf(evidenceRefs); unresolvedItemIds = copyIds(unresolvedItemIds); pendingDecisionRefs = copyIds(pendingDecisionRefs); failedActionRefs = List.copyOf(failedActionRefs); inflightInvocationIds = copyIds(inflightInvocationIds); ids(budgetRevision); times(createdAt); }
    }

    private static void invalid() { throw new IllegalArgumentException("SCHEMA_VALIDATION_FAILED"); }
}
