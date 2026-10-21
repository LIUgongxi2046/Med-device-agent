package com.ruoyi.medical.persistence;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskPlanDraftArtifactBoundaryTest {
    private final ArtifactRegistryProtocol.ArtifactRow artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);
    private final ArtifactRegistryProtocol.BindingRow binding=mock(ArtifactRegistryProtocol.BindingRow.class);
    @Test void malformedIdentityFailsClosed(){assertThrows(TenantAccessException.class,()->TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,List.of(),List.of()));}
    @Test void dedicatedInputKindCannotUseGenericRead(){when(artifact.artifactKind()).thenReturn("PLAN_DRAFT_INPUT");assertThrows(TenantAccessException.class,()->TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,List.of(),List.of()));}
    @Test void originalOwnerPreventsRelabellingBypass(){when(artifact.artifactKind()).thenReturn("PLAN_CANDIDATE");assertThrows(TenantAccessException.class,()->TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,List.of("original-write"),List.of()));}
    @Test void exactInputSchemaDeniesWithoutBindingOwner(){when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn("urn:medical:task-plan-draft-invocation:1.0.0#/$defs/ExactInput");assertThrows(TenantAccessException.class,()->TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,List.of(),List.of()));}
    @Test void publicCandidateStillNeedsOtherNormalAuthorities(){when(artifact.artifactKind()).thenReturn("PLAN_CANDIDATE");when(binding.schemaRef()).thenReturn("urn:medical:artifacts:1.0.0#/$defs/PlanCandidate");assertDoesNotThrow(()->TaskPlanDraftArtifactBoundary.requireGenericAllowed(artifact,binding,List.of(),List.of()));}
}
