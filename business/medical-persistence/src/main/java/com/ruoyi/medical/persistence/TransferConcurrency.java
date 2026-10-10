package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.redisson.RedissonObject;
import org.redisson.api.NameMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

final class TransferConcurrency {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RedissonClient client;
    private final String prefix;
    private final NameMapper names;

    TransferConcurrency(RedissonClient client, String prefix) throws IOException {
        this.client = Objects.requireNonNull(client); this.prefix = Objects.requireNonNull(prefix);
        if (!prefix.matches("[a-z0-9:-]{1,256}")) invalid();
        // 已发送结果未知的取得操作不能自动重试。
        names = new RetryConfiguration(client.getConfig()).requireZeroRetries();
    }

    Lease acquire(String tenantId, String userId, Limits limits, S3ObjectReader.Admission admission) throws Exception {
        Objects.requireNonNull(admission).requireCurrent(); WireValues.uuid(tenantId); WireValues.uuid(userId); Objects.requireNonNull(limits);
        long started = System.nanoTime();
        var tenant = scope("tenant", tenantId, limits.tenantPermits(), limits);
        var user = scope("user", userId, limits.userPermits(), limits);
        admission.requireCurrent();
        String tenantPermit = tenant.semaphore().tryAcquire(0, remaining(started, limits), TimeUnit.MILLISECONDS);
        if (tenantPermit == null) throw new TenantAccessException(ErrorCode.RATE_LIMITED);
        boolean complete = false; String userPermit = null;
        try {
            admission.requireCurrent(); tenant.requireCurrent(); user.requireCurrent();
            userPermit = user.semaphore().tryAcquire(0, remaining(started, limits), TimeUnit.MILLISECONDS);
            if (userPermit == null) throw new TenantAccessException(ErrorCode.RATE_LIMITED);
            var lease = new Lease(tenant, user, tenantPermit, userPermit, limits, started, admission,
                permitState(tenant.semaphore(), tenantPermit), permitState(user.semaphore(), userPermit));
            lease.requireCurrent(); complete = true; return lease;
        } finally {
            if (!complete) {
                try { tenant.semaphore().tryRelease(tenantPermit); }
                finally { if (userPermit != null) user.semaphore().tryRelease(userPermit); }
            }
        }
    }

    PartLease tryPart(String tenantId, String uploadId, long partNo, Limits limits) throws InterruptedException {
        WireValues.uuid(tenantId); WireValues.uuid(uploadId); Objects.requireNonNull(limits);
        if (partNo < 1 || partNo > 4294967295L) invalid();
        long started = System.nanoTime();
        var semaphore = client.getPermitExpirableSemaphore(prefix + ":part:" + tenantId + ":" + uploadId + ":" + partNo);
        semaphore.trySetPermits(1);
        if (semaphore.getPermits() != 1) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        String permit = semaphore.tryAcquire(0, remaining(started, limits), TimeUnit.MILLISECONDS);
        boolean complete = false;
        try {
            // 容量键在最后一次准入的全部租期之后过期，周期核查不延长原接收期限。
            if (!semaphore.expire(limits.maximumDuration().multipliedBy(2))) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            if (permit == null) { complete = true; return null; }
            var lease = new PartLease(semaphore, permit, limits, started, permitState(semaphore, permit));
            lease.requireCurrent(); complete = true; return lease;
        } finally { if (!complete && permit != null) semaphore.tryRelease(permit); }
    }

    static final class PartLease implements AutoCloseable, S3ObjectReader.Admission {
        private final RPermitExpirableSemaphore semaphore;
        private final String permit;
        private final Limits limits;
        private final long started;
        private final PermitState state;
        private volatile boolean closed;

        private PartLease(RPermitExpirableSemaphore semaphore, String permit, Limits limits, long started, PermitState state) {
            this.semaphore = semaphore; this.permit = permit; this.limits = limits; this.started = started; this.state = state;
        }
        @Override public void requireCurrent() {
            if (closed) throw new TenantAccessException(ErrorCode.LEASE_STALE);
            remaining(started, limits);
            if (semaphore.getPermits() != 1) throw new TenantAccessException(ErrorCode.LEASE_STALE);
            state.requireCurrent();
            remaining(started, limits);
        }
        @Override public void close() {
            if (closed) return;
            semaphore.tryRelease(permit); closed = true;
        }
    }

    private PermitState permitState(RPermitExpirableSemaphore semaphore, String permit) {
        // 固定Redisson版本的许可期限采用原二进制身份；只读取，不续租或修改容量。
        String raw = RedissonObject.suffixName(((RedissonObject) semaphore).getRawName(), "timeout");
        RScoredSortedSet<byte[]> deadlines = client.getScoredSortedSet(names.unmap(raw), ByteArrayCodec.INSTANCE);
        if (!raw.equals(((RedissonObject) deadlines).getRawName())) invalid();
        return new PermitState(deadlines, HexFormat.of().parseHex(permit));
    }

    private record PermitState(RScoredSortedSet<byte[]> deadlines, byte[] id) {
        void requireCurrent() {
            Double deadline = deadlines.getScore(id);
            if (deadline == null || !Double.isFinite(deadline) || deadline <= System.currentTimeMillis())
                throw new TenantAccessException(ErrorCode.LEASE_STALE);
        }
    }

