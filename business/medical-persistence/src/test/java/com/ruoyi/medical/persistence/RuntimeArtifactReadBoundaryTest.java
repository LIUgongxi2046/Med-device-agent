package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class RuntimeArtifactReadBoundaryTest {
 @Test void replayKindCannotHideBehindOtherSchema(){var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);when(artifact.artifactKind()).thenReturn("REPLAY_MANIFEST");when(binding.schemaRef()).thenReturn("other");assertThrows(TenantAccessException.class,()->RuntimeArtifactReadBoundary.requireGenericAllowed(artifact,binding));}
 @Test void replaySchemaCannotHideBehindOtherKind(){var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn(RuntimeReplaySchema.REF);assertThrows(TenantAccessException.class,()->RuntimeArtifactReadBoundary.requireGenericAllowed(artifact,binding));}
 @Test void ordinaryArtifactContinuesToOriginalGuards(){var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn("other");assertDoesNotThrow(()->RuntimeArtifactReadBoundary.requireGenericAllowed(artifact,binding));}
}
