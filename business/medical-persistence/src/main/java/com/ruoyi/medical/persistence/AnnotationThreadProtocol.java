package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;

public final class AnnotationThreadProtocol {
    private AnnotationThreadProtocol() { }
    public record Row(String tenantId, String projectId, String annotationId, String documentRevision, String nodeId,
            String selectionJson, String selectionArtifactId, String selectionSha256, String status, String content,
            String actorId, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt,
            String classification, BigInteger revision, LocalDateTime updatedAt, BigInteger replyCount) { }
    public record Command(String annotationId, String artifactId, String requestId, String documentRevision,
            String expectedRevision, String bodySha256, String authorizationSha256, String authorizationClock,
            String selectionSha256, S3ObjectWriter.WriteIntent intent, boolean writing, InterfaceProtocol.ObjectRef reference) { }
    public record Review(String reviewId, String documentRevision, String decision, String targetKey) { }
    public record Invalidation(String invalidationId, String reviewId, String triggerVersion, String reasonCode) { }
}
