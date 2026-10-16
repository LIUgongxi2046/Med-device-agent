package com.ruoyi.medical.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.retrievals")
public record RetrievalPolicy(String environment, String region, int maximumResults) {
    public RetrievalPolicy {
        ProjectSchema.text(environment, 128); ProjectSchema.text(region, 128);
        if (maximumResults <= 0 || maximumResults > 500) throw new IllegalArgumentException("RETRIEVAL_RESULT_LIMIT_REQUIRED");
    }
}
