package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.CommonProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskProtocol {
    public record Query(int page, int pageSize, String watermark, String filterSha256, String status,
                        String ownerId, java.time.LocalDateTime updatedFrom, java.time.LocalDateTime updatedTo) { }

    private TaskProtocol() { }

    public record Create(String schema_version, String project_id, String profile_revision, String document_type,
            String template_version, String agent_release_id, CommonProtocol.SourceScope scope_selection,
            String owner_id, String goal, String based_on_document_revision) { }

    public record ValidatedCreate(Create request, String canonical_request, String request_sha256,
            String scope_sha256, String schema_manifest_sha256) { }

    public record PlanVersionInput(String schema_version, String expected_revision, String base_version, String schema_ref,
            ArtifactProtocol.PlanCandidate content, String reason) { }

    public record Head(String tenantId, String projectId, String taskId, String status, BigInteger revision,
            String ownerId, String taskRevision, String profileRevision, String snapshotId, String templateVersion,
            String agentReleaseId, String planRevision, String contentHash, String registryHash, LocalDateTime updatedAt) { }

    public record Input(String documentType, String ownerId, String goal, String basedOnDocumentRevision,
            String requestJson, String requestHash, String scopeJson, String scopeHash, String missingRequirements,
            String requirementsHash, String schemaVersion, String manifest) { }

    public record Snapshot(String tenantId, String projectId, String snapshotId, String manifest, String manifestHash,
            BigInteger authEpoch, BigInteger governanceEpoch, LocalDateTime createdAt, String registryHash) { }

    public record ScopeSnapshotManifest(String schema_version, String tenant_id, String project_id, String snapshot_id,
            String profile_revision, CommonProtocol.SourceScope scope_selection, String auth_epoch,
            String governance_epoch, List<ScopeSnapshotMember> members) {
        public ScopeSnapshotManifest { members = List.copyOf(members); }
    }

    public record ScopeSnapshotMember(String source_id, String source_version, String parse_version, String index_version,
            String approval_revision, String binding_revision, String rights_revision, String original_sha256) { }

    public record Plan(String planRevision, String taskRevision, String templateVersion, String snapshotId,
            String status, String decisionId, String contentJson, String contentHash, String registryHash,
            String schemaRef, String schemaVersion, String manifest) { }
}
