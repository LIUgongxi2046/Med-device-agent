package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.SourceRightsProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceRightsMapper {
    List<SourceRightsProtocol.StateRow> state(Map<String,Object> values);
    List<SourceRightsProtocol.OperationRow> operation(Map<String,Object> values);
    int insertRights(Map<String,Object> values);
    int replaceSourceRights(Map<String,Object> values);
    int revokeRights(Map<String,Object> values);
    int insertEvidenceLineage(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    int insertDecision(Map<String,Object> values);
    int advanceState(Map<String,Object> values);
}
