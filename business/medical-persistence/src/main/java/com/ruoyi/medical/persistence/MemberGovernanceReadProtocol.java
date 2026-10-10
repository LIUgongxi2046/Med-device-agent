package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;

public final class MemberGovernanceReadProtocol {
    private MemberGovernanceReadProtocol() { }
    public record Invitation(String invitationId, String userId, String roles, String scope, String authorizations,
        String basisVersion, String basisSha256, String status, BigInteger revision, LocalDateTime expiresAt,
        String createdBy, LocalDateTime createdAt, String schemaVersion, String manifest) { }
}
