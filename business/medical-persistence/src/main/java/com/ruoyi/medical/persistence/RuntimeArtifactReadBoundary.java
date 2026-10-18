package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
/** Mandatory generic-reader gate until it can consume both runtime current proofs. */
public final class RuntimeArtifactReadBoundary {
 private RuntimeArtifactReadBoundary(){}
 static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding){
  if("REPLAY_MANIFEST".equals(artifact.artifactKind())||RuntimeReplaySchema.REF.equals(binding.schemaRef()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
 }
}
