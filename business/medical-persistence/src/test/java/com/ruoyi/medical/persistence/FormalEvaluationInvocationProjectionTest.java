package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
/** Deferred source only: no test execution requested during implementation. */
class FormalEvaluationInvocationProjectionTest {
 @Test void neverAcceptTaskPurposeAsEvaluation()throws Exception {
  var json=new ObjectMapper();var approved=json.readTree("{\"messages\":[{\"role\":\"system\",\"content\":\"fixed\"},{\"role\":\"user\",\"content\":\"sample\"}],\"tools\":[]}");
  var parameters=json.readTree("{\"purpose\":\"TASK\"}");var observed=json.readTree("{\"provider\":\"enterprise-model\",\"parameters\":{\"purpose\":\"TASK\"}}");
  assertThrows(TenantAccessException.class,()->FormalEvaluationInvocationProjection.require(observed,"7a57bb86-093a-4a41-8049-4ff44d5e68d6",approved,parameters));
 }
 @Test void preservesActuallyApprovedTools()throws Exception {
  var json=new ObjectMapper();var approved=json.readTree("{\"messages\":[{\"role\":\"user\",\"content\":\"sample\"}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"read\",\"description\":\"fixed\",\"parameters\":{\"type\":\"object\"}}}]}");
  var observed=json.readTree("{\"provider\":\"enterprise-model\",\"session_id\":\"session\",\"system\":null,\"parameters\":{\"purpose\":\"EVALUATION\"},\"tool_history\":null,\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"sample\"}]}],\"tools\":[{\"name\":\"read\",\"description\":\"fixed\",\"parameters\":{\"type\":\"object\"}}]}");
  assertEquals(approved,FormalEvaluationInvocationProjection.require(observed,"session",approved,observed.required("parameters")));
 }
 @Test void terminalCannotBeConstructedByCaller(){assertTrue(java.util.Arrays.stream(FormalEvaluationInvocationService.Terminal.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));}
 @Test void evaluationIsNotTaskBinding(){assertFalse(FormalInvocationBinding.class.isAssignableFrom(FormalEvaluationInvocationService.Terminal.class));}
}
