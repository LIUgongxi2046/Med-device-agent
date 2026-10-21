package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real private Reads and VerifiedControl are minted. JDBC/mTLS verification boundaries are mocked,
 * so these tests do not claim a live database, TLS handshake or object-store acceptance. */
class FormalRunControlAccessTest {
 FormalWorkerControlAuthorityTest base;FormalWorkerAccessTest f;FormalRunControlAccess access;
 JdbcTemplate db;TenantTransaction tx;VersionResourceMapper owners;ArtifactRegistryMapper registry;
 String version,command,hash="e".repeat(64);TenantWatermarks epochs=new TenantWatermarks(BigInteger.ONE,BigInteger.ONE);
 @BeforeEach void setup()throws Exception{
  base=new FormalWorkerControlAuthorityTest();base.setup();f=base.f;version=f.id(60);command=f.id(61);
  base.replay.put("task_id",f.task).put("project_id",f.project).put("agent_release_id",f.id(62));
  base.replay.set("principal_manifest",new com.fasterxml.jackson.databind.ObjectMapper().readTree(f.persisted.capabilities()));
  base.replay.with("execution_policy").putObject("worker").put("service_uri","spiffe://medical/runtime").put("audience","medical-runtime");
  base.replay.putArray("dependency_versions").addObject().put("version_id",version).put("project_id",f.project).put("entity_kind","ARTIFACT").put("content_sha256",hash);
  var c=(com.fasterxml.jackson.databind.node.ObjectNode)base.start.required("command");c.put("command_id",command).put("replay_sha256",f.schema.sha256(base.replay));c.remove("command_sha256");c.put("command_sha256",f.schema.sha256(base.start));
  f.cleanup();f.scope=null;
  db=mock(JdbcTemplate.class);tx=mock(TenantTransaction.class);owners=mock(VersionResourceMapper.class);registry=mock(ArtifactRegistryMapper.class);
  when(db.query(contains("SELECT r.created_by"),any(RowMapper.class),eq(f.tenant),eq(f.run),eq(command))).thenAnswer(call->{
   var rs=mock(java.sql.ResultSet.class);when(rs.getString(1)).thenReturn(f.actor);when(rs.getString(2)).thenReturn(f.schema.canonical(base.replay));when(rs.getString(3)).thenReturn(f.schema.canonical(base.start));
   return List.of(((RowMapper<?>)call.getArgument(1)).mapRow(rs,0));
  });
  when(db.query(contains("SELECT auth_epoch"),any(RowMapper.class),eq(f.tenant))).thenReturn(List.of(epochs));
  when(db.query(contains("SELECT r.replay_manifest"),any(RowMapper.class),eq(f.tenant),eq(f.run),eq(f.actor),eq(command)))
   .thenAnswer(call->List.of(List.of(f.schema.canonical(base.replay),f.schema.canonical(base.start))));
  when(tx.execute(any())).thenAnswer(call->{
   var scope=TenantScope.requireCurrent();Object key=new Object(),value=new Object();
   TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(key,value);scope.verifyTransaction(epochs);
   try{return ((Function<TenantWatermarks,?>)call.getArgument(0)).apply(epochs);}
   finally{scope.finishTransaction();TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.setActualTransactionActive(false);}
  });
  when(registry.version(version)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(version,f.project,"ARTIFACT",hash)));
  when(owners.ownersAcrossDomains(eq(List.of(version)),anyList())).thenReturn(List.of(new VersionResourceProtocol.DomainOwnerRow("ARTIFACT",
   new VersionResourceProtocol.OwnerRow(f.tenant,version,f.project,f.project,"TASK",f.task,hash,null,null,null,null))));
  access=new FormalRunControlAccess(db,tx,base.authority,base.services,f.permissions,owners,registry,new PermissionSchema(),f.schema,Duration.ofMinutes(1),10);
 }
 @AfterEach void cleanup(){TransactionSynchronizationManager.setActualTransactionActive(false);}
 @Test void realReadsPermitExactArtifactAndProjectButNotWrite()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{
   assertTrue(a.controlWorker());assertNull(a.contextId());assertThrows(TenantAccessException.class,a::subject);
   a.controlProof().requireVersion(version,hash);
   a.controlProof().require(new PermissionProtocol.Scope(f.tenant,f.project,"ARTIFACT",version),"artifact.read","AGENT_PUBLICATION");
   a.controlProof().require(new PermissionProtocol.Scope(f.tenant,f.project,"PROJECT",f.project),"project.read","AGENT_PUBLICATION");
   assertThrows(TenantAccessException.class,()->a.controlProof().require(f.resource,"artifact.register","TASK_EXECUTION"));
   return null;
  }));
 }
 @Test void sameOwnerSiblingAndChangedHashFail()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{
   assertThrows(TenantAccessException.class,()->a.controlProof().requireVersion(f.id(99),hash));
   assertThrows(TenantAccessException.class,()->a.controlProof().requireVersion(version,"f".repeat(64)));return null;
  }));
 }
 @Test void nestedCallbacksShareOnlyCurrentProofAndEscapedProofDies()throws Exception{
  var retained=new AtomicReference<FormalRunControlAccess.Reads>();var admission=new AtomicReference<EnterpriseAccess.Admission>();
  access.withReads(f.http,f.tenant,f.run,command,reads->{retained.set(reads);return reads.execute(a->{admission.set(a);return reads.execute(nested->{assertSame(a,nested);assertEquals(a.actorProof(),nested.actorProof());return null;});});});
  assertThrows(TenantAccessException.class,()->retained.get().execute(a->null));assertThrows(TenantAccessException.class,()->admission.get().actorId());
 }
 @Test void revokedTaskGrantBlocksBeforeRead()throws Exception{
  doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(base.grants).requireResource(any(),eq(f.actor),eq("local"),eq("task.run"),eq("TASK_EXECUTION"),any(),any());
  assertThrows(TenantAccessException.class,()->access.withReads(f.http,f.tenant,f.run,command,r->fail("must not expose reads")));
 }
 @Test void mismatchedBootstrapRunOrTaskDenied()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{a.controlProof().requireBootstrap(f.task,f.run);
   assertThrows(TenantAccessException.class,()->a.controlProof().requireBootstrap(f.task,f.id(98)));
   assertThrows(TenantAccessException.class,()->a.controlProof().requireBootstrap(f.id(98),f.run));return null;}));
 }
 @Test void changedCommandAndCrossThreadCannotUseRead()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->{var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{reads.requireActive();}catch(Throwable e){failure.set(e);}});thread.start();try{thread.join();}catch(InterruptedException e){throw new AssertionError(e);}assertInstanceOf(TenantAccessException.class,failure.get());return null;});
  assertThrows(TenantAccessException.class,()->access.withReads(f.http,f.tenant,f.run,command,reads->{base.start.put("changed",true);return reads.execute(a->null);}));
 }
 @Test void startRunCapabilityDoesNotBypassCreationGrant()throws Exception{
  var grants=mock(PermissionAuthority.class);var resources=new ResourceReadAuthority(f.permissions,grants,new PermissionSchema());
  when(f.permissions.resourceOwner("TASK",f.task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(f.task,f.project)));
  when(f.permissions.databaseTime()).thenReturn(java.time.LocalDateTime.of(2026,10,19,0,0));
  when(grants.findResource(any(),anyString(),anyString(),anyString(),anyString(),anyList(),any())).thenReturn(Optional.empty());
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{
   var start=a.controlProof().startRun();start.require(a,f.resource,"RUN_CREATION");
   assertThrows(TenantAccessException.class,()->start.require(a,f.resource,"TASK_EXECUTION"));
   assertThrows(TenantAccessException.class,()->resources.requireStartRun(a,f.resource,start));
   return null;
  }));
  verify(grants).findResource(eq(f.resource),eq(f.actor),eq("local"),eq("task.run"),eq("RUN_CREATION"),anyList(),any());
 }
 @Test void replacedTransactionResourceCannotUseProof()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{Object key=new Object();TransactionSynchronizationManager.bindResource(key,new Object());
   try{assertThrows(TenantAccessException.class,a::actorId);}finally{TransactionSynchronizationManager.unbindResource(key);}return null;
  }));
 }
 @Test void expiredReadWindowDoesNotExposeCallback()throws Exception{
  var expired=new FormalRunControlAccess(db,tx,base.authority,base.services,f.permissions,owners,registry,new PermissionSchema(),f.schema,Duration.ofNanos(1),10);
  assertThrows(TenantAccessException.class,()->expired.withReads(f.http,f.tenant,f.run,command,r->fail("expired")));
 }
 @Test void changedTrustOrCertificateIsNotSameReadAuthority()throws Exception{
  var replacement=mock(com.ruoyi.medical.identity.ServiceCertificateIdentity.Verified.class);
  when(replacement.serviceUri()).thenReturn("spiffe://medical/runtime");when(replacement.certificateSha256()).thenReturn("a".repeat(64));when(replacement.trustRevision()).thenReturn("2");
  assertThrows(TenantAccessException.class,()->access.withReads(f.http,f.tenant,f.run,command,reads->org.springframework.util.function.ThrowingSupplier.of(()->{when(base.services.require(f.http)).thenReturn(replacement);return reads.execute(a->null);}).get()));
  when(base.services.require(f.http)).thenReturn(base.service);when(replacement.trustRevision()).thenReturn("1");when(replacement.certificateSha256()).thenReturn("b".repeat(64));
  assertThrows(TenantAccessException.class,()->access.withReads(f.http,f.tenant,f.run,command,reads->org.springframework.util.function.ThrowingSupplier.of(()->{when(base.services.require(f.http)).thenReturn(replacement);return reads.execute(a->null);}).get()));
 }
 @Test void wrongProjectOrUnpinnedReleaseFails()throws Exception{
  access.withReads(f.http,f.tenant,f.run,command,reads->reads.execute(a->{
   assertThrows(TenantAccessException.class,()->a.controlProof().require(new PermissionProtocol.Scope(f.tenant,f.id(99),"PROJECT",f.id(99)),"project.read","AGENT_PUBLICATION"));
   assertThrows(TenantAccessException.class,()->reads.requirePublication(f.id(99),"RUN_CREATION"));return null;
  }));
 }

}
