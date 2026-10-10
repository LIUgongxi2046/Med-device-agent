package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class PermissionAuthority {
    private final PermissionSchema schema;
    public PermissionAuthority(PermissionSchema schema) { this.schema = schema; }

    ProjectProtocol.Access projectAccess(String tenantId, String identityMode, String action, String purpose,
            List<PermissionProtocol.GrantRow> grants) throws IOException {
        boolean enterprise = false;
        var projects = new java.util.TreeSet<String>();
        for (var grant : grants) {
            var condition = schema.condition(grant.condition());
            if (!"1.0.0".equals(grant.schemaVersion()) || !schema.manifestSha256().equals(grant.manifest())
                    || !grant.status().equals("ACTIVE") || !grant.action().equals(action)
                    || !condition.identity_modes().contains(identityMode) || !allows(condition.purposes(), purpose)) continue;
            if (grant.resourceKind().equals("ENTERPRISE") && grant.projectId() == null && tenantId.equals(grant.resourceId())) enterprise = true;
            if (grant.resourceKind().equals("PROJECT") && grant.projectId() != null && grant.projectId().equals(grant.resourceId())) projects.add(grant.projectId());
        }
        return new ProjectProtocol.Access(enterprise, List.copyOf(projects));
    }

    PermissionProtocol.GrantRow requireProject(String tenantId, String identityMode, String projectId, String action,
            String purpose, List<PermissionProtocol.GrantRow> grants) throws IOException {
        for (var grant : grants) {
            var allowed = projectAccess(tenantId, identityMode, action, purpose, List.of(grant));
            if (projectId == null ? allowed.enterprise() : allowed.permits(projectId)) return grant;
        }
        throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }

    PermissionProtocol.GrantRow requireResource(PermissionProtocol.Scope scope, String subjectId, String identityMode,
            String action, String purpose, List<PermissionProtocol.GrantRow> grants, LocalDateTime now) throws IOException {
        return findResource(scope, subjectId, identityMode, action, purpose, grants, now)
                .orElseThrow(() -> new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }

    java.util.Optional<PermissionProtocol.GrantRow> findResource(PermissionProtocol.Scope scope, String subjectId, String identityMode,
            String action, String purpose, List<PermissionProtocol.GrantRow> grants, LocalDateTime now) throws IOException {
        schema.scope(schema.tree(Objects.requireNonNull(scope)));
        com.ruoyi.medical.contracts.WireValues.uuid(Objects.requireNonNull(subjectId));
        Objects.requireNonNull(now); Objects.requireNonNull(grants);
        if (scope.resource_id() == null || action == null || !action.matches("[a-z][a-z0-9_.-]{0,63}")
                || purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,63}")
                || !List.of("local", "oidc").contains(identityMode)) {
            throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
        for (var grant : grants) {
            if (containsResource(grant, scope) && eligible(grant, subjectId, identityMode, action, purpose, now)) return java.util.Optional.of(grant);
        }
        return java.util.Optional.empty();
    }

    ResourceSelection taskSelection(String tenantId, String projectId, String subjectId, String mode,
            List<PermissionProtocol.GrantRow> grants, LocalDateTime now) throws IOException {
        var ids = new java.util.TreeSet<String>(); var witnesses = new java.util.ArrayList<PermissionProtocol.GrantRow>();
        boolean all = false;
        for (var grant : grants) {
            if (grant.projectId() != null && !grant.projectId().equals(projectId)
                    || !eligible(grant, subjectId, mode, "task.read", "TASK_READING", now)) continue;
            boolean whole = grant.resourceKind().equals("ENTERPRISE") && grant.projectId() == null && tenantId.equals(grant.resourceId())
                || grant.resourceKind().equals("PROJECT") && projectId.equals(grant.projectId()) && projectId.equals(grant.resourceId())
                || grant.resourceKind().equals("TASK") && grant.resourceId() == null;
            if (!whole && !grant.resourceKind().equals("TASK")) continue;
            if (whole) all = true; else { com.ruoyi.medical.contracts.WireValues.uuid(grant.resourceId()); ids.add(grant.resourceId()); }
            witnesses.add(grant);
        }
        witnesses.sort(java.util.Comparator.comparing(PermissionProtocol.GrantRow::grantId));
        return new ResourceSelection(all, List.copyOf(ids), List.copyOf(witnesses));
    }

    private boolean eligible(PermissionProtocol.GrantRow grant, String subjectId, String mode, String action,
            String purpose, LocalDateTime now) throws IOException {
        if (!subjectId.equals(grant.subjectId()) || !"ACTIVE".equals(grant.status()) || !action.equals(grant.action())
                || !"1.0.0".equals(grant.schemaVersion()) || !schema.manifestSha256().equals(grant.manifest())
                || grant.validFrom() == null || grant.validFrom().isAfter(now)
                || grant.validTo() != null && !grant.validTo().isAfter(now)) return false;
        var condition = schema.condition(grant.condition());
        return condition.identity_modes().contains(mode) && allows(condition.purposes(), purpose);
    }

    record ResourceSelection(boolean all, List<String> ids, List<PermissionProtocol.GrantRow> witnesses) { }

    private static boolean containsResource(PermissionProtocol.GrantRow grant, PermissionProtocol.Scope scope) {
        if (grant.projectId() != null && !grant.projectId().equals(scope.project_id())) return false;
        return switch (grant.resourceKind()) {
            case "ENTERPRISE" -> grant.projectId() == null && scope.tenant_id().equals(grant.resourceId());
            case "PROJECT" -> grant.projectId() != null && grant.projectId().equals(grant.resourceId())
                    && grant.projectId().equals(scope.project_id());
            default -> grant.resourceKind().equals(scope.resource_kind())
                    && (grant.resourceId() == null || grant.resourceId().equals(scope.resource_id()));
        };
    }

    PermissionProtocol.GrantRow require(PermissionProtocol.Request request, String identityMode,
            List<PermissionProtocol.GrantRow> grants) throws IOException {
        return require(request, identityMode, grants, "PERMISSION_ADMINISTRATION", false, List.of());
    }

    PermissionProtocol.GrantRow requireInvitation(PermissionProtocol.Request request, String identityMode,
            List<PermissionProtocol.GrantRow> grants, List<String> roles) throws IOException {
        return require(request, identityMode, grants, "INVITATION_ADMINISTRATION", true, roles);
    }

    List<PermissionProtocol.GrantRow> roleManagers(String tenantId, String identityMode,
            List<PermissionProtocol.GrantRow> grants) throws IOException {
        var scope = new PermissionProtocol.Scope(tenantId, null, "ENTERPRISE", tenantId);
        List<PermissionProtocol.GrantRow> result = new java.util.ArrayList<>();
        for (var grant : grants) {
            var condition = schema.condition(grant.condition());
            if ("1.0.0".equals(grant.schemaVersion()) && schema.manifestSha256().equals(grant.manifest())
                    && grant.status().equals("ACTIVE") && grant.action().equals("role.manage")
                    && grant.projectId() == null && grant.resourceKind().equals("ENTERPRISE")
                    && tenantId.equals(grant.resourceId()) && condition.identity_modes().contains(identityMode)
                    && allows(condition.purposes(), "ROLE_ADMINISTRATION") && covers(grant, condition.delegation(), scope)) result.add(grant);
        }
        if (result.isEmpty()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return List.copyOf(result);
    }

    PermissionProtocol.GrantRow requireRole(String tenantId, String identityMode, List<PermissionProtocol.GrantRow> grants,
            String roleId, InvitationProtocol.RoleDefinition definition, LocalDateTime from, LocalDateTime to, LocalDateTime now) throws IOException {
        for (var grant : roleManagers(tenantId, identityMode, grants)) {
            var condition = schema.condition(grant.condition());
            if (!condition.delegation().role_ids().contains(roleId)) continue;
            if (definition != null && (!condition.delegation().actions().containsAll(definition.actions())
                    || !canDelegate(grant, condition, definition.condition()))) continue;
            if (definition != null && (from.isBefore(grant.validFrom())
                    || grant.validTo() != null && (to == null || to.isAfter(grant.validTo()) || !from.isBefore(grant.validTo())))) continue;
            if (definition != null) requireValidity(grant, from, to, now);
            return grant;
        }
        throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }

    private PermissionProtocol.GrantRow require(PermissionProtocol.Request request, String identityMode,
            List<PermissionProtocol.GrantRow> grants, String purpose, boolean invitation, List<String> roles) throws IOException {
        return findGovernance(request, identityMode, grants, purpose, invitation, roles)
                .orElseThrow(() -> new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }

    java.util.Optional<PermissionProtocol.GrantRow> findGovernance(PermissionProtocol.Request request, String identityMode,
            List<PermissionProtocol.GrantRow> grants, String purpose, boolean invitation, List<String> roles) throws IOException {
        for (var grant : grants) {
            var condition = schema.condition(grant.condition());
            if (!"1.0.0".equals(grant.schemaVersion()) || !condition.identity_modes().contains(identityMode)
                    || !allows(condition.purposes(), purpose) || !condition.delegation().role_ids().containsAll(roles)) continue;
            boolean manager = grant.action().equals("member.manage")
                    || !invitation && grant.action().equals("project.grant") && request.scope().project_id() != null
                       && !request.decision().equals("DISABLE_MEMBER");
            if (!manager || !covers(grant, condition.delegation(), request.scope())) continue;
            if (request.action() != null && !condition.delegation().actions().contains(request.action())) continue;
            if (request.condition() != null && !canDelegate(grant, condition, request.condition())) continue;
            return java.util.Optional.of(grant);
        }
        return java.util.Optional.empty();
    }

    void requireValidity(PermissionProtocol.GrantRow authority, LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        if (from.isBefore(authority.validFrom()) || to != null && (!to.isAfter(from) || !to.isAfter(now))
                || authority.validTo() != null && (to == null || to.isAfter(authority.validTo()) || !from.isBefore(authority.validTo()))) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static boolean covers(PermissionProtocol.GrantRow grant, PermissionProtocol.Delegation delegation, PermissionProtocol.Scope scope) {
        if (!delegation.resource_kinds().contains(scope.resource_kind())
                || !allows(delegation.project_ids(), scope.project_id()) || !allows(delegation.resource_ids(), scope.resource_id())) return false;
        if (grant.projectId() != null && !grant.projectId().equals(scope.project_id())) return false;
        return switch (grant.resourceKind()) {
            case "ENTERPRISE" -> grant.projectId() == null && scope.tenant_id().equals(grant.resourceId());
            case "PROJECT" -> grant.projectId() != null && grant.projectId().equals(grant.resourceId())
                    && grant.projectId().equals(scope.project_id());
            default -> grant.resourceKind().equals(scope.resource_kind())
                    && (grant.resourceId() == null || grant.resourceId().equals(scope.resource_id()));
        };
    }

    private static boolean canDelegate(PermissionProtocol.GrantRow grant, PermissionProtocol.Condition parent, PermissionProtocol.Condition child) {
        var allowed = parent.delegation(); var requested = child.delegation();
        return parent.identity_modes().containsAll(child.identity_modes()) && subset(parent.purposes(), child.purposes())
                && allowed.actions().containsAll(requested.actions()) && allowed.role_ids().containsAll(requested.role_ids())
                && allowed.resource_kinds().containsAll(requested.resource_kinds())
                && subset(allowed.project_ids(), requested.project_ids()) && subset(allowed.resource_ids(), requested.resource_ids())
                && (grant.projectId() == null || requested.project_ids() != null && requested.project_ids().stream().allMatch(grant.projectId()::equals))
                && (grant.resourceKind().equals("ENTERPRISE") || grant.resourceKind().equals("PROJECT")
                    || requested.resource_kinds().stream().allMatch(grant.resourceKind()::equals)
                       && (grant.resourceId() == null || requested.resource_ids() != null && requested.resource_ids().stream().allMatch(grant.resourceId()::equals)));
    }

    private static boolean allows(List<String> allowed, String requested) { return allowed == null || requested != null && allowed.contains(requested); }
    private static boolean subset(List<String> allowed, List<String> requested) { return allowed == null || requested != null && allowed.containsAll(requested); }
    static LocalDateTime time(String value) {
        LocalDateTime parsed = LocalDateTime.ofInstant(com.ruoyi.medical.contracts.WireValues.utcTime(Objects.requireNonNull(value)), ZoneOffset.UTC);
        if (parsed.getYear() < 1000 || parsed.getYear() > 9999) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return parsed;
    }
}
