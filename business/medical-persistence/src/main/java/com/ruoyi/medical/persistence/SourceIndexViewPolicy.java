package com.ruoyi.medical.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-index-views")
public record SourceIndexViewPolicy(int maximumBuilds) {
    public SourceIndexViewPolicy {
        if(maximumBuilds<0 || maximumBuilds>1000) throw new IllegalArgumentException("索引状态读取容量无效");
    }
    void requireConfigured() {
        if(maximumBuilds==0) throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
