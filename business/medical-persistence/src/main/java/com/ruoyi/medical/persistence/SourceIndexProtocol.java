package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class SourceIndexProtocol {
    static final String CONSUMER="SOURCE_INDEX_FULLTEXT";
    private SourceIndexProtocol() { }
    record Job(String tenant,String event,String attempt) { }
    public record Build(String tenantId,String projectId,String eventId,String sourceVersion,String parseVersion,String attemptId,
        String indexVersion,String configRevision,String rightsRevision,String inputSha256,String manifest,String manifestSha256,
        String status,LocalDateTime startedAt,LocalDateTime deadline,String resultRef,String schemaManifestSha256,
        String createdBy,String classification,BigInteger revision) { }
    public record View(String tenantId,String projectId,String sourceVersion,String parseVersion,String eventId,String indexVersion,
        String buildStatus,BigInteger revision,LocalDateTime startedAt,LocalDateTime deadline,LocalDateTime updatedAt,
        BigInteger expectedCount,BigInteger verifiedCount,String indexStatus,String indexMode,String manifestSha256,
        String indexSha256,String registeredSha256,String inputSha256,String resultRef,String receiptRef,String receiptInputSha256,
        String deliveryStatus,String schemaManifestSha256,String deliverySchema,String receiptSchema,
        String createdBy,String deliveryCreator,String receiptCreator) { }
}
