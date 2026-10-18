package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dedicated BX-37 cursor key; absence never falls back to a document/node cursor key. */
@ConfigurationProperties("medical.artifact-content-read")
public record ArtifactContentReadPolicy(String cursorKeyId, String cursorKey, Duration cursorLifetime, int maximumChunkBytes,
        long maximumCacheBytes, int maximumCacheEntries, long maximumTenantCacheBytes, long maximumSubjectCacheBytes,
        int maximumConcurrentTransfers) {
    static final int DEFAULT_LIMIT_BYTES = 64 * 1024;
    static final int MAXIMUM_LIMIT_BYTES = 1024 * 1024;

    public ArtifactContentReadPolicy {
        boolean absent = cursorKeyId == null && cursorKey == null && cursorLifetime == null && maximumChunkBytes == 0
            && maximumCacheBytes == 0 && maximumCacheEntries == 0 && maximumTenantCacheBytes == 0
            && maximumSubjectCacheBytes == 0 && maximumConcurrentTransfers == 0;
        if (!absent) {
            WireValues.uuid(cursorKeyId);
            if (cursorKey == null || Base64.getUrlDecoder().decode(cursorKey).length != 32 || cursorLifetime == null
                    || cursorLifetime.isZero() || cursorLifetime.isNegative() || cursorLifetime.toMillis() < 1
                    || maximumChunkBytes < DEFAULT_LIMIT_BYTES || maximumChunkBytes > MAXIMUM_LIMIT_BYTES
                    || maximumSubjectCacheBytes < 1 || maximumTenantCacheBytes < maximumSubjectCacheBytes
                    || maximumCacheBytes < maximumTenantCacheBytes || maximumCacheEntries < 1
                    || maximumConcurrentTransfers < 1 || maximumConcurrentTransfers > maximumCacheEntries)
                throw new IllegalArgumentException("制品字节游标身份、独立AES-256密钥、期限和分片容量必须完整配置");
        }
    }

    void requireConfigured() {
        if (cursorKeyId == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    void requireLimit(int limitBytes) {
        requireConfigured();
        if (limitBytes < 1 || limitBytes > maximumChunkBytes) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
    }

    ObjectContentPaging paging(S3ObjectReader reader, ArtifactContentPageCache cache, ArtifactContentPageCache.Owner owner) {
        requireConfigured();
        return new ObjectContentPaging(reader, new SecretKeySpec(Base64.getUrlDecoder().decode(cursorKey), "AES"),
            cursorKeyId, cursorLifetime, maximumChunkBytes, java.util.Objects.requireNonNull(cache), java.util.Objects.requireNonNull(owner));
    }
}
