package com.ruoyi.medical.persistence.mapper;
import com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface AgentExecutionPolicyMapper {
 List<Row> policy(Map<String,Object> v);List<State> state(Map<String,Object> v);List<Component> components(Map<String,Object> v);
 List<DecisionRow> decisions(Map<String,Object> v);List<String> parents(Map<String,Object> v);List<String> componentParents(Map<String,Object> v);
 List<DomainRow> source(Map<String,Object> v);List<DomainRow> role(Map<String,Object> v);List<DomainRow> model(Map<String,Object> v);
 List<String> list(Map<String,Object> v);
 int insertRegistry(Map<String,Object> v);int insertPolicy(Map<String,Object> v);int insertState(Map<String,Object> v);
 int insertComponent(Map<String,Object> v);int insertLineage(Map<String,Object> v);int insertDecision(Map<String,Object> v);
 int advanceState(Map<String,Object> v);int advanceGovernance(Map<String,Object> v);
}
