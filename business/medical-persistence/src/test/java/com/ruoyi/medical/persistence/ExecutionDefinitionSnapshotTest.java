package com.ruoyi.medical.persistence;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import java.util.*;import java.math.BigInteger;import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.ruoyi.medical.persistence.mapper.*;import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
class ExecutionDefinitionSnapshotTest {
 final PermissionSchema json=new PermissionSchema();final EvaluationSchema schema=new EvaluationSchema(json);
 final EvaluationMapper mapper=mock(EvaluationMapper.class); final PermissionMapper permissions=mock(PermissionMapper.class);
 final ResourceReadAuthority authority=mock(ResourceReadAuthority.class);final EvaluationRuleAuthority rules=mock(EvaluationRuleAuthority.class);
 final EnterpriseAccess.Admission actor=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);
 final EvaluationService service;final EvaluationProtocol.Definition definition; final EvaluationProtocol.Stored row;
 ExecutionDefinitionSnapshotTest()throws Exception {
  when(actor.context().tenant_id()).thenReturn(EvaluationFixture.id(2));when(actor.actorId()).thenReturn(EvaluationFixture.id(5));
  when(permissions.databaseTime()).thenReturn(LocalDateTime.of(2026,10,18,0,0));when(permissions.projectStatus(anyString())).thenReturn(List.of("ACTIVE"));
  service=new EvaluationService(null,mapper,permissions,authority,mock(EvaluationReferences.class),schema,new EvaluationVerdict(schema),json,new IdentityDirectoryPolicy("TEST"),rules,null);
  definition=EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(1000,"REAL","DEVELOPMENT")),List.of("EVAL-02"),1);
  var projection=EvaluationService.class.getDeclaredMethod("projection",EvaluationProtocol.Create.class,List.class);projection.setAccessible(true);
  row=new EvaluationProtocol.Stored(definition.tenant_id(),definition.request().project_id(),definition.evaluation_id(),json.canonicalObject(definition),schema.hash(definition),schema.hash(definition),json.canonicalObject(projection.invoke(service,definition.request(),List.of())),"QUEUED",BigInteger.ONE,null,null,"1.0.0",schema.manifestSha256(),definition.created_by(),LocalDateTime.of(2026,10,16,0,0),"TEST");
  when(mapper.definition(anyMap())).thenReturn(List.of(row));
  when(mapper.candidate(anyMap())).thenAnswer(c->{var id=((Map<?,?>)c.getArgument(0)).get("candidateId");var x=definition.candidate_snapshots().stream().filter(v->v.version_id().equals(id)).findFirst().orElseThrow();return List.of(new EvaluationProtocol.AgentRow(definition.tenant_id(),definition.request().project_id(),x.version_id(),x.content_sha256(),x.content_sha256(),x.agent_id(),x.created_by(),json.canonicalObject(x.dependency_manifest()),json.canonicalObject(x.limits()),"DRAFT",x.schema_version(),x.schema_manifest_sha256()));});
  var refs=EvaluationService.class.getDeclaredMethod("definitionRefs",EvaluationProtocol.Create.class);refs.setAccessible(true);
  @SuppressWarnings("unchecked") var bases=(List<PermissionProtocol.Basis>)refs.invoke(service,definition.request());when(mapper.parents(anyMap())).thenReturn(bases.stream().map(PermissionProtocol.Basis::version_id).toList());
  when(rules.boundSamples(eq(definition.request()),any())).thenReturn(definition.approved_expectation_samples());
  when(authority.require(any(),any(),any(),any())).thenReturn(new PermissionProtocol.GrantRow(EvaluationFixture.id(90),EvaluationFixture.id(5),definition.request().project_id(),"EVALUATION",definition.evaluation_id(),"evaluation.run","{}","ACTIVE",LocalDateTime.of(2026,1,1,0,0),null,EvaluationFixture.id(91),"1.0.0","b".repeat(64),BigInteger.ONE));
 }
 TenantScope scope(){return TenantScope.open(new TenantScope.Identity(actor.context().tenant_id(),actor.actorId(),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));}
 EvaluationService.ExecutionDefinitionSnapshot read()throws Exception{return service.requireExecutionDefinition(actor,definition.evaluation_id());}
 @Test void mutableReturnedJsonCannotChangeOriginalProof()throws Exception{try(var scope=scope()){var proof=read();((com.fasterxml.jackson.databind.node.ObjectNode)proof.definition().candidate_snapshots().get(0).dependency_manifest()).put("forged",true);((com.fasterxml.jackson.databind.node.ObjectNode)proof.definition().candidate_snapshots().get(0).limits()).put("forged",true);assertFalse(proof.definition().candidate_snapshots().get(0).limits().has("forged"));assertFalse(proof.definition().candidate_snapshots().get(0).dependency_manifest().has("forged"));proof.requireUnchanged();}}
 @Test void sameScopeReloadsActualOriginal()throws Exception{try(var scope=scope()){var proof=read();clearInvocations(mapper);proof.requireUnchanged();verify(mapper).definition(anyMap());}}
 @Test void replacementScopeWithSameIdentityCannotReuseProof()throws Exception{EvaluationService.ExecutionDefinitionSnapshot proof;try(var scope=scope()){proof=read();}try(var scope=scope()){clearInvocations(mapper);assertThrows(TenantAccessException.class,proof::requireUnchanged);verifyNoInteractions(mapper);}}
 @Test void otherThreadCannotReuseProofEvenWithMatchingScope()throws Exception{try(var scope=scope()){var proof=read();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();Thread t=new Thread(()->{try(var other=scope()){try{proof.requireUnchanged();}catch(Throwable x){failure.set(x);}}});t.start();t.join();assertInstanceOf(TenantAccessException.class,failure.get());}}
 @Test void originalDisappearanceRevokesProof()throws Exception{try(var scope=scope()){var proof=read();when(mapper.definition(anyMap())).thenReturn(List.of());assertThrows(TenantAccessException.class,proof::requireUnchanged);}}
 @Test void changedRunGrantRevokesProof()throws Exception{try(var scope=scope()){var proof=read();when(authority.require(any(),any(),eq("evaluation.run"),any())).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,proof::requireUnchanged);}}
}
