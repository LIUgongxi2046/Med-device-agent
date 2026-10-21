package com.ruoyi.medical.persistence;
/** Physical input registration grants no generic read authority, even after relabeling. */
public final class TaskNodeInputArtifactBoundary {
 private TaskNodeInputArtifactBoundary() { }
 static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact, ArtifactRegistryProtocol.BindingRow binding, java.util.List<String> owners) {
  if (TaskNodeInputRegistration.KIND.equals(artifact.artifactKind()) || TaskNodeInputSchema.REF.equals(binding.schemaRef())
      || owners == null || !owners.isEmpty()) throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
 }
}
