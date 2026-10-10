package com.ruoyi.medical.identity;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.identity.directory")
public record IdentityDirectoryPolicy(String classification) {
    public IdentityDirectoryPolicy {
        if (classification == null || classification.isBlank()
                || classification.codePointCount(0, classification.length()) > 24
                || !StandardCharsets.UTF_8.newEncoder().canEncode(classification)) {
            throw new IllegalArgumentException("IDENTITY_DIRECTORY_CONFIGURATION_REQUIRED");
        }
    }
}
