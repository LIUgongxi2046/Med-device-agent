package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class PermissionProtocol {
    private PermissionProtocol() { }
    public record Scope(String tenant_id, String project_id, String resource_kind, String resource_id) { }
    public record Basis(String version_id, String content_sha256) { }
    public record Delegation(List<String> actions, List<String> role_ids, List<String> resource_kinds,
                             List<String> project_ids, List<String> resource_ids) {
        public Delegation {
            actions = List.copyOf(actions); role_ids = List.copyOf(role_ids); resource_kinds = List.copyOf(resource_kinds);
            if (project_ids != null) project_ids = List.copyOf(project_ids);
            if (resource_ids != null) resource_ids = List.copyOf(resource_ids);
        }
    }
    public record Condition(String schema_version, List<String> identity_modes, List<String> purposes, Delegation delegation) {
        public Condition { identity_modes = List.copyOf(identity_modes); if (purposes != null) purposes = List.copyOf(purposes); }
    }
    public record Request(String schema_version, String decision, String subject_id, Scope scope, String action,
                          String grant_id, Condition condition, String valid_from, String valid_to, Basis basis,
                          String expected_revision, String reason) { }
    public record GrantRow(String grantId, String subjectId, String projectId, String resourceKind, String resourceId,
                           String action, String condition, String status, LocalDateTime validFrom, LocalDateTime validTo,
                           String basisVersion, String schemaVersion, String manifest, BigInteger revision) { }
    public record BindingRow(String userId, String identityId, String mode, String issuer, String subject,
                             String localUserId, String status) { }
    public record VersionRow(String id, String ownerProjectId, String sha256) { }
    public record OwnerRow(String id, String projectId) { }
}
