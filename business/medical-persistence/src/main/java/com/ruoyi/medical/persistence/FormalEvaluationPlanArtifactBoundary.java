package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;

/** Generic reads cannot discharge original083/088/090 lineage and dedicated current read authority.
 * Metadata-only status does not grant object content access; a kind rename cannot bypass immutable ownership. */
public final class FormalEvaluationPlanArtifactBoundary {
    private FormalEvaluationPlanArtifactBoundary() {
    }

    public static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact, ArtifactRegistryProtocol.BindingRow binding,
            java.util.List<String> owners) {
        if (FormalEvaluationPlanPreparationSchema.KIND.equals(artifact.artifactKind()) || owners == null
                || !owners.isEmpty()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
