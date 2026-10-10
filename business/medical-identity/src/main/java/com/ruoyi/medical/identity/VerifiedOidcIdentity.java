package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

record VerifiedOidcIdentity(String issuer, String subject) {
    static VerifiedOidcIdentity requireCurrent(OidcPolicy policy) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oidc) || !oidc.isAuthenticated()
                || !(oidc.getPrincipal() instanceof OidcUser user)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_REQUIRED.name()));
        }
        Objects.requireNonNull(policy);
        String issuer = user.getIdToken().getIssuer().toString();
        String subject = user.getIdToken().getSubject();
        if (!policy.registrationId().equals(oidc.getAuthorizedClientRegistrationId())
                || !policy.issuer().toASCIIString().equals(issuer)
                || issuer.codePointCount(0, issuer.length()) > 512
                || subject == null || subject.isEmpty() || subject.codePointCount(0, subject.length()) > 255
                || !StandardCharsets.UTF_8.newEncoder().canEncode(subject)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
        }
        return new VerifiedOidcIdentity(issuer, subject);
    }

    @Override public String toString() { return "VerifiedOidcIdentity"; }
}
