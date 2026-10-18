package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;import java.math.BigInteger;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
/** Actual candidate service/store/journal; DB and typed dependency proof use explicit fixtures. */
class FormalAgentCandidateServiceTest extends PublicationLedgerFixture {
 final FormalAgentPublicationSchema formal=new FormalAgentPublicationSchema(json);
 final FormalAgentCandidateDependencies dependencies=mock(FormalAgentCandidateDependencies.class);
 final FormalAgentCandidateStore store=new FormalAgentCandidateStore(runtimeRows,guarded,journal,formal,json,resources);
 final FormalAgentCandidateService service=new FormalAgentCandidateService(runtimeRows,guarded,journal,formal,store,dependencies,resources,json,new IdentityDirectoryPolicy("INTERNAL"));
 final FormalAgentCandidateAuthority identity=new FormalAgentCandidateAuthority(store,formal);
 AgentPublicationMapper.Agent agent;FormalAgentCandidateMapper.Binding binding;final List<String> edges=new ArrayList<>();
 FormalAgentCandidateServiceTest()throws Exception{
  PublicationManifestFixture.load();
  when(dependencies.require(eq(a),nullable(String.class),any(),any(),eq(false))).thenReturn(new FormalAgentCandidateDependencies.Checked(List.of(),json.object().put("explicit_typed_dependency_fixture",true)));
  when(m.insertAgent(anyMap())).thenAnswer(i->{var v=v(i);agent=new AgentPublicationMapper.Agent(id(1),(String)v.get("projectId"),(String)v.get("configId"),(String)v.get("versionId"),(String)v.get("name"),(String)v.get("dependencyManifest"),(String)v.get("limits"),null,(String)v.get("contentHash"),"DRAFT","1.0.0",(String)v.get("manifest"),actor,(java.time.LocalDateTime)v.get("now"),(String)v.get("classification"),BigInteger.ONE);return 1;});
  when(m.agent(anyMap())).thenAnswer(i->agent!=null&&agent.versionId().equals(v(i).get("versionId"))?List.of(agent):List.of());
  when(m.insertLineage(anyMap())).thenAnswer(i->{var v=v(i);edges.add(v.get("relation")+":"+v.get("upstreamId"));return 1;});
  when(runtimeRows.parents(anyMap())).thenAnswer(i->edges.stream().sorted().toList());
  when(runtimeRows.insertCandidate(anyMap())).thenAnswer(i->{var v=v(i);binding=new FormalAgentCandidateMapper.Binding(id(1),(String)v.get("projectId"),(String)v.get("versionId"),(String)v.get("contentHash"),(String)v.get("authorRecordId"),(String)v.get("policyVersionId"),(String)v.get("policySha256"),(String)v.get("fixedJson"),"1.0.0",(String)v.get("manifest"),actor,(java.time.LocalDateTime)v.get("now"),(String)v.get("classification"));return 1;});
  when(runtimeRows.candidate(anyMap())).thenAnswer(i->binding!=null&&binding.versionId().equals(v(i).get("versionId"))?List.of(binding):List.of());
 }
 JsonNode request()throws Exception{var manifest=PublicationManifestFixture.input();for(String field:List.of("tenant_id","agent_version_id","agent_version_sha256","release_id","evaluation_id","evaluation_sha256"))manifest.remove(field);var request=json.object().put("schema_version","1.0.0").putNull("project_id").put("name","Formal candidate").put("reason","Real authored configuration fixture");request.set("manifest",manifest);request.set("execution_policy",json.tree(new PermissionProtocol.Basis(id(80),H)));return request;}
 JsonNode author()throws Exception{return service.author(a,"candidate-original-key-001",formal.canonical(request()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @Test void createsRealDraftHeadVersionBindingAndImmutableJournal()throws Exception{var r=author();assertEquals("DRAFT",agent.status());assertNull(agent.evaluationId());assertEquals("AUTHOR",records.get(r.path("resource_id").asText()).kind());assertEquals(1,records.size());assertEquals(1,operations.size());assertEquals(1,audits.size());assertEquals(1,events.size());assertEquals(binding.versionId(),service.read(a,r.path("resource_id").asText()).path("fixed").path("version_id").asText());}
 @Test void originalKeyReplayDoesNotDuplicateCandidate()throws Exception{var body=formal.canonical(request()).getBytes(java.nio.charset.StandardCharsets.UTF_8);var r=service.author(a,"candidate-original-key-001",body);assertEquals(r,service.author(a,"candidate-original-key-001",body));verify(m,times(1)).insertAgent(anyMap());}
 @Test void changedSameKeyBodyRejected()throws Exception{var body=request();service.author(a,"candidate-original-key-001",formal.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));((com.fasterxml.jackson.databind.node.ObjectNode)body).put("reason","changed");assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,()->service.author(a,"candidate-original-key-001",formal.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8))).code());}
 @Test void noWriteWhenTypedDependencyProofRejects()throws Exception{when(dependencies.require(eq(a),nullable(String.class),any(),any(),eq(false))).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));assertThrows(TenantAccessException.class,this::author);verify(m,never()).insertAgent(anyMap());}
 @Test void missingOriginalAuditRejectsRead()throws Exception{var r=author();audits.clear();assertThrows(TenantAccessException.class,()->service.read(a,r.path("resource_id").asText()));}
 @Test void extraLineageRelationIsNotHidden()throws Exception{var r=author();edges.add("UNDECLARED:"+id(987));assertThrows(TenantAccessException.class,()->service.read(a,r.path("resource_id").asText()));}
 @Test void receiptNeedsCurrentReadButNotRetainedWriteGrant()throws Exception{var r=author();when(resources.require(eq(a),any(),eq("agent.configure"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertEquals(r,service.receipt(a,r.path("resource_id").asText(),r.path("operation_id").asText()));when(resources.require(eq(a),any(),eq("configuration.read"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->service.receipt(a,r.path("resource_id").asText(),r.path("operation_id").asText()));}
 @Test void anotherActorCannotClaimOriginalOperation()throws Exception{var r=author();actor=id(99);assertThrows(TenantAccessException.class,()->service.receipt(a,r.path("resource_id").asText(),r.path("operation_id").asText()));}
 @Test void candidateIdentityNeverPretendsToBePublished()throws Exception{author();var basis=new PermissionProtocol.Basis(binding.versionId(),binding.contentSha256());var proof=identity.require(a,basis,null,"EVALUATION_RESULTS");assertEquals("DRAFT",proof.status());assertEquals(basis,proof.candidate());proof.requireConsumer(a,basis,null,"EVALUATION_RESULTS");assertThrows(TenantAccessException.class,()->proof.requireConsumer(a,basis,id(80),"EVALUATION_RESULTS"));}
}
