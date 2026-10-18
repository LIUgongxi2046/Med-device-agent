package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.EvaluationProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EvaluationMapper {
    List<EvaluationProtocol.Stored> definition(Map<String,Object> values);
    List<EvaluationProtocol.AgentRow> candidate(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.SimulationEvaluationCandidateAuthority.ConfigurationState> configurationStates(Map<String,Object> values);
    List<String> simulationPrompt(Map<String,Object> values);
    List<EvaluationProtocol.StoredScore> scores(Map<String,Object> values);
    List<String> parents(Map<String,Object> values);
    int insertVersion(Map<String,Object> values);
    int insertBatch(Map<String,Object> values);
    int insertDefinition(Map<String,Object> values);
    int insertLineage(Map<String,Object> values);
    int insertScore(Map<String,Object> values);
    int insertObservation(Map<String,Object> values);
    int advanceBatch(Map<String,Object> values);
    int insertResult(Map<String,Object> values);
    List<SelectionOperationRow> currentOperation(Map<String,Object> values);
    int saveOperation(Map<String,Object> values);
    List<String> audit(Map<String,Object> values);
    List<String> event(Map<String,Object> values);
}
