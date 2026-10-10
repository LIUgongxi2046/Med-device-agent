package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ArtifactRegistryProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArtifactRegistryMapper {
    List<ArtifactRegistryProtocol.ArtifactRow> artifact(@Param("id") String id);
    List<ArtifactRegistryProtocol.BindingRow> binding(@Param("id") String id);
    List<ArtifactRegistryProtocol.ManifestRow> manifest(@Param("id") String id);
    List<ArtifactRegistryProtocol.MemberRow> members(@Param("id") String id);
    List<ArtifactRegistryProtocol.VersionRow> version(@Param("id") String id);
}