    private Scope scope(String kind, String id, int permits, Limits limits) throws IOException {
        String key = prefix + ":" + kind + ":" + id;
        var policy = JSON.createObjectNode(); policy.put("revision", limits.revision()); policy.put("permits", permits);
        policy.put("maximum_duration_ms", limits.maximumDuration().toMillis());
        String expected = JSON.writeValueAsString(policy);
        RBucket<String> stored = client.getBucket(key + ":policy", StringCodec.INSTANCE);
        stored.compareAndSet(null, expected);
        if (!expected.equals(stored.get())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var semaphore = client.getPermitExpirableSemaphore(key + ":permits");
        // 配置版本不进入容量键；版本变化不能创建第二组名额。
        semaphore.trySetPermits(permits);
        var result = new Scope(stored, expected, semaphore, permits); result.requireCurrent(); return result;
    }

    private static long remaining(long started, Limits limits) {
        long left = limits.maximumDuration().toNanos() - (System.nanoTime() - started);
        if (left <= 0) throw new TenantAccessException(ErrorCode.LEASE_STALE);
        long millis = TimeUnit.NANOSECONDS.toMillis(left);
        if (millis <= 0) throw new TenantAccessException(ErrorCode.LEASE_STALE);
        return millis;
    }
    private static void invalid() { throw new TenantAccessException(ErrorCode.SCHEMA_INVALID); }

    private static final class RetryConfiguration extends Config {
        private RetryConfiguration(Config original) { super(original); }
        private NameMapper requireZeroRetries() {
            int configured = 0; NameMapper names = null;
            for (var server : Arrays.asList(getSingleServerConfig(), getClusterServersConfig(), getSentinelServersConfig(),
                    getMasterSlaveServersConfig(), getReplicatedServersConfig())) {
                if (server != null) {
                    configured++;
                    if (server.getRetryAttempts() != 0) invalid();
                    // 当前许可和配置必须从主节点读取，不能接受副本上的旧身份。
                    if (server instanceof BaseMasterSlaveServersConfig<?> replicated && replicated.getReadMode() != ReadMode.MASTER) invalid();
                    names = server.getNameMapper();
                }
            }
            if (configured != 1) invalid();
            return Objects.requireNonNull(names);
        }
    }

    record Limits(String revision, int tenantPermits, int userPermits, Duration maximumDuration) {
        Limits {
            WireValues.uuid(revision); Objects.requireNonNull(maximumDuration);
            if (tenantPermits < 1 || userPermits < 1 || maximumDuration.isNegative() || maximumDuration.toMillis() < 1) invalid();
            maximumDuration.toNanos();
        }
    }

    private record Scope(RBucket<String> policy, String expected, RPermitExpirableSemaphore semaphore, int permits) {
        void requireCurrent() {
            if (!expected.equals(policy.get()) || semaphore.getPermits() != permits) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
    }

    static final class Lease implements AutoCloseable, S3ObjectReader.TimedAdmission {
        private final Scope tenant, user;
        private final String tenantPermit, userPermit;
        private final Limits limits;
        private final long started;
        private final S3ObjectReader.Admission admission;
        private final PermitState tenantState, userState;
        private volatile boolean closed;

        private Lease(Scope tenant, Scope user, String tenantPermit, String userPermit, Limits limits,
                long started, S3ObjectReader.Admission admission, PermitState tenantState, PermitState userState) {
            this.tenant = tenant; this.user = user; this.tenantPermit = tenantPermit; this.userPermit = userPermit;
            this.limits = limits; this.started = started; this.admission = admission;
            this.tenantState = tenantState; this.userState = userState;
        }

        @Override public void requireCurrent() {
            if (closed) throw new TenantAccessException(ErrorCode.LEASE_STALE);
            remaining(started, limits); admission.requireCurrent(); remaining(started, limits);
            tenant.requireCurrent(); remaining(started, limits); user.requireCurrent(); remaining(started, limits);
            tenantState.requireCurrent();
            remaining(started, limits);
            userState.requireCurrent();
            remaining(started, limits);
        }

        @Override public Duration remainingTime() {
            requireCurrent(); return Duration.ofMillis(remaining(started, limits));
        }

        S3ObjectReader.TimedAdmission guard(S3ObjectReader.Admission stage) {
            Objects.requireNonNull(stage);
            return new S3ObjectReader.TimedAdmission() {
                @Override public void requireCurrent() {
                    // 领域准入也消耗原租期，返回后必须再次核查原名额。
                    Lease.this.requireCurrent(); stage.requireCurrent(); Lease.this.requireCurrent();
                }
                @Override public Duration remainingTime() {
                    requireCurrent(); return Lease.this.remainingTime();
                }
            };
        }

        @Override public void close() {
            if (closed) return;
            // 到期名额已由Redis释放；清理只使用本次原permit_id，不增加容量。
            try { tenant.semaphore().tryRelease(tenantPermit); }
            finally { user.semaphore().tryRelease(userPermit); }
            closed = true;
        }
    }
}
