package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.identity.local-management")
public record LocalManagementPolicy(String hmacKey, String hmacKeyId) {
    public LocalManagementPolicy {
        if (hmacKey == null || !hmacKey.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("LOCAL_MANAGEMENT_SECRET_REQUIRED");
        WireValues.uuid(hmacKeyId);
    }
    @Override public String toString() { return "LocalManagementPolicy"; }
}
