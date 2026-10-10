package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.TenantMemberRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantMemberMapper {
    List<TenantMemberRow> currentMember(@Param("userId") String userId, @Param("memberId") String memberId);
    List<BusinessSessionProtocol.Action> currentActions(@Param("userId") String userId);
}
