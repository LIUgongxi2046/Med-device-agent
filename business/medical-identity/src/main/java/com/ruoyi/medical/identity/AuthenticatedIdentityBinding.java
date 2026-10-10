package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import java.util.Objects;

public final class AuthenticatedIdentityBinding {
    private final View value;

    private AuthenticatedIdentityBinding(View value) {
        this.value = Objects.requireNonNull(value);
        WireValues.uuid(value.user_id());
    }

    static AuthenticatedIdentityBinding local(String userId) {
        return new AuthenticatedIdentityBinding(new View("local", userId, null, null));
    }

    static AuthenticatedIdentityBinding oidc(String userId, VerifiedOidcIdentity identity) {
        Objects.requireNonNull(identity);
        return new AuthenticatedIdentityBinding(new View("oidc", userId, identity.issuer(), identity.subject()));
    }

    public View view() { return value; }

    public boolean matches(AuthenticatedIdentityBinding other) {
        return value.equals(Objects.requireNonNull(other).value);
    }

    public boolean matches(View intended) {
        return value.equals(Objects.requireNonNull(intended));
    }

    @Override public String toString() { return "AuthenticatedIdentityBinding"; }

    // 传输对象描述本人绑定，不能作为认证主体或者企业授权证明。
    public record View(String mode, String user_id, String issuer, String subject) {
        @Override public String toString() { return "IdentityBindingView"; }
    }
}
