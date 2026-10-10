package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.TenantWatermarks;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantWatermarkMapper {
    TenantWatermarks lockCurrentTenant();
}
