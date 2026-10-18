package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
/** Human qualification governance. No professional licence, expert score, or run is generated. */
public final class ExpertQualificationProtocol {
 private ExpertQualificationProtocol(){}
 public record Evidence(String kind,PermissionProtocol.Basis basis){}
 public record Author(String schema_version,String project_id,String expert_id,String domain,List<String> dimensions,String purpose,String valid_from,String valid_to,List<Evidence> evidence,String reason){public Author{dimensions=List.copyOf(dimensions);evidence=List.copyOf(evidence);}}
 public record Definition(String schema_version,String tenant_id,String qualification_id,Author request,String created_by,String created_at){}
 public record Decide(String schema_version,PermissionProtocol.Basis qualification,String expected_revision,String decision,List<PermissionProtocol.Basis> reviewed_evidence,Boolean originals_reviewed,Boolean scope_confirmed,String reason){public Decide{reviewed_evidence=List.copyOf(reviewed_evidence);}}
 public record Decision(String schema_version,PermissionProtocol.Basis qualification,String previous_decision,String expected_revision,String resulting_revision,String decision,Decide request,String decided_by,String decided_at){}
 public record BindScore(String schema_version,String evaluation_id,String score_id,String definition_sha256,String score_sha256,PermissionProtocol.Basis qualification,String domain,String reason){}
 public record BoundScore(String schema_version,BindScore request,PermissionProtocol.Basis approval,EvaluationProtocol.Score score,String bound_by,String bound_at){}
 public record Original(String tenantId,String sourceVersion,String sha256,String objectKey,String objectVersion,BigInteger sizeBytes,String mediaType){public InterfaceProtocol.ObjectRef reference(){return new InterfaceProtocol.ObjectRef(objectKey,objectVersion,sha256,sizeBytes.toString(),mediaType);}}
 public record Journal(String tenantId,String projectId,String recordId,String kind,String qualificationId,String operationId,String idempotencyKey,String requestHash,String bodyHash,String requestJson,String requestContext,String requestSession,String contentJson,String contentHash,String authorizationsJson,String authorizationsHash,String receiptJson,String receiptHash,String auditId,String eventId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 public record Qualification(String tenantId,String projectId,String qualificationId,String contentHash,String expertId,String domain,String purpose,String dimensionsJson,LocalDateTime validFrom,LocalDateTime validTo,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 public record Head(String tenantId,String projectId,String qualificationId,String status,BigInteger revision,String decisionId,String decisionKind,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification,LocalDateTime updatedAt){}
 public record ScoreAuthorityWitness(String tenantId,String projectId,String scoreId,String evaluationId,String sampleId,String candidateId,String dimension,int repeatIndex,String scoreHash,String definitionHash,String originalOperationId,String authorityJson,String authorityHash,String auditId,String eventId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 public record ScoreBinding(String tenantId,String projectId,String bindingId,String bindingHash,String qualificationId,String qualificationHash,String approvalId,String approvalHash,String evaluationId,String definitionHash,String scoreId,String scoreHash,String sampleId,String candidateId,String dimension,int repeatIndex,String scorerId,LocalDateTime scoreCreatedAt,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
}
