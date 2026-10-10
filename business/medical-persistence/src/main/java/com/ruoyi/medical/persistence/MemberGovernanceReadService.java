package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.MemberGovernanceReadMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class MemberGovernanceReadService {
    private final EnterpriseAccess access;
    private final MemberGovernanceReadMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final InvitationSchema invitations;
    private final MemberGovernanceReadSchema schema;
    private final PermissionAuthority authority;
    private final int capacity;

    public MemberGovernanceReadService(EnterpriseAccess access, MemberGovernanceReadMapper mapper, PermissionMapper permissions,
        PermissionSchema json, InvitationSchema invitations, MemberGovernanceReadSchema schema, PermissionAuthority authority,
        @Value("${medical.member-governance.maximum-read-candidates}") int capacity) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.json = json;
        this.invitations = invitations; this.schema = schema; this.authority = authority;
        if (capacity < 100 || capacity > 100000) throw new IllegalArgumentException("MEMBER_READ_CAPACITY_INVALID");
        this.capacity = capacity;
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String kind, MemberGovernanceReadSchema.Query query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> read(admission, context, kind, query)).get());
    }

    private JsonNode read(EnterpriseAccess.Admission admission, String context, String kind, MemberGovernanceReadSchema.Query query) throws IOException {
        if (!admission.context().context_revision().equals(context)) denied(ErrorCode.CONTEXT_CHANGED);
        String actor = admission.subject().user().user_id(), tenant = admission.context().tenant_id();
        String mode = admission.subject().binding().view().mode();
        var managers = managers(actor, mode, kind, Objects.requireNonNull(permissions.databaseTime()));
        var items = new ArrayList<JsonNode>();
        switch (kind) {
            case "Members" -> {
                for (var member : bounded(mapper.members(actor, query.subjectId(), capacity + 1))) {
                    var scope = new PermissionProtocol.Scope(tenant, null, "MEMBER", member.memberId());
                    if (!managed(member.userId(), scope, null, null, managers, mode, false, List.of())) continue;
                    if (!tenant.equals(member.tenantId())) denied(ErrorCode.CONTENT_MISMATCH);
                    var item = json.object(); item.put("member_id", member.memberId()); item.put("user_id", member.userId());
                    item.put("status", member.status()); item.put("revision", member.revision().toString());
                    item.put("valid_from", time(member.validFrom())); item.put("valid_to", time(member.validTo())); items.add(item);
                }
            }
            case "Grants" -> {
                for (var grant : bounded(mapper.grants(actor, query.subjectId(), capacity + 1))) {
                    var scope = new PermissionProtocol.Scope(tenant, grant.projectId(), grant.resourceKind(), grant.resourceId());
                    if (!managed(grant.subjectId(), scope, grant.action(), null, managers, mode, false, List.of())) continue;
                    if (!"1.0.0".equals(grant.schemaVersion()) || !json.manifestSha256().equals(grant.manifest())) denied(ErrorCode.CONTENT_MISMATCH);
                    var condition = json.condition(grant.condition()); var basis = basis(grant.basisVersion());
                    var item = json.object(); item.put("grant_id", grant.grantId()); item.put("subject_id", grant.subjectId());
                    item.set("scope", json.tree(scope)); item.put("action", grant.action()); item.set("condition", json.tree(condition));
                    item.put("status", grant.status()); item.put("revision", grant.revision().toString());
                    item.put("valid_from", time(grant.validFrom())); item.put("valid_to", time(grant.validTo()));
                    item.set("basis", json.tree(basis)); items.add(item);
                }
            }
            case "Invitations" -> {
                for (var invitation : bounded(mapper.invitations(actor, query.subjectId(), capacity + 1))) {
                    if (!"1.0.0".equals(invitation.schemaVersion()) || !invitations.manifestSha256().equals(invitation.manifest())) denied(ErrorCode.CONTENT_MISMATCH);
                    var roles = invitations.roles(invitation.roles()); var scope = json.scope(invitations.read(invitation.scope().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    if (!tenant.equals(scope.tenant_id())) denied(ErrorCode.CONTENT_MISMATCH);
                    var authorizations = invitations.authorizations(invitation.authorizations());
                    if (authorizations.isEmpty()) denied(ErrorCode.CONTENT_MISMATCH);
                    var roleIds = roles.stream().map(InvitationProtocol.RoleReference::role_id).toList(); boolean visible = true;
                    for (var authorization : authorizations) {
                        if (roles.stream().noneMatch(role -> role.role_id().equals(authorization.role_id()) && role.version_id().equals(authorization.role_version())))
                            denied(ErrorCode.CONTENT_MISMATCH);
                        if (!managed(invitation.userId(), scope, authorization.action(), authorization.condition(), managers, mode, true, roleIds)) visible = false;
                    }
                    if (!visible) continue;
                    var item = json.object(); item.put("invitation_id", invitation.invitationId()); item.put("user_id", invitation.userId());
                    item.set("roles", json.tree(roles)); item.set("scope", json.tree(scope)); item.put("status", invitation.status());
                    item.put("revision", invitation.revision().toString()); item.put("expires_at", time(invitation.expiresAt()));
                    item.put("created_by", invitation.createdBy()); item.put("created_at", time(invitation.createdAt()));
                    item.set("basis", json.tree(new PermissionProtocol.Basis(invitation.basisVersion(), invitation.basisSha256()))); items.add(item);
                }
            }
            default -> throw new IllegalArgumentException("MEMBER_READ_KIND_INVALID");
        }
        String idField = switch (kind) { case "Members" -> "member_id"; case "Grants" -> "grant_id"; default -> "invitation_id"; };
        items.sort(java.util.Comparator.comparing(item -> item.required(idField).textValue()));
        var identity = json.object(); identity.put("actor", actor); identity.set("current_context", json.tree(admission.context()));
        identity.put("kind", kind); identity.put("subject_id", query.subjectId()); identity.set("items", json.tree(items));
        String watermark = DigestUtils.sha256Hex(json.canonical(identity));
        if (query.watermark() != null && !query.watermark().equals(watermark)) denied(ErrorCode.REVISION_CONFLICT);
        int start = 0;
        if (query.after() != null) {
            while (start < items.size() && !query.after().equals(items.get(start).required(idField).textValue())) start++;
            if (start == items.size()) denied(ErrorCode.REVISION_CONFLICT);
            start++;
        }
        int end = Math.min(items.size(), start + query.limit());
        var page = json.object(); page.put("schema_version", "1.0.0"); page.put("tenant_id", tenant);
        page.set("items", json.tree(items.subList(start, end))); page.put("dataset_watermark", watermark);
        page.put("next_after", end < items.size() ? items.get(end - 1).required(idField).textValue() : null);
        page.set("current_context", json.tree(admission.context())); return schema.envelope(kind, page);
    }

    private List<PermissionProtocol.GrantRow> managers(String actor, String mode, String kind, LocalDateTime now) throws IOException {
        var result = new ArrayList<PermissionProtocol.GrantRow>();
        String purpose = kind.equals("Invitations") ? "INVITATION_ADMINISTRATION" : "PERMISSION_ADMINISTRATION";
        for (var grant : permissions.currentDelegations(actor)) {
            var condition = json.condition(grant.condition());
            boolean action = grant.action().equals("member.manage") || kind.equals("Grants") && grant.action().equals("project.grant");
            if (!action || !actor.equals(grant.subjectId()) || !"ACTIVE".equals(grant.status()) || !"1.0.0".equals(grant.schemaVersion())
                || !json.manifestSha256().equals(grant.manifest()) || grant.validFrom().isAfter(now)
                || grant.validTo() != null && !grant.validTo().isAfter(now) || !condition.identity_modes().contains(mode)
                || condition.purposes() != null && !condition.purposes().contains(purpose)
                || kind.equals("Members") && !condition.delegation().resource_kinds().contains("MEMBER")) continue;
            result.add(grant);
        }
        if (result.isEmpty()) denied(ErrorCode.ACCESS_DENIED); return List.copyOf(result);
    }

    private boolean managed(String target, PermissionProtocol.Scope scope, String action, PermissionProtocol.Condition condition,
        List<PermissionProtocol.GrantRow> managers, String mode, boolean invitation, List<String> roles) throws IOException {
        var request = new PermissionProtocol.Request("1.0.0", action == null && !invitation ? "DISABLE_MEMBER" : "REVOKE",
            target, scope, action, null, condition, null, null, null, "0", "");
        return authority.findGovernance(request, mode, managers, invitation ? "INVITATION_ADMINISTRATION" : "PERMISSION_ADMINISTRATION", invitation, roles).isPresent();
    }
    private PermissionProtocol.Basis basis(String id) {
        var rows = permissions.basis(id); if (rows.size() != 1) denied(ErrorCode.CONTENT_MISMATCH);
        return new PermissionProtocol.Basis(id, rows.get(0).sha256());
    }
    private <T> List<T> bounded(List<T> rows) { if (rows.size() > capacity) denied(ErrorCode.CONTEXT_LIMIT); return rows; }
    private static String time(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC).toString(); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
