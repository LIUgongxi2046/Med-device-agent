package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
/** A release manifest is governed by complete publication evidence. Generic content access cannot discharge that authority. */
public final class FormalPublicationArtifactBoundary {
 private FormalPublicationArtifactBoundary(){}
 public static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding){
  if(FormalAgentPublicationRegistration.ARTIFACT_KIND.equals(artifact.artifactKind())||"urn:medical:agent-release:1.0.0".equals(binding.schemaRef()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
 }
}
