package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
/** Exact model context is dedicated-only even if someone relabels its artifact metadata. */
public final class FormalEvaluationInvocationArtifactBoundary {
 private FormalEvaluationInvocationArtifactBoundary(){}
 static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding,List<String> owners){
  if(artifact==null||binding==null||owners==null||!owners.isEmpty()||"FORMAL_EVALUATION_CALL_EXACT_INPUT".equals(artifact.artifactKind())||FormalEvaluationInvocationSchema.EXACT_INPUT_REF.equals(binding.schemaRef()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
 }
}
