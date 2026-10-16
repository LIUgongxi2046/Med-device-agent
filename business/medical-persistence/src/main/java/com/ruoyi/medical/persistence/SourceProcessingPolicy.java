package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-processing")
public record SourceProcessingPolicy(String revision, List<String> tenantIds, Path projectRoot, Path pythonExecutable,
        String pythonSha256, Duration polling, Duration lease, Duration heartbeat, Duration timeout,
        int maximumSourceBytes, int maximumBundleBytes, String environment, String region, List<Binding> bindings,
        List<CodeFile> sourceFiles) {
    public SourceProcessingPolicy {
        WireValues.uuid(revision); WireValues.hash(pythonSha256);
        tenantIds=List.copyOf(tenantIds); bindings=List.copyOf(bindings); sourceFiles=List.copyOf(sourceFiles);
        if(tenantIds.isEmpty() || tenantIds.size()>100 || tenantIds.stream().distinct().count()!=tenantIds.size()
                || bindings.isEmpty() || bindings.size()>100 || bindings.stream().map(Binding::configRevision).distinct().count()!=bindings.size()
                || sourceFiles.size()<4 || sourceFiles.size()>1000
                || sourceFiles.stream().map(CodeFile::path).distinct().count()!=sourceFiles.size())
            throw new IllegalArgumentException("来源处理企业与配置范围无效");
        tenantIds.forEach(WireValues::uuid);
        Objects.requireNonNull(projectRoot); Objects.requireNonNull(pythonExecutable);
        Objects.requireNonNull(polling); Objects.requireNonNull(lease); Objects.requireNonNull(heartbeat); Objects.requireNonNull(timeout);
        if(!projectRoot.isAbsolute() || !Files.isDirectory(projectRoot) || !pythonExecutable.isAbsolute()
                || !Files.isRegularFile(pythonExecutable) || !Files.isExecutable(pythonExecutable)
                || polling.compareTo(Duration.ofMillis(1000))<0 || polling.compareTo(Duration.ofMinutes(1))>0
                || lease.compareTo(Duration.ofSeconds(45))<0 || lease.compareTo(Duration.ofMinutes(2))>0
                || heartbeat.compareTo(Duration.ofSeconds(5))<0 || heartbeat.multipliedBy(3).compareTo(lease)>0
                || timeout.compareTo(Duration.ofMinutes(3))<0 || timeout.compareTo(Duration.ofMinutes(15))>0
                || maximumSourceBytes<=0 || maximumSourceBytes>104857600
                || maximumBundleBytes<=0 || maximumBundleBytes>268435456
                || environment==null || environment.isBlank() || region==null || region.isBlank())
            throw new IllegalArgumentException("来源处理容量、执行路径与期限无效");
    }

    public record CodeFile(String path,String sha256) {
        public CodeFile {
            WireValues.hash(sha256);
            if(path==null || !(path.startsWith("document/src/medical_document/") || path.startsWith("contracts/python/src/medical_contracts/"))
                    || !path.endsWith(".py") || !Path.of(path).normalize().toString().equals(path))
                throw new IllegalArgumentException("来源处理制品路径无效");
        }
    }

    public record Binding(String configRevision, String configSha256, Path scannerReleasePath, String scannerReleaseSha256,
            String artifactDigest, String indexConfigRevision) {
        public Binding {
            WireValues.uuid(configRevision); WireValues.hash(configSha256); WireValues.hash(scannerReleaseSha256); WireValues.hash(artifactDigest);
            if(indexConfigRevision!=null) WireValues.uuid(indexConfigRevision);
            if(scannerReleasePath==null || !scannerReleasePath.isAbsolute() || !Files.isRegularFile(scannerReleasePath))
                throw new IllegalArgumentException("来源扫描发布配置路径无效");
        }
    }

    Binding binding(String revision) {
        return bindings.stream().filter(item -> item.configRevision().equals(revision)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("来源处理配置没有登记到当前 Worker"));
    }
}
