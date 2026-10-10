package com.ruoyi.medical.persistence;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.run-events")
public record RunEventPolicy(int sessionConnections, int maximumConnections, int readThreads, int readQueue,
        int bufferBytes, Duration heartbeat, Duration polling, Duration lease, String namespace) {
    public RunEventPolicy {
        Objects.requireNonNull(heartbeat); Objects.requireNonNull(polling); Objects.requireNonNull(lease); Objects.requireNonNull(namespace);
        if (sessionConnections < 1 || sessionConnections > 4 || maximumConnections < sessionConnections || readThreads < 1
                || readThreads > maximumConnections || readQueue < 1 || bufferBytes < 1024 || bufferBytes > 1048576
                || heartbeat.toMillis() < 1 || polling.toMillis() < 1 || polling.compareTo(heartbeat) > 0 || heartbeat.multipliedBy(2).compareTo(lease) >= 0
                || !namespace.matches("[a-z0-9:-]{1,128}")) throw new IllegalArgumentException("运行事件传输配置无效");
    }
}
