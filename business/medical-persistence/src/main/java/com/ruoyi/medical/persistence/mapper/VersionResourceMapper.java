package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.VersionResourceProtocol.OwnerRow;
import com.ruoyi.medical.persistence.VersionResourceProtocol.DomainOwnerRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VersionResourceMapper {
    List<OwnerRow> owners(@Param("id") String id, @Param("domain") String domain);
    List<DomainOwnerRow> ownersAcrossDomains(@Param("ids") List<String> ids, @Param("domains") List<String> domains);
}
