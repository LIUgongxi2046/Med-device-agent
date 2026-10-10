package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.PermissionProtocol;
import com.ruoyi.medical.persistence.SelectionOperationRow;
import com.ruoyi.medical.persistence.TenantMemberRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PermissionMapper {
    List<PermissionProtocol.GrantRow> currentDelegations(@Param("userId") String userId);
    List<PermissionProtocol.GrantRow> resourceGrants(@Param("userId") String userId, @Param("action") String action);
    List<SelectionOperationRow> currentOperation(@Param("userId") String userId, @Param("key") String key);
    List<TenantMemberRow> targetMember(@Param("targetId") String targetId);
    List<PermissionProtocol.BindingRow> targetBinding(@Param("targetId") String targetId);
    List<PermissionProtocol.BindingRow> currentBinding(@Param("userId") String userId);
    List<PermissionProtocol.GrantRow> currentGrant(@Param("grantId") String grantId);
    List<PermissionProtocol.VersionRow> basis(@Param("versionId") String versionId);
    List<PermissionProtocol.OwnerRow> resourceOwner(@Param("kind") String kind, @Param("resourceId") String resourceId);
    List<String> projectStatus(@Param("projectId") String projectId);
    LocalDateTime databaseTime();
    int insertGrant(@Param("grant") PermissionProtocol.GrantRow grant, @Param("userId") String userId, @Param("classification") String classification);
    int revokeGrant(@Param("grantId") String grantId, @Param("targetId") String targetId, @Param("revision") String revision);
    int disableMember(@Param("memberId") String memberId, @Param("targetId") String targetId, @Param("revision") String revision);
    int revokeMemberGrants(@Param("targetId") String targetId);
    int terminateInvitations(@Param("targetId") String targetId, @Param("reason") String reason);
    int advanceAuthEpoch(@Param("epoch") String epoch);
    int saveOperation(@Param("userId") String userId, @Param("operationId") String operationId, @Param("key") String key,
                      @Param("sha256") String sha256, @Param("response") String response, @Param("targetId") String targetId,
                      @Param("manifest") String manifest, @Param("classification") String classification);
    int saveAudit(@Param("userId") String userId, @Param("projectId") String projectId, @Param("auditId") String auditId,
                  @Param("action") String action, @Param("resource") String resource, @Param("requestId") String requestId,
                  @Param("manifest") String manifest, @Param("classification") String classification);
    int saveEvent(@Param("userId") String userId, @Param("eventId") String eventId, @Param("targetId") String targetId,
                  @Param("revision") String revision, @Param("eventType") String eventType, @Param("payload") String payload);
}
