package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class AgentReleaseStoredProtocol {
    private AgentReleaseStoredProtocol() { }

    public record ReleaseRow(String tenantId, String projectId, String releaseId, String agentVersionId,
            String manifestArtifactId, String manifest, String manifestSha256, String contentSha256,
            String approvedBy, LocalDateTime releasedAt, String schemaVersion, String schemaManifest,
            String createdBy, LocalDateTime createdAt, String classification,
            String registeredProjectId, String registeredKind, String registeredSha256) { }

    public record AgentRow(String tenantId, String projectId, String agentVersionId, String agentId, String status,
            String dependencyManifest, String limits, String evaluationId, String contentSha256,
            String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt,
            String registeredProjectId, String registeredKind, String registeredSha256) { }

    public record EvaluationRow(String tenantId, String projectId, String evaluationId, String datasetVersion,
            String sampleManifest, String sampleManifestArtifactId, String ruleRevision, String releaseCandidates,
            String authorizedUse, String splitManifest, String variancePolicy, String missingRequirements,
            String status, String contentSha256, String schemaVersion, String schemaManifest,
            String createdBy, LocalDateTime createdAt, String classification, BigInteger revision,
            LocalDateTime updatedAt, String registeredProjectId, String registeredKind, String registeredSha256) { }

    public record ScopeRow(String tenantId, String projectId, String scopeRevision, String releaseId,
            String audienceManifest, String status, String contentSha256, String schemaVersion, String schemaManifest,
            String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt,
            String registeredProjectId, String registeredKind, String registeredSha256) { }

    public record RoleOrigin(String grantId, String roleVersion, String roleId, String invitationId) { }
}
