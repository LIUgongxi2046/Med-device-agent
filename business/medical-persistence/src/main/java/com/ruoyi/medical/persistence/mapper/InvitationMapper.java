package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.InvitationProtocol;
import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvitationMapper {
    List<com.ruoyi.medical.persistence.EnterpriseRoleProtocol.Head> roleHead(@Param("roleId") String roleId);
    int insertOrigin(Map<String,Object> values);
    List<com.ruoyi.medical.persistence.EnterpriseRoleProtocol.GrantOrigin> origins(@Param("invitationId") String invitationId);
    List<InvitationProtocol.RoleRow> role(@Param("versionId") String versionId);
    List<InvitationProtocol.Row> currentInvitation(@Param("invitationId") String invitationId);
    List<InvitationProtocol.Row> pending(@Param("targetId") String targetId);
    List<PermissionProtocol.GrantRow> inviterDelegations(@Param("inviterId") String inviterId);
    List<SelectionOperationRow> currentOperation(@Param("userId") String userId, @Param("operation") String operation, @Param("key") String key);
    int insertBinding(Map<String,Object> values);
    int insertMember(Map<String,Object> values);
    int insertInvitation(Map<String,Object> values);
    int acceptInvitation(@Param("id") String id, @Param("targetId") String targetId, @Param("revision") String revision);
    int activateMember(@Param("id") String id, @Param("targetId") String targetId, @Param("revision") String revision);
    int expire(@Param("id") String id, @Param("revision") String revision);
    int saveOperation(Map<String,Object> values);
    int saveAudit(Map<String,Object> values);
    int saveEvent(Map<String,Object> values);
}
