package com.ruoyi.medical.persistence;
import java.util.*;
import com.ruoyi.medical.persistence.mapper.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Real lineage traversal and content closure; source/current grants mocked at boundary. */
class TaskRunReplayContentAuthorityTest {
 FormalWorkerAccessTest f;RuntimeReplaySchema.VerifiedInputs input;SourceReadAuthority sourceAuthority;SourceReadMapper graph;ArtifactRegistryMapper registry;VersionResourceMapper owners;TaskRunReplayContentAuthority authority;
 com.fasterxml.jackson.databind.node.ObjectNode value;String source,parse,index,rights;SourceReadProtocol.RightsRow right;SourceReadProtocol.SourceRow sourceRow;SourceReadProtocol.SelectionRow selection;
 @BeforeEach void setup()throws Exception{
  f=new FormalWorkerAccessTest();f.setUp();source=f.id(70);parse=f.id(71);index=f.id(72);rights=f.id(73);
  input=mock(RuntimeReplaySchema.VerifiedInputs.class);sourceAuthority=mock(SourceReadAuthority.class);graph=mock(SourceReadMapper.class);registry=mock(ArtifactRegistryMapper.class);owners=mock(VersionResourceMapper.class);
  value=f.schema.object().put("tenant_id",f.tenant).put("project_id",f.project);value.putObject("scope_snapshot").putArray("members").addObject().put("source_id",f.id(74)).put("source_version",source).put("parse_version",parse).put("index_version",index).put("approval_revision",f.id(75)).put("binding_revision",f.id(76)).put("rights_revision",rights).put("original_sha256","a".repeat(64));var deps=value.putArray("dependency_versions");var edges=value.putArray("dependency_edges");edges.addObject().put("consumer_version_id",parse).put("upstream_version_id",source).put("relation_kind","PARSED_FROM");edges.addObject().put("consumer_version_id",index).put("upstream_version_id",parse).put("relation_kind","INDEXED_FROM");
  var rows=new ArrayList<VersionResourceProtocol.DomainOwnerRow>();
  for(var pair:List.of(Map.entry(source,"SOURCE_VERSION"),Map.entry(parse,"PARSE_VERSION"),Map.entry(index,"INDEX_MANIFEST"))){String id=pair.getKey(),kind=pair.getValue();deps.addObject().put("version_id",id).put("project_id",f.project).put("entity_kind",kind).put("content_sha256","a".repeat(64));when(registry.version(id)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(id,f.project,kind,"a".repeat(64))));rows.add(new VersionResourceProtocol.DomainOwnerRow(kind,new VersionResourceProtocol.OwnerRow(f.tenant,id,f.project,f.project,"SOURCE",f.id(74),"a".repeat(64),source,null,null,null)));}
  when(owners.ownersAcrossDomains(anyList(),anyList())).thenReturn(rows);when(graph.parents(source)).thenReturn(List.of());when(graph.parents(parse)).thenReturn(List.of(new SourceReadProtocol.ParentRow(parse,source,"PARSED_FROM")));when(graph.parents(index)).thenReturn(List.of(new SourceReadProtocol.ParentRow(index,parse,"INDEXED_FROM")));
  sourceRow=mock(SourceReadProtocol.SourceRow.class);when(sourceRow.sourceId()).thenReturn(f.id(74));when(sourceRow.sourceVersion()).thenReturn(source);when(sourceRow.originalSha256()).thenReturn("a".repeat(64));when(sourceRow.ownerProjectId()).thenReturn(f.project);
  right=mock(SourceReadProtocol.RightsRow.class);when(right.rightsRevision()).thenReturn(rights);selection=mock(SourceReadProtocol.SelectionRow.class);when(selection.indexVersion()).thenReturn(index);when(selection.approvalRevision()).thenReturn(f.id(75));when(selection.bindingRevision()).thenReturn(f.id(76));
  when(sourceAuthority.require(any(),any())).thenReturn(new SourceReadProtocol.VerifiedSource(sourceRow,right,selection,null,null,null,null));when(input.content()).thenAnswer(x->value.deepCopy());when(input.runId()).thenReturn(f.run);when(input.snapshotId()).thenReturn(f.id(77));var policy=mock(AgentExecutionPolicyProtocol.Definition.class);when(policy.businessPurpose()).thenReturn("DOCUMENT_BODY");when(policy.environment()).thenReturn("test");when(policy.region()).thenReturn("local");when(input.executionPolicy()).thenReturn(policy);
  authority=new TaskRunReplayContentAuthority(sourceAuthority,new DependencyLineage(graph,registry),owners,new DependencyLineage.Limits(50,50));
 }
 void run(java.util.function.Consumer<TaskRunReplayContentAuthority.Verified> inspect)throws Exception{f.access.execute(f.http,"actual-token-fixture",f.request,a->{try{var proof=mock(TaskRunBootstrapAuthority.Verified.class);when(proof.admission()).thenReturn(a);when(proof.replay()).thenReturn(input);when(proof.runId()).thenReturn(f.run);when(proof.tenantId()).thenReturn(f.tenant);when(proof.projectId()).thenReturn(f.project);var result=authority.require(proof);inspect.accept(result);return null;}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}});}
 @AfterEach void cleanup(){f.cleanup();}
 @Test void completeParseAndIndexAncestryUsesOnlyActualSourceRights()throws Exception{run(p->{assertEquals(3,p.members().size());assertTrue(p.members().stream().allMatch(m->rights.equals(m.rightsRevision())));assertEquals(List.of(source,parse,index),p.roots());});}
 @Test void missingUpstreamInFixedReplayCannotBeHidden(){((com.fasterxml.jackson.databind.node.ArrayNode)value.required("dependency_versions")).remove(0);assertThrows(TenantAccessException.class,()->run(p->fail()));}
 @Test void governanceOnlyAncestorCannotAcquireInventedRights(){when(owners.ownersAcrossDomains(anyList(),anyList())).thenReturn(List.of());assertThrows(TenantAccessException.class,()->run(p->fail()));}
 @Test void revokedSourceReadPreventsContentProof()throws Exception{when(sourceAuthority.require(any(),any())).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->run(p->fail()));}
 @Test void changedCurrentRightsCannotBorrowSavedRights(){when(right.rightsRevision()).thenReturn(f.id(99));assertThrows(TenantAccessException.class,()->run(p->fail()));}
 @Test void proofRechecksCurrentSourceAfterConstruction()throws Exception{run(p->{try{when(sourceAuthority.require(any(),any())).thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,p::requireUnchanged);}catch(Exception e){throw new RuntimeException(e);}});}
 @Test void changedGovernanceHashCannotReusePinnedGraph(){when(registry.version(index)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(index,f.project,"INDEX_MANIFEST","f".repeat(64))));assertThrows(TenantAccessException.class,()->run(p->fail()));}
 @Test void undeclaredGovernanceEdgeCannotBeHidden(){when(graph.parents(index)).thenReturn(List.of(new SourceReadProtocol.ParentRow(index,parse,"SUBSTITUTED")));assertThrows(TenantAccessException.class,()->run(p->fail()));}
}
