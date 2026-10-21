package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** N05 text-only first-call projection. Data projection does not mint a verifier proof. */
final class TaskSourceVerifierInvocationProjection {
 private TaskSourceVerifierInvocationProjection() { }
 static JsonNode initial(JsonNode observed,String session,String launch,List<String> systems,JsonNode tools) {
  if(!tools.isArray()||!tools.isEmpty())throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
  return TaskInvocationProjection.initial(observed,session,launch,systems,tools);
 }
}
