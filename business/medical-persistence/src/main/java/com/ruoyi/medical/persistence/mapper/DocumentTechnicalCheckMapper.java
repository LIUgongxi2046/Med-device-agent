package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
/** Staged local technical-check storage. Every mutation must run in the existing tenant scope.
 * Common values: projectId,userId,now,classification,manifest (local schema hash).
 * Registration writes use row (Registration); other exact map keys are documented in the XML placeholders.
 * operation command is canonical recoverable state; immutable registration is only written at final commit.
 */
@Mapper public interface DocumentTechnicalCheckMapper {
 record Registration(String tenantId, String projectId, String checkId, String runId, String documentId, String documentRevision, String executionManifestId, String canonicalManifest, String manifestSha256, String canonicalInput, String inputSha256, String canonicalCoverage, String canonicalFindings, String coverageArtifactId, String findingsArtifactId, String coverageIntent, String findingsIntent, String coverageReference, String findingsReference, String accessSetId, String dependencyManifestSha256, String baselineJson, BigInteger postEvidenceWatermark, BigInteger postIssueWatermark, String canonicalRequest, String requestBodySha256, String operationRequestSha256, String operationId, String receiptJson, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification, String executionMode) {}
 record Manifest(String tenantId, String projectId, String executionManifestId, String canonicalManifest, String manifestSha256,
  String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) {}
 List<Manifest> manifest(Map<String,Object> values);
 List<String> existingInput(Map<String,Object> values);
 int insertManifest(Map<String,Object> values);
 record ArtifactOwner(String documentRevision, String runId) {}
 List<ArtifactOwner> artifactOwner(Map<String,Object> values);
 record Head(String tenantId, String projectId, String documentId, String documentRevision, String status, BigInteger revision) {}
 record Check(String tenantId, String projectId, String checkId, String targetKind, String targetId, String targetRevision, String inputSha256, String scopeManifestId, String ruleManifestId, String ruleManifest, String inputWatermarks, BigInteger evidenceWatermark, BigInteger issueWatermark, String coverageArtifactId, String findingsArtifactId, String resultRef, String invalidationReason, String runId, String stepId, String actionId, String status, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) {}
 record Job(String tenantId, String projectId, String runId, String executionKind, String jobType, String initiatorId, String inputManifest, String configRevision, String targetKind, String targetId, String targetRevision, String inputSha256, String scopeManifestId, String ruleManifestId, String coverageArtifactId, String status, String workflowId, LocalDateTime startedAt, LocalDateTime endedAt, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) {}
 record Execution(String tenantId, String projectId, String runId, String executionKind, String scopeManifest, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) {}
 List<Registration> registration(Map<String,Object> values);
 List<Registration> registrationByOperation(Map<String,Object> values);
 List<Head> lockHead(Map<String,Object> values);
 List<Check> check(Map<String,Object> values);
 List<Job> job(Map<String,Object> values);
 List<Execution> execution(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 List<BigInteger> operationRevision(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
 int insertVersion(Map<String,Object> values);
 int insertExecution(Map<String,Object> values);
 int insertJob(Map<String,Object> values);
 int claimJob(Map<String,Object> values);
 int completeJob(Map<String,Object> values);
 int insertCheck(Map<String,Object> values);
 int claimCheck(Map<String,Object> values);
 int completeCheck(Map<String,Object> values);
 int insertRegistration(Map<String,Object> values);
 int insertArtifact(Map<String,Object> values);
 int insertBinding(Map<String,Object> values);
 int insertLineage(Map<String,Object> values);
 int startOperation(Map<String,Object> values);
 int updateCommand(Map<String,Object> values);
 int completeOperation(Map<String,Object> values);
 record RecoveryIntent(String tenantId, String projectId, String operationId, String checkId, String documentRevision, String idempotencyKey, String canonicalCommand, String intentSha256, String startEventId, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) {}
 record RecoveryAttempt(String tenantId, String projectId, String operationId, String checkId, String outputKind, String writeId, String artifactId, String intentSha256, String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) {}
 List<RecoveryIntent> recoveryIntentByKey(Map<String,Object> values);
 List<SelectionOperationRow> recoveryOriginalOperation(Map<String,Object> values);
 List<RecoveryIntent> recoveryIntent(Map<String,Object> values);
 List<RecoveryAttempt> recoveryAttempts(Map<String,Object> values);
 List<String> recoveryStartAudit(Map<String,Object> values);
 List<String> recoveryStartEvent(Map<String,Object> values);
 List<BigInteger> recoveryRevision(Map<String,Object> values);
 int insertRecoveryIntent(Map<String,Object> values);
 int insertRecoveryAttempt(Map<String,Object> values);
}
