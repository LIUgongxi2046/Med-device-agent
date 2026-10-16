package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
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

final class SourceProcessingAccess {
    private final JdbcTemplate jdbc;
    private final TenantTransaction transactions;
    private final TenantMemberMapper members;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final SourceReadMapper sources;
    private final SourceRightsSchema rights;
    private final SourceGovernanceMapper governance;
    private final SourceProcessingMapper mapper;
    private final SourceProcessingPolicy policy;
    private final SourceGovernanceSchema json;
    private final SourceProcessingResult results;

    SourceProcessingAccess(DataSource data, TenantTransaction transactions, TenantMemberMapper members,
            PermissionMapper permissions, PermissionAuthority authority, SourceReadMapper sources,
            SourceRightsSchema rights, SourceGovernanceMapper governance, SourceProcessingMapper mapper,
            SourceProcessingPolicy policy, SourceGovernanceSchema json) {
        jdbc=new JdbcTemplate(data); this.transactions=transactions; this.members=members; this.permissions=permissions;
        this.authority=authority; this.sources=sources; this.rights=rights; this.governance=governance;
        this.mapper=mapper; this.policy=policy; this.json=json;
        results=ThrowingSupplier.of(SourceProcessingResult::new).get();
    }

    List<String> pending(String tenant) {
        if(!policy.tenantIds().contains(tenant)) denied(ErrorCode.ACCESS_DENIED);
        return jdbc.query("""
            SELECT a.attempt_id FROM processing_attempt a
            JOIN source_version s ON s.tenant_id=a.tenant_id AND s.source_version=a.source_version
            JOIN rights_revision r ON r.tenant_id=s.tenant_id AND r.rights_revision=s.rights_revision AND r.source_version=s.source_version
            WHERE a.tenant_id=? AND a.job_type='PARSE' AND r.status='APPROVED'
              AND (r.valid_from IS NULL OR r.valid_from<=UTC_TIMESTAMP(6))
              AND (r.valid_to IS NULL OR r.valid_to>UTC_TIMESTAMP(6))
              AND (a.status='QUEUED' OR a.status='RUNNING' AND a.lease_expires_at<=UTC_TIMESTAMP(6))
            ORDER BY a.created_at,a.attempt_id LIMIT 10
            """,(row,n)->row.getString(1),tenant);
    }

