package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-upload-status")
public record SourceUploadStatusPolicy(int maximumReviews) {
    public SourceUploadStatusPolicy {
        if(maximumReviews<0 || maximumReviews>1000) throw new IllegalArgumentException("上传状态读取容量无效");
    }
    void requireConfigured() { if(maximumReviews==0) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
}
