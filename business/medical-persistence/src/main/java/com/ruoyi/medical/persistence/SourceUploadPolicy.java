package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-uploads")
public record SourceUploadPolicy(BigInteger maximumBytes, long expiresAfterSeconds) {
    public SourceUploadPolicy {
        if (maximumBytes == null || maximumBytes.signum() <= 0 || expiresAfterSeconds <= 0)
            throw new IllegalArgumentException("SOURCE_UPLOAD_LIMITS_REQUIRED");
        WireValues.revision(maximumBytes.toString());
    }
}
