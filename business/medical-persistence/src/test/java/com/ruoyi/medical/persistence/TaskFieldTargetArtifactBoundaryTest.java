package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskFieldTargetArtifactBoundaryTest {
 @Test void originalIntentOwnerBlocksRenamedTargetAndMissingOwnerQueryFailsClosed(){
  var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
  when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn("urn:other");
  assertThrows(TenantAccessException.class,()->TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of(TaskFieldTargetSchemaTest.id(1))));
  assertThrows(TenantAccessException.class,()->TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,null));
  assertDoesNotThrow(()->TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
 }
 @Test void eitherKindOrSchemaDeniesEvenWithNoClaimedOwner(){
  var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
  when(artifact.artifactKind()).thenReturn(TaskFieldTargetProtocol.ARTIFACT_KIND);
  assertThrows(TenantAccessException.class,()->TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
  when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn(TaskFieldTargetProtocol.SCHEMA_REF);
  assertThrows(TenantAccessException.class,()->TaskFieldTargetArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
 }
}
