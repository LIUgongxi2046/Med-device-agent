package com.ruoyi.medical.persistence;

import java.math.BigInteger;

public final class EnterpriseRoleProtocol {
    private EnterpriseRoleProtocol() { }
    public record Publish(String schema_version, String name, InvitationProtocol.RoleDefinition definition,
            String valid_from, String valid_to, PermissionProtocol.Basis approval_basis, String reason,
            boolean approval_attested, String expected_head_revision) { }
    public record Revoke(String schema_version, String expected_revision, String reason, boolean approval_attested) { }
    public record Head(String roleId, String currentVersion, BigInteger revision) { }
    public record Publication(String versionId, String name, String approvalBasis, String approvalBasisSha256,
            String reason, String authorityGrant, BigInteger authorityRevision, String schemaVersion, String manifest) { }
    public record GrantOrigin(String grantId, String roleVersion, String invitationId) { }
}
