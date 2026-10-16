package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-index")
public record SourceIndexPolicy(Duration polling,Duration lease,Duration timeout,int maximumChunks,
        int maximumBundleBytes,int ngramTokenSize,String environment,String region,List<Binding> bindings) {
    public SourceIndexPolicy {
        Objects.requireNonNull(polling); Objects.requireNonNull(lease); Objects.requireNonNull(timeout);
        ProjectSchema.text(environment,128); ProjectSchema.text(region,128); bindings=List.copyOf(bindings);
        if(polling.compareTo(Duration.ofSeconds(1))<0 || polling.compareTo(Duration.ofMinutes(1))>0
                || lease.compareTo(Duration.ofSeconds(30))<0 || lease.compareTo(Duration.ofMinutes(2))>0
                || timeout.compareTo(Duration.ofMinutes(5))<0 || timeout.compareTo(Duration.ofMinutes(30))>0
                || maximumChunks<1 || maximumChunks>100000 || maximumBundleBytes<1 || maximumBundleBytes>268435456
                || ngramTokenSize<1 || ngramTokenSize>10 || bindings.isEmpty() || bindings.size()>100
                || bindings.stream().map(Binding::configRevision).distinct().count()!=bindings.size())
            throw new IllegalArgumentException("全文索引的配置、容量与执行期限无效");
    }
    public record Binding(String configRevision,String configSha256,String artifactDigest) {
        public Binding { WireValues.uuid(configRevision); WireValues.hash(configSha256); WireValues.hash(artifactDigest); }
    }
    Binding binding(String revision) {
        return bindings.stream().filter(item -> item.configRevision().equals(revision)).findFirst()
            .orElseThrow(() -> new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.OBJECT_UNAVAILABLE));
    }
}
