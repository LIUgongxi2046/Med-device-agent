package com.ruoyi.medical.persistence;
import java.time.LocalDateTime;
/** Immutable registration; original bytes and authority remain independently verifiable. */
public final class SimulationEvaluationPreparationProtocol {
 private SimulationEvaluationPreparationProtocol() { }
 public record Registration(String tenantId,String artifactId,String kind,String sourceVersion,String rightsRevision,
   String canonicalDocument,String documentSha256,String canonicalRequest,String requestBodySha256,
   String operationRequestSha256,String receiptJson,String operationId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification) { }
}
