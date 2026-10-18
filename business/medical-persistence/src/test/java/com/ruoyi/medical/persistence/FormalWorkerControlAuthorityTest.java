package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Actual metadata control proof; mTLS verifier/JDBC mocked here. Not a live TLS acceptance test. */
class FormalWorkerControlAuthorityTest {
 FormalWorkerAccessTest f;ServiceCertificateIdentity.Verified service;ServiceCertificateIdentity services;PermissionAuthority grants;FormalWorkerControlAuthority authority;
 com.fasterxml.jackson.databind.node.ObjectNode replay,start;
 @BeforeEach void setup()throws Exception{
  f=new FormalWorkerAccessTest();f.setUp();services=mock(ServiceCertificateIdentity.class);grants=mock(PermissionAuthority.class);
  service=mock(ServiceCertificateIdentity.Verified.class);when(service.certificateSha256()).thenReturn("a".repeat(64));when(service.trustRevision()).thenReturn("1");when(service.serviceUri()).thenReturn("spiffe://medical/runtime");when(services.require(f.http)).thenReturn(service);
  when(f.persisted.schema()).thenReturn("1.0.0");when(f.persisted.manifest()).thenReturn(f.schema.manifestSha256());when(f.persisted.principalManifest()).thenReturn(f.schema.manifestSha256());
  when(f.permissions.projectStatus(f.project)).thenReturn(List.of("ACTIVE"));
  when(f.jdbc.query(contains("platform_local_account"),any(RowMapper.class),eq(f.actor))).thenReturn(List.of(f.actor));
  when(f.jdbc.query(contains("tenant_member"),any(RowMapper.class),eq(f.tenant),eq(f.actor))).thenReturn(List.of(f.actor));
  replay=f.schema.object();replay.putObject("execution_policy_version").put("version_id",f.id(51)).put("content_sha256","d".repeat(64));replay.putObject("execution_policy").put("business_purpose","DOCUMENT_BODY");
  start=f.schema.object();var c=start.putObject("command").put("schema_version","1.0.0").put("kind","START_RUN").put("task_id",f.task).put("workflow_id",f.tenant+"/"+f.run).put("run_id",f.run).put("principal_id",f.principal).put("tenant_id",f.tenant).put("project_id",f.project).put("replay_sha256",f.schema.sha256(replay));start.putObject("decision").put("actor_id",f.actor);c.put("command_sha256",f.schema.sha256(start));
  when(f.jdbc.query(contains("SELECT r.replay_manifest"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(f.actor))).thenAnswer(x->List.of(List.of(f.schema.canonical(replay),f.schema.canonical(start))));
  authority=new FormalWorkerControlAuthority(f.jdbc,f.store,f.schema,services,f.permissions,grants);
 }
 @AfterEach void cleanup(){f.cleanup();}
 @Test void runMetadataProofExpiresAndCannotPretendNodeOrContentPermission()throws Exception{
  var retained=new AtomicReference<FormalWorkerControlAuthority.VerifiedControl>();
  authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->{retained.set(p);assertTrue(p.runOnly());assertEquals("DOCUMENT_BODY",p.businessPurpose());assertEquals(f.run,p.runId());assertThrows(TenantAccessException.class,p::nodeKind);assertThrows(TenantAccessException.class,()->p.requireLiteralMetadataPermission(new PermissionProtocol.Scope(f.tenant,f.project,"CONFIGURATION",f.id(51)),"TASK_EXECUTION"));return null;});
  assertThrows(TenantAccessException.class,()->retained.get().runId());verify(services,atLeast(2)).require(f.http);
 }
 @Test void modifiedReplayCannotBorrowOriginalStartWitness()throws Exception{replay.with("execution_policy").put("business_purpose","REVIEW");assertThrows(TenantAccessException.class,()->authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->null));}
 @Test void missingCurrentTaskGrantFailsBeforeMetadataIsReturned()throws Exception{doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(grants).requireResource(any(),eq(f.actor),eq("local"),eq("task.run"),eq("TASK_EXECUTION"),any(),any());assertThrows(TenantAccessException.class,()->authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->fail("must not return proof")));}
 @Test void sameServiceNewCertificateCannotReplaceOriginalProof()throws Exception{assertThrows(TenantAccessException.class,()->authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->{when(service.certificateSha256()).thenReturn("b".repeat(64));return null;}));}
 @Test void rehashedWrongCommandKindStillRejected()throws Exception{var command=(com.fasterxml.jackson.databind.node.ObjectNode)start.required("command");command.put("kind","NOT_A_START");command.remove("command_sha256");command.put("command_sha256",f.schema.sha256(start));assertThrows(TenantAccessException.class,()->authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->null));}
 @Test void changedRunDuringCallbackRejected()throws Exception{assertThrows(TenantAccessException.class,()->authority.withRunControl(f.http,f.run,f.actor,"medical-runtime",p->{when(f.persisted.status()).thenReturn("CANCELLED");return null;}));}
}
