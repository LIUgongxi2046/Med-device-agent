package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class SourceUploadProtocol {
    public static final String COMPLETION_CONSUMER = "SOURCE_UPLOAD_COMPLETION";
    private SourceUploadProtocol() { }
    public record Declaration(String tenantId, String projectId, String uploadId, String operationId, String sourceId,
            String versionIntent, String maintainerId, String requestJson, String requestSha256, String schemaRef,
            String schemaDocumentSha256, String authorityGrant, BigInteger authorityRevision, BigInteger contextRevision,
            BigInteger authEpoch, BigInteger governanceEpoch, String schemaVersion, String schemaManifestSha256,
            String createdBy, LocalDateTime createdAt, String classification) { }
    public record Owner(String sourceId, String ownerProjectId, String ownership, String classification) { }
    public record CompletionDelivery(String tenantId, String eventId, String consumerId, String status,
            BigInteger attemptNo, LocalDateTime nextAttemptAt, String leaseOwner, LocalDateTime leaseUntil,
            BigInteger leaseGeneration, String lastError, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt, String schemaVersion, String schemaManifestSha256) { }
}
