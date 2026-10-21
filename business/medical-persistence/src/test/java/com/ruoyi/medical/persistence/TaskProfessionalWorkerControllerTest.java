package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-process protocol orchestration only. No TLS service, provider or sandbox is executed. */
class TaskProfessionalWorkerControllerTest {
 final JsonMapper json=new JsonMapper();final ServiceCertificateIdentity identities=mock(ServiceCertificateIdentity.class);final TaskHostControlBootstrap bootstrap=mock(TaskHostControlBootstrap.class);final TaskProfessionalHostOrchestrator runtime=mock(TaskProfessionalHostOrchestrator.class);final TaskInvocationBridge models=mock(TaskInvocationBridge.class);final TaskResearchToolBridge tools=mock(TaskResearchToolBridge.class);final TaskResearchFinishOrchestrator finish=mock(TaskResearchFinishOrchestrator.class);final ProfessionalRuntimeObservations observations=mock(ProfessionalRuntimeObservations.class);final TaskProfessionalHostAccess.Handle handle=mock(TaskProfessionalHostAccess.Handle.class);
 final TaskProfessionalWorkerController controller=new TaskProfessionalWorkerController(identities,bootstrap,runtime,models,tools,finish,observations);
 final JsonNode binding=json.createObjectNode().put("tenant_id",id(1)).put("run_id",id(2)).put("step_id",id(3)).put("action_id",id(4)).put("lease_generation",1).put("input_sha256",hash('a')).put("step_envelope_sha256",hash('b'));
 final WorkflowProtocol.Dispatch dispatch=new WorkflowProtocol.Dispatch(id(2),id(3),id(4),id(5),hash('a'),1,id(6),hash('b'));
 final WorkflowProtocol.Reference reference=new WorkflowProtocol.Reference("1.0.0",id(1),id(7),id(2),WorkflowProtocol.ExecutionKind.TASK,id(8),hash('c'),id(9),9999999999999L,10,10,1,1000,1000,1000,"control","professional");
 TaskProfessionalWorkerControllerTest()throws Exception{when(bootstrap.open(any(),any(ProfessionalRuntimeObservations.Binding.class))).thenReturn(handle);when(runtime.heartbeat(handle,id(10))).thenReturn(session().put("admission","CURRENT"));}
 com.fasterxml.jackson.databind.node.ObjectNode session(){var result=json.createObjectNode().put("session_id",id(10));result.set("binding",binding);return result;}
 MockHttpServletRequest request(JsonNode body)throws Exception{return request(json.writeValueAsString(body));}MockHttpServletRequest request(String body){var http=new MockHttpServletRequest();http.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));return http;}
 JsonNode openBody(){var body=json.createObjectNode();body.set("binding",binding);body.set("reference",json.valueToTree(reference));body.set("dispatch",json.valueToTree(dispatch));return body;}
 @Test void openVerifiesFullOriginalDispatchBeforeAnyPreparedRecovery()throws Exception{
  var http=request(openBody());var prepared=mock(TaskProfessionalHostOrchestrator.PreparedLaunch.class);when(runtime.prepare(handle,id(5))).thenReturn(prepared);when(prepared.dispatch()).thenReturn(dispatch);var launch=session().put("admission","PREPARE");when(prepared.launch()).thenReturn(launch);
  assertSame(launch,controller.open(http,new MockHttpServletResponse()));var order=inOrder(identities,bootstrap,runtime,prepared);order.verify(identities).require(http);order.verify(bootstrap).requireDispatch(http,ProfessionalRuntimeObservations.jsonBinding(binding),reference,dispatch);order.verify(bootstrap).open(http,ProfessionalRuntimeObservations.jsonBinding(binding));order.verify(runtime).prepare(handle,id(5));order.verify(prepared).dispatch();order.verify(prepared).launch();
 }
 @Test void missingOrChangedOriginalDispatchNeverPreparesOrReturnsLaunch()throws Exception{
  var http=request(openBody());doThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH)).when(bootstrap).requireDispatch(any(),any(),any(),any());assertThrows(TenantAccessException.class,()->controller.open(http,new MockHttpServletResponse()));verifyNoInteractions(runtime);
 }
 @Test void recoveryCannotReturnSiblingDispatchOrBinding()throws Exception{
  var prepared=mock(TaskProfessionalHostOrchestrator.PreparedLaunch.class);when(runtime.prepare(handle,id(5))).thenReturn(prepared);when(prepared.dispatch()).thenReturn(new WorkflowProtocol.Dispatch(id(2),id(3),id(4),id(11),hash('a'),1,id(6),hash('b')));assertThrows(TenantAccessException.class,()->controller.open(request(openBody()),new MockHttpServletResponse()));verify(prepared,never()).launch();
  when(prepared.dispatch()).thenReturn(dispatch);var wrong=session();wrong.set("binding",json.createObjectNode());when(prepared.launch()).thenReturn(wrong);assertThrows(TenantAccessException.class,()->controller.open(request(openBody()),new MockHttpServletResponse()));
 }
 @Test void admissionUsesServerOriginalCommandNotCallerAuthority()throws Exception{
  var body=session();var http=request(body);when(bootstrap.commandId(http,ProfessionalRuntimeObservations.jsonBinding(binding))).thenReturn(id(5));var expected=session().put("admission","CURRENT").put("isolation_receipt_id",id(12)).put("supervision_sha256",hash('d'));when(runtime.admit(http,handle,id(10),id(5))).thenReturn(expected);assertSame(expected,controller.admit(http,new MockHttpServletResponse()));
  body.put("command_id",id(5));assertThrows(Exception.class,()->controller.admit(request(body),new MockHttpServletResponse()));
 }
 @Test void heartbeatRequiresExactCurrentSessionBinding()throws Exception{
  assertEquals("CURRENT",controller.heartbeat(request(session()),new MockHttpServletResponse()).required("admission").textValue());when(runtime.heartbeat(handle,id(10))).thenReturn(session().put("admission","CLOSED"));assertThrows(TenantAccessException.class,()->controller.heartbeat(request(session()),new MockHttpServletResponse()));
 }
 com.fasterxml.jackson.databind.node.ObjectNode bridge(String method,String route){var b=session().put("method",method).put("route",route).putNull("idempotency_key").putNull("model_request_observation");b.set("body",json.createObjectNode());return b;}
 @Test void exactReadToolRouteUsesIndependentToolBridgeNotModelGrant()throws Exception{
  for(String id:List.of("AT-01","AT-02")){var body=bridge("POST","/v1/tools/"+id);var http=request(body);var expected=json.createObjectNode().put("kind","TASK_READ_RESULT");when(tools.forward(http,handle,TaskProfessionalWorkerControllerTest.id(10),id,body.required("body"))).thenReturn(expected);assertSame(expected,controller.bridge(http,new MockHttpServletResponse()));}verifyNoInteractions(models);
 }
 @Test void readToolRejectsModelObservationOrIdempotencySubstitution()throws Exception{
  for(String field:List.of("idempotency_key","model_request_observation")){var body=bridge("POST","/v1/tools/AT-01");body.put(field,id(12));assertThrows(TenantAccessException.class,()->controller.bridge(request(body),new MockHttpServletResponse()));}verifyNoInteractions(tools,models);
 }
 @Test void modelRoutePreservesExactObservedLocatorAndCanonicalResult()throws Exception{
  var body=bridge("POST","/v1/model-contexts");var locator=json.createObjectNode().put("request_id",id(12));body.set("model_request_observation",locator);var http=request(body);var expected=json.createObjectNode().put("context_manifest_id",id(13));when(models.forward(http,handle,id(10),"POST","/v1/model-contexts",body.required("body"),null,locator)).thenReturn(expected);assertSame(expected,controller.bridge(http,new MockHttpServletResponse()));verifyNoInteractions(tools);
 }
 @Test void currentAdmissionDenialStopsBothBridges()throws Exception{
  when(runtime.heartbeat(handle,id(10))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->controller.bridge(request(bridge("POST","/v1/tools/AT-01")),new MockHttpServletResponse()));verifyNoInteractions(tools,models);
 }
 @Test void finishReturnsOnlyActualRegisteredCompletionAndPropagatesMissingEvidence()throws Exception{
  var body=session();var observation=session().put("finish_reason","stop").put("final_response","candidate");body.set("observation",observation);body.putArray("files");var http=request(body);var expected=json.createObjectNode().put("resultArtifactId",id(14));when(finish.finish(http,handle,body)).thenReturn(expected);assertSame(expected,controller.finish(http,new MockHttpServletResponse()));
  when(finish.finish(any(),eq(handle),any())).thenThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));assertThrows(TenantAccessException.class,()->controller.finish(request(body),new MockHttpServletResponse()));verifyNoInteractions(models,tools);
 }
 @Test void revokedWorkerCanOnlyDelegateOriginalServiceReconciliationWithoutNewToken()throws Exception{
  var body=session().put("reason","LEASE_LOST");var http=request(body);var expected=session().put("admission","CLOSED");when(observations.close(http,body)).thenReturn(expected);assertSame(expected,controller.close(http,new MockHttpServletResponse()));body.put("host_process_exited",false);http=request(body);when(observations.failure(http,body)).thenReturn(expected);assertSame(expected,controller.failure(http,new MockHttpServletResponse()));verifyNoInteractions(bootstrap,runtime,finish);
 }
 @Test void missingMtlsDuplicateTrailingUnknownQueryAndOversizeNeverMintAuthority()throws Exception{
  var http=request("{}");doThrow(new SecurityException("MISSING_MTLS")).when(identities).require(http);assertThrows(SecurityException.class,()->controller.open(http,new MockHttpServletResponse()));
  for(String invalid:List.of("{\"binding\":{},\"binding\":{}}","{} {}","{}"))assertThrows(Exception.class,()->controller.open(request(invalid),new MockHttpServletResponse()));var query=request(openBody());query.setQueryString("allow=true");assertThrows(TenantAccessException.class,()->controller.open(query,new MockHttpServletResponse()));assertThrows(TenantAccessException.class,()->controller.open(request(" ".repeat(65537)),new MockHttpServletResponse()));verifyNoInteractions(bootstrap,runtime);
 }
 static String id(int value){return String.format("00000000-0000-4000-8000-%012d",value);}static String hash(char value){return String.valueOf(value).repeat(64);}
}
