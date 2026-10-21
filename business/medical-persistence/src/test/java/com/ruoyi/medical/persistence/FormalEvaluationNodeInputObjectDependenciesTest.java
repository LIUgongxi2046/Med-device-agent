package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real graph traversal and member/classification derivation. Current governance permission
 * authority and database rows are explicit boundaries, never fabricated production evidence. */
class FormalEvaluationNodeInputObjectDependenciesTest extends NodeInputObjectFixture {
    final AgentPublicationDependencies authority = mock(AgentPublicationDependencies.class);
    final SourceReadMapper sourceRows = mock(SourceReadMapper.class);
    final ArtifactRegistryMapper registry = mock(ArtifactRegistryMapper.class);
    final FormalEvaluationExecutionAdmission.Prepared root = mock(FormalEvaluationExecutionAdmission.Prepared.class);
    final FormalEvaluationNodeInputObjectDependencies service = new FormalEvaluationNodeInputObjectDependencies(
            authority, new DependencyLineage(sourceRows, registry), sourceRows, registry, json);
    final List<String> ids = new ArrayList<>();

    FormalEvaluationNodeInputObjectDependenciesTest() throws Exception {
        when(root.projectId()).thenReturn(project);
        when(root.classification()).thenReturn("INTERNAL");
        var policy = mock(AgentExecutionPolicyProtocol.Definition.class);
        when(policy.businessPurpose()).thenReturn("EVALUATION_RESULTS");
        when(root.policy()).thenReturn(policy);
        int index = 0;
        for (int artifact : List.of(20, 30, 31, 32, 40, 41)) {
            int source = artifact + 100;
            String artifactId = id(artifact), sourceId = id(source), rightsId = id(source + 100);
            ids.add(artifactId); ids.add(sourceId);
            when(registry.version(artifactId)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(artifactId, project, "ARTIFACT", hash('a'))));
            when(registry.version(sourceId)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(sourceId, null, "SOURCE_VERSION", hash('a'))));
            when(sourceRows.parents(artifactId)).thenReturn(List.of(new SourceReadProtocol.ParentRow(artifactId, sourceId, "DEPENDS_ON")));
            when(sourceRows.parents(sourceId)).thenReturn(List.of());
            var artifactRow = mock(ArtifactRegistryProtocol.ArtifactRow.class);
            when(artifactRow.classification()).thenReturn("INTERNAL");
            when(registry.artifact(artifactId)).thenReturn(List.of(artifactRow));
            var sourceRow = mock(SourceReadProtocol.SourceRow.class);
            when(sourceRow.sourceVersion()).thenReturn(sourceId);
            when(sourceRow.rightsRevision()).thenReturn(rightsId);
            when(sourceRow.classification()).thenReturn(index++ == 2 ? "PERSONAL_HEALTH" : "INTERNAL");
            when(sourceRows.source(sourceId)).thenReturn(List.of(sourceRow));
            var rights = mock(SourceReadProtocol.RightsRow.class);
            when(rights.sourceVersion()).thenReturn(sourceId);
            when(rights.classification()).thenReturn("RESTRICTED");
            when(sourceRows.rights(rightsId)).thenReturn(List.of(rights));
        }
        when(registry.version(id(42))).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(id(42),project,"ARTIFACT",hash('a'))));
        var owned=mock(ArtifactRegistryProtocol.ArtifactRow.class);when(owned.classification()).thenReturn("INTERNAL");when(registry.artifact(id(42))).thenReturn(List.of(owned));
        when(sourceRows.parents(id(42))).thenReturn(List.of(new SourceReadProtocol.ParentRow(id(42),id(20),"EVALUATION_PLAN_PREPARATION"),new SourceReadProtocol.ParentRow(id(42),id(40),"EVALUATION_PLAN_PREPARATION"),new SourceReadProtocol.ParentRow(id(42),id(41),"EVALUATION_PLAN_PREPARATION")));
        when(authority.requireByUse(eq(admission), eq(project), anyList())).thenAnswer(invocation ->
                new AgentPublicationDependencies.Checked(ids, json.object().put("authority", "explicit test double")));
    }

    FormalEvaluationNodeInputObjectDependencies.Checked read() throws Exception {
        return tx(() -> service.require(admission, root, checked.node(), evidence()));
    }


    com.fasterxml.jackson.databind.JsonNode evidence(){var e=(com.fasterxml.jackson.databind.node.ObjectNode)checked.evidence();var p=e.putObject("planning_original");p.putObject("receipt").put("artifact_id",id(42)).put("artifact_sha256",hash('a'));var data=p.putObject("planning");data.set("template",json.tree(basis(40)));data.put("profile_revision",id(41)).put("profile_sha256",hash('a'));return e;}
    @Test void allAncestorsAndTypedTopologyRetained()throws Exception{var result=read();assertEquals(13,result.members().size());assertEquals(7,result.roots().size());assertEquals(13,result.topology().required("versions").size());assertEquals(9,result.topology().required("edges").size());assertEquals("PERSONAL_HEALTH",result.classification());}
    @Test void sameMemberTypedEdgeChangeInvalidatesCurrentProof()throws Exception{var before=read();when(sourceRows.parents(id(30))).thenReturn(List.of(new SourceReadProtocol.ParentRow(id(30),id(130),"CHANGED_RELATION")));var after=read();assertEquals(before.members(),after.members());assertEquals(before.authorizations(),after.authorizations());assertNotEquals(before.topology(),after.topology());assertNotEquals(before,after);}
    @Test void forgedPlanGraphCannotIncludeAnotherAncestor()throws Exception{when(sourceRows.parents(id(42))).thenReturn(List.of(new SourceReadProtocol.ParentRow(id(42),id(30),"EVALUATION_PLAN_PREPARATION")));assertThrows(TenantAccessException.class,this::read);}
    @Test void sourcePurposesAreIndependentAndExpectedAnswerNeverIncluded()throws Exception{read();var capture=org.mockito.ArgumentCaptor.forClass(List.class);verify(authority).requireByUse(eq(admission),eq(project),capture.capture());List<AgentPublicationDependencies.DependencyUse> uses=capture.getValue();assertEquals(6,uses.size());assertEquals("EVALUATION_RESULTS",uses.get(0).purpose());assertTrue(uses.subList(1,4).stream().allMatch(u->u.purpose().equals("AGENT_PUBLICATION")));assertTrue(uses.subList(4,6).stream().allMatch(u->u.purpose().equals("EVALUATION_RESULTS")));assertTrue(uses.stream().noneMatch(u->u.root().equals(checked.node().cell().expected())));}
}
