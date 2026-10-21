package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Orchestration regression over boundary doubles. Private permission proof semantics are tested
 * separately by FormalRunControlAccessTest; no external transport acceptance is claimed here. */
class TaskRunReplayBootstrapTest {
 FormalRunControlAccess control;FormalRunControlAccess.Reads reads;EnterpriseAccess.Admission admission;
 AgentPublicationAuthority publications;AgentPublicationAuthority.PreparedPublication prepared;AgentPublicationAuthority.VerifiedPublication publication;
 AgentExecutionPolicyAuthority policies;AgentExecutionPolicyAuthority.VerifiedPolicy policy;RuntimeRetentionAuthority retention;
 TaskRunBootstrapAuthority authority;TaskRunBootstrapAuthority.Verified proof;TaskRunReplayLedger ledger;RuntimeObjectTransport objects;
 TaskRunReplayRegistration registration;TaskRunReplayContentAuthority content;TaskRunReplayContentAuthority.Verified checked;TaskRunReplayBootstrap bootstrap;
 TaskRunReplayLedger.Intent intent;TaskRunReplayLedger.Attempt attempt;jakarta.servlet.http.HttpServletRequest http;
 String tenant=id(1),run=id(2),command=id(3),release=id(4),project=id(5),policyId=id(6),retentionId=id(7),hash="a".repeat(64);
 @BeforeEach void setup()throws Exception{
  control=mock(FormalRunControlAccess.class);reads=mock(FormalRunControlAccess.Reads.class);admission=mock(EnterpriseAccess.Admission.class);
  publications=mock(AgentPublicationAuthority.class);prepared=mock(AgentPublicationAuthority.PreparedPublication.class);publication=mock(AgentPublicationAuthority.VerifiedPublication.class);
  policies=mock(AgentExecutionPolicyAuthority.class);policy=mock(AgentExecutionPolicyAuthority.VerifiedPolicy.class);retention=mock(RuntimeRetentionAuthority.class);
  authority=mock(TaskRunBootstrapAuthority.class);proof=mock(TaskRunBootstrapAuthority.Verified.class);ledger=mock(TaskRunReplayLedger.class);objects=mock(RuntimeObjectTransport.class);
  registration=mock(TaskRunReplayRegistration.class);content=mock(TaskRunReplayContentAuthority.class);checked=mock(TaskRunReplayContentAuthority.Verified.class);
  intent=mock(TaskRunReplayLedger.Intent.class);attempt=mock(TaskRunReplayLedger.Attempt.class);http=mock(jakarta.servlet.http.HttpServletRequest.class);
  var identity=mock(IdentityDirectoryPolicy.class);when(identity.classification()).thenReturn("INTERNAL");
  var json=new PermissionSchema();var replay=json.object().put("agent_release_id",release).put("project_id",project);replay.putObject("retention").put("policy_revision",retentionId).put("content_sha256",hash);
  when(reads.replay()).thenReturn(replay);when(reads.policyVersion()).thenReturn(policyId);when(reads.policySha256()).thenReturn(hash);
  when(control.withReads(eq(http),eq(tenant),eq(run),eq(command),any())).thenAnswer(c->((Function<FormalRunControlAccess.Reads,?>)c.getArgument(4)).apply(reads));
  when(reads.execute(any())).thenAnswer(c->((Function<EnterpriseAccess.Admission,?>)c.getArgument(0)).apply(admission));
  when(publications.prepareControl(reads,release,"RUN_CREATION")).thenReturn(prepared);when(publications.require(admission,release,"RUN_CREATION",prepared)).thenReturn(publication);
  when(policies.requireByVersion(admission,policyId,hash,project,"INTERNAL","RUN_CREATION")).thenReturn(policy);
  var retained=mock(RuntimeRetentionAuthority.Verified.class);when(retention.requireFixedForRun(admission,retentionId,hash,project,"INTERNAL","RUN_CREATION")).thenReturn(retained);
  when(authority.require(admission,publication,policy,retained,run)).thenReturn(proof);when(content.require(proof)).thenReturn(checked);
  when(ledger.prepare(proof)).thenReturn(intent);when(ledger.attempt(proof,intent)).thenReturn(attempt);
  bootstrap=new TaskRunReplayBootstrap(control,publications,policies,retention,authority,ledger,objects,registration,identity,content);
 }
 @Test void revokedContentBeforeAttemptNeverMovesBytes()throws Exception{
  when(content.require(proof)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
  assertThrows(TenantAccessException.class,()->bootstrap.consume(http,tenant,run,command));verifyNoInteractions(objects,registration);verify(ledger,never()).prepare(any());
 }
 @Test void everyPhysicalSegmentRechecksContentBeforeRegistration()throws Exception{
  var transfer=mock(RuntimeObjectTransport.VerifiedTransfer.class);var result=mock(TaskRunReplayRegistration.Registered.class);
  when(objects.transferVerified(eq(attempt),any())).thenAnswer(call->{var guard=(S3ObjectReader.Admission)call.getArgument(1);guard.requireCurrent();guard.requireCurrent();return transfer;});
  when(registration.register(proof,attempt,transfer)).thenReturn(result);
  assertSame(result,bootstrap.consume(http,tenant,run,command));verify(content,times(5)).require(proof);verify(checked,times(5)).requireUnchanged();
 }
 @Test void rightsRevokedBetweenPutOrRecoverySegmentsPreventsRegistration()throws Exception{
  when(objects.transferVerified(eq(attempt),any())).thenAnswer(call->{var guard=(S3ObjectReader.Admission)call.getArgument(1);guard.requireCurrent();
   when(content.require(proof)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));guard.requireCurrent();throw new AssertionError("must stop I/O");});
  assertThrows(TenantAccessException.class,()->bootstrap.consume(http,tenant,run,command));verifyNoInteractions(registration);
 }
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
