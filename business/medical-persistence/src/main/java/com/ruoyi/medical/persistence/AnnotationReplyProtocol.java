package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

/** Additive reply projection; the original annotation and fixed document identities remain authoritative. */
public final class AnnotationReplyProtocol {
    private AnnotationReplyProtocol() { }
    public record Create(String document_revision, String expected_revision, String content) { }
    public record Parent(String tenantId, String projectId, String annotationId, String documentId,
            String documentRevision, String currentDocumentRevision, String bodySha256,
            BigInteger documentHeadRevision, BigInteger revision, String status, String classification) { }
    public record Row(String tenantId, String projectId, String replyId, String annotationId,
            String content, String actorId, String createdBy, LocalDateTime createdAt, String classification) { }
}
