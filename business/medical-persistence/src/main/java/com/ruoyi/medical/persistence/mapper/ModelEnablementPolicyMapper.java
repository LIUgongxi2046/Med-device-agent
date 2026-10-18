package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.ModelEnablementPolicyProtocol.*;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.*;import java.time.LocalDateTime;import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ModelEnablementPolicyMapper {
 record Classification(String kind,String id,String sha256,String classification){}
 List<Classification> classifications(Map<String,Object> values);
 List<Original> original(Map<String,Object> values);
 List<String> lockActor(Map<String,Object> values);
 List<String> lockSource(Map<String,Object> values);
 LocalDateTime databaseTime(Map<String,Object> values);
 List<Stored> stored(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 int insertStored(Map<String,Object> values);
 int insertOperation(Map<String,Object> values);
 int insertAudit(Map<String,Object> values);
 int insertEvent(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
}
