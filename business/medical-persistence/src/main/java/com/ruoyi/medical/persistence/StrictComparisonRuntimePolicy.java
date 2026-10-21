package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment capacity only; never grants domain access or approves a comparison rule. */
@ConfigurationProperties("medical.strict-comparison")
public record StrictComparisonRuntimePolicy(int maximumRequestBytes, int maximumCombinations, int maximumChunkCodepoints) {
    public StrictComparisonRuntimePolicy {
        if (maximumRequestBytes < 0 || maximumRequestBytes > 8388608 || maximumCombinations < 0 || maximumCombinations > 10000
                || maximumChunkCodepoints < 0 || maximumChunkCodepoints > 8388608) throw new IllegalArgumentException("INVALID_COMPARISON_CAPACITY");
    }
    void requireConfigured() { if (maximumRequestBytes == 0 || maximumCombinations == 0 || maximumChunkCodepoints == 0) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
}
