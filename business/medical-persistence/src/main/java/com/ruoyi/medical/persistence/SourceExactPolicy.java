package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-exact")
public record SourceExactPolicy(int maximumFields,int maximumMatches) {
    public SourceExactPolicy {
        if(maximumFields<0 || maximumFields>10000 || maximumMatches<0 || maximumMatches>100000)
            throw new IllegalArgumentException("EXACT字段或匹配容量无效");
    }
    void requireConfigured() {
        if(maximumFields==0 || maximumMatches==0) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
