package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Public BX-37 sequences only. Verified private spools never outlive their original cursor expiry. */
@Component
final class ArtifactContentPageCache implements AutoCloseable {
    record Owner(String tenantId, String subjectId) {
        Owner { WireValues.uuid(tenantId); WireValues.uuid(subjectId); }
    }
    private record Key(Owner owner, ObjectRef object, String authorization, Instant issued, Instant expires, int limit) { }
    private static final class Entry {
        final Key key;
        final long bytes;
        int readers = 1;
        boolean loading = true, invalid;
        FileChannel channel;
        Entry(Key key, long bytes) { this.key = key; this.bytes = bytes; }
    }
    private final Path directory;
    private final long maximumBytes, maximumTenantBytes, maximumSubjectBytes;
    private final int maximumEntries;
    private final Semaphore permits;
    private final ScheduledExecutorService cleanup;
    private final Map<Key,Entry> entries = new HashMap<>();
    private final Map<String,Long> tenantBytes = new HashMap<>();
    private final Map<Owner,Long> subjectBytes = new HashMap<>();
    private long reservedBytes;
    private boolean closed;

    @Autowired
    ArtifactContentPageCache(SourceUploadTransportPolicy storage, ArtifactContentReadPolicy policy) throws IOException {
        this(policy.cursorKeyId() == null ? null : storage.storageDirectory(), policy.maximumCacheBytes(),
            policy.maximumCacheEntries(), policy.maximumTenantCacheBytes(), policy.maximumSubjectCacheBytes(),
            policy.maximumConcurrentTransfers());
    }

