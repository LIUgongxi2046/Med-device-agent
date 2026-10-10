package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.DependencyAccessProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DependencyAccessMapper {
    List<DependencyAccessProtocol.HeaderRow> header(@Param("id") String id);
}
