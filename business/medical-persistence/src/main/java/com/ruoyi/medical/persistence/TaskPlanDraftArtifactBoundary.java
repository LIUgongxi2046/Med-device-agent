package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Set;

/** N01 control/exact inputs are dedicated-only, including before registration or
 * after a corrupted/relabelled artifact row. A public candidate is a different kind. */
public final class TaskPlanDraftArtifactBoundary {
    private TaskPlanDraftArtifactBoundary() { }
    private static final Set<String> KINDS=Set.of("PLAN_DRAFT_INPUT","PLAN_DRAFT_EXACT_INPUT");
    private static final Set<String> SCHEMAS=Set.of("urn:medical:task-plan-draft:1.0.0#/$defs/InputManifest",
            "urn:medical:task-plan-draft-invocation:1.0.0#/$defs/ExactInput");
    static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.BindingRow binding,List<String> inputOwners,List<String> exactOwners) {
        if(artifact==null||binding==null||artifact.artifactKind()==null||inputOwners==null||exactOwners==null||!inputOwners.isEmpty()||!exactOwners.isEmpty()
                ||KINDS.contains(artifact.artifactKind())||binding.schemaRef()!=null&&SCHEMAS.contains(binding.schemaRef()))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
