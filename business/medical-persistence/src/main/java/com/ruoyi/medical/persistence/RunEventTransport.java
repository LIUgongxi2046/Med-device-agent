package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.Redisson;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
@EnableConfigurationProperties(RunEventPolicy.class)
final class RunEventTransport {
    private final RunEventService events;
    private final RunEventPolicy policy;
    private final RedissonClient redis;
    private final Scheduler reading;
    private final PermissionSchema json;

    RunEventTransport(RunEventService events, RunEventPolicy policy, RedisProperties settings, PermissionSchema json) {
        this.events = events; this.policy = policy; this.json = json;
        var config = new Config(); config.setThreads(2).setNettyThreads(2);
        config.useSingleServer().setAddress("redis://" + settings.getHost() + ":" + settings.getPort()).setPassword(settings.getPassword())
            .setDatabase(settings.getDatabase()).setRetryAttempts(0).setConnectTimeout(3000).setTimeout(3000).setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);
        redis = Redisson.create(config); reading = Schedulers.newBoundedElastic(policy.readThreads(), policy.readQueue(), "medical-run-events");
    }

    Flux<ServerSentEvent<JsonNode>> subscribe(ApplicationSessionIdentity.Verified subject, String context, String runId, String after,
            org.springframework.security.core.Authentication authentication) {
        var security = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext(); security.setAuthentication(authentication);
        return Flux.usingWhen(Mono.fromCallable(new org.springframework.security.concurrent.DelegatingSecurityContextCallable<>(
                () -> acquire(subject, context, runId, after), security)).subscribeOn(reading),
            lease -> {
                var cursor = new AtomicReference<>(after); var heartbeat = new java.util.concurrent.atomic.AtomicLong(0);
                return Flux.defer(() -> Mono.delay(policy.polling()).then(Mono.fromCallable(
                    new org.springframework.security.concurrent.DelegatingSecurityContextCallable<ServerSentEvent<JsonNode>>(() -> {
                        lease.renew(); var batch = events.read(subject, context, runId, cursor.get());
                        if (!batch.events().isEmpty()) {
                            // 每帧只选择新准入事务中的首项，后续帧重新读取当前许可和同一持久目录。
                            var event = batch.events().get(0);
                            if (json.canonical(event).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > policy.bufferBytes())
                                throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
                            cursor.set(event.required("event_id").textValue()); heartbeat.set(System.nanoTime());
                            return ServerSentEvent.<JsonNode>builder(event).id(cursor.get()).event(event.required("event_type").textValue()).build();
                        }
                        if (System.nanoTime() - heartbeat.get() >= policy.heartbeat().toNanos()) {
                            heartbeat.set(System.nanoTime()); return ServerSentEvent.<JsonNode>builder().comment(" heartbeat").build();
                        }
                        return null;
                    }, security)).subscribeOn(reading))).repeat();
            }, lease -> Mono.fromRunnable(lease::close).subscribeOn(reading));
    }

    private Lease acquire(ApplicationSessionIdentity.Verified subject, String context, String runId, String after) throws InterruptedException {
        events.read(subject, context, runId, after);
        var global = semaphore(policy.namespace() + ":global", policy.maximumConnections());
        var session = semaphore(policy.namespace() + ":session:" + DigestUtils.sha256Hex(subject.sessionId()), policy.sessionConnections());
        String first = global.tryAcquire(0, policy.lease().toMillis(), TimeUnit.MILLISECONDS); if (first == null) denied();
        boolean complete = false;
        try {
            String second = session.tryAcquire(0, policy.lease().toMillis(), TimeUnit.MILLISECONDS); if (second == null) denied();
            var lease = new Lease(global, session, first, second); complete = true; return lease;
        } finally { if (!complete) global.tryRelease(first); }
    }

    private RPermitExpirableSemaphore semaphore(String name, int permits) {
        var result = redis.getPermitExpirableSemaphore(name); result.trySetPermits(permits);
        if (result.getPermits() != permits) throw new TenantAccessException(ErrorCode.SCOPE_STALE); return result;
    }
    private static void denied() { throw new TenantAccessException(ErrorCode.RATE_LIMITED); }
    private final class Lease implements AutoCloseable {
        private final RPermitExpirableSemaphore global, session;
        private final String first, second;
        private Lease(RPermitExpirableSemaphore global, RPermitExpirableSemaphore session, String first, String second) {
            this.global = global; this.session = session; this.first = first; this.second = second;
        }
        void renew() {
            if (!global.updateLeaseTime(first, policy.lease().toMillis(), TimeUnit.MILLISECONDS)
                    || !session.updateLeaseTime(second, policy.lease().toMillis(), TimeUnit.MILLISECONDS)) throw new TenantAccessException(ErrorCode.LEASE_STALE);
        }
        @Override public void close() { try { global.tryRelease(first); } finally { session.tryRelease(second); } }
    }
    @PreDestroy void close() { reading.dispose(); redis.shutdown(); }
}
