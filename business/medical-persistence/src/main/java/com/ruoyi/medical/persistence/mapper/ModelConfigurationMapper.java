package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ModelConfigurationProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelConfigurationMapper {
    List<ModelConfigurationProtocol.Head> head(Map<String,Object> values);
    List<ModelConfigurationProtocol.Stored> stored(Map<String,Object> values);
    List<String> parents(Map<String,Object> values);
    int insertHead(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertModel(Map<String,Object> values);
    int insertDefinition(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    int initializeHead(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
