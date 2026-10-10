package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.AgentReleaseStoredProtocol;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentReleaseMapper {
    List<AgentReleaseStoredProtocol.ReleaseRow> release(Map<String,Object> values);
    List<AgentReleaseStoredProtocol.AgentRow> agent(Map<String,Object> values);
    List<AgentReleaseStoredProtocol.EvaluationRow> evaluation(Map<String,Object> values);
    List<AgentReleaseStoredProtocol.ScopeRow> scopes(Map<String,Object> values);
    List<AgentReleaseStoredProtocol.RoleOrigin> roleOrigins(Map<String,Object> values);
}
