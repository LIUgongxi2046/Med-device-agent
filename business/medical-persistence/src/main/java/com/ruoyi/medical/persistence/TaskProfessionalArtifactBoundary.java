package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Set;

/** Immutable typed-v2 ownership is authoritative even before admission, after relabeling,
 * or if the 099 binding is missing. Historical v1 kinds retain their original behavior. */
public final class TaskProfessionalArtifactBoundary {
    private TaskProfessionalArtifactBoundary(){}
    private static final Set<String> DEDICATED=Set.of(TaskProfessionalRuntimeService.ENVELOPE_REF,
            TaskResearchCompletionAuthority.EXPORT_REF,TaskResearchCompletionAuthority.RESULT_REF,
            TaskInvocationContextSchema.EXACT_INPUT_REF);
    static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.BindingRow binding,List<String> intentOwners) {
        if(intentOwners==null||!intentOwners.isEmpty()||binding.schemaRef()!=null&&DEDICATED.contains(binding.schemaRef()))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
