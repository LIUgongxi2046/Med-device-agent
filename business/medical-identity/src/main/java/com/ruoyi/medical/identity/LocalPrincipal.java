package com.ruoyi.medical.identity;

import java.math.BigInteger;
import java.util.List;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class LocalPrincipal extends LoginUser {
    private static final long serialVersionUID = 1L;
    final String businessUserId;
    final BigInteger credentialRevision;
    final String credentialSha256;
    final String platformRole;

    LocalPrincipal(LoginUser identity, String businessUserId, String platformRole, BigInteger credentialRevision) {
        super(identity.getUserId(), identity.getDeptId(), identity.getUser(), identity.getPermissions());
        this.businessUserId = businessUserId;
        this.credentialRevision = credentialRevision;
        this.credentialSha256 = org.apache.commons.codec.digest.DigestUtils.sha256Hex(identity.getPassword());
        this.platformRole = platformRole;
    }

    @Override public List<SimpleGrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority(platformRole)); }
    @Override public String toString() { return "LocalPrincipal"; }
}
