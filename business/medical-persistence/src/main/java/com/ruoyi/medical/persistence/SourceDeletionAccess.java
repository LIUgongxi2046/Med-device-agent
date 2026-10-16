package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceDeletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

final class SourceDeletionAccess {
    private final JdbcTemplate jdbc;
    private final TenantTransaction transactions;
    private final TenantMemberMapper members;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final SourceGovernanceMapper governance;
    private final SourceDeletionMapper mapper;
    private final SourceDeletionPolicy policy;

    SourceDeletionAccess(DataSource data, TenantTransaction transactions, TenantMemberMapper members,
            PermissionMapper permissions, PermissionAuthority authority, SourceGovernanceMapper governance,
            SourceDeletionMapper mapper, SourceDeletionPolicy policy) {
        jdbc=new JdbcTemplate(data); this.transactions=transactions; this.members=members;
        this.permissions=permissions; this.authority=authority; this.governance=governance;
        this.mapper=mapper; this.policy=policy;
    }

    <T> T execute(String tenant,String itemId,Operation<T> operation) {
        if(!policy.tenantIds().contains(tenant)) denied(ErrorCode.ACCESS_DENIED);
        // 调度查询只接受服务端批准企业；业务 SQL 继续使用原企业拦截器。
        var rows=jdbc.query("""
            SELECT j.created_by,t.auth_epoch,t.governance_epoch FROM deletion_item i
            JOIN deletion_job j ON j.tenant_id=i.tenant_id AND j.deletion_id=i.deletion_id
            JOIN tenant t ON t.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.item_id=?
            """,(r,n)->new Identity(r.getString(1),new BigInteger(r.getString(2)),new BigInteger(r.getString(3))),tenant,itemId);
        if(rows.size()!=1) denied(ErrorCode.OBJECT_UNAVAILABLE);
        var actor=rows.get(0);
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor.user(),BigInteger.ZERO,actor.auth(),actor.governance()))) {
            return transactions.execute(watermarks -> ThrowingSupplier.of(() -> {
                var values=new HashMap<String,Object>(); values.put("userId",actor.user()); values.put("itemId",itemId);
                var item=single(governance.deletionItemForUpdate(values));
                if(!actor.user().equals(mapper.actor(values))) denied(ErrorCode.SCOPE_STALE);
                var bindings=permissions.currentBinding(actor.user());
                if(bindings.size()!=1) denied(ErrorCode.ACCESS_DENIED);
                var binding=bindings.get(0);
                List<String> active;
                if("local".equals(binding.mode())) {
                    active=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                        (r,n)->r.getString(1),actor.user());
                    if(!actor.user().equals(binding.localUserId())) denied(ErrorCode.ACCESS_DENIED);
                } else if("oidc".equals(binding.mode())) {
                    active=jdbc.query("""
                        SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=?
                        AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE
                        """,(r,n)->r.getString(1),binding.identityId(),actor.user(),binding.issuer(),binding.subject());
                    if(binding.localUserId()!=null) denied(ErrorCode.ACCESS_DENIED);
                } else throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                if(active.size()!=1) denied(ErrorCode.ACCESS_DENIED);
                var memberIds=jdbc.query("SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",
                    (r,n)->r.getString(1),tenant,actor.user());
                if(memberIds.size()!=1) denied(ErrorCode.ACCESS_DENIED);
                var member=members.currentMember(actor.user(),memberIds.get(0));
                if(member.size()!=1 || !"ACTIVE".equals(member.get(0).status())) denied(ErrorCode.ACCESS_DENIED);
                LocalDateTime now=Objects.requireNonNull(permissions.databaseTime());
                var resource=new PermissionProtocol.Scope(tenant,item.ownerProjectId(),"SOURCE",item.sourceId());
                authority.requireResource(resource,actor.user(),binding.mode(),"source.purge","SOURCE_PURGE",
                    permissions.resourceGrants(actor.user(),"source.purge"),now);
                if(!"PURGE_PENDING".equals(item.lifecycleStatus()) || !List.of("QUEUED","RUNNING").contains(item.jobStatus())
                        || !List.of("QUEUED","RUNNING").contains(item.itemStatus())) denied(ErrorCode.ACTION_STALE);
                values.put("sourceVersion",item.sourceVersion()); values.put("deletionId",item.deletionId());
                var job=single(governance.deletionJob(values)); values.put("policyRevision",job.policyRevision());
                var retention=single(governance.retentionPolicy(values)); var state=single(governance.lifecycleStateForUpdate(values));
                if(!"SOURCE".equals(retention.category()) || !"SOURCE_CREATED".equals(retention.startEvent())
                        || !"APPROVED".equals(retention.status()) || retention.effectiveFrom().isAfter(now)
                        || !"VERIFIED_DELETE".equals(retention.purgeMethod()) || retention.retentionDuration().signum()<=0
                        || retention.backupExpiry().signum()<=0 || item.backupExpiry()==null)
                    denied(ErrorCode.RETENTION_UNCONFIGURED);
                if(state.sourceCreatedAt().plusSeconds(retention.retentionDuration().longValueExact()).isAfter(now))
                    denied(ErrorCode.ACTION_STALE);
                if(governance.blockingReferenceCount(values).signum()!=0 || mapper.additionalReferenceCount(values).signum()!=0)
                    denied(ErrorCode.REFERENCE_HELD);
                values.put("now",now); values.put("leaseSeconds",policy.lease().toSeconds());
                values.put("governanceEpoch",watermarks.governanceEpoch());
                values.put("expectedRevision",item.itemRevision());
                return operation.apply(new Admission(item,actor.user(),now,values));
            }).get());
        }
    }

    void requireExclusiveObject(String tenant,SourceDeletionTarget target,String key) {
        if(!key.startsWith("tenants/"+tenant+"/")) denied(ErrorCode.ACCESS_DENIED);
        long references=Objects.requireNonNull(jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM source_version WHERE object_key=? AND NOT (tenant_id=? AND source_version=?))
              +(SELECT COUNT(*) FROM parse_version WHERE artifact_key=? AND NOT
                (tenant_id=? AND source_version=? AND parse_version<=>?))
              +(SELECT COUNT(*) FROM artifact WHERE object_key=?)
              +(SELECT COUNT(*) FROM source_upload_result WHERE JSON_UNQUOTE(JSON_EXTRACT(original_object_ref,'$.object_key'))=?
                AND NOT (tenant_id=? AND source_version=?))
            """,Long.class,key,tenant,target.sourceVersion(),key,tenant,target.sourceVersion(),target.parseVersion(),key,key,tenant,target.sourceVersion()));
        if(references!=0) denied(ErrorCode.REFERENCE_HELD);
    }

    record Admission(SourceGovernanceProtocol.DeletionItemRow item,String actor,LocalDateTime now,Map<String,Object> values) { }
    private record Identity(String user,BigInteger auth,BigInteger governance) { }
    @FunctionalInterface interface Operation<T> { T apply(Admission admission) throws Exception; }
    private static <T> T single(List<T> values) {
        if(values.size()!=1) denied(ErrorCode.CONTENT_MISMATCH); return values.get(0);
    }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
