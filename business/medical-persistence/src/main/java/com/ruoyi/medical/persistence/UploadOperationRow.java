package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record UploadOperationRow(String tenantId, String operationId, String subjectId, String operationName,
        String idempotencyKey, String requestSha256, String state, Integer httpStatus, String responseRef,
        String targetId, String errorCode, String schemaVersion, String schemaManifestSha256, String createdBy,
        LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt,
        LocalDateTime dedupExpiresAt, String dedupPolicyRevision) { }
