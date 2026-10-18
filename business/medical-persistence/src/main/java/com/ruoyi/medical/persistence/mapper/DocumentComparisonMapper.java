package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
/** Immutable comparison identity/core plus operation CAS. Every map carries current userId.
 * Common: projectId,userId,now,classification,manifest (local schema manifest hash).
 * See XML placeholders for exact method-specific keys. No method creates rights or grants.
 */
@Mapper public interface DocumentComparisonMapper {
 record Intent(String tenantId,String projectId,String comparisonId,String operationId,String manifestId,String accessSetId,String artifactId,String writeId,String startEventId,String documentId,String leftRevision,String rightRevision,String requestContext,String requestSession,String idempotencyKey,String canonicalRequest,String requestBodySha256,String operationRequestSha256,String inputManifest,String inputSha256,String dependencyManifest,String dependencySha256,String canonicalCore,String coreSha256,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,LocalDateTime deadline,String classification) {}
 record Registration(String tenantId,String projectId,String comparisonId,String operationId,String artifactId,String intentSha256,String reference,String receiptJson,String coreSha256,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification) {}
 record Attempt(String tenantId,String projectId,String comparisonId,String operationId,String writeId,String intentSha256,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification) {}
 List<Attempt> attempt(Map<String,Object> values);
 int insertAttempt(Map<String,Object> values);
 record ArtifactOwner(String documentRevision,String runId) {}
 List<Intent> intent(Map<String,Object> values);
 List<Intent> intentByKey(Map<String,Object> values);
 List<Intent> intentByOperation(Map<String,Object> values);
 List<Registration> registration(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 List<BigInteger> operationRevision(Map<String,Object> values);
 List<String> startAudit(Map<String,Object> values);
 List<String> startEvent(Map<String,Object> values);
 List<String> finalAudit(Map<String,Object> values);
 List<String> finalEvent(Map<String,Object> values);
 List<ArtifactOwner> artifactOwner(Map<String,Object> values);
 int insertVersion(Map<String,Object> values);
 int insertAccessSet(Map<String,Object> values);
 int insertAccessManifest(Map<String,Object> values);
 int insertAccessMember(Map<String,Object> values);
 int insertLineage(Map<String,Object> values);
 int insertIntent(Map<String,Object> values);
 int startOperation(Map<String,Object> values);
 int updateCommand(Map<String,Object> values);
 int insertArtifact(Map<String,Object> values);
 int insertBinding(Map<String,Object> values);
 int insertRegistration(Map<String,Object> values);
 int completeOperation(Map<String,Object> values);
}
