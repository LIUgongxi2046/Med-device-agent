package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
/** Local RECORD_ONLY export persistence. All calls require EnterpriseAccess/TenantScope and userId.
 * Intent is the immutable source of truth, never operation response_ref. Immutable Attempt is the sole write-claim authority. Operation revision 1 is prepared,
 * 2 is durably attempted, 3 is completed. No command mutation or revision reset is exposed.
 * Writes take row (Intent/Registration). Other keys are explicit in the mapper XML.
 */
@Mapper public interface GruRecordExportMapper {
 record Intent(String tenantId, String projectId, String exportId, String conversationId, String ownerId, BigInteger conversationRevision, String documentId, String documentRevision, String snapshotId, String canonicalRequest, String requestBodySha256, String operationRequestSha256, String operationId, String idempotencyKey, String sessionId, String contextJson, String basisJson, String messageManifestJson, String messageManifestSha256, BigInteger messageCount, String canonicalContent, String contentSha256, BigInteger sizeBytes, String writeIntentJson, String artifactId, String accessSetId, String dependencyManifestSha256, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, LocalDateTime deadline, String classification) {}
 record Registration(String tenantId, String projectId, String exportId, String operationId, String artifactId, String accessSetId, String dependencyManifestSha256, String objectReference, String contentSha256, BigInteger sizeBytes, String receiptJson, String auditEventId, String outboxEventId, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) {}
 record Attempt(String tenantId, String projectId, String exportId, String operationId, String createdBy, LocalDateTime attemptedAt, String schemaVersion, String schemaManifest, String classification, LocalDateTime createdAt) {}
 record ArtifactOwner(String documentRevision, String runId) {}
 List<Attempt> attempt(Map<String,Object> values);
 List<ArtifactOwner> artifactOwner(Map<String,Object> values);
 int insertAttempt(Map<String,Object> values);
 int insertBinding(Map<String,Object> values);
 List<Intent> intent(Map<String,Object> values);
 List<Intent> intentById(Map<String,Object> values);
 List<Intent> intentByKey(Map<String,Object> values);
 List<Intent> intentByOperation(Map<String,Object> values);
 List<Registration> registration(Map<String,Object> values);
 List<Registration> registrationByOperation(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 List<BigInteger> operationRevision(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
 int insertIntent(Map<String,Object> values);
 int insertRegistration(Map<String,Object> values);
 int startOperation(Map<String,Object> values);
 int markAttempted(Map<String,Object> values);
 int completeOperation(Map<String,Object> values);
 int insertVersion(Map<String,Object> values);
 int insertArtifact(Map<String,Object> values);
 int insertLineage(Map<String,Object> values);
}
