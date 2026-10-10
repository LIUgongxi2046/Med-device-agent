package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.EnterpriseRoleProtocol;
import com.ruoyi.medical.persistence.InvitationProtocol;
import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EnterpriseRoleMapper {
    List<PermissionProtocol.GrantRow> managers(@Param("userId") String userId);
    List<EnterpriseRoleProtocol.Head> head(@Param("roleId") String roleId);
    List<EnterpriseRoleProtocol.Head> heads(@Param("roles") List<String> roles, @Param("after") String after, @Param("limit") int limit);
    List<EnterpriseRoleProtocol.Publication> publication(@Param("versionId") String versionId);
    int insertVersion(Map<String,Object> values);
    int insertRole(Map<String,Object> values);
    int insertPublication(Map<String,Object> values);
    int insertHead(Map<String,Object> values);
    int advanceHead(Map<String,Object> values);
    int revokeVersion(@Param("versionId") String versionId, @Param("revision") String revision);
    List<InvitationProtocol.Row> pendingInvitations(@Param("versionId") String versionId);
    List<PermissionProtocol.GrantRow> activeRoleGrants(@Param("versionId") String versionId);
    int revokeInvitation(@Param("id") String id, @Param("revision") String revision, @Param("reason") String reason);
    int advanceEpochs(@Param("auth") String auth, @Param("governance") String governance);
    List<SelectionOperationRow> currentOperation(@Param("userId") String userId, @Param("operation") String operation, @Param("key") String key);
    int saveOperation(Map<String,Object> values);
}
