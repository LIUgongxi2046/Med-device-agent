package com.ruoyi.medical.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

public final class RetrievalProtocol {
    private RetrievalProtocol() { }

    public record SearchInput(String schema_version, String project_id, String query, String purpose) { }

    public record QueryRow(String tenantId, String projectId, String retrievalId, String subjectId, String queryText,
            String querySha256, String purpose, String mode, String selectionSha256, BigInteger authEpoch,
            BigInteger governanceEpoch, BigInteger resultCount, String status, String createdBy,
            LocalDateTime createdAt, String classification) { }

    public record Candidate(String sourceId, String sourceVersion, String parseVersion, String indexVersion,
            String chunkId, String approvalRevision, String rightsRevision, String rightsPurpose,
            String title, String excerpt, boolean excerptTruncated, String locator, String locatorKind,
            String quoteHash, BigDecimal relevance) { }

    public record Hit(BigInteger rank, String sourceId, String sourceVersion, String parseVersion,
            String indexVersion, String chunkId, String approvalRevision, String rightsRevision,
            String rightsPurpose, String title, String excerpt, boolean excerptTruncated, String locator,
            String locatorKind, String quoteHash, BigDecimal relevance) { }

    public record PageQuery(int page, int pageSize) { }
}
