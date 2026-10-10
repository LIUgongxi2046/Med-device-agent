package com.ruoyi.medical.identity;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.identity.local")
public record LocalIdentityPolicy(URI browserOrigin) {
    public LocalIdentityPolicy {
        if (browserOrigin == null) throw new IllegalArgumentException("LOCAL_IDENTITY_CONFIGURATION_REQUIRED");
        boolean loopback = "127.0.0.1".equals(browserOrigin.getHost()) || "localhost".equals(browserOrigin.getHost());
        if ((!"https".equals(browserOrigin.getScheme()) && !(loopback && "http".equals(browserOrigin.getScheme())))
                || browserOrigin.getHost() == null || browserOrigin.getRawUserInfo() != null
                || browserOrigin.getRawQuery() != null || browserOrigin.getRawFragment() != null
                || (browserOrigin.getRawPath() != null && !browserOrigin.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("LOCAL_IDENTITY_CONFIGURATION_INVALID");
        }
    }
}
