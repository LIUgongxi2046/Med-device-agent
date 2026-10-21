package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real original dispatch verifier, explicit JDBC and current-mTLS doubles only. */
class FormalInvocationRecoveryAuthorityTest {
 FormalInvocationSchema schema;FormalInvocationOutcomeAuthority authority;JdbcTemplate db;TenantTransaction transactions;
 ServiceCertificateIdentity services;HttpServletRequest http;ObjectNode binding,journal;String request;boolean absent;
 String tenant=id(1),actor=id(2),run=id(3),step=id(4),invocation=id(5),attempt=id(6),hash="a".repeat(64);
 final LocalDateTime admitted=LocalDateTime.of(2026,10,19,0,0);
 @BeforeEach void fixture()throws Exception {
  schema=new FormalInvocationSchema();db=mock(JdbcTemplate.class);transactions=mock(TenantTransaction.class);
  services=mock(ServiceCertificateIdentity.class);http=mock(HttpServletRequest.class);
  var currentService=service("spiffe://test/gateway",hash,"original-trust");when(services.require(http)).thenReturn(currentService);
  request=schema.canonical(schema.parse("{\"execution_kind\":\"TASK\"}"));String requestHash=schema.hash(schema.parse(request));
  binding=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("project_id",id(7)).put("invocation_id",invocation).put("run_id",run).put("step_id",step)
   .put("action_id",id(8)).put("config_revision",id(9)).put("context_manifest_id",id(10)).put("input_sha256",hash).put("created_by",actor)
   .put("classification","INTERNAL").put("attempt_id",attempt).put("service_uri","spiffe://test/gateway").put("certificate_sha256",hash)
   .put("trust_revision","original-trust").put("lease_generation","1").put("request_sha256",requestHash).put("canonical_request",request).put("binding_request_sha256",requestHash);
  var send=binding.putObject("actual_send_manifest").put("invocation_id",invocation).put("attempt_id",attempt).put("input_sha256",hash).put("request_sha256",requestHash);
  var body=send.putObject("provider_body").put("model","original-model");send.put("canonical_provider_body_sha256",schema.hash(body));binding.put("actual_send_sha256",schema.hash(send));
  journal=send.deepCopy().put("service_uri","spiffe://test/gateway").put("certificate_sha256",hash).put("trust_revision","original-trust").put("lease_generation","1")
   .put("admitted_at",admitted.toString()).put("deadline_at",admitted.plusMinutes(1).toString()).put("actual_send_sha256",schema.hash(send));
  when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
   if(sql.startsWith("SELECT auth_epoch"))return List.of(new TenantWatermarks(BigInteger.TEN,BigInteger.TEN));
   if(sql.contains("FROM invocation i JOIN formal_invocation_attempt"))return absent?List.of():List.of(schema.canonical(binding));
   if(sql.startsWith("SELECT admitted_at"))return List.of(List.of(admitted,admitted.plusMinutes(1),admitted));
   if(sql.startsWith("SELECT resource_ref")||sql.startsWith("SELECT payload"))return List.of(schema.canonical(journal));
   throw new AssertionError(sql);
  });
  when(transactions.execute(any())).thenAnswer(call->{TransactionSynchronizationManager.setActualTransactionActive(true);try{return ((Function<TenantWatermarks,?>)call.getArgument(0)).apply(new TenantWatermarks(BigInteger.TEN,BigInteger.TEN));}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}});
  authority=new FormalInvocationOutcomeAuthority(services,transactions,db,schema,32768);
 }
 @AfterEach void cleanup(){TransactionSynchronizationManager.clear();}
 private FormalInvocationOutcomeAuthority.VerifiedRecovery proof()throws Exception{return authority.recover(http,tenant,actor,run,step,invocation,attempt,p->{p.requireActive();return p;});}
 @Test void expiredLeaseOrUserGrantDoesNotRequireFreshExecutionAuthority()throws Exception {
  // Original dispatch is yesterday and tenant epochs have advanced. Only current mTLS
  // and the exact original journals are required; no worker-token APIs exist here.
  var p=proof();assertEquals(invocation,p.invocationId());assertEquals(attempt,p.attemptId());assertThrows(TenantAccessException.class,p::requireActive);
  assertEquals(run,p.current(now->{now.requireActive();return now.runId();}));verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void revokedOriginalServiceCannotRecover()throws Exception {when(services.require(http)).thenThrow(new SecurityException("REVOKED"));assertThrows(SecurityException.class,this::proof);verifyNoInteractions(transactions);}
 @Test void differentCertificateOrTrustRevisionCannotRecover()throws Exception {
  for(var service:List.of(service("spiffe://test/other",hash,"original-trust"),service("spiffe://test/gateway","b".repeat(64),"original-trust"),service("spiffe://test/gateway",hash,"changed-trust"))){when(services.require(http)).thenReturn(service);assertThrows(TenantAccessException.class,this::proof);}
 }
 @Test void exactRunStepAndAttemptCannotBeSubstituted(){
  assertThrows(TenantAccessException.class,()->authority.recover(http,tenant,actor,id(11),step,invocation,attempt,p->p));
  assertThrows(TenantAccessException.class,()->authority.recover(http,tenant,actor,run,id(11),invocation,attempt,p->p));
  assertThrows(TenantAccessException.class,()->authority.recover(http,tenant,actor,run,step,invocation,id(11),p->p));
 }
 @Test void reservedOrAbsentDispatchCannotMintOriginalProof(){absent=true;assertThrows(TenantAccessException.class,this::proof);}
 @Test void alteredOriginalRequestCannotRecover(){binding.put("canonical_request","{\"execution_kind\":\"FORGED\"}");assertThrows(TenantAccessException.class,this::proof);}
 @Test void forgedCompleteDispatchJournalCannotRecover(){journal.put("lease_generation","2");assertThrows(TenantAccessException.class,this::proof);}
 @Test void proofRevalidatesCurrentServiceAndOriginalJournalBeforeReuse()throws Exception {
  var p=proof();journal.put("trust_revision","mutated");assertThrows(TenantAccessException.class,()->p.current(now->null));
 }
 @Test void currentServiceRevocationDuringObservationBlocksDisclosure()throws Exception {
  assertThrows(SecurityException.class,()->authority.recover(http,tenant,actor,run,step,invocation,attempt,p->{when(services.require(http)).thenThrow(new SecurityException("REVOKED"));return "hidden";}));
 }
 @Test void proofHasNoPublicConstructorBodyResultOrExecutionCapability(){
  assertTrue(Arrays.stream(FormalInvocationOutcomeAuthority.VerifiedRecovery.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
  assertTrue(Arrays.stream(FormalInvocationOutcomeAuthority.VerifiedRecovery.class.getDeclaredMethods()).noneMatch(m->Set.of("bytes","binding","result","providerBody","beginDispatch","reserve").contains(m.getName())));
 }
 private static ServiceCertificateIdentity.Verified service(String uri,String certificate,String trust){var value=mock(ServiceCertificateIdentity.Verified.class);when(value.serviceUri()).thenReturn(uri);when(value.certificateSha256()).thenReturn(certificate);when(value.trustRevision()).thenReturn(trust);return value;}
 private static String id(int n){return TaskInvocationFixture.id(n);}
}