    ArtifactContentPageCache(Path storage, long maximumBytes, int maximumEntries, long maximumTenantBytes,
            long maximumSubjectBytes, int maximumConcurrentTransfers) throws IOException {
        this.maximumBytes = maximumBytes; this.maximumEntries = maximumEntries;
        this.maximumTenantBytes = maximumTenantBytes; this.maximumSubjectBytes = maximumSubjectBytes;
        if (storage == null) { directory = null; cleanup = null; permits = new Semaphore(0); return; }
        if (maximumBytes < 1 || maximumEntries < 1 || maximumSubjectBytes < 1 || maximumTenantBytes < maximumSubjectBytes
                || maximumBytes < maximumTenantBytes || maximumConcurrentTransfers < 1 || maximumConcurrentTransfers > maximumEntries)
            throw new IllegalArgumentException("制品缓存容量和并发限制必须完整配置");
        var root = storage.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("制品缓存必须使用私有目录");
        directory = Files.createTempDirectory(root, "artifact-content-cache-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        permits = new Semaphore(maximumConcurrentTransfers);
        cleanup = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "artifact-content-cache-expiry"); thread.setDaemon(true); return thread;
        });
        cleanup.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.SECONDS);
    }

    byte[] read(S3ObjectReader reader, ObjectRef object, Owner owner, String authorization, Instant issued,
            Instant expires, int limit, long start, int length, S3ObjectReader.Admission admission) throws Exception {
        Objects.requireNonNull(reader); Objects.requireNonNull(object); Objects.requireNonNull(owner);
        Objects.requireNonNull(admission); WireValues.hash(authorization);
        long size = WireValues.revision(object.sizeBytes()).longValueExact();
        if (size < 1 || start < 0 || length < 1 || length > limit || start > size - length
                || !expires.isAfter(issued) || !expires.isAfter(Instant.now()))
            throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
        requireAdmission(admission);
        synchronized (this) { requireAvailable(); }
        if (!permits.tryAcquire()) throw new TenantAccessException(ErrorCode.RATE_LIMITED);
        Entry entry = null; boolean attached = false, success = false;
        try {
            var key = new Key(owner, object, authorization, issued, expires, limit);
            synchronized (this) {
                requireAvailable(); expire(); entry = entries.get(key);
                if (entry == null) {
                    // Never re-download for a continuation: restart/loss requires a new first-page request.
                    if (start != 0) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
                    reserve(owner, size); entry = new Entry(key, size); entries.put(key, entry);
                } else {
                    if (entry.loading) throw new TenantAccessException(ErrorCode.RATE_LIMITED);
                    if (entry.invalid) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
                    entry.readers++;
                }
                attached = true;
            }
            if (entry.loading) populate(entry, reader, admission);
            requireAdmission(admission);
            var buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining()) {
                if (entry.channel.read(buffer, start + buffer.position()) < 0) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            }
            requireAdmission(admission);
            synchronized (this) { requireAvailable(); }
            if (!expires.isAfter(Instant.now())) throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);
            success = true; return buffer.array();
        } finally {
            synchronized (this) {
                if (attached) {
                    if (!success) entry.invalid = true;
                    entry.readers--;
                    if (entry.readers == 0 && (entry.invalid || closed || !entry.key.expires().isAfter(Instant.now()))) remove(entry);
                }
                deleteClosedDirectory();
            }
            permits.release();
        }
    }

    private void populate(Entry entry, S3ObjectReader reader, S3ObjectReader.Admission admission) throws Exception {
        // Original S3 reader first verifies the entire immutable object. No page is published before both checks succeed.
        try (var verified = reader.readVerified(entry.key.object(), admission)) {
            var file = Files.createTempFile(directory, "verified-", ".content",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            FileChannel channel = null;
            try {
                channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE,
                    StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
                // An unlinked file cannot be replaced/tampered with through a later pathname or survive process exit.
                Files.deleteIfExists(file);
                var digest = MessageDigest.getInstance("SHA-256");
                for (long start = 0; start < entry.bytes;) {
                    int length = (int) Math.min(S3ObjectReader.MAX_SEGMENT_BYTES, entry.bytes - start);
                    var bytes = verified.chunk(start, length); digest.update(bytes);
                    var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
                    start += length; admission.requireCurrent();
                }
                if (channel.size() != entry.bytes || !entry.key.object().sha256().equals(HexFormat.of().formatHex(digest.digest())))
                    throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                channel.force(true); admission.requireCurrent();
                synchronized (this) { requireAvailable(); entry.channel = channel; entry.loading = false; }
                channel = null;
            } finally { if (channel != null) channel.close(); Files.deleteIfExists(file); }
        }
    }

    private void reserve(Owner owner, long bytes) {
        long tenant = tenantBytes.getOrDefault(owner.tenantId(), 0L), subject = subjectBytes.getOrDefault(owner, 0L);
        if (entries.size() >= maximumEntries || bytes > maximumBytes - reservedBytes
                || bytes > maximumTenantBytes - tenant || bytes > maximumSubjectBytes - subject)
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        reservedBytes += bytes; tenantBytes.put(owner.tenantId(), tenant + bytes); subjectBytes.put(owner, subject + bytes);
    }

    private static void requireAdmission(S3ObjectReader.Admission admission) {
        admission.requireCurrent();
        if (admission instanceof S3ObjectReader.TimedAdmission timed
                && Objects.requireNonNull(timed.remainingTime()).toMillis() < 1)
            throw new TenantAccessException(ErrorCode.LEASE_STALE);
    }

    private synchronized void expire() {
        for (var entry : java.util.List.copyOf(entries.values()))
            if (entry.readers == 0 && (closed || entry.invalid || !entry.key.expires().isAfter(Instant.now()))) remove(entry);
        deleteClosedDirectory();
    }

    private void remove(Entry entry) {
        if (!entries.remove(entry.key, entry)) return;
        try { if (entry.channel != null) entry.channel.close(); }
        catch (IOException failure) { closed = true; }
        reservedBytes -= entry.bytes;
        decrement(tenantBytes, entry.key.owner().tenantId(), entry.bytes); decrement(subjectBytes, entry.key.owner(), entry.bytes);
    }

    private static <T> void decrement(Map<T,Long> counts, T key, long bytes) {
        long remaining = counts.get(key) - bytes; if (remaining == 0) counts.remove(key); else counts.put(key, remaining);
    }

    private void requireAvailable() {
        if (directory == null || closed) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    synchronized long reservedBytes() { return reservedBytes; }

    @Override @PreDestroy public synchronized void close() {
        closed = true; if (cleanup != null) cleanup.shutdownNow(); expire();
    }

    private void deleteClosedDirectory() {
        if (closed && directory != null && entries.isEmpty()) {
            try { Files.deleteIfExists(directory); }
            catch (IOException failure) { /* No readable files remain; a later close retries directory cleanup. */ }
        }
    }
}
