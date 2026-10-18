package com.ruoyi.medical.persistence.mapper;
import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
/** Read-only supported audit adapter. Every map MUST carry current userId for TenantScope.
 * scan: projectId,action,from,to,actorId(nullable),afterCreatedAt/afterAuditId(both nullable),limit.
 * payload: projectId,auditId,maximumPayloadBytes. operation: projectId,auditId,operationId.
 * operationResponse: operation keys plus maximumPayloadBytes. target: projectId,auditId,documentId,documentRevision.
 * Payload methods retain oversized/missing values as null sentinels; never interpret null as no audit.
 * Document filter is deliberately applied only after bounded scan and target authorization by service.
 */
@Mapper public interface BusinessAuditMapper {
 record Candidate(String tenantId,String projectId,String auditId,String subjectId,String action,String result,String reasonCode,String requestId,String traceId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification,BigInteger payloadBytes) {}
 record Operation(String operationId,String requestSha256,String subjectId,String operationName,String state,Integer httpStatus,String errorCode,String targetId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification,BigInteger responseBytes) {}
 record Target(String revisionProjectId,String revisionDocumentId,String documentRevision,String revisionSnapshotId,String revisionBodySha256,String metadataProjectId,String metadataDocumentId,String metadataRevision,String metadataSnapshotId,String metadataBodySha256,String metadataContentSha256,String basedOnRevision,String createdBy,LocalDateTime createdAt,String classification,String versionId,String versionProjectId,String versionKind,String versionContentSha256,String ownerProjectId,String ownerDocumentId) {}
 List<Candidate> scan(Map<String,Object> values);
 List<String> payload(Map<String,Object> values);
 List<Operation> operation(Map<String,Object> values);
 List<String> operationResponse(Map<String,Object> values);
 List<Target> target(Map<String,Object> values);
 List<String> checks(Map<String,Object> values);
 List<String> reviews(Map<String,Object> values);
 List<String> signatures(Map<String,Object> values);
}
