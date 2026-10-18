package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationArtifactRegistryHookTest {
    final ArtifactRegistryMapper mapper=mock(ArtifactRegistryMapper.class);
    final ResourceReadAuthority authority=mock(ResourceReadAuthority.class);
    final DependencyManifestSchema manifests=mock(DependencyManifestSchema.class);
    final DependencyLineage lineage=mock(DependencyLineage.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final SimulationArtifactReadGuard guard=mock(SimulationArtifactReadGuard.class);
    final String tenant=EvaluationFixture.id(1),artifactId=EvaluationFixture.id(2),access=EvaluationFixture.id(3),evaluation=EvaluationFixture.id(4),hash="a".repeat(64);
    final SchemaCatalogue catalogue=SchemaCatalogue.fromClasspath();
    ArtifactRegistryProtocol.ArtifactRow artifact;
    ArtifactRegistryProtocol.BindingRow binding;
    ArtifactRegistryProtocol.ReadRequirement requirement;
    SimulationArtifactRegistryHookTest()throws Exception {
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));
        var ref=new ObjectRef("test/result","v1",hash,"1","application/json");String schema=catalogue.schemaDigest("model-stream");
        artifact=new ArtifactRegistryProtocol.ArtifactRow(tenant,null,artifactId,"MODEL_RESULT",StoredObjectReferences.encode(ref),ref.objectKey(),ref.objectVersion(),hash,BigInteger.ONE,ref.mediaType(),access,hash,"1.0.0",schema,"INTERNAL");
        binding=new ArtifactRegistryProtocol.BindingRow(tenant,null,artifactId,"EVALUATION",evaluation,catalogue.schemaRef("model-stream","ModelResult"),schema,access,hash);
        requirement=new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,null,"EVALUATION",evaluation),"evaluation.read","SIMULATION_EVALUATION",binding.schemaRef(),schema,schema,new DependencyLineage.Limits(512,2048));
        when(mapper.artifact(artifactId)).thenReturn(List.of(artifact));when(mapper.binding(artifactId)).thenReturn(List.of(binding));
        when(mapper.version(artifactId)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(artifactId,null,"ARTIFACT",hash)));
        when(mapper.version(access)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(access,null,"DEPENDENCY_ACCESS_SET",hash)));
        var row=new ArtifactRegistryProtocol.ManifestRow(tenant,null,access,"{}",hash,hash,BigInteger.ZERO,"1.0.0",hash);
        when(mapper.manifest(access)).thenReturn(List.of(row));when(mapper.members(access)).thenReturn(List.of());
        when(manifests.verify(row,List.of())).thenReturn(new ArtifactRegistryProtocol.DependencyManifest("1.0.0",tenant,null,access,List.of()));
        when(lineage.require(eq(artifactId),eq(List.of()),any())).thenReturn(mock(DependencyLineage.VerifiedLineage.class));
    }
    @Test void missingGuardFailsClosedAfterOrdinaryChecks()throws Exception {
        var registry=new ArtifactRegistry(mapper,authority,manifests,lineage);
        assertThrows(TenantAccessException.class,()->registry.requireRegistration(admission,artifactId,requirement));
        verify(authority).require(eq(admission),eq(requirement.scope()),eq("evaluation.read"),eq("SIMULATION_EVALUATION"));
        verify(authority).require(eq(admission),eq(new PermissionProtocol.Scope(tenant,null,"ARTIFACT",artifactId)),eq("artifact.read"),eq("SIMULATION_EVALUATION"));
    }
    @Test void injectedGuardIsResolvedOnlyWhenActualNewArtifactReadOccurs()throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<SimulationArtifactReadGuard> provider=mock(ObjectProvider.class);
        var registry=new ArtifactRegistry(mapper,authority,manifests,lineage,provider);verifyNoInteractions(provider);
        when(provider.getIfAvailable()).thenReturn(guard);
        assertNotNull(registry.requireRegistration(admission,artifactId,requirement));verify(guard).require(admission,artifact,binding,requirement);
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(guard).require(admission,artifact,binding,requirement);
        assertThrows(TenantAccessException.class,()->registry.requireRegistration(admission,artifactId,requirement));
    }
}
