package com.ruoyi.medical.persistence;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-upload-transport")
public record SourceUploadTransportPolicy(Path storageDirectory, String region, int tenantPermits, int userPermits,
        int segmentBytes, int demand, Duration maximumDuration, Duration admissionInterval, String namespace, String revision) {
    public SourceUploadTransportPolicy {
        com.ruoyi.medical.contracts.WireValues.uuid(revision);
        Objects.requireNonNull(storageDirectory); Objects.requireNonNull(region); Objects.requireNonNull(maximumDuration);
        Objects.requireNonNull(admissionInterval); Objects.requireNonNull(namespace); Objects.requireNonNull(revision);
        if (!storageDirectory.isAbsolute() || region.isBlank() || tenantPermits <= 0 || userPermits <= 0 || userPermits > 2
                || segmentBytes <= 0 || segmentBytes > S3ObjectReader.MAX_SEGMENT_BYTES || demand <= 0 || demand > 16
                || maximumDuration.isNegative() || maximumDuration.isZero() || maximumDuration.compareTo(Duration.ofSeconds(60)) > 0
                || admissionInterval.toMillis() < 1 || admissionInterval.compareTo(maximumDuration) >= 0
                || !namespace.matches("[a-z0-9:-]{1,256}") || revision.isBlank())
            throw new IllegalArgumentException("公开分段接收配置无效");
    }
}
