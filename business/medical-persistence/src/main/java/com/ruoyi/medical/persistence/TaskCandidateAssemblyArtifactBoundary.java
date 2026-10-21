package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Set;
/** Generic artifact reads cannot turn an N06 private context or draft body into
 * adopted document authority. Intent ownership survives labels and partial writes. */
public final class TaskCandidateAssemblyArtifactBoundary {
    private TaskCandidateAssemblyArtifactBoundary(){}
    static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,ArtifactRegistryProtocol.BindingRow binding,List<String> owners){
        if(artifact==null||artifact.artifactKind()==null||binding==null||owners==null||!owners.isEmpty()||TaskCandidateAssemblyObjectLedger.REF.equals(binding.schemaRef())||Set.of("ASSEMBLY_COMPANION","CROSS_FILE_CHECK_REPORT").contains(artifact.artifactKind()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}
