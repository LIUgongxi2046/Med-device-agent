package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ArtifactRegistryProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArtifactRegistryMapper {
    java.util.List<String> evaluationRuntimeObjectOwners(String artifactId);
    List<ArtifactRegistryProtocol.ArtifactRow> artifact(@Param("id") String id);
    List<ArtifactRegistryProtocol.BindingRow> binding(@Param("id") String id);
    List<ArtifactRegistryProtocol.ManifestRow> manifest(@Param("id") String id);
    List<ArtifactRegistryProtocol.MemberRow> members(@Param("id") String id);
    List<String> planDraftInputOwners(@Param("id") String id);
    List<String> planDraftExactInputOwners(@Param("id") String id);
    List<String> sectionWriterInputOwners(@Param("id") String id);
    List<String> sectionWriterExactInputOwners(@Param("id") String id);
    List<String> candidateAssemblyOwners(@Param("id") String id);
    List<String> sourceVerifierInputOwners(@Param("id") String id);
    List<String> evaluationCallExactInputOwners(@Param("id") String id);
    List<String> documentReviewExactInputOwners(@Param("id") String id);
    List<String> sourceVerifierExactInputOwners(@Param("id") String id);
    List<String> runtimeProfessionalOwners(@Param("id") String id);
    List<String> taskNodeInputOwners(@Param("id") String id);
    List<String> taskFieldTargetOwners(@Param("id") String id);
    List<String> evaluationNodeInputOwners(@Param("id") String id);
    List<String> evaluationPlanPreparationOwners(@Param("id") String id);
    List<String> evaluationExactInputOwners(@Param("id") String id);
    List<ArtifactRegistryProtocol.VersionRow> version(@Param("id") String id);
}
