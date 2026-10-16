package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

public final class SourceGovernanceProtocol {
    private SourceGovernanceProtocol() { }

    public record DecisionInput(String schema_version, String parse_version, String index_version,
            String rights_revision, String binding_revision, String reviewed_manifest_sha256,
            String decision, String reason, String expected_revision) { }

    public record BindingInput(String schema_version, String source_version, String profile_revision,
            String profile_sha256, String applicability, String reason, String expected_binding_revision,
            boolean approval_attested) { }

    public record ProcessingInput(String schema_version, String project_id, String config_revision,
            String stage, String reason) { }

    public record LifecycleInput(String schema_version, String decision, String reason,
            String policy_revision, String expected_revision) { }

    public record DeletionReceiptInput(String schema_version, String deletion_id, String item_id,
            String store_kind, String target_sha256, JsonNode receipt_ref, String expected_revision,
            String expected_lifecycle_revision) { }

    public record FolderCreateInput(String schema_version, String ownership, String owner_project_id,
            String parent_id, String category, String name) { }

    public record FolderDecisionInput(String schema_version, String decision, String name,
            String target_parent_id, String expected_revision) { }

    public record OrganizationItemInput(String item_id, String source_version, String decision,
            String folder_id, List<String> labels, String expected_revision) { }

    public record OrganizationInput(String schema_version, List<OrganizationItemInput> items) { }

    public record UsageQuery(int limit, String after, String projectId, String usageKind,
            String datasetWatermark) { }

    public record BindingRow(String projectId, String sourceVersion, String bindingRevision,
            String applicability, String decisionManifest) { }

    public record ReviewRow(String tenantId, String sourceId, String sourceVersion, String ownerProjectId,
            String ownership, String title, String classification, String originalSha256, String mediaType,
            BigInteger sizeBytes, String rightsRevision, String rightsPurpose, String rightsStatus,
            String parseVersion, String parseStatus, String indexVersion, String indexMode, String indexStatus,
            BigInteger expectedCount, BigInteger verifiedCount, String approvalRevision, String uploadStatus,
            String approvalStatus, String lifecycle, BigInteger gateRevision, String currentDecision,
            String reviewedManifestSha256, LocalDateTime decidedAt) { }

    public record CatalogueQuery(int limit, String after, String projectId, String ownership,
            String approvalStatus, String lifecycle, Boolean bookmarked, String category, String tag,
            String folderId, String collectionVersion) { }

    public record ProcessingQueueQuery(int limit, String after, String projectId, String status) { }

    public record ArtifactCatalogueQuery(int limit, String after, String projectId, String artifactKind) { }

    public record ArtifactSubmissionEvidence(String source_version, String rights_revision) { }

    public record ArtifactSubmissionRights(String purpose, String license, List<String> allowed_actions,
            List<String> providers, List<String> regions, String valid_from, String valid_to) { }

    public record ArtifactSubmissionInput(String schema_version, String ownership, String target_project_id,
            String category, String title, List<ArtifactSubmissionEvidence> original_evidence,
            ArtifactSubmissionRights rights, String maintainer_id, List<String> applicable_project_ids,
            String reviewer_id) { }

    public record CatalogueRow(String tenantId, String sourceId, String sourceVersion, String ownerProjectId,
            String ownership, String title, String classification, String mediaType, BigInteger sizeBytes,
            String rightsRevision, String rightsStatus, String folderId, String category, String labels,
            BigInteger organizationRevision, Boolean bookmarked, String processingStatus,
            String approvalStatus, String lifecycle, LocalDateTime createdAt) { }

    public record SourceVersionRow(String tenantId, String sourceId, String sourceVersion,
            BigInteger versionNo, String publisherVersion, String originalSha256, String mediaType,
            BigInteger sizeBytes, String rightsRevision, String lifecycle, LocalDateTime createdAt) { }

    public record FolderRow(String tenantId, String folderId, String parentId, String ownerProjectId,
            String category, String name, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt) { }

    public record OrganizationRow(String tenantId, String sourceId, String sourceVersion,
            String ownerProjectId, String ownership, String classification, String folderId,
            String labels, BigInteger revision, Boolean bookmarked) { }

    public record ContentQuery(String parseVersion, String representation, String purpose, BigInteger startByte,
            BigInteger endByte, String chunkId, String locatorSha256, BigInteger afterOrdinal, int limit) { }

