package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

record VerifiedOidcSession(VerifiedOidcIdentity identity, String sessionId, String registrationId,
        String clientId, String providerSessionId, Instant issuedAt) {
    static VerifiedOidcSession requireCurrent(OidcPolicy policy, ClientRegistration registration, HttpSession session) {
        VerifiedOidcIdentity identity = VerifiedOidcIdentity.requireCurrent(policy);
        policy.verify(registration);
        var authentication = (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        var user = (OidcUser) authentication.getPrincipal();
        var token = user.getIdToken();
        Object sid = token.getClaims().get("sid");
        if (session == null || !validText(session.getId(), 255)
                || !validText(registration.getRegistrationId(), 100) || !validText(registration.getClientId(), 255)
                || token.getAudience() == null || !token.getAudience().contains(registration.getClientId())
                || token.getIssuedAt() == null || token.getIssuedAt().getNano() % 1000 != 0
                || (token.getClaims().containsKey("sid") && (!(sid instanceof String text) || !validText(text, 512)))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
        }
        return new VerifiedOidcSession(identity, session.getId(), registration.getRegistrationId(),
                registration.getClientId(), (String) sid, token.getIssuedAt());
    }

    private static boolean validText(String text, int maximum) {
        return text != null && !text.isEmpty() && text.codePointCount(0, text.length()) <= maximum
                && StandardCharsets.UTF_8.newEncoder().canEncode(text);
    }

    @Override public String toString() { return "VerifiedOidcSession"; }
}
