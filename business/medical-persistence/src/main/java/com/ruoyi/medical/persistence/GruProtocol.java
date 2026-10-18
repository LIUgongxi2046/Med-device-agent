package com.ruoyi.medical.persistence;
import java.math.BigInteger;
import java.time.LocalDateTime;
public final class GruProtocol {
 private GruProtocol() { }
 public record Counts(BigInteger messages,BigInteger records) { }
 public record Head(String tenantId,String projectId,String conversationId,String ownerId,String documentId,String status,
  BigInteger revision,String createdBy,LocalDateTime createdAt,String classification) { }
 public record Context(String tenantId,String conversationId,String projectId,String documentId,String documentRevision,String snapshotId,
  String scopeJson,String scopeManifestHash,String accessSetId,String canonicalRequest,String requestBodyHash,String operationRequestHash,
  String operationId,String receiptJson,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification) { }
 public record Message(String tenantId,String projectId,String conversationId,String messageId,String snapshotId,String documentRevision,
  String selectionJson,String contentRef,String contentHash,String accessSetId,String operationId,String candidateIds,String senderKind,
  String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification,BigInteger sequence,
  String canonicalRequest,String requestBodyHash,String operationRequestHash,String receiptJson,String intent,String executionState) { }
}
