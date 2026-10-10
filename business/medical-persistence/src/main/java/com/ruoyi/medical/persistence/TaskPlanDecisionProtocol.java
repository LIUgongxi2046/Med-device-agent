package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class TaskPlanDecisionProtocol {
    private TaskPlanDecisionProtocol() { }

    public record Baseline(String schemaVersion, String tenantId, String projectId, String taskId, String taskRevision,
            String planRevision, String planSha256, String snapshotId, String templateVersion, String profileRevision,
            String taskRevisionSha256, String inputSha256, String scopeSha256, String requirementsSha256,
            String taskStatus, String expectedRevision, String planHeadRevision, String contextRevision,
            String authEpoch, String governanceEpoch, String dependencyAuthorizationsSha256) { }

    public record Request(String schemaVersion, String targetRevision, String expectedRevision, String decision,
            String reason, Baseline baselineManifest) { }

    public record Receipt(String schemaVersion, String operationId, String decisionId, String tenantId, String projectId,
            String taskId, String targetPlanRevision, String targetPlanSha256, String basisTaskRevision,
            String resultingTaskRevision, String resultingPlanRevision, String decision, String fromStatus, String toStatus,
            String expectedRevision, String resultingRevision, String actorId, String decidedAt, String reason) { }

    public record Row(String tenantId, String projectId, String taskId, String decisionId, String operationId,
            String targetPlanRevision, String targetPlanSha256, String basisTaskRevision, String resultingTaskRevision,
            String resultingPlanRevision, String decision, String fromStatus, String toStatus, BigInteger expectedRevision,
            BigInteger resultingRevision, String reason, String requestJson, String baselineManifest, String baselineSha256,
            String dependencyAuthorizations, String receiptJson, String receiptSha256, String authorityGrant,
            BigInteger authorityRevision, BigInteger authEpoch, BigInteger governanceEpoch, String schemaVersion,
            String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) { }

    public record TaskRevision(String tenantId, String projectId, String taskId, String taskRevision, String profileRevision,
            String snapshotId, String templateVersion, String agentReleaseId, String planRevision, String parentRevision,
            String reason, String contentSha256, String createdBy, LocalDateTime createdAt, String classification,
            String registeredProjectId, String registeredKind, String registeredSha256) { }
}
