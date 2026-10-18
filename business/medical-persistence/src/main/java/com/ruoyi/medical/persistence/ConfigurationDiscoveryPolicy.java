package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dedicated deployment-supplied cursor identity; never generate or borrow a persistent credential. */
@ConfigurationProperties("medical.configuration-discovery")
public record ConfigurationDiscoveryPolicy(String cursorKeyId, String cursorKey, Duration cursorLifetime) {
    public ConfigurationDiscoveryPolicy {
        boolean absent = cursorKeyId == null && cursorKey == null && cursorLifetime == null;
        if (!absent) {
            WireValues.uuid(cursorKeyId);
            if (cursorKey == null || !cursorKey.matches("[A-Za-z0-9_-]{43}") || Base64.getUrlDecoder().decode(cursorKey).length != 32
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(cursorKey)).equals(cursorKey)
                    || cursorLifetime == null || cursorLifetime.toMillis() < 1 || cursorLifetime.compareTo(Duration.ofHours(1)) > 0)
                throw new IllegalArgumentException("DISCOVERY_CURSOR_CONFIGURATION_INVALID");
        }
    }
    void requireConfigured() { if (cursorKeyId == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    @Override public String toString() { return "ConfigurationDiscoveryPolicy[redacted]"; }
}