    <T> T execute(String tenant, String attempt, Operation<T> operation) {
        WireValues.uuid(tenant); WireValues.uuid(attempt);
        if(!policy.tenantIds().contains(tenant)) denied(ErrorCode.ACCESS_DENIED);
        var identities=jdbc.query("""
            SELECT a.created_by,t.auth_epoch,t.governance_epoch FROM processing_attempt a
            JOIN tenant t ON t.tenant_id=a.tenant_id WHERE a.tenant_id=? AND a.attempt_id=? AND a.job_type='PARSE'
            """,(row,n)->new Identity(row.getString(1),new BigInteger(row.getString(2)),new BigInteger(row.getString(3))),tenant,attempt);
        var identity=single(identities);
        // 发起人员身份来自持久作业；当前账号、成员与逐动作许可在每个短事务重新核查。
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,identity.actor(),BigInteger.ZERO,
                identity.auth(),identity.governance()))) {
            return transactions.execute(watermarks -> ThrowingSupplier.of(() -> {
                var values=new HashMap<String,Object>(); values.put("userId",identity.actor()); values.put("attemptId",attempt);
                var row=single(mapper.current(values));
                var binding=single(permissions.currentBinding(identity.actor()));
                List<String> active;
                if("local".equals(binding.mode())) {
                    if(!identity.actor().equals(binding.localUserId())) denied(ErrorCode.ACCESS_DENIED);
                    active=jdbc.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                        (r,n)->r.getString(1),identity.actor());
                } else if("oidc".equals(binding.mode())) {
                    if(binding.localUserId()!=null) denied(ErrorCode.ACCESS_DENIED);
                    active=jdbc.query("""
                        SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=?
                        AND issuer=? AND subject=? AND status='ACTIVE' FOR UPDATE
                        """,(r,n)->r.getString(1),binding.identityId(),identity.actor(),binding.issuer(),binding.subject());
                } else throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                if(active.size()!=1) denied(ErrorCode.ACCESS_DENIED);
                var memberId=single(jdbc.query("SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=? FOR UPDATE",
                    (r,n)->r.getString(1),tenant,identity.actor()));
                var member=single(members.currentMember(identity.actor(),memberId));
                if(!"ACTIVE".equals(member.status())) denied(ErrorCode.ACCESS_DENIED);
                LocalDateTime now=Objects.requireNonNull(permissions.databaseTime());
                if(!tenant.equals(row.tenantId()) || !identity.actor().equals(row.createdBy())
                        || !"ACTIVE".equals(row.lifecycle()) || !"ACTIVE".equals(single(permissions.projectStatus(row.projectId()))))
                    denied(ErrorCode.OBJECT_UNAVAILABLE);
                var source=single(sources.source(row.sourceVersion()));
                if(!source.originalSha256().equals(source.registeredSha256())
                        || !Objects.equals(source.ownerProjectId(),source.registeredProjectId())
                        || !source.originalSha256().equals(row.originalSha256())
                        || source.ownerProjectId()!=null && !source.ownerProjectId().equals(row.projectId()))
                    denied(ErrorCode.CONTENT_MISMATCH);
                var sourceScope=new PermissionProtocol.Scope(tenant,row.ownerProjectId(),"SOURCE",row.sourceId());
                var rightsRow=single(sources.rights(row.rightsRevision()));
                authority.requireResource(sourceScope,identity.actor(),binding.mode(),"source.read",rightsRow.purpose(),
                    permissions.resourceGrants(identity.actor(),"source.read"),now);
                authority.requireResource(sourceScope,identity.actor(),binding.mode(),"source.write","SOURCE_PROCESSING",
                    permissions.resourceGrants(identity.actor(),"source.write"),now);
                authority.requireResource(new PermissionProtocol.Scope(tenant,row.projectId(),"PROJECT",row.projectId()),
                    identity.actor(),binding.mode(),"project.read","SOURCE_PROCESSING",
                    permissions.resourceGrants(identity.actor(),"project.read"),now);
                String conversationId=null;
                if("SESSION".equals(row.ownership())) {
                    conversationId=single(jdbc.query("""
                        SELECT c.conversation_id FROM source_session_binding b
                        JOIN conversation c ON c.tenant_id=b.tenant_id AND c.project_id=b.project_id AND c.conversation_id=b.session_id
                        WHERE b.tenant_id=? AND b.project_id=? AND b.source_id=? AND c.owner_id=? AND c.status='ACTIVE' FOR UPDATE
                        """,(r,n)->r.getString(1),tenant,row.projectId(),row.sourceId(),identity.actor()));
                }
                rights.require(rightsRow,source,new SourceReadProtocol.ReadRequirement(row.sourceVersion(),row.rightsRevision(),
                    row.projectId(),null,null,conversationId,SourceReadProtocol.Use.REVIEW,rightsRow.purpose(),
                    policy.environment(),policy.region(),null),now);
                boolean mayStore=false;
                for(var action:rights.verify(rightsRow).required("allowed_actions")) if("STORE".equals(action.textValue())) mayStore=true;
                if(!mayStore) denied(ErrorCode.ACCESS_DENIED);
                values.put("configRevision",row.configRevision());
                var config=single(governance.processingConfig(values)); var configured=policy.binding(row.configRevision());
                if(!"CAPABILITY_VERSION".equals(config.entityKind()) || !"CAPABILITY".equals(config.configurationKind())
                        || !"TOOL".equals(config.capabilityKind()) || !"PUBLISHED".equals(config.status())
                        || !configured.configSha256().equals(config.registeredSha256())
                        || !configured.configSha256().equals(config.contentSha256())
                        || config.projectId()!=null && !config.projectId().equals(row.projectId())) denied(ErrorCode.OBJECT_UNAVAILABLE);
                results.requireCapability(config,configured,policy);
                authority.requireResource(new PermissionProtocol.Scope(tenant,config.projectId(),"CONFIGURATION",config.configId()),
                    identity.actor(),binding.mode(),"configuration.read","SOURCE_PROCESSING",
                    permissions.resourceGrants(identity.actor(),"configuration.read"),now);
                var request=json.read(row.requestManifest());
                if(!"1.0.0".equals(request.required("schema_version").textValue())
                        || !row.sourceVersion().equals(request.required("source_version").textValue())
                        || !row.projectId().equals(request.required("project_id").textValue())
                        || !row.configRevision().equals(request.required("config_revision").textValue())
                        || !List.of("PARSE_ONLY","PARSE_AND_INDEX").contains(request.required("stage").textValue()))
                    denied(ErrorCode.CONTENT_MISMATCH);
                if("PARSE_AND_INDEX".equals(request.required("stage").textValue())) {
                    if(configured.indexConfigRevision()==null) denied(ErrorCode.OBJECT_UNAVAILABLE);
                    var indexValues=new HashMap<String,Object>(); indexValues.put("userId",identity.actor());
                    indexValues.put("configRevision",configured.indexConfigRevision());
                    var index=single(governance.processingConfig(indexValues));
                    if(!"PUBLISHED".equals(index.status()) || !"CAPABILITY_VERSION".equals(index.entityKind())
                            || !"TOOL".equals(index.capabilityKind()) || !index.contentSha256().equals(index.registeredSha256())
                            || index.projectId()!=null && !row.projectId().equals(index.projectId())) denied(ErrorCode.OBJECT_UNAVAILABLE);
                    authority.requireResource(new PermissionProtocol.Scope(tenant,index.projectId(),"CONFIGURATION",index.configId()),
                        identity.actor(),binding.mode(),"configuration.read","SOURCE_PROCESSING",
                        permissions.resourceGrants(identity.actor(),"configuration.read"),now);
                }
                if(row.sizeBytes().signum()<=0 || row.sizeBytes().compareTo(BigInteger.valueOf(policy.maximumSourceBytes()))>0)
                    denied(ErrorCode.FILE_TOO_LARGE);
                values.put("projectId",row.projectId()); values.put("sourceVersion",row.sourceVersion()); values.put("parseVersion",row.parseVersion());
                values.put("workerId",row.leaseOwner()); values.put("generation",row.leaseGeneration());
                values.put("expectedRevision",row.revision()); values.put("now",now);
                values.put("governanceEpoch",watermarks.governanceEpoch()); values.put("classification",row.classification());
                values.put("ownerProjectId",row.ownerProjectId());
                return operation.apply(new Admission(row,now,configured,values));
            }).get());
        }
    }

    record Admission(SourceProcessingRow row, LocalDateTime now, SourceProcessingPolicy.Binding binding, Map<String,Object> values) { }
    private record Identity(String actor, BigInteger auth, BigInteger governance) { }
    @FunctionalInterface interface Operation<T> { T apply(Admission admission) throws Exception; }
    private static <T> T single(List<T> rows) {
        if(rows.size()!=1) denied(ErrorCode.ACCESS_DENIED); return rows.get(0);
    }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
