package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Explicit authority/SQL doubles; verifies dispatch of exact dependency checks, not real grants or database access. */
class SimulationRuleDependenciesTest {
    static final String TENANT="00000000-0000-4000-8000-000000000001", ROOT="00000000-0000-4000-8000-000000000002",
        SOURCE="00000000-0000-4000-8000-000000000003", RIGHTS="00000000-0000-4000-8000-000000000004", HASH="a".repeat(64);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final VersionResourceAuthority versions=mock(VersionResourceAuthority.class);
    final SourceReadAuthority authority=mock(SourceReadAuthority.class);
    final SourceReadMapper sources=mock(SourceReadMapper.class);
    final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class);
    final TemplateMapper templates=mock(TemplateMapper.class);
    final SimulationRuleVersionMapper rules=mock(SimulationRuleVersionMapper.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    PermissionSchema json; SimulationRuleVersionSchema schema; SimulationRuleDependencies dependencies;
    @BeforeEach void setup() throws Exception {
        json=new PermissionSchema();schema=new SimulationRuleVersionSchema(json,new TemplateApplicabilitySchema(json),new RuleExpression());
        dependencies=new SimulationRuleDependencies(lineage,versions,authority,sources,artifacts,templates,rules,
            new TemplateDependencyPolicy("TEST","TEST",32,64,List.of()),schema,json);
        when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(TENANT,"1","1","1"));
        var subject=mock(com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified.class);
        when(subject.user()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.User(TENANT,"test","Test",null));
        when(admission.subject()).thenReturn(subject);when(admission.actorId()).thenReturn(TENANT);
    }
    private SimulationRuleVersionProtocol.Validated request() throws Exception {
        var expression=new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"true",List.of(),List.of());
        return schema.version(new SimulationRuleVersionProtocol.VersionInput("1.0.0",null,SimulationRuleVersionSchema.CONTENT_REF,
            new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,expression,List.of(new PermissionProtocol.Basis(ROOT,HASH))),
            "Synthetic dependency test",SimulationRuleVersionSchema.USE,false));
    }
    private DependencyLineage.VerifiedLineage graph(ArtifactRegistryProtocol.VersionRow... rows) {
        var g=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);
        var map=new java.util.LinkedHashMap<String,ArtifactRegistryProtocol.VersionRow>();
        for(var row:rows){g.addVertex(row.id());map.put(row.id(),row);}
        if(rows.length>1)g.addEdge(ROOT,SOURCE,new SourceReadProtocol.ParentRow(ROOT,SOURCE,"TEST"));
        var graph=new DependencyLineage.VerifiedLineage(ROOT,g,map);when(lineage.inspect(eq(ROOT),any())).thenReturn(graph);return graph;
    }
    @Test void currentSourceAuthorityGetsOnlyReviewAndExactSimulationPurpose() throws Exception {
        graph(new ArtifactRegistryProtocol.VersionRow(ROOT,null,"SOURCE_VERSION",HASH));
        when(sources.source(ROOT)).thenReturn(List.of(new SourceReadProtocol.SourceRow(TENANT,SOURCE,ROOT,null,"ENTERPRISE","INTERNAL",RIGHTS,HASH,HASH,null)));
        when(authority.require(eq(admission),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->dependencies.requireRule(admission,request())).code());
        var requirement=ArgumentCaptor.forClass(SourceReadProtocol.ReadRequirement.class);verify(authority).require(eq(admission),requirement.capture());
        assertEquals(SourceReadProtocol.Use.REVIEW,requirement.getValue().use());
        assertEquals("SIMULATION_EVALUATION",requirement.getValue().purpose());assertEquals(RIGHTS,requirement.getValue().rightsRevision());
        assertNull(requirement.getValue().projectId());verifyNoInteractions(rules);
    }
    @Test void projectOwnedAncestorIsRejectedRatherThanDroppingItsScope() throws Exception {
        graph(new ArtifactRegistryProtocol.VersionRow(ROOT,null,"CONTEXT_MANIFEST",HASH),new ArtifactRegistryProtocol.VersionRow(SOURCE,TENANT,"SOURCE_VERSION",HASH));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->dependencies.requireRule(admission,request())).code());
        verifyNoInteractions(authority,versions,rules);
    }
    @Test void artifactWithoutExactDeploymentProfileIsRejected() throws Exception {
        graph(new ArtifactRegistryProtocol.VersionRow(ROOT,null,"ARTIFACT",HASH));
        var binding=mock(ArtifactRegistryProtocol.BindingRow.class);when(binding.schemaRef()).thenReturn("urn:synthetic:missing-profile");
        when(artifacts.binding(ROOT)).thenReturn(List.of(binding));
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(TenantAccessException.class,()->dependencies.requireRule(admission,request())).code());
        verifyNoInteractions(versions,rules);
    }
    @Test void legacyRuleAncestorWithoutSimulationBindingCannotPassAsSimulation() throws Exception {
        var graph=graph(new ArtifactRegistryProtocol.VersionRow(ROOT,null,"RULE_VERSION",HASH));
        var owner=new VersionResourceProtocol.OwnerRow(TENANT,ROOT,null,null,"RULE",ROOT,HASH,null,null,null,null);
        var version=new VersionResourceAuthority.VerifiedVersion(graph.versions().get(ROOT),VersionResourceProtocol.Domain.RULE_VERSION,owner,null,null,null);
        when(versions.require(eq(admission),eq(graph),eq("SIMULATION_EVALUATION"),any(),anyMap())).thenReturn(Map.of(ROOT,version));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->dependencies.requireRule(admission,request())).code());
        verify(rules).definition(anyMap());verify(rules,never()).approvedWorkflow(anyMap());
        var context=ArgumentCaptor.forClass(VersionResourceProtocol.ReadContext.class);
        verify(versions).require(eq(admission),eq(graph),eq("SIMULATION_EVALUATION"),context.capture(),anyMap());
        assertEquals(SourceReadProtocol.Use.REVIEW,context.getValue().use());
    }
    @Test void legacyTemplateTraversalRejectsSimulationBindingBeforeAcceptingApprovedState() throws Exception {
        var graph=graph(new ArtifactRegistryProtocol.VersionRow(ROOT,null,"RULE_VERSION",HASH));
        var owner=new VersionResourceProtocol.OwnerRow(TENANT,ROOT,null,null,"RULE",ROOT,HASH,null,null,null,null);
        var version=new VersionResourceAuthority.VerifiedVersion(graph.versions().get(ROOT),VersionResourceProtocol.Domain.RULE_VERSION,owner,null,null,null);
        when(versions.require(eq(admission),eq(graph),eq("TEMPLATE_MAINTENANCE"),any(),anyMap())).thenReturn(Map.of(ROOT,version));
        var legacy=mock(RuleVersionMapper.class);when(legacy.simulationBinding(anyMap())).thenReturn(List.of(HASH));
        var old=new TemplateDependencyAuthority(lineage,versions,authority,sources,artifacts,templates,legacy,new TemplateDependencyPolicy("TEST","TEST",32,64,List.of()));
        var request=request();var input=request.request();
        var ordinary=new RuleVersionProtocol.Validated(new RuleVersionProtocol.VersionInput(input.schema_version(),input.base_version(),input.content_schema_ref(),input.content(),input.reason()),
            request.canonical_content(),request.content_sha256(),request.dependency_refs());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->old.requireRule(admission,ordinary)).code());
        verify(legacy).simulationBinding(anyMap());verify(legacy,never()).approvedWorkflow(anyMap());
    }

}
