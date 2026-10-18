package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
/** Optional real model-governance integration. Absence rejects every model-bearing policy approval.
 * Returned JSON is a descriptive verified snapshot, not an independently usable execution permit. */
public interface AgentExecutionPolicyModelPort {
 JsonNode require(EnterpriseAccess.Admission admission,AgentExecutionPolicyProtocol.NodeBinding node,String project,List<String> classifications,String region)throws Exception;
}
