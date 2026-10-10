package com.ruoyi.medical.contracts;

import java.util.List;
import com.ruoyi.medical.contracts.CommonProtocol.ArtifactRef;
import com.ruoyi.medical.contracts.CommonProtocol.Coverage;
import com.ruoyi.medical.contracts.CommonProtocol.EvidenceDecision;
import com.ruoyi.medical.contracts.CommonProtocol.Selection;
import com.ruoyi.medical.contracts.CommonProtocol.SourceScope;
import static com.ruoyi.medical.contracts.CommonProtocol.*;

public final class ResourceProtocol {
    private ResourceProtocol() { }
    public enum TaskStatus { NEEDS_MATERIALS, PLAN_PENDING, READY, RUNNING, REVIEW_PENDING, SIGN_PENDING, SIGNED, FAILED, RETURNED, CANCEL_REQUESTED, CANCELLED }
    public enum TargetKind { DOCUMENT, CANDIDATE }
    public enum CheckStatus { QUEUED, RUNNING, SUCCEEDED, WAITING_INPUT, FAILED, CANCELLED }
    public enum Validity { VALID, INVALIDATED }
    public enum AnnotationStatus { OPEN, RESOLVED, RETURNED }
    public enum LocationStatus { CURRENT_VERSION, ORIGINAL_VERSION }
    public enum SenderKind { USER, AGENT, SYSTEM }
    public enum ConfigurationKind { AGENT, CAPABILITY, MODEL, CONNECTOR }
    public enum EvaluationStatus { QUEUED, RUNNING, COMPLETED, FAILED, INSUFFICIENT_EVIDENCE }
    public enum ScoreStatus { SCORED, UNKNOWN, BLOCKED }
    public enum Applicability { APPLICABLE, NOT_APPLICABLE, UNKNOWN }
    public enum ImpactResourceKind { DOCUMENT, FIELD, REVIEW, INDEX, APPLICATION_NODE }
    public enum ImpactStatus { PENDING, CONFIRMED, COMPLETED, BLOCKED }
    public enum DifferenceKind { ADDED, REMOVED, CHANGED, UNCHANGED }

