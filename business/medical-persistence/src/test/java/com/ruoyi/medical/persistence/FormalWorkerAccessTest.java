package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real formal bridge/schema/ResourceReadAuthority, mocked transport and persistence boundary.
 * No deployed mTLS/token/DB acceptance claim. Private production proofs are created only by bridge.
 */
class FormalWorkerAccessTest {
 final String tenant=id(1),project=id(2),actor=id(3),run=id(4),principal=id(5),step=id(6),action=id(7),task=id(8);
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final RunTokenAuthorityStore store=mock(RunTokenAuthorityStore.class);
 final RunTokenAuthority tokens=mock(RunTokenAuthority.class);
 final RunTokenAuthority.Admission token=mock(RunTokenAuthority.Admission.class);
 final PermissionMapper permissions=mock(PermissionMapper.class);
 final RunTokenAuthoritySchema schema=new RunTokenAuthoritySchema();
 final RunTokenAuthorityStore.Run persisted=mock(RunTokenAuthorityStore.Run.class);
 final RunTokenAuthorityStore.Step leased=mock(RunTokenAuthorityStore.Step.class);
 final HttpServletRequest http=mock(HttpServletRequest.class);
 final FormalWorkerAccess access=new FormalWorkerAccess(jdbc,store,tokens,schema,permissions);
 final PermissionProtocol.Scope resource=new PermissionProtocol.Scope(tenant,project,"TASK",task);
 final RunTokenAuthority.ActionRequest request=new RunTokenAuthority.ActionRequest(run,step,action,BigInteger.ONE,"a".repeat(64),"b".repeat(64),"READ","1",resource,"task.read","TASK_READING");
 TenantScope scope;final Object transactionKey=new Object(),transactionIdentity=new Object();
 FormalWorkerAccessTest()throws Exception{}
 @BeforeEach void setUp()throws Exception {
  scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE));
  TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(transactionKey,transactionIdentity);scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
  when(tokens.execute(eq(http),eq("actual-token-fixture"),eq(request),any())).thenAnswer(call->((RunTokenAuthority.Operation<?>)call.getArgument(3)).apply(token));
  when(token.tenantId()).thenReturn(tenant);when(token.projectId()).thenReturn(project);when(token.initiatorId()).thenReturn(actor);
  when(token.principalId()).thenReturn(principal);when(token.runId()).thenReturn(run);when(token.stepId()).thenReturn(step);when(token.actionId()).thenReturn(action);
  when(token.leaseGeneration()).thenReturn(BigInteger.ONE);when(token.authEpoch()).thenReturn(BigInteger.ONE);when(token.governanceEpoch()).thenReturn(BigInteger.ONE);
  when(token.delegatedActions()).thenReturn(List.of("task.read"));when(token.serviceUri()).thenReturn("spiffe://medical/runtime");
  when(store.run(run,actor)).thenReturn(persisted);when(persisted.tenant()).thenReturn(tenant);when(persisted.project()).thenReturn(project);
  when(persisted.id()).thenReturn(run);when(persisted.actor()).thenReturn(actor);when(persisted.executionActor()).thenReturn(actor);when(persisted.principal()).thenReturn(principal);
  when(persisted.principalStatus()).thenReturn("ACTIVE");when(persisted.kind()).thenReturn("TASK");when(persisted.task()).thenReturn(task);when(persisted.status()).thenReturn("RUNNING");
  var s=schema.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run).put("execution_kind","TASK");s.putArray("resources").add(schema.tree(resource));
  var p=s.deepCopy();p.remove(List.of("execution_kind","resources"));p.put("principal_id",principal).put("scope_sha256",schema.sha256(s));
  p.putArray("services").addObject().put("service_uri","spiffe://medical/runtime").put("audience","medical-runtime");
  var rules=p.putArray("permissions");rules.addObject().put("action","task.read").put("purpose","TASK_READING").set("scope",schema.tree(resource));
  rules.addObject().put("action","task.run").put("purpose","TASK_EXECUTION").set("scope",schema.tree(resource));
  when(token.delegatedPermissions()).thenReturn(schema.permissions(p).stream().filter(value->value.action().equals("task.read")).toList());
  when(persisted.scope()).thenReturn(schema.canonical(s));when(persisted.capabilities()).thenReturn(schema.canonical(p));
  when(store.step(persisted,step)).thenReturn(leased);when(leased.status()).thenReturn("RUNNING");when(leased.generation()).thenReturn(BigInteger.ONE);
  when(leased.owner()).thenReturn("spiffe://medical/runtime");when(leased.expires()).thenReturn(LocalDateTime.of(2026,10,18,21,0));when(store.now()).thenReturn(LocalDateTime.of(2026,10,18,20,0));
  when(permissions.currentBinding(actor)).thenReturn(List.of(new PermissionProtocol.BindingRow(actor,null,"local",null,null,actor,"ACTIVE")));
 }
 @AfterEach void cleanup(){if(scope!=null)scope.finishTransaction();TransactionSynchronizationManager.unbindResourceIfPossible(transactionKey);TransactionSynchronizationManager.setActualTransactionActive(false);if(scope!=null)scope.close();}
 @Test void delegatedReadHasNoSessionAndExpiresAfterCallback()throws Exception{
  var retained=new AtomicReference<EnterpriseAccess.Admission>();
  assertEquals("ok",access.execute(http,"actual-token-fixture",request,a->{retained.set(a);assertTrue(a.formalWorker());assertFalse(a.simulationWorker());assertEquals(actor,a.actorId());assertNull(a.contextId());assertThrows(TenantAccessException.class,a::subject);a.formalProof().require(resource,"task.read","TASK_READING");return "ok";}));
  assertThrows(TenantAccessException.class,()->retained.get().actorId());
 }
 @Test void sameActionOnAnotherPrincipalResourceIsNotImplicitlyDelegated()throws Exception{
  var other=new PermissionProtocol.Scope(tenant,project,"TASK",id(99));var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var declared=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(persisted.scope());((com.fasterxml.jackson.databind.node.ArrayNode)declared.required("resources")).add(schema.tree(other));
  var capabilities=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(persisted.capabilities());capabilities.put("scope_sha256",schema.sha256(declared));
  ((com.fasterxml.jackson.databind.node.ArrayNode)capabilities.required("permissions")).add(schema.tree(new RunTokenAuthoritySchema.Permission(other,"task.read","TASK_READING")));
  when(persisted.scope()).thenReturn(schema.canonical(declared));when(persisted.capabilities()).thenReturn(schema.canonical(capabilities));
  access.execute(http,"actual-token-fixture",request,a->{a.formalProof().require(resource,"task.read","TASK_READING");assertThrows(TenantAccessException.class,()->a.formalProof().require(other,"task.read","TASK_READING"));return null;});
 }
 @Test void authenticatedPermissionCannotExceedActualPrincipal()throws Exception{
  when(token.delegatedPermissions()).thenReturn(List.of(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,project,"TASK",id(99)),"task.read","TASK_READING")));
  assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->null));
 }
 @Test void singleActionTokenCannotUpgradeToPrincipalAllActions()throws Exception{
  access.execute(http,"actual-token-fixture",request,a->{assertThrows(TenantAccessException.class,()->a.formalProof().require(resource,"task.run","TASK_EXECUTION"));return null;});
 }
 @Test void wrongScopeOrPurposeCannotUseDelegation()throws Exception{
  access.execute(http,"actual-token-fixture",request,a->{assertThrows(TenantAccessException.class,()->a.formalProof().require(new PermissionProtocol.Scope(tenant,null,"TASK",task),"task.read","TASK_READING"));assertThrows(TenantAccessException.class,()->a.formalProof().require(resource,"task.read","SIMULATION_EVALUATION"));return null;});
 }
 @Test void missingDelegationExpiredLeaseAndRevokedActorFailBeforeOperation()throws Exception{
  when(token.delegatedActions()).thenReturn(List.of());assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->fail("must not run")));
  when(token.delegatedActions()).thenReturn(List.of("task.read"));when(leased.expires()).thenReturn(LocalDateTime.of(2026,10,18,20,0));assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->fail("must not run")));
  when(leased.expires()).thenReturn(LocalDateTime.of(2026,10,18,20,1));when(permissions.currentBinding(actor)).thenReturn(List.of());assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->fail("must not run")));
 }
 @Test void realResourceReaderStillRequiresCurrentGrant()throws Exception{
  var grants=mock(PermissionAuthority.class);var reader=new ResourceReadAuthority(permissions,grants,new PermissionSchema());
  when(permissions.resourceOwner("TASK",task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(task,project)));
  when(permissions.resourceGrants(actor,"task.read")).thenReturn(List.of());when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,20,0));
  when(grants.findResource(any(),anyString(),anyString(),anyString(),anyString(),anyList(),any())).thenReturn(Optional.empty());
  access.execute(http,"actual-token-fixture",request,a->{assertThrows(TenantAccessException.class,()->reader.require(a,resource,"task.read","TASK_READING"));return null;});
  verify(grants).findResource(eq(resource),eq(actor),eq("local"),eq("task.read"),eq("TASK_READING"),eq(List.of()),any());
 }
 @Test void changedLeaseAfterCallbackAndEscapedThreadRefuse()throws Exception{
  assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->{when(leased.generation()).thenReturn(BigInteger.TWO);return "not returned";}));
 }
 @Test void systemJobCannotBecomeFormalWithoutExactProducerType()throws Exception{
  when(persisted.kind()).thenReturn("SYSTEM_JOB");when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
  assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->fail("must not run")));
 }
 @Test void executeActionProvidesSameRealAdmissionAndThreadBoundReadProof()throws Exception{
  access.executeAction(http,"actual-token-fixture",request,(a,actual)->{assertSame(token,actual);var failure=new AtomicReference<Throwable>();Thread other=new Thread(()->{try{a.actorId();}catch(Throwable e){failure.set(e);}});other.start();try{other.join();}catch(InterruptedException e){throw new AssertionError(e);}assertInstanceOf(TenantAccessException.class,failure.get());return null;});
 }
 @Test void permittedResourceStillChecksRealOwnerAndGrant()throws Exception{
  var grants=mock(PermissionAuthority.class);var reader=new ResourceReadAuthority(permissions,grants,new PermissionSchema());var grant=mock(PermissionProtocol.GrantRow.class);
  when(permissions.resourceOwner("TASK",task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(task,project)));when(permissions.resourceGrants(actor,"task.read")).thenReturn(List.of(grant));when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,20,0));
  when(grants.findResource(eq(resource),eq(actor),eq("local"),eq("task.read"),eq("TASK_READING"),eq(List.of(grant)),any())).thenReturn(Optional.of(grant));
  access.execute(http,"actual-token-fixture",request,a->{try{assertSame(grant,reader.require(a,resource,"task.read","TASK_READING"));}catch(java.io.IOException e){throw new AssertionError(e);}when(permissions.resourceOwner("TASK",task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(task,null)));assertThrows(TenantAccessException.class,()->reader.require(a,resource,"task.read","TASK_READING"));return null;});
 }
 @Test void productionProofAndTokenAdmissionHaveNoPublicConstructor(){
  assertEquals(0,FormalWorkerAccess.Proof.class.getConstructors().length);assertEquals(0,RunTokenAuthority.Admission.class.getConstructors().length);
 }
 @Test void mismatchedVerifiedServiceCannotBorrowAnotherWorkersLease()throws Exception{
  when(token.serviceUri()).thenReturn("spiffe://medical/other-worker");assertThrows(TenantAccessException.class,()->access.execute(http,"actual-token-fixture",request,a->fail("must not run")));
 }
 @Test void differentTransactionResourceOrNestedActorScopeCannotReuseProof()throws Exception{
  access.execute(http,"actual-token-fixture",request,a->{
   assertThrows(TenantAccessException.class,()->TenantScope.open(new TenantScope.Identity(tenant,id(99),BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE)));
   TransactionSynchronizationManager.unbindResource(transactionKey);TransactionSynchronizationManager.bindResource(transactionKey,new Object());
   assertThrows(TenantAccessException.class,a::actorId);
   TransactionSynchronizationManager.unbindResource(transactionKey);TransactionSynchronizationManager.bindResource(transactionKey,transactionIdentity);assertEquals(actor,a.actorId());return null;
  });
 }
 @Test void formalWorkerCannotInheritActorsPrivateConversationSourceForAnyUse()throws Exception{
  var sourceMapper=mock(com.ruoyi.medical.persistence.mapper.SourceReadMapper.class);var readAuthority=mock(ResourceReadAuthority.class);var rights=mock(SourceRightsSchema.class);
  var sources=new SourceReadAuthority(sourceMapper,permissions,readAuthority,rights);String version=id(91),source=id(92);
  when(sourceMapper.source(version)).thenReturn(List.of(new SourceReadProtocol.SourceRow(tenant,source,version,project,"SESSION","INTERNAL",id(93),"a".repeat(64),"a".repeat(64),project)));
  access.execute(http,"actual-token-fixture",request,a->{for(var use:List.of(SourceReadProtocol.Use.REVIEW,SourceReadProtocol.Use.HISTORY,SourceReadProtocol.Use.FORMAL,SourceReadProtocol.Use.MODEL_SEND)){
   var requirement=new SourceReadProtocol.ReadRequirement(version,id(93),project,id(94),id(95),id(96),use,"TASK_READING","INTERNAL","LOCAL",use==SourceReadProtocol.Use.MODEL_SEND?"approved-provider":null);
   assertThrows(TenantAccessException.class,()->sources.require(a,requirement));
  }return null;});verifyNoInteractions(readAuthority,rights);
 }
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
}
