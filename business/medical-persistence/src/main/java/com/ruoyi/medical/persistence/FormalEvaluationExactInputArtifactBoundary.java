package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;

/** Generic reads cannot discharge the original088 context and current model-send authority.
 * No historical or execution read is enabled by a stored ArtifactRef or a kind rename. */
public final class FormalEvaluationExactInputArtifactBoundary {
    private FormalEvaluationExactInputArtifactBoundary() {
    }

    public static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact, ArtifactRegistryProtocol.BindingRow binding,
            java.util.List<String> owners) {
        if (FormalEvaluationExactInputObjectSchema.KIND.equals(artifact.artifactKind()) || owners == null
                || !owners.isEmpty()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