    public record TaskDetail(String taskId, String taskRevision, String projectId, String ownerId, TaskStatus status,
                             String planRevision, String snapshotId, List<String> documentIds, List<Requirement> missingRequirements,
                             List<String> waitingItemIds, List<String> activeRunIds) {
        public TaskDetail {
            ids(taskId, taskRevision, projectId, ownerId); nullableIds(planRevision, snapshotId);
            documentIds = copyIds(documentIds); waitingItemIds = copyIds(waitingItemIds); activeRunIds = copyIds(activeRunIds);
            missingRequirements = List.copyOf(missingRequirements);
        }
    }
    // requirement_id 由原始 Schema 限定为文本，不能转换为生产 UUID。
    public record Requirement(String requirementId, String nodeId, String fieldId, String reasonCode, List<String> allowedActions) {
        public Requirement { nullableIds(nodeId, fieldId); allowedActions = List.copyOf(allowedActions); }
    }
    public record CheckDetail(String checkId, TargetKind targetKind, String targetId, String targetRevision, String inputSha256,
                              String ruleManifestId, CheckStatus status, Coverage coverage, ArtifactRef findings, List<String> issueIds,
                              String evidenceWatermark, String issueWatermark, String invalidationReason) {
        public CheckDetail { ids(checkId, targetId, targetRevision, ruleManifestId); issueIds = copyIds(issueIds); revisions(evidenceWatermark, issueWatermark); }
    }
    public record EvidenceDecisionDetail(String decisionId, String batchId, String documentRevision, String actorId, String decidedAt,
                                         EvidenceDecision decision, List<String> checkIds, String evidenceWatermark, String issueWatermark,
                                         Validity validity, String invalidationReason) {
        public EvidenceDecisionDetail { ids(decisionId, batchId, documentRevision, actorId); times(decidedAt); checkIds = copyIds(checkIds); revisions(evidenceWatermark, issueWatermark); }
    }
    public record AnnotationDetail(String annotationId, String documentRevision, Selection selection, String content, String actorId,
                                   String createdAt, AnnotationStatus status, LocationStatus locationStatus, String replyCount) {
        public AnnotationDetail { ids(annotationId, documentRevision, actorId); times(createdAt); revisions(replyCount); }
    }
    public record ReplyDetail(String replyId, String annotationId, String actorId, String createdAt, String content) {
        public ReplyDetail { ids(replyId, annotationId, actorId); times(createdAt); }
    }
    public record SignatureDetail(String signatureId, String documentId, String documentRevision, String signerId, String signedAt,
                                  String purpose, String policyRevision, List<String> reviewIds, List<String> checkIds, String manifestSha256,
                                  String authEpoch, String governanceEpoch) {
        public SignatureDetail { ids(signatureId, documentId, documentRevision, signerId, policyRevision); times(signedAt); reviewIds = copyIds(reviewIds); checkIds = copyIds(checkIds); revisions(authEpoch, governanceEpoch); }
    }
    public record Message(String messageId, String conversationId, String projectId, String documentRevision, Selection selection,
                          SourceScope scope, SenderKind senderKind, String content, String runId, String operationId,
                          List<String> candidateIds, String createdAt) {
        public Message { ids(messageId, conversationId); nullableIds(projectId, documentRevision, runId, operationId); candidateIds = copyIds(candidateIds); times(createdAt); }
    }
    public record ConfigurationDetail(String configId, ConfigurationKind kind, String version, ArtifactRef content, String parameterSchemaRef,
                                      List<String> dependencyVersionIds, List<String> approvalIds, List<String> releaseIds, List<String> verificationIds,
                                      String credentialReferenceId, String endpointIdentity) {
        public ConfigurationDetail { ids(configId, version); nullableIds(credentialReferenceId); dependencyVersionIds = copyIds(dependencyVersionIds); approvalIds = copyIds(approvalIds); releaseIds = copyIds(releaseIds); verificationIds = copyIds(verificationIds); }
    }
    public record EvaluationDetail(String evaluationId, ArtifactRef sampleManifest, String ruleRevision, List<String> releaseCandidateIds,
                                   List<String> scoreIds, EvaluationStatus status, List<String> missingRequirements) {
        public EvaluationDetail { ids(evaluationId, ruleRevision); releaseCandidateIds = copyIds(releaseCandidateIds); scoreIds = copyIds(scoreIds); missingRequirements = List.copyOf(missingRequirements); }
    }
    public record ScoreDetail(String scoreId, String evaluationId, String sampleId, String agentVersionId, String dimension, String value,
                              String numerator, String denominator, String scorerId, String ruleRevision, ArtifactRef input, ArtifactRef output,
                              ArtifactRef evidence, String supersedes, ScoreStatus status) {
        public ScoreDetail { ids(scoreId, evaluationId, sampleId, agentVersionId, scorerId, ruleRevision); nullableIds(supersedes); }
    }
    public record FeedbackDetail(String feedbackId, String taskId, String documentRevision, String targetVersion, String content, String actorId,
                                 String proposalId, List<String> decisionIds) {
        public FeedbackDetail { ids(feedbackId, targetVersion, actorId); nullableIds(taskId, documentRevision, proposalId); decisionIds = copyIds(decisionIds); }
    }
    public record CollectionDetail(String collectionId, String currentVersion, List<String> versionIds, ArtifactRef members) {
        public CollectionDetail { ids(collectionId); nullableIds(currentVersion); versionIds = copyIds(versionIds); }
    }
    public record FolderDetail(String folderId, String parentId, List<String> pathIds, String childCount) {
        public FolderDetail { ids(folderId); nullableIds(parentId); pathIds = copyIds(pathIds); revisions(childCount); }
    }
    public record TemplateDetail(String templateVersion, String documentType, List<TemplateNode> nodes, ArtifactRef fieldDefinitions, List<String> decisionIds) {
        public TemplateDetail { ids(templateVersion); nodes = List.copyOf(nodes); decisionIds = copyIds(decisionIds); }
    }
    public record TemplateNode(String nodeId, String parentId, long ordinal, String title, boolean required, Applicability condition,
                               String conditionRuleRevision, List<String> evidenceLinkIds, List<String> fieldIds,
                               List<String> attachmentRequirementIds, List<String> decisionIds) {
        public TemplateNode { ids(nodeId); nullableIds(parentId, conditionRuleRevision); evidenceLinkIds = copyIds(evidenceLinkIds); fieldIds = copyIds(fieldIds); attachmentRequirementIds = copyIds(attachmentRequirementIds); decisionIds = copyIds(decisionIds); }
    }
    public record ChangeProposalDetail(String proposalId, String oldVersion, String newVersion, List<String> decisionIds, List<Impact> impacts,
                                       ArtifactRef applicationSourceLinks, Coverage coverage) {
        public ChangeProposalDetail { ids(proposalId, oldVersion, newVersion); decisionIds = copyIds(decisionIds); impacts = List.copyOf(impacts); }
    }
    public record Impact(ImpactResourceKind resourceKind, String resourceId, String resourceRevision, String reasonCode, String requiredAction, ImpactStatus status) {
        public Impact { ids(resourceId); revisions(resourceRevision); }
    }
    public record Difference(String nodeId, DifferenceKind kind, Selection leftSelection, Selection rightSelection, List<String> fieldIds,
                             List<String> addedEvidenceLinkIds, List<String> removedEvidenceLinkIds, List<String> changedSupportLinkIds,
                             List<String> affectedCheckIds) {
        public Difference { ids(nodeId); fieldIds = copyIds(fieldIds); addedEvidenceLinkIds = copyIds(addedEvidenceLinkIds); removedEvidenceLinkIds = copyIds(removedEvidenceLinkIds); changedSupportLinkIds = copyIds(changedSupportLinkIds); affectedCheckIds = copyIds(affectedCheckIds); }
    }
}
