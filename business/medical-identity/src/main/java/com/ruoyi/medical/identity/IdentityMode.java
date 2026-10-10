package com.ruoyi.medical.identity;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.identity")
public record IdentityMode(String mode) {
    public IdentityMode {
        if (mode == null) throw new IllegalArgumentException("IDENTITY_MODE_REQUIRED");
        if (!Set.of("local", "oidc").contains(mode)) throw new IllegalArgumentException("IDENTITY_MODE_INVALID");
    }
}
