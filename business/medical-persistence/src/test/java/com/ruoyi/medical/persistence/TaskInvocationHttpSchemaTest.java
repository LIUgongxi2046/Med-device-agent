package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskInvocationHttpSchemaTest {
 private ObjectNode request()throws Exception{var f=new TaskInvocationFixture();var action=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(f.action); // ActionRequest Java names deliberately not the wire projection.
  var n=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("expected_dispatch_generation","0");var a=n.putObject("worker_action");a.put("run_id",f.run).put("step_id",f.step).put("action_id",f.action.actionId()).put("lease_generation","1").put("input_sha256",f.hash).put("arguments_sha256",f.binding.requestHash()).put("kind","MODEL_CALL").put("tool_version",f.modelId).put("action","model.send").put("purpose","DOCUMENT_BODY");a.set("scope",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(f.action.scope()));return n;
 }
 private TaskInvocationHttpSchema schema()throws Exception{return new TaskInvocationHttpSchema(new FormalInvocationSchema());}
 @Test void explicitModelCallAndStringGenerationAreRead()throws Exception{var s=schema();var n=request();var actual=s.action(s.read("BeginSend",ContentReadAuthorization.canonicalBytes(n)));assertEquals("MODEL_CALL",actual.kind());assertEquals("DOCUMENT_BODY",actual.purpose());assertEquals(java.math.BigInteger.ONE,actual.generation());}
 @Test void hostOrVerificationAuthorityCannotUseTaskTransport()throws Exception{var s=schema();var n=request();((ObjectNode)n.required("worker_action")).put("kind","HOST_CONTROL");assertThrows(TenantAccessException.class,()->s.read("BeginSend",ContentReadAuthorization.canonicalBytes(n)));((ObjectNode)n.required("worker_action")).put("kind","MODEL_CALL").put("purpose","MODEL_VERIFICATION");assertThrows(TenantAccessException.class,()->s.read("BeginSend",ContentReadAuthorization.canonicalBytes(n)));}
 @Test void numericGenerationAndUnpublishedKeysAreRejected()throws Exception{var s=schema();var n=request();((ObjectNode)n.required("worker_action")).put("lease_generation",1);assertThrows(TenantAccessException.class,()->s.read("BeginSend",ContentReadAuthorization.canonicalBytes(n)));((ObjectNode)n.required("worker_action")).put("lease_generation","1");n.put("provider_body","caller body");assertThrows(TenantAccessException.class,()->s.read("BeginSend",ContentReadAuthorization.canonicalBytes(n)));}
 @Test void malformedUtf8DoesNotBecomeReplacementContent()throws Exception{var s=schema();assertThrows(java.nio.charset.CharacterCodingException.class,()->s.read("BeginSend",new byte[]{(byte)0xc3,(byte)0x28}));}
 @Test void recoveryAcceptsOnlyOriginalIdentifiersWithoutExecutionFields()throws Exception {
  var s=schema();var n=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0");
  for(String field:java.util.List.of("tenant_id","original_actor_id","run_id","step_id","dispatch_attempt_id"))n.put(field,TaskInvocationFixture.id(1));
  assertEquals(n,s.read("Recovery",ContentReadAuthorization.canonicalBytes(n)));
  for(String field:java.util.List.of("run_token","worker_action","provider_body","idempotency_key","new_send_admission")){
   var forged=n.deepCopy().put(field,"forged");assertThrows(TenantAccessException.class,()->s.read("Recovery",ContentReadAuthorization.canonicalBytes(forged)));
  }
  var missing=n.deepCopy();missing.remove("dispatch_attempt_id");assertThrows(TenantAccessException.class,()->s.read("Recovery",ContentReadAuthorization.canonicalBytes(missing)));
 }
 @Test void recoveryRejectsRunTokenKeyAndQueryHeaders(){
  for(String forbidden:java.util.List.of("X-Run-Token","Idempotency-Key")){
   var q=org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);org.mockito.Mockito.when(q.getHeaders("X-Schema-Version")).thenReturn(java.util.Collections.enumeration(java.util.List.of("1.0.0")));org.mockito.Mockito.when(q.getHeader("X-Schema-Version")).thenReturn("1.0.0");org.mockito.Mockito.when(q.getHeader(forbidden)).thenReturn("must not be accepted");assertThrows(TenantAccessException.class,()->TaskInvocationController.recoveryHeaders(q));
  }
 }
}
