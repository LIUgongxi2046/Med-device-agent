package com.ruoyi.medical.persistence;

import org.mockito.ArgumentMatchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Orchestration doubles only. No database mutation, process, provider or object transfer. */
class TaskInvocationRecoveryTest {
 TaskInvocationFixture f;TaskInvocationRecovery recovery;FormalInvocationOutcomeAuthority outcomes;FormalInvocationOutcomeAuthority.VerifiedRecovery proof;
 TaskInvocationRecords records;TaskNodeProducerAuthority producers;FormalInvocationReservations reservations;FormalInvocationSettlement settlement;
 FormalInvocationObjectRegistration objects;FormalProviderCaptureEvidence captures;TaskInvocationResults results;TaskInvocationLocalBridge adapter;JdbcTemplate db;HttpServletRequest http;
 Optional<FormalInvocationSettlement.Recorded> accounting=Optional.empty();String invocation=id(60),attempt=id(61);ObjectNode commitment;
 @BeforeEach void fixture()throws Exception {
  f=new TaskInvocationFixture();outcomes=mock(FormalInvocationOutcomeAuthority.class);proof=mock(FormalInvocationOutcomeAuthority.VerifiedRecovery.class);
  records=mock(TaskInvocationRecords.class);producers=mock(TaskNodeProducerAuthority.class);reservations=mock(FormalInvocationReservations.class);
  settlement=mock(FormalInvocationSettlement.class);objects=mock(FormalInvocationObjectRegistration.class);captures=mock(FormalProviderCaptureEvidence.class);
  results=mock(TaskInvocationResults.class);adapter=mock(TaskInvocationLocalBridge.class);db=mock(JdbcTemplate.class);http=mock(HttpServletRequest.class);
  when(proof.tenantId()).thenReturn(f.tenant);when(proof.actorId()).thenReturn(f.actor);when(proof.projectId()).thenReturn(f.project);when(proof.runId()).thenReturn(f.run);when(proof.stepId()).thenReturn(f.step);
  when(proof.invocationId()).thenReturn(invocation);when(proof.attemptId()).thenReturn(attempt);when(proof.actionId()).thenReturn(f.action.actionId());when(proof.contextId()).thenReturn(f.contextId);
  when(proof.modelConfigRevision()).thenReturn(f.modelId);when(proof.inputSha256()).thenReturn(f.hash);when(proof.leaseGeneration()).thenReturn("1");when(proof.serviceUri()).thenReturn(f.request.node().worker_service_uri());
  when(outcomes.recover(eq(http),eq(f.tenant),eq(f.actor),eq(f.run),eq(f.step),eq(invocation),eq(attempt),any())).thenAnswer(call->((FormalInvocationOutcomeAuthority.RecoveryWork<?>)call.getArgument(7)).apply(proof));
  when(proof.current(any())).thenAnswer(call->((FormalInvocationOutcomeAuthority.RecoveryWork<?>)call.getArgument(0)).apply(proof));
  when(records.byInvocation(f.tenant,invocation)).thenReturn(f.binding);
  commitment=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("tenant_id",f.tenant).put("actor_id",f.actor).put("project_id",f.project).put("run_id",f.run).put("step_id",f.step)
   .put("action_id",f.action.actionId()).put("invocation_id",invocation).put("context_id",f.contextId).put("model_config_revision",f.modelId).put("input_sha256",f.hash).put("lease_generation","1").put("worker_service_uri",f.request.node().worker_service_uri());
  when(producers.verifyOriginalCommitment(f.tenant,invocation)).thenReturn(commitment);when(settlement.read(f.tenant,invocation)).thenAnswer(call->accounting);
  when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("SELECT status,settlement_status"))return List.of(List.of("DISPATCHING","PENDING"));
   if(sql.startsWith("SELECT status,settlement_receipt_id"))return List.of(Arrays.asList("RESERVED",null,null,null),Arrays.asList("RESERVED",null,null,null));throw new AssertionError(sql);
  });
  recovery=new TaskInvocationRecovery(db,outcomes,records,producers,reservations,settlement,objects,captures,results,adapter);
 }
 private JsonNode recover()throws Exception{return recovery.recover(http,f.tenant,f.actor,f.run,f.step,invocation,attempt);}
 private void settled()throws Exception {
  var value=mock(FormalInvocationSettlement.Recorded.class);var receipt=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("status","SUCCEEDED").put("settlement_status","SETTLED").put("attempt_id",attempt);
  when(value.receipt()).thenReturn(receipt);when(value.settled()).thenReturn(true);when(value.receiptSha256()).thenReturn(f.hash);accounting=Optional.of(value);
  when(results.verifiedAnyResult(f.binding,invocation)).thenReturn(new TaskInvocationResults.Result(invocation,f.schema.parse("{}"),f.hash,"b".repeat(64),"c".repeat(64)));
 }
 @Test void recordedAdapterAcknowledgementAloneStaysUnknownAndKeepsReservations()throws Exception {
  when(adapter.recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any())).thenReturn(new TaskInvocationLocalBridge.Outcome(invocation,attempt,true));var actual=recover();
  assertEquals("UNKNOWN",actual.required("status").textValue());assertEquals("PENDING",actual.required("settlement_status").textValue());assertFalse(actual.required("recorded").booleanValue());assertFalse(actual.required("reservations_released").booleanValue());assertFalse(actual.required("content_available").booleanValue());
  verify(adapter).recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any());verify(db,never()).update(anyString(),any(Object[].class));verifyNoInteractions(objects,captures,results);
 }
 @Test void oneUseCommandHasExactlyFourRecoveryFieldsAndNoNewExecutionAuthority()throws Exception {
  var command=new AtomicReference<TaskInvocationRecovery.RecoveryCall>();when(adapter.recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any())).thenAnswer(call->{var c=(TaskInvocationRecovery.RecoveryCall)call.getArgument(0);command.set(c);var wire=f.schema.parse(new String(c.consume(),java.nio.charset.StandardCharsets.UTF_8));
   assertEquals(Set.of("schema_version","mode","invocation_id","dispatch_attempt_id"),keys(wire));assertEquals("RECOVER",wire.required("mode").textValue());assertEquals(invocation,wire.required("invocation_id").textValue());assertEquals(attempt,wire.required("dispatch_attempt_id").textValue());return new TaskInvocationLocalBridge.Outcome(invocation,attempt,true);});
  recover();assertThrows(TenantAccessException.class,()->command.get().consume());verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void lostAdapterResponseStillReadsCommittedOriginalSettlement()throws Exception {
  when(adapter.recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any())).thenAnswer(call->{settled();throw unknown();});var actual=recover();assertEquals("SETTLED",actual.required("settlement_status").textValue());assertEquals(f.hash,actual.required("accounting_receipt_sha256").textValue());verify(results).verifiedAnyResult(f.binding,invocation);verify(adapter).recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any());
 }
 @Test void uncertainUnrecordedResponseNeverRetriesDispatchOrReleasesBudget()throws Exception {
  when(adapter.recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any())).thenThrow(unknown());assertEquals("UNKNOWN",recover().required("status").textValue());verify(adapter).recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any());verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void repeatedRecoveryReturnsSameVerifiedOriginalWithoutAnotherProcess()throws Exception {
  settled();assertEquals(recover(),recover());verifyNoInteractions(adapter);verify(results,times(2)).verifiedAnyResult(f.binding,invocation);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void forgedProducerOriginalBlocksProcessAndSettlement()throws Exception {commitment.put("actor_id",id(99));assertThrows(TenantAccessException.class,this::recover);verifyNoInteractions(adapter,settlement,results);}
 @Test void serviceRevocationBeforeProcessConsumeBlocksIt()throws Exception {
  when(adapter.recover(ArgumentMatchers.<TaskInvocationRecovery.RecoveryCall>any())).thenAnswer(call->{doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(proof).requireActive();return ((TaskInvocationRecovery.RecoveryCall)call.getArgument(0)).consume();});assertThrows(TenantAccessException.class,this::recover);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void claimedSettlementMustPassTypedCaptureAndAccountingVerifier()throws Exception {
  settled();when(results.verifiedAnyResult(f.binding,invocation)).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));assertThrows(TenantAccessException.class,this::recover);verifyNoInteractions(adapter);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void pendingRecoveryFailsClosedIfBudgetReservationWasReleased()throws Exception {
  when(db.query(startsWith("SELECT status,settlement_receipt_id"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(Arrays.asList("RELEASED",null,null,null),Arrays.asList("RESERVED",null,null,null)));
  assertThrows(TenantAccessException.class,this::recover);verifyNoInteractions(adapter);
 }
 @Test void recoveryDependencyGraphHasNoTokenIssuerContextOrDispatchService(){
  for(var field:TaskInvocationRecovery.class.getDeclaredFields())assertFalse(Set.of(RunTokenAuthority.class,TaskInvocationContext.class,TaskInvocationService.class,FormalInvocationDispatch.class,FormalWorkerAccess.class).contains(field.getType()),field.getName());
 }
 static TaskInvocationLocalBridge.Unknown unknown()throws Exception{var c=TaskInvocationLocalBridge.Unknown.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 static Set<String> keys(JsonNode n){var result=new HashSet<String>();n.fieldNames().forEachRemaining(result::add);return result;}
 static String id(int n){return TaskInvocationFixture.id(n);}
}
