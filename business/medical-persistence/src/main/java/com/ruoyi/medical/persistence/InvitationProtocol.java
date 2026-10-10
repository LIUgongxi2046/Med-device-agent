package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public final class InvitationProtocol {
    private InvitationProtocol() { }
    public record RoleReference(String role_id, String version_id, String content_sha256) { }
    public record Create(String schema_version, AuthenticatedIdentityBinding.View identity_binding, List<RoleReference> roles,
            PermissionProtocol.Scope scope, String expires_at, PermissionProtocol.Basis basis) {
        public Create { roles = List.copyOf(roles); }
    }
    public record Accept(String schema_version, String credential, String expected_revision) {
        @Override public String toString() { return "InvitationAcceptance"; }
    }
    public record RoleDefinition(String schema_version, String role_id, List<String> actions, PermissionProtocol.Condition condition) {
        public RoleDefinition { actions = List.copyOf(actions); }
    }
    public record Authorization(String role_id, String role_version, String action, PermissionProtocol.Condition condition,
            String authority_id, String authority_revision, String valid_to) { }
    public record RoleRow(String versionId, String roleId, String definition, String status, String approvedBy,
            LocalDateTime approvedAt, LocalDateTime validFrom, LocalDateTime validTo, String schemaVersion, String manifest, BigInteger revision) { }
    public record Row(String invitationId, String userId, String identityId, String roles, String scope, String authorizations,
            String basisVersion, String credentialSha256, LocalDateTime expiresAt, String status, String createdBy,
            LocalDateTime createdAt, String schemaVersion, String manifest, BigInteger revision) { }
    public record Outcome(JsonNode response, com.ruoyi.medical.contracts.ErrorCode rejection) { }
}
