package com.ruoyi.medical.persistence.mapper;
import java.time.LocalDateTime;
import java.math.BigInteger;
import java.util.*;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AgentPublicationMapper {
 record Journal(String tenantId,String projectId,String recordId,String kind,String resourceId,String operationId,String idempotencyKey,String requestSha256,String requestJson,String contentJson,String contentSha256,String authorizationsJson,String authorizationsSha256,String receiptJson,String receiptSha256,String auditId,String eventId,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification){}
 record Agent(String tenantId,String projectId,String agentId,String versionId,String name,String dependencyManifest,String limits,String evaluationId,String contentSha256,String status,String schemaVersion,String schemaManifest,String createdBy,LocalDateTime createdAt,String classification,BigInteger revision){}
 List<Journal> policyDecision(Map<String,Object> v);int insertPolicyDecision(Map<String,Object> v);
 List<Journal> capabilityAuthor(Map<String,Object> v);
 List<String> candidateRecords(Map<String,Object> v);
 List<com.ruoyi.medical.persistence.ArtifactRegistryProtocol.ArtifactRow> packageArtifact(Map<String,Object> v);
 List<com.ruoyi.medical.persistence.ArtifactRegistryProtocol.BindingRow> packageBinding(Map<String,Object> v);
 List<Journal> journal(Map<String,Object> v);List<Journal> journalByKey(Map<String,Object> v);
 List<SelectionOperationRow> operation(Map<String,Object> v);
 List<String> audit(Map<String,Object> v);List<String> event(Map<String,Object> v);
 List<com.ruoyi.medical.persistence.ArtifactRegistryProtocol.VersionRow> version(Map<String,Object> v);
 List<Agent> agent(Map<String,Object> v);
 int insertJournal(Map<String,Object> v);int insertOperation(Map<String,Object> v);
 int insertCapability(Map<String,Object> v);
 int insertVersion(Map<String,Object> v);int insertHead(Map<String,Object> v);int insertAgent(Map<String,Object> v);int initializeHead(Map<String,Object> v);
 int insertLineage(Map<String,Object> v);
}
