package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.time.LocalDateTime;
import java.util.*;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationContextDependenciesTest {
    private static String id(int n){return String.format("00000000-0000-0000-0000-%012d",n);}
    private static final String HASH="a".repeat(64);
    final SimulationFixtureMapper mapper=mock(SimulationFixtureMapper.class);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final EvaluationReferences references=mock(EvaluationReferences.class);
    final SourceReadMapper sources=mock(SourceReadMapper.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final PermissionSchema json=new PermissionSchema();
    final SimulationContextDependencies helper=new SimulationContextDependencies(mapper,lineage,references,json,sources,new RetrievalPolicy("TEST","local",10));
    SimulationContextDependenciesTest()throws Exception{
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(admission.actorId()).thenReturn(id(9));
        when(lineage.inspect(eq(id(2)),any())).thenReturn(graph(HASH));
        when(mapper.contextConfigurationClassifications(anyMap())).thenReturn(List.of(new SimulationContextDependencies.Classification(id(2),"INTERNAL"),
            new SimulationContextDependencies.Classification(id(3),"INTERNAL")));
    }
    private DependencyLineage.VerifiedLineage graph(String promptHash){
        var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);graph.addVertex(id(2));graph.addVertex(id(3));
        graph.addEdge(id(2),id(3),new SourceReadProtocol.ParentRow(id(2),id(3),"CONFIGURATION_DEPENDENCY"));
        return new DependencyLineage.VerifiedLineage(id(2),graph,Map.of(id(2),new ArtifactRegistryProtocol.VersionRow(id(2),null,"AGENT_VERSION",HASH),
            id(3),new ArtifactRegistryProtocol.VersionRow(id(3),null,"CAPABILITY_VERSION",promptHash)));
    }
    private SimulationInvocationSources.Context context(){return new SimulationInvocationSources.Context(id(1),null,id(4),"{}",id(5),HASH,HASH,"SIMULATION","[]",id(3),"1.0.0",HASH,id(9),LocalDateTime.of(2026,1,1,0,0),"INTERNAL");}
    private void capture(){
        when(mapper.insertSimulationContextGraph(anyMap())).thenAnswer(invocation->{Map<String,Object> v=invocation.getArgument(0);
            var stored=new SimulationContextDependencies.Stored(id(1),null,id(4),id(2),id(3),(String)v.get("graphJson"),(String)v.get("graphHash"),"1.0.0",(String)v.get("graphSchema"));
            when(mapper.contextGraph(anyMap())).thenReturn(List.of(stored));return 1;});
    }
    @Test void sourceFreeConfigurationGraphUsesRealVersionPermissionsWithoutInventingRights()throws Exception{
        var manifest=helper.current(admission,null,id(4),id(2),id(3));
        assertEquals(2,manifest.required("versions").size());assertEquals(1,manifest.required("relations").size());
        assertFalse(manifest.toString().contains("rights_revision"));
        verify(references).require(eq(admission),isNull(),eq(List.of(new PermissionProtocol.Basis(id(2),HASH))),eq("SIMULATION_EVALUATION"));
        verify(sources,atLeastOnce()).source(id(2));
    }
    @Test void persistedManifestRechecksCurrentGraphAndRejectsChangedHashes()throws Exception{
        capture();helper.persist(admission,context(),id(2));helper.require(admission,context(),id(2));
        when(lineage.inspect(eq(id(2)),any())).thenReturn(graph("b".repeat(64)));
        assertThrows(TenantAccessException.class,()->helper.require(admission,context(),id(2)));
    }
    @Test void unrelatedPromptOrCandidateCannotBorrowContextBinding()throws Exception{
        assertThrows(TenantAccessException.class,()->helper.current(admission,null,id(4),id(2),id(99)));
        capture();helper.persist(admission,context(),id(2));
        assertThrows(TenantAccessException.class,()->helper.require(admission,context(),id(98)));
    }
    @Test void missingUnknownAndEscalatedConfigurationClassificationsFailClosed()throws Exception{
        capture();helper.persist(admission,context(),id(2));
        when(mapper.contextConfigurationClassifications(anyMap())).thenReturn(List.of(new SimulationContextDependencies.Classification(id(2),"INTERNAL"),
            new SimulationContextDependencies.Classification(id(3),"PERSONAL_HEALTH")));
        assertThrows(TenantAccessException.class,()->helper.require(admission,context(),id(2)));
        assertEquals("PERSONAL_HEALTH",helper.current(admission,null,id(4),id(2),id(3)).required("classification").textValue());
        when(mapper.contextConfigurationClassifications(anyMap())).thenReturn(List.of(new SimulationContextDependencies.Classification(id(2),"UNKNOWN")));
        assertThrows(TenantAccessException.class,()->helper.current(admission,null,id(4),id(2),id(3)));
        when(mapper.contextConfigurationClassifications(anyMap())).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->helper.current(admission,null,id(4),id(2),id(3)));
    }
    @Test void revocationIsRecheckedEvenWhenManifestBytesMatch()throws Exception{
        capture();helper.persist(admission,context(),id(2));
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(references)
            .require(eq(admission),isNull(),anyList(),eq("SIMULATION_EVALUATION"));
        assertThrows(TenantAccessException.class,()->helper.require(admission,context(),id(2)));
    }
}
