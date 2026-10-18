package com.ruoyi.medical.persistence.mapper;
import java.util.*;import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ModelEligibilityMapper {
 record Row(String tenantId,String projectId,String eligibilityId,String modelRevision,String configId,String verificationId,String fixedJson,String contentHash,String requestHash,String operationId,String key,String createdBy,String createdAt,String schemaVersion,String manifest,String classification,String status,String revision,String receiptJson,String auditId,String eventId){}
 List<Row> model(Map<String,Object> values);List<Row> byKey(Map<String,Object> values);List<String> registry(Map<String,Object> values);List<String> operation(Map<String,Object> values);List<String> audit(Map<String,Object> values);List<String> event(Map<String,Object> values);
 int insertEligibility(Map<String,Object> values);int insertVersion(Map<String,Object> values);int insertOperation(Map<String,Object> values);
}
