package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class UploadTransferProtocol {
    private UploadTransferProtocol() { }
    public static final BigInteger PART_BYTES = BigInteger.valueOf(8388608);

    public record DocumentRow(String tenantId, String projectId, String documentId, String currentDocumentRevision,
            String taskId, String status, String createdBy, LocalDateTime createdAt, String classification,
            BigInteger revision, LocalDateTime updatedAt, String projectStatus) { }

    public record BodyPublicationRow(String tenantId, String projectId, String documentId, String documentRevision,
            String schemaRevision, String schemaRef, String schemaSha256) { }

    public record SessionRow(String tenantId, String projectId, String uploadId, String subjectId, String purpose,
            String sourceUploadId, String grantId, BigInteger expectedSize, String expectedSha256, BigInteger receivedSize,
            String mediaType, String objectKey, String objectKeySha256, BigInteger maxPartBytes, LocalDateTime expiresAt,
            String state, String objectVersion, String contentSha256, String objectRef, String schemaVersion,
            String schemaManifestSha256, String createdBy, LocalDateTime createdAt, String classification,
            BigInteger revision, LocalDateTime updatedAt) { }

    public record PartRow(String tenantId, String projectId, String uploadId, Long partNo, String writeId,
            BigInteger totalSize, BigInteger maxPartBytes, BigInteger startByte, BigInteger endByte, BigInteger sizeBytes,
            String sha256, String objectKey, String objectKeySha256, String state, String objectVersion, String objectRef,
            String schemaVersion, String schemaManifestSha256, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt) { }

    public record BindingRow(String tenantId, String projectId, String grantId, String uploadId, String subjectId,
            String purpose, String documentId, String runId, String stepId, BigInteger expectedRevision,
            BigInteger contextRevision, String principalId, BigInteger leaseGeneration, String dependencyAccessSetId,
            String dependencyManifestSha256, String schemaDocumentSha256, String authorizationManifest,
            String authorizationSha256, String schemaVersion, String schemaManifestSha256, String createdBy,
            LocalDateTime createdAt, String classification) { }

    public record GrantRow(String tenantId, String projectId, String grantId, String subjectId, String purpose,
            String documentId, String runId, String stepId, String objectKey, String objectVersion,
            String objectIdentitySha256, String sha256, BigInteger sizeBytes, String mediaType, String schemaRef,
            LocalDateTime expiresAt, String status, String artifactId, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt) { }

    public record SourceRow(String tenantId, String projectId, String uploadId, String batchId, String sessionId,
            String ownership, String fileName, BigInteger expectedSize, BigInteger receivedSize, String expectedSha256,
            String actualSha256, String objectRef, String status, LocalDateTime expiresAt, String schemaVersion,
            String schemaManifestSha256, String createdBy, LocalDateTime createdAt, String classification,
            BigInteger revision, LocalDateTime updatedAt) { }
}
