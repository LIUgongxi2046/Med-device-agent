package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.ModelVerificationInputProtocol.*;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface ModelVerificationInputMapper {
 List<Original> original(Map<String,Object> values);
 List<String> lockActor(Map<String,Object> values);
 List<String> lockSource(Map<String,Object> values);
 LocalDateTime databaseTime(Map<String,Object> values);
 List<Registration> registration(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 int insertVersion(Map<String,Object> values);
 int insertAccessSet(Map<String,Object> values);
 int insertManifest(Map<String,Object> values);
 int insertMember(Map<String,Object> values);
 int insertLineage(Map<String,Object> values);
 int insertArtifact(Map<String,Object> values);
 int insertBinding(Map<String,Object> values);
 int insertRegistration(Map<String,Object> values);
 int saveOperation(Map<String,Object> values);
 int saveAudit(Map<String,Object> values);
 int saveEvent(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
}
