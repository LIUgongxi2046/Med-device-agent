package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.exports")
public record ExportExecutionPolicy(Path projectRoot, Path pythonExecutable, Path converterExecutable,
        String converterRevision, String converterVersion, String converterSha256, Duration timeout,
        int maximumBytes, String inputDataKind) {
    public ExportExecutionPolicy {
        Objects.requireNonNull(projectRoot); Objects.requireNonNull(pythonExecutable); Objects.requireNonNull(converterExecutable);
        Objects.requireNonNull(converterVersion); Objects.requireNonNull(timeout); Objects.requireNonNull(inputDataKind);
        WireValues.uuid(converterRevision); WireValues.hash(converterSha256);
        if (!projectRoot.isAbsolute() || !pythonExecutable.isAbsolute() || !converterExecutable.isAbsolute()
                || !Files.isDirectory(projectRoot) || !Files.isRegularFile(pythonExecutable) || !Files.isExecutable(pythonExecutable)
                || !Files.isRegularFile(converterExecutable) || !Files.isExecutable(converterExecutable)
                || converterVersion.isBlank() || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(10)) > 0 || maximumBytes < 1024 || maximumBytes > 1_000_000_000
                || !java.util.Set.of("SIMULATION", "REGISTERED").contains(inputDataKind))
            throw new IllegalArgumentException("导出执行配置无效");
    }
}
