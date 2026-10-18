package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
/** Compile/install only with the real B model eligibility implementation. No alternate success path. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${medical.model-runtime.enabled:false} and ${medical.formal-invocation.enabled:false}")
public final class AgentExecutionPolicyModelAdapter implements AgentExecutionPolicyModelPort {
 private final ModelEligibilityAuthority models;private final PermissionSchema json;
 public AgentExecutionPolicyModelAdapter(ModelEligibilityAuthority models,PermissionSchema json){this.models=models;this.json=json;}
 @Override public JsonNode require(EnterpriseAccess.Admission a,AgentExecutionPolicyProtocol.NodeBinding node,String project,List<String> classifications,String region)throws Exception{
  var current=models.require(a,node.modelVersion().version_id(),AgentExecutionPolicySchema.PURPOSE,project);
  if(!node.modelVersion().content_sha256().equals(current.contentSha256()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  for(String classification:classifications)current.requireDataUse(classification,region,false);
  current.requireParameters(node.generationParameters());current.requireUnchanged();
  var value=json.object();value.set("model_binding",json.tree(current.safeReplayBinding()));value.set("model_authorizations",AgentPublicationWitness.tree(current.authorizations()));return value;
 }
}
