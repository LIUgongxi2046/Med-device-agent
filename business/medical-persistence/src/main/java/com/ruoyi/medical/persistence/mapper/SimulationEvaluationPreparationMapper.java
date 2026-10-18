package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.SimulationEvaluationPreparationProtocol.Registration;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SimulationEvaluationPreparationMapper {
 List<Registration> registration(Map<String,Object> values);
 List<String> lockSource(Map<String,Object> values);
 List<SelectionOperationRow> operation(Map<String,Object> values);
 List<SelectionOperationRow> operationById(Map<String,Object> values);
 int insertRegistration(Map<String,Object> values);
 int insertArtifact(Map<String,Object> values);
 int insertBinding(Map<String,Object> values);
 int saveOperation(Map<String,Object> values);
 List<String> audit(Map<String,Object> values);
 List<String> event(Map<String,Object> values);
}
