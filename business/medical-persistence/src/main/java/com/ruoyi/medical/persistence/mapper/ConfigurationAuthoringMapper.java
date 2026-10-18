package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ConfigurationAuthoringProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigurationAuthoringMapper {
    List<ConfigurationAuthoringProtocol.Stored> stored(Map<String,Object> values);
    List<ConfigurationAuthoringProtocol.Parent> parents(Map<String,Object> values);
    int insertHead(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertCapability(Map<String,Object> values);
    int insertAgent(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    int initializeHead(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int advanceGovernance(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