    public record ContentHeadRow(String tenantId, String sourceId, String sourceVersion, String ownerProjectId,
            String ownership, String title, String classification, String originalSha256, String objectKey,
            String objectVersion, String mediaType, BigInteger sizeBytes, String rightsRevision,
            String rightsPurpose, String rightsStatus, String parseVersion, String parseStatus,
            String contentSha256, String lifecycle) { }

    public record ContentChunkRow(String sourceVersion, String parseVersion, String chunkId, BigInteger ordinal,
            String nodeId, String nodeKind, String locator, String contentRef, String nodeHash,
            String quoteHash, String rawText) { }

    public record ReadHistoryRow(String userId, String sourceVersion, LocalDateTime lastReadAt, String locator,
            String schemaVersion, String schemaManifestSha256, BigInteger revision) { }

    public record ProcessingConfigRow(String tenantId, String configId, String projectId, String versionId,
            String entityKind, String registeredSha256, String contentSha256, String configurationKind,
            String capabilityKind, String status, String inputSchemaRef, String outputSchemaRef,
            String toolScope, String artifactDigest) { }

    public record ProcessingAttemptRow(String tenantId, String projectId, String attemptId, String sourceVersion,
            String parseVersion, String jobType, String configRevision, String status, String failureManifest,
            String requestManifest, String runId, String schemaVersion, String schemaManifestSha256,
            String createdBy, LocalDateTime createdAt, String classification, BigInteger revision,
            LocalDateTime updatedAt, String parseStatus, String processorManifest) { }

    public record ProcessingQueueRow(String tenantId, String sourceId, String sourceVersion, String ownerProjectId,
            String title, String classification, String projectId, String attemptId, String parseVersion,
            String jobType, String configRevision, String status, String failureManifest, String requestManifest,
            String runId, String createdBy, LocalDateTime createdAt, BigInteger revision,
            LocalDateTime updatedAt, String parseStatus) { }

    public record ArtifactCatalogueRow(String tenantId, String projectId, String artifactId, String runId,
            String documentRevision, String artifactKind, String sha256, BigInteger sizeBytes, String mediaType,
            LocalDateTime createdAt, String classification) { }

    public record SourceEvidenceRow(String sourceVersion, String rightsRevision, String ownerProjectId,
            String purpose) { }

    public record ArtifactSubmissionRow(String tenantId, String projectId, String submissionId, String artifactId,
            String artifactOwnerProjectId, String targetSourceId, String targetSourceVersion,
            String parseVersion, String rightsRevision, String sharingDecision, String originalEvidence, String status, String createdBy,
            LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) { }

    public record UsageRow(String usageKey, String usageKind, String relationKind, String projectId,
            String resourceKind, String resourceId, String versionId, String title, String status,
            String impactStatus, LocalDateTime createdAt) { }

    public record LifecycleStateRow(String tenantId, String sourceId, String sourceVersion,
            String ownerProjectId, String ownership, String originalSha256, String objectKey,
            String objectVersion, String mediaType, BigInteger sizeBytes, String classification,
            LocalDateTime sourceCreatedAt, String status, String lastDecisionId, BigInteger revision,
            String ownerProjectStatus) { }

    public record RetentionPolicyRow(String policyRevision, String category, BigInteger retentionDuration,
            String startEvent, Boolean holdOverride, String approverId, LocalDateTime effectiveFrom,
            String purgeMethod, BigInteger backupExpiry, String status, BigInteger revision) { }

    public record LifecycleDecisionRow(String sourceVersion, String decisionId, String decision,
            String fromStatus, String toStatus, String reason, String policyRevision, String deletionId,
            String actorId, LocalDateTime decidedAt, BigInteger expectedRevision,
            BigInteger resultingRevision, String schemaManifestSha256) { }

    public record DeletionJobRow(String deletionId, String targetVersion, String policyRevision,
            String status, BigInteger revision, LocalDateTime updatedAt) { }

    public record DeletionItemRow(String tenantId, String sourceId, String sourceVersion,
            String ownerProjectId, String ownership, String deletionId, String jobStatus,
            BigInteger jobRevision, String itemId, String storeKind, String targetRef,
            String targetSha256, String itemStatus, String receiptRef, LocalDateTime backupExpiry,
            BigInteger itemRevision, String lifecycleStatus, BigInteger lifecycleRevision,
            String classification) { }
}
