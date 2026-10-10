package com.ruoyi.medical.identity;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

@ConfigurationProperties("medical.identity.backchannel")
public record BackchannelLogoutPolicy(Duration clockSkew, Duration connectTimeout, Duration readTimeout,
        Set<SignatureAlgorithm> signatureAlgorithms) {
    public BackchannelLogoutPolicy {
        if (clockSkew == null || connectTimeout == null || readTimeout == null
                || signatureAlgorithms == null || signatureAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("LOGOUT_TOKEN_CONFIGURATION_REQUIRED");
        }
        if (clockSkew.isNegative() || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero() || signatureAlgorithms.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("LOGOUT_TOKEN_CONFIGURATION_INVALID");
        }
        signatureAlgorithms = Set.copyOf(signatureAlgorithms);
    }
}
