package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.AgentReleaseMapper;
import com.ruoyi.medical.persistence.mapper.InvitationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class AgentReleaseScopeAuthority {
    private final AgentReleaseMapper mapper;
    private final AgentReleaseStore releases;
    private final PermissionMapper permissions;
    private final InvitationMapper invitations;
    private final ResourceReadAuthority resources;
    private final PermissionAuthority authority;
    private final PermissionSchema json;
    private final InvitationSchema invitationSchema;
    private final AgentReleaseManifestSchema schema;

    public AgentReleaseScopeAuthority(AgentReleaseMapper mapper, AgentReleaseStore releases, PermissionMapper permissions,
            InvitationMapper invitations, ResourceReadAuthority resources, PermissionAuthority authority,
            PermissionSchema json, InvitationSchema invitationSchema) throws IOException, NoSuchAlgorithmException {
        this.mapper = mapper; this.releases = releases; this.permissions = permissions; this.invitations = invitations;
        this.resources = resources; this.authority = authority; this.json = json; this.invitationSchema = invitationSchema;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/agent-release.schema.json"))) {
            schema = new AgentReleaseManifestSchema(input.readAllBytes(), SchemaCatalogue.fromClasspath());
        }
    }

    record RoleProof(AgentReleaseStoredProtocol.RoleOrigin origin, InvitationProtocol.RoleRow role,
            InvitationProtocol.Row invitation, PermissionProtocol.GrantRow grant) { }

    static final class VerifiedScope {
        private final AgentReleaseStore.Stored release;
        private final PermissionProtocol.Scope target;
        private final String action;
        private final String classification;
        private final DependencyLineage.Limits limits;
        private final List<AgentReleaseStoredProtocol.ScopeRow> scopes;
        private final List<RoleProof> roles;
        private final PermissionProtocol.GrantRow grant;
        private final FormalRunControlAccess.StartRun start;
        private final TaskPlanDraftControlAccess.Proof planDraft;

        private VerifiedScope(AgentReleaseStore.Stored release, PermissionProtocol.Scope target, String action,
                String classification, DependencyLineage.Limits limits, List<AgentReleaseStoredProtocol.ScopeRow> scopes,
                List<RoleProof> roles, PermissionProtocol.GrantRow grant,FormalRunControlAccess.StartRun start,TaskPlanDraftControlAccess.Proof planDraft) {
            this.release = release; this.target = target; this.action = action; this.classification = classification;
            this.limits = limits; this.scopes = List.copyOf(scopes); this.roles = List.copyOf(roles); this.grant = grant;this.start=start;this.planDraft=planDraft;
        }
        AgentReleaseStore.Stored release() { return release; }
        PermissionProtocol.Scope target() { return target; }
        String action() { return action; }
        String classification() { return classification; }
        List<AgentReleaseStoredProtocol.ScopeRow> scopes() { return scopes; }
        List<RoleProof> roles() { return roles; }
        PermissionProtocol.GrantRow grant() { return grant; }
    }

    VerifiedScope require(AgentReleaseStore.Stored release, PermissionProtocol.Scope target, String action,
            String classification, DependencyLineage.Limits limits) throws IOException {
        return require(release,target,action,classification,limits,null);
    }
    VerifiedScope requireStartRun(AgentReleaseStore.Stored release,PermissionProtocol.Scope target,String classification,
            DependencyLineage.Limits limits,FormalRunControlAccess.StartRun start)throws IOException {
        Objects.requireNonNull(start).require(release.admission(),target,release.purpose());
        start.requireRelease(release.admission(),release.release().releaseId());
        return require(release,target,"task.run",classification,limits,start);
    }
    VerifiedScope requirePlanDraft(AgentReleaseStore.Stored release,PermissionProtocol.Scope target,String classification,
            DependencyLineage.Limits limits,TaskPlanDraftControlAccess.Proof proof)throws IOException {
        Objects.requireNonNull(proof).requireActive();
        if(!release.admission().planDraftWorker()||release.admission().planDraftProof()!=proof)denied();
        proof.requireRelease(release.release().releaseId());proof.requireTask(target.resource_id());
        return require(release,target,"task.run",classification,limits,null,proof);
    }
    private VerifiedScope require(AgentReleaseStore.Stored release,PermissionProtocol.Scope target,String action,String classification,
            DependencyLineage.Limits limits,FormalRunControlAccess.StartRun start)throws IOException {
        return require(release,target,action,classification,limits,start,null);
    }
    private VerifiedScope require(AgentReleaseStore.Stored release,PermissionProtocol.Scope target,String action,String classification,
            DependencyLineage.Limits limits,FormalRunControlAccess.StartRun start,TaskPlanDraftControlAccess.Proof planDraft)throws IOException {
        Objects.requireNonNull(release); Objects.requireNonNull(limits); json.scope(json.tree(target));
        ProjectSchema.text(classification, 24);
        if (!Set.of("task.create", "plan.confirm", "task.run").contains(action))
            throw new IllegalArgumentException("发布范围动作无效");
        var admission = release.admission(); String tenant = admission.context().tenant_id();
        String actor = admission.actorId(), project = target.project_id();
        if (!tenant.equals(target.tenant_id()) || project == null || target.resource_id() == null
                || release.agent().projectId() != null && !project.equals(release.agent().projectId())) denied();
        if ("task.create".equals(action) ? !"PROJECT".equals(target.resource_kind()) || !project.equals(target.resource_id())
                : !"TASK".equals(target.resource_kind())) mismatch();
        releases.requireUnchanged(release);
        if (!"ACTIVE".equals(single(permissions.projectStatus(project)))) denied();
        var grant = (planDraft!=null?planDraft.requireOriginalAction(target,action,release.purpose()):start==null?resources.require(admission, target, action, release.purpose()):resources.requireStartRun(admission,target,start));
        var approved = release.content().manifest().releaseScope();
        if (!tenant.equals(approved.tenantId()) || !approved.allowedClassifications().contains(classification)
                || "PROJECTS".equals(approved.projectMode()) && !approved.projectIds().contains(project)) denied();
        var values = new HashMap<String,Object>(); values.put("userId", actor); values.put("id", release.release().releaseId());
        values.put("projectId", project); values.put("action", action); values.put("limit", Math.addExact(limits.maximumVersions(), 1));
        var scopes = bounded(mapper.scopes(values), limits.maximumVersions()); if (scopes.isEmpty()) denied();
        for (var row : scopes) {
            WireValues.uuid(row.scopeRevision()); WireValues.uuid(row.createdBy()); WireValues.revision(row.revision().toString());
            WireValues.hash(row.contentSha256()); Objects.requireNonNull(row.createdAt()); Objects.requireNonNull(row.updatedAt());
            ProjectSchema.text(row.classification(), 24);
            if (!tenant.equals(row.tenantId()) || !release.release().releaseId().equals(row.releaseId()) || !"ACTIVE".equals(row.status())
                    || !"1.0.0".equals(row.schemaVersion()) || !releases.manifestSha256().equals(row.schemaManifest())
                    || !Objects.equals(row.projectId(), row.registeredProjectId()) || !"RELEASE_SCOPE".equals(row.registeredKind())
                    || !row.contentSha256().equals(row.registeredSha256()) || row.revision().signum() <= 0
                    || row.updatedAt().isBefore(row.createdAt()) || row.createdAt().isBefore(release.release().releasedAt())) mismatch();
            var scope = schema.bindScope(row.audienceManifest().getBytes(StandardCharsets.UTF_8));
            if (!approved.equals(scope) || !row.contentSha256().equals(DigestUtils.sha256Hex(json.canonicalObject(scope)))) mismatch();
            if ("ENTERPRISE".equals(scope.projectMode()) ? row.projectId() != null : !project.equals(row.projectId())) mismatch();
        }
        boolean member = !"ROLES".equals(approved.audienceMode()) && approved.memberIds().contains(actor);
        var roleProofs = new ArrayList<RoleProof>();
        if (!member && !"MEMBERS".equals(approved.audienceMode())) {
            values.put("roleIds", approved.roleIds());
            var origins = bounded(mapper.roleOrigins(values), limits.maximumVersions());
            var now = Objects.requireNonNull(permissions.databaseTime());
            for (var origin : origins) {
                var proof = role(release, target, action, approved.roleIds(), origin, now);
                if (proof != null) roleProofs.add(proof);
            }
        }
        if (!member && roleProofs.isEmpty()) denied();
        // 范围名单不授予业务权限，原动作许可和当前持久范围共同进入结果。
        releases.requireUnchanged(release);
        if (!grant.equals((planDraft!=null?planDraft.requireOriginalAction(target,action,release.purpose()):start==null?resources.require(admission, target, action, release.purpose()):resources.requireStartRun(admission,target,start)))) stale();
        return new VerifiedScope(release, target, action, classification, limits, scopes, roleProofs, grant,start,planDraft);
    }

    private RoleProof role(AgentReleaseStore.Stored release, PermissionProtocol.Scope target, String action,
            List<String> acceptedRoles, AgentReleaseStoredProtocol.RoleOrigin origin, LocalDateTime now) throws IOException {
        var admission = release.admission(); String actor = admission.actorId();
        WireValues.uuid(origin.grantId()); WireValues.uuid(origin.roleVersion()); WireValues.uuid(origin.invitationId());
        WireValues.uuid(origin.roleId());
        if (!acceptedRoles.contains(origin.roleId())) mismatch();
        var role = single(invitations.role(origin.roleVersion())); var definition = invitationSchema.role(role.definition());
        var version = single(permissions.basis(origin.roleVersion()));
        if (!origin.roleVersion().equals(role.versionId()) || !origin.roleId().equals(role.roleId())
                || !origin.roleId().equals(definition.role_id()) || !"ACTIVE".equals(role.status())
                || !"1.0.0".equals(role.schemaVersion()) || !invitationSchema.manifestSha256().equals(role.manifest())
                || role.approvedAt().isAfter(now) || role.validFrom().isAfter(now)
                || role.validTo() != null && !role.validTo().isAfter(now) || !definition.actions().contains(action)
                || !origin.roleVersion().equals(version.id()) || version.ownerProjectId() != null
                || !version.sha256().equals(DigestUtils.sha256Hex(json.canonicalObject(definition)))) mismatch();
        var invitation = single(invitations.currentInvitation(origin.invitationId()));
        if (!origin.invitationId().equals(invitation.invitationId()) || !actor.equals(invitation.userId())
                || !"ACCEPTED".equals(invitation.status()) || !"1.0.0".equals(invitation.schemaVersion())
                || !invitationSchema.manifestSha256().equals(invitation.manifest())) mismatch();
        var reference = single(invitationSchema.roles(invitation.roles()).stream().filter(item -> item.role_id().equals(origin.roleId())
            && item.version_id().equals(origin.roleVersion())).toList());
        if (!reference.content_sha256().equals(version.sha256())) mismatch();
        var authorization = single(invitationSchema.authorizations(invitation.authorizations()).stream().filter(item ->
            origin.roleId().equals(item.role_id()) && origin.roleVersion().equals(item.role_version()) && action.equals(item.action())).toList());
        if (!definition.condition().equals(authorization.condition())) mismatch();
        var scope = json.scope(invitationSchema.read(invitation.scope().getBytes(StandardCharsets.UTF_8)));
        var grant = single(permissions.currentGrant(origin.grantId()));
        WireValues.revision(role.revision().toString()); WireValues.revision(invitation.revision().toString());
        WireValues.revision(grant.revision().toString());
        if (role.revision().signum() <= 0 || invitation.revision().signum() <= 0 || grant.revision().signum() <= 0
                || !target.tenant_id().equals(scope.tenant_id())
                || !origin.grantId().equals(grant.grantId()) || !actor.equals(grant.subjectId()) || !"ACTIVE".equals(grant.status())
                || !Objects.equals(scope.project_id(), grant.projectId()) || !scope.resource_kind().equals(grant.resourceKind())
                || !Objects.equals(scope.resource_id(), grant.resourceId()) || !action.equals(grant.action())
                || !authorization.condition().equals(json.condition(grant.condition())) || !invitation.basisVersion().equals(grant.basisVersion())
                || !Objects.equals(authorization.valid_to() == null ? null : PermissionAuthority.time(authorization.valid_to()), grant.validTo())) mismatch();
        if (authority.findResource(target, actor, admission.bindingView().mode(), action, release.purpose(), List.of(grant), now).isEmpty())
            return null;
        return new RoleProof(origin, role, invitation, grant);
    }

    void requireUnchanged(VerifiedScope original) throws IOException {
        Objects.requireNonNull(original);
        var current = require(original.release, original.target, original.action, original.classification, original.limits,original.start,original.planDraft);
        if (!original.scopes.equals(current.scopes) || !original.roles.equals(current.roles) || !original.grant.equals(current.grant)) stale();
    }

    private static <T> List<T> bounded(List<T> rows, int maximum) {
        if (rows.size() > maximum) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); return List.copyOf(rows);
    }
    private static <T> T single(List<T> values) { if (values.size() != 1) denied(); return values.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void stale() { throw new TenantAccessException(ErrorCode.SCOPE_STALE); }
}
