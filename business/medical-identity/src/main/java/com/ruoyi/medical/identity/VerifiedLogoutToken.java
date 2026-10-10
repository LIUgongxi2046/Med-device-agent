package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

final class VerifiedLogoutToken {
    final String issuer, clientId, jti, subject, providerSessionId, tokenSha256, logoutKey;
    final Instant issuedAt, expiresAt;

    private VerifiedLogoutToken(String raw, BackchannelLogoutVerifier verifier) throws IOException, ParseException {
        var jwt = verifier.verify(raw);
        issuer = verifier.issuer();
        clientId = verifier.clientId();
        jti = jwt.getClaimAsString("jti");
        subject = jwt.getSubject();
        providerSessionId = jwt.getClaimAsString("sid");
        issuedAt = jwt.getIssuedAt();
        expiresAt = jwt.getExpiresAt();
        requireText(issuer, 512);
        requireText(clientId, 255);
        if (subject != null) requireText(subject, 255);
        if (providerSessionId != null) requireText(providerSessionId, 512);
        if (issuedAt == null || expiresAt == null || issuedAt.getNano() % 1000 != 0
                || expiresAt.getNano() % 1000 != 0 || !expiresAt.isAfter(issuedAt)) failed();
        tokenSha256 = DigestUtils.sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
        logoutKey = CanonicalOidcKey.hash(issuer, clientId, jti);
    }

    static VerifiedLogoutToken verify(String raw, BackchannelLogoutVerifier verifier) throws IOException, ParseException {
        BackchannelLogoutVerifier.requireSignedToken(raw);
        return new VerifiedLogoutToken(raw, Objects.requireNonNull(verifier));
    }

    private static void requireText(String value, int maximum) {
        if (value == null || value.isEmpty() || value.codePointCount(0, value.length()) > maximum
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) failed();
    }

    private static void failed() {
        throw new OAuth2AuthenticationException(new OAuth2Error(ErrorCode.AUTHENTICATION_FAILED.name()));
    }

    @Override public String toString() { return "VerifiedLogoutToken"; }
}
