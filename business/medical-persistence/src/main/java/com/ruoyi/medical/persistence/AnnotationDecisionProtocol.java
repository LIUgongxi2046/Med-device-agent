package com.ruoyi.medical.persistence;
import java.math.BigInteger;
import java.time.LocalDateTime;
public final class AnnotationDecisionProtocol {
 private AnnotationDecisionProtocol() { }
 public record History(String kind, BigInteger previousRevision, BigInteger revision, String previousStatus, String status) { }
 public record Create(String document_revision, String expected_revision, String expected_document_revision, String decision, String reason) { }
 public record Row(String tenantId, String projectId, String decisionId, String annotationId, String documentRevision,
   String decision, String reason, String previousStatus, String status, BigInteger previousRevision, BigInteger revision,
   String actorId, String createdBy, LocalDateTime createdAt, String classification, String schemaVersion, String schemaManifest) { }
}
