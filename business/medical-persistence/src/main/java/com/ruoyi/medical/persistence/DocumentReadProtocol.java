package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class DocumentReadProtocol {
    private DocumentReadProtocol() { }
    public record Header(String tenantId, String projectId, String documentId, String documentRevision,
            String currentDocumentRevision, BigInteger revision, String status, String projectStatus, String taskId, String taskRevision,
            String snapshotId, String profileRevision, String bodyArtifactId, String bodySha256, String objectKey,
            String objectVersion, String bodySchemaRevision, String planRevision, String templateVersion,
            String accessSetId, BigInteger issueWatermark, String contentSha256, String createdBy,
            LocalDateTime createdAt, String classification) { }
    public record Projection(String artifactId, String artifactKind) { }
    public record Watermarks(BigInteger issueWatermark, BigInteger evidenceWatermark) { }
    public record StreamRow(String streamId, String resourceId, String sequence) { }
}
