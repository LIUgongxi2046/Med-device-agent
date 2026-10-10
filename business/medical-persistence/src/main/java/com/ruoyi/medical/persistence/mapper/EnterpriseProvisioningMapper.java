package com.ruoyi.medical.persistence.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.medical.persistence.PermissionProtocol;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EnterpriseProvisioningMapper {
    int insertTenant(Map<String,Object> values);
    int insertBinding(Map<String,Object> values);
    int insertMember(Map<String,Object> values);
    int insertBasis(Map<String,Object> values);
    int insertGrant(Map<String,Object> values);
    int saveEvent(Map<String,Object> values);
    List<Map<String,Object>> state(Map<String,Object> values);
    List<PermissionProtocol.GrantRow> grants(Map<String,Object> values);
    List<Map<String,Object>> lockTenant(Map<String,Object> values);
}
