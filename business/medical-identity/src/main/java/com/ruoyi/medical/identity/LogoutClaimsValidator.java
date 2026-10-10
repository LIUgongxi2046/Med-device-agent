package com.ruoyi.medical.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class LogoutClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";
    private final String issuer;
    private final String audience;
    private final Duration clockSkew;
    private final Clock clock = Clock.systemUTC();

    LogoutClaimsValidator(String issuer, String audience, Duration clockSkew) {
        this.issuer = Objects.requireNonNull(issuer);
        this.audience = Objects.requireNonNull(audience);
        this.clockSkew = Objects.requireNonNull(clockSkew);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Objects.requireNonNull(token);
        Map<String, Object> claims = token.getClaims();
        Object subject = claims.get("sub");
        Object sessionId = claims.get("sid");
        Object events = claims.get("events");
        Object type = token.getHeaders().get("typ");
        Instant issuedAt = token.getIssuedAt();
        Instant expiresAt = token.getExpiresAt();
        boolean typeValid = type == null || "JWT".equals(type) || "logout+jwt".equals(type)
                || "application/logout+jwt".equals(type);
        boolean identityValid = (!claims.containsKey("sub") || text(subject))
                && (!claims.containsKey("sid") || text(sessionId)) && (text(subject) || text(sessionId));
        boolean eventsValid = events instanceof Map<?, ?> eventMap && eventMap.get(EVENT) instanceof Map<?, ?>;
        if (!typeValid || !issuer.equals(claims.get("iss")) || !audienceMatches(claims.get("aud"))
                || !text(claims.get("jti")) || !identityValid || !eventsValid || claims.containsKey("nonce")
                || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)
                || Duration.between(clock.instant(), issuedAt).compareTo(clockSkew) > 0) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "LOGOUT_TOKEN_INVALID", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean audienceMatches(Object value) {
        if (value instanceof String text) return audience.equals(text);
        return value instanceof List<?> values && !values.isEmpty()
                && values.stream().allMatch(LogoutClaimsValidator::text) && values.contains(audience);
    }

    private static boolean text(Object value) {
        return value instanceof String text && !text.isEmpty() && StandardCharsets.UTF_8.newEncoder().canEncode(text);
    }
}
