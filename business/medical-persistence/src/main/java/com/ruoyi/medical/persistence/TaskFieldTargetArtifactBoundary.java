package com.ruoyi.medical.persistence;
/** Exact target input authority cannot be replaced by generic artifact permission or a kind rename. */
public final class TaskFieldTargetArtifactBoundary {
 private TaskFieldTargetArtifactBoundary(){}
 static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding,java.util.List<String> owners){
  if(TaskFieldTargetProtocol.ARTIFACT_KIND.equals(artifact.artifactKind())||TaskFieldTargetProtocol.SCHEMA_REF.equals(binding.schemaRef())
    ||owners==null||!owners.isEmpty())throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
 }
}
