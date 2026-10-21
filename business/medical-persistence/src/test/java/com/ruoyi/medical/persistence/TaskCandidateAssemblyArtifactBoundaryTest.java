package com.ruoyi.medical.persistence;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Authored for deferred execution with the remaining A8 integration tests. */
class TaskCandidateAssemblyArtifactBoundaryTest {
    @Test void ownershipSurvivesRelabelAndPartialRegistration(){
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
        when(artifact.artifactKind()).thenReturn("RELABELLED");when(binding.schemaRef()).thenReturn("urn:ordinary");
        assertThrows(TenantAccessException.class,()->TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,List.of("immutable-intent")));
        assertThrows(TenantAccessException.class,()->TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,null));
        when(binding.schemaRef()).thenReturn(TaskCandidateAssemblyObjectLedger.REF);
        assertThrows(TenantAccessException.class,()->TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,List.of()));
    }
    @Test void restrictedAssemblyContextCannotUseGenericArtifactPath(){
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);when(binding.schemaRef()).thenReturn("urn:ordinary");
        for(String kind:List.of("ASSEMBLY_COMPANION","CROSS_FILE_CHECK_REPORT")){when(artifact.artifactKind()).thenReturn(kind);assertThrows(TenantAccessException.class,()->TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,List.of()));}
        when(artifact.artifactKind()).thenReturn("DOCUMENT_BODY");assertDoesNotThrow(()->TaskCandidateAssemblyArtifactBoundary.requireGenericAllowed(artifact,binding,List.of()));
    }
    @Test void proofsHaveNoCallerConstructorsOrCallerObjectReferences(){
        for(Class<?> type:List.of(TaskCandidateAssemblyProducerAuthority.Verified.class,TaskCandidateAssemblyObjectLedger.Attempt.class,TaskCandidateAssemblyObjectLedger.Registered.class))for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        assertTrue(RuntimeObjectAttempt.class.isSealed());assertTrue(List.of(RuntimeObjectAttempt.class.getPermittedSubclasses()).contains(TaskCandidateAssemblyObjectLedger.Attempt.class));
        assertEquals(List.of("ASSEMBLY_MANIFEST","DOCUMENT_BODY","ASSEMBLY_COMPANION","CROSS_FILE_CHECK_REPORT"),TaskCandidateAssemblyObjectLedger.KINDS);
    }
}
