package com.ruoyi.medical.persistence.mapper;
import java.util.*;import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ConnectionVerificationMapper {
 record Intent(String tenantId,String projectId,String verificationId,String actorId,String modelRevision,String configId,String modelHash,String credentialReferenceId,String credentialRevision,String requestJson,String requestHash,String key,String operationId,String schemaVersion,String manifest,String classification,String createdAt,String grantId,String grantRevision,String auditId,String eventId){}
 record Binding(String tenantId,String projectId,String verificationId,String runId,String stepId,String actionId,String invocationId,String requestHash,String inputHash,String modelRevision,String credentialReferenceId,String credentialRevision,String receiptJson,String createdBy,String createdAt,String schemaVersion,String manifest,String classification){}
 record Completed(String tenantId,String projectId,String verificationId,String invocationId,String artifactJson,String receiptHash,String priceVersion,String currency,String settledAmount,String usageReceiptId,String auditId,String eventId,String createdBy,String createdAt,String schemaVersion,String manifest,String classification){}
 List<Intent> intent(Map<String,Object> values);List<Intent> byKey(Map<String,Object> values);List<Binding> binding(Map<String,Object> values);List<Completed> completed(Map<String,Object> values);
 List<String> intentAudit(Map<String,Object> values);List<String> intentEvent(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);List<String> event(Map<String,Object> values); List<String> verification(Map<String,Object> values); List<String> operation(Map<String,Object> values); int insertOperation(Map<String,Object> values); int completeOperation(Map<String,Object> values);
 int insertIntent(Map<String,Object> values);int insertVerification(Map<String,Object> values);int insertBinding(Map<String,Object> values);int markRunning(Map<String,Object> values);int insertCompleted(Map<String,Object> values);int markPassed(Map<String,Object> values);
}
