package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record SourceProcessingRow(String tenantId, String projectId, String attemptId, String sourceVersion,
        String parseVersion, String configRevision, String status, String failureManifest, String requestManifest,
        String runId, String createdBy, String classification, BigInteger revision, String leaseOwner,
        BigInteger leaseGeneration, LocalDateTime leaseExpiresAt, LocalDateTime executionDeadline,
        String commandRef, String resultRef, String writeIntent, String sourceId, String ownerProjectId,
        String ownership, String rightsRevision, String originalSha256, String objectKey, String objectVersion,
        String mediaType, BigInteger sizeBytes, String parseStatus, String lifecycle) { }
