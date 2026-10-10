package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.MemberGovernanceReadProtocol;
import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.TenantMemberRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemberGovernanceReadMapper {
    List<TenantMemberRow> members(@Param("userId") String actor, @Param("targetId") String target, @Param("capacity") int capacity);
    List<PermissionProtocol.GrantRow> grants(@Param("userId") String actor, @Param("targetId") String target, @Param("capacity") int capacity);
    List<MemberGovernanceReadProtocol.Invitation> invitations(@Param("userId") String actor, @Param("targetId") String target, @Param("capacity") int capacity);
}
