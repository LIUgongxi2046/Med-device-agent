package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.document-read")
public record DocumentReadPolicy(String cursorKeyId, String cursorKey, Duration cursorLifetime, int maximumPageBytes) {
    public DocumentReadPolicy {
        boolean absent = cursorKeyId == null && cursorKey == null && cursorLifetime == null && maximumPageBytes == 0;
        if (!absent) {
            WireValues.uuid(cursorKeyId);
            if (cursorKey == null || Base64.getUrlDecoder().decode(cursorKey).length != 32 || cursorLifetime == null
                    || cursorLifetime.isZero() || cursorLifetime.isNegative() || maximumPageBytes < 1)
                throw new IllegalArgumentException("正文游标身份、AES-256密钥、期限和分页字节容量必须完整配置");
        }
    }
    void requireConfigured() { if (cursorKeyId == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
}
