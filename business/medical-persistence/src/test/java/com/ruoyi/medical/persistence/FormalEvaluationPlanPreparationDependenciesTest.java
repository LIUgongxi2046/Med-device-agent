package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real graph traversal and member/classification derivation. Current governance permission
 * authority and database rows are explicit boundaries, never fabricated production evidence. */
class FormalEvaluationPlanPreparationDependenciesTest extends PlanPreparationFixture {
    final AgentPublicationDependencies authority = mock(AgentPublicationDependencies.class);
    final SourceReadMapper sourceRows = mock(SourceReadMapper.class);
    final ArtifactRegistryMapper registry = mock(ArtifactRegistryMapper.class);
    final FormalEvaluationExecutionAdmission.Prepared root = mock(FormalEvaluationExecutionAdmission.Prepared.class);
    final FormalEvaluationPlanPreparationDependencies service = new FormalEvaluationPlanPreparationDependencies(
            authority, new DependencyLineage(sourceRows, registry), sourceRows, registry, json);
    final List<String> ids = new ArrayList<>();

    FormalEvaluationPlanPreparationDependenciesTest() throws Exception {
        when(root.projectId()).thenReturn(project);
        when(root.classification()).thenReturn("INTERNAL");
        var policy = mock(AgentExecutionPolicyProtocol.Definition.class);
        when(policy.businessPurpose()).thenReturn("EVALUATION_RESULTS");
        when(root.policy()).thenReturn(policy);
        int index = 0;
        for (int artifact : List.of(20, 30, 31)) {
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
        when(authority.requireByUse(eq(admission), eq(project), anyList())).thenAnswer(invocation ->
                new AgentPublicationDependencies.Checked(ids, json.object().put("authority", "explicit test double")));
    }

    FormalEvaluationPlanPreparationDependencies.Checked read() throws Exception {
        return tx(() -> service.require(admission, root, checked.node(), basis(30), basis(31)));
    }

    @Test void allTransitiveSourcesAndActualRightsAreRetainedWithMaximumClassification() throws Exception {
        var result = read();
        assertEquals(6, result.members().size());
        assertEquals(3, result.roots().size());
        assertEquals("PERSONAL_HEALTH", result.classification());
        for (var member : result.members()) {
            int version = Integer.parseInt(member.upstreamVersionId().substring(24));
            assertEquals(id(version < 100 ? version + 200 : version + 100), member.rightsRevision());
        }
    }

    @Test void purposesAreExplicitAndExpectedAnswerIsNeverAContentRoot() throws Exception {
        read();
        var capture = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(authority).requireByUse(eq(admission), eq(project), capture.capture());
        List<AgentPublicationDependencies.DependencyUse> uses = capture.getValue();
        assertEquals(checked.node().cell().input(), uses.get(0).root());
        assertEquals("EVALUATION_RESULTS", uses.get(0).purpose());
        assertTrue(uses.subList(1, uses.size()).stream().allMatch(use -> use.purpose().equals("EVALUATION_RESULTS")));
        assertTrue(uses.stream().noneMatch(use -> use.root().equals(checked.node().cell().expected())));
    }

    @Test void unreportedAncestorCannotDisappearFromTheAccessSet() throws Exception {
        when(authority.requireByUse(any(), any(), anyList())).thenReturn(
                new AgentPublicationDependencies.Checked(ids.subList(1, ids.size()), json.object()));
        assertThrows(TenantAccessException.class, this::read);
    }

    @Test void sourceFreeMetadataCannotReceiveAnInventedRightsRevision() throws Exception {
        when(sourceRows.parents(id(30))).thenReturn(List.of());
        ids.remove(id(130));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class, this::read).code());
    }

    @Test void rightsForAnotherSourceCannotBeSubstituted() throws Exception {
        var wrong = mock(SourceReadProtocol.RightsRow.class);
        when(wrong.sourceVersion()).thenReturn(id(999));
        when(sourceRows.rights(id(230))).thenReturn(List.of(wrong));
        assertThrows(TenantAccessException.class, this::read);
    }

    @Test void revokedCurrentPermissionPropagatesAndNoMemberIsProduced() throws Exception {
        when(authority.requireByUse(any(), any(), anyList())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, this::read).code());
    }
    @Test void historicalReadTraversalUsesSameExactClosureWithoutExecutionProof() throws Exception {
        var uses=List.of(new AgentPublicationDependencies.DependencyUse(basis(20),"EVALUATION_RESULTS"),new AgentPublicationDependencies.DependencyUse(basis(30),"EVALUATION_RESULTS"),new AgentPublicationDependencies.DependencyUse(basis(31),"EVALUATION_RESULTS"));
        var result=tx(()->service.require(admission,project,"INTERNAL",uses));assertEquals(6,result.members().size());verifyNoInteractions(root);
        var source=mock(SourceReadProtocol.SourceRow.class);when(source.sourceVersion()).thenReturn(id(130));when(source.rightsRevision()).thenReturn(id(998));when(source.classification()).thenReturn("RESTRICTED");when(sourceRows.source(id(130))).thenReturn(List.of(source));
        var rights=mock(SourceReadProtocol.RightsRow.class);when(rights.sourceVersion()).thenReturn(id(130));when(rights.classification()).thenReturn("RESTRICTED");when(sourceRows.rights(id(998))).thenReturn(List.of(rights));
        var changed=tx(()->service.require(admission,project,"INTERNAL",uses));assertNotEquals(result.members(),changed.members());assertTrue(changed.members().stream().filter(m->Set.of(id(30),id(130)).contains(m.upstreamVersionId())).allMatch(m->m.rightsRevision().equals(id(998))));
    }

    @Test void sameMembersWithDifferentRelationshipStillChangeCurrentTopology()throws Exception {
        var original=read();when(sourceRows.parents(id(30))).thenReturn(List.of(new SourceReadProtocol.ParentRow(id(30),id(130),"DERIVED_FROM")));
        var changed=read();assertEquals(original.members(),changed.members());assertEquals(original.roots(),changed.roots());assertNotEquals(original.topology(),changed.topology());
        var mutable=(com.fasterxml.jackson.databind.node.ObjectNode)original.topology();mutable.removeAll();assertTrue(original.topology().has("edges"));
    }

}
