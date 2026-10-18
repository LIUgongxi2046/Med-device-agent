package com.ruoyi.medical.persistence.mapper;
import java.util.*;import org.apache.ibatis.annotations.Mapper;
@Mapper public interface FormalCapabilityCancellationMapper {
 record Cancellation(String tenantId,String projectId,String cancellationId,String fixedJson,String contentHash,String operationId,String cancelKey,String originalKind,String originalKey,String originalHash,String createdBy,String createdAt,String classification,String schemaVersion,String manifest){}
 List<Cancellation> cancellationByOriginal(Map<String,Object> v);List<Cancellation> cancellationByKey(Map<String,Object> v);
 List<String> cancellationOperation(Map<String,Object> v);List<String> cancellationAudit(Map<String,Object> v);List<String> cancellationEvent(Map<String,Object> v);
 int insertCancelOperation(Map<String,Object> v);int insertCancellation(Map<String,Object> v);
}
