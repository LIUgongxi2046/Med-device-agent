package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class FormalPublicationArtifactBoundaryTest {
 @Test void releaseKindCannotUseGenericReader(){var a=mock(ArtifactRegistryProtocol.ArtifactRow.class);var b=mock(ArtifactRegistryProtocol.BindingRow.class);when(a.artifactKind()).thenReturn("AGENT_RELEASE_MANIFEST");assertThrows(TenantAccessException.class,()->FormalPublicationArtifactBoundary.requireGenericAllowed(a,b));}
 @Test void renamingKindCannotBypassReleaseSchemaGuard(){var a=mock(ArtifactRegistryProtocol.ArtifactRow.class);var b=mock(ArtifactRegistryProtocol.BindingRow.class);when(a.artifactKind()).thenReturn("OTHER");when(b.schemaRef()).thenReturn("urn:medical:agent-release:1.0.0");assertThrows(TenantAccessException.class,()->FormalPublicationArtifactBoundary.requireGenericAllowed(a,b));}
 @Test void unrelatedProfilesKeepExistingReader(){var a=mock(ArtifactRegistryProtocol.ArtifactRow.class);var b=mock(ArtifactRegistryProtocol.BindingRow.class);when(a.artifactKind()).thenReturn("DOCUMENT_BODY");when(b.schemaRef()).thenReturn("urn:medical:document-body:1.0.0");assertDoesNotThrow(()->FormalPublicationArtifactBoundary.requireGenericAllowed(a,b));}
}
