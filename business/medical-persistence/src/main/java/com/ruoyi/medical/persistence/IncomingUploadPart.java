package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.ProxyInputStream;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

final class IncomingUploadPart implements AutoCloseable {
    private final Path directory, content;
    private final PosixFileAttributes directoryIdentity, contentIdentity;
    private final long size;
    private final String sha256;
    private final S3ObjectReader.Admission admission;
    private boolean closed;

    private IncomingUploadPart(Path directory, Path content, PosixFileAttributes directoryIdentity,
            PosixFileAttributes contentIdentity, long size, String sha256, S3ObjectReader.Admission admission) {
        this.directory = directory; this.content = content; this.directoryIdentity = directoryIdentity;
        this.contentIdentity = contentIdentity; this.size = size; this.sha256 = sha256; this.admission = admission;
    }

    static IncomingUploadPart captureRequest(Path directory, ServerHttpRequest request, long size, String sha256,
            int segmentBytes, int demand, S3ObjectReader.TimedAdmission admission, Scheduler deadlines,
            Duration admissionInterval, Scheduler checks, Mono<Void> cancellation) throws IOException {
        if (Schedulers.isInNonBlockingThread()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        Objects.requireNonNull(admission).requireCurrent(); Objects.requireNonNull(request); WireValues.hash(sha256);
        Objects.requireNonNull(deadlines); Objects.requireNonNull(checks); Objects.requireNonNull(admissionInterval); Objects.requireNonNull(cancellation);
        if (checks == deadlines || admissionInterval.isNegative() || admissionInterval.toMillis() < 1)
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        admissionInterval.toNanos();
        if (size <= 0 || segmentBytes <= 0 || demand <= 0
                || (long) segmentBytes * ((long) demand + 2) > UploadTransferProtocol.PART_BYTES.longValueExact())
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (size > UploadTransferProtocol.PART_BYTES.longValueExact()) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (request.getHeaders().getContentLength() != size) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var remaining = admission.remainingTime();
        if (remaining.isNegative() || remaining.toMillis() < 1) throw new TenantAccessException(ErrorCode.LEASE_STALE);
        // 单次截止持续使用原剩余量，收到新的片段不会重新计时。
        var expiry = Mono.delay(remaining, deadlines).then(Mono.error(new TenantAccessException(ErrorCode.LEASE_STALE)));
        // 等待输入时仍核查原准入；阻塞查询使用独立线程，检查队列保持有界。
        var current = Mono.fromRunnable(() -> {
            if (Schedulers.isInNonBlockingThread()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
            admission.requireCurrent();
        }).subscribeOn(checks).repeatWhen(completed -> completed.delayElements(admissionInterval, deadlines)).then();
        var body = request.getBody().<DataBuffer>handle((buffer, sink) -> {
            boolean forwarded = false;
            try {
                if (buffer.capacity() > segmentBytes || buffer.readableByteCount() > segmentBytes)
                    throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
                sink.next(buffer); forwarded = true;
            } finally { if (!forwarded) DataBufferUtils.release(buffer); }
        }).takeUntilOther(Mono.firstWithSignal(expiry, current, cancellation)).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        try (var source = DataBufferUtils.subscriberInputStream(body, demand)) {
            return capture(directory, source, size, sha256, segmentBytes, admission);
        }
    }

    static IncomingUploadPart capture(Path directory, InputStream source, long size, String sha256,
            int segmentBytes, S3ObjectReader.Admission admission) throws IOException {
        Objects.requireNonNull(admission).requireCurrent(); Objects.requireNonNull(source); WireValues.hash(sha256);
        if (size <= 0 || segmentBytes <= 0 || segmentBytes > UploadTransferProtocol.PART_BYTES.intValueExact())
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (size > UploadTransferProtocol.PART_BYTES.longValueExact()) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        Path actual = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        var directoryIdentity = attributes(actual);
        requireDirectory(actual, directoryIdentity);
        Path content = Files.createTempFile(actual, "upload-input-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        boolean complete = false;
        try {
            var saved = new IncomingUploadPart(actual, content, directoryIdentity, attributes(content), size, sha256, admission);
            saved.requireCurrent();
            var bounded = BoundedInputStream.builder().setInputStream(source).setMaxCount(size + 1).setPropagateClose(false).get();
            var guarded = new ProxyInputStream(bounded) {
                private long received;
                @Override protected void beforeRead(int count) throws IOException { saved.requireCurrent(); }
                @Override protected void afterRead(int count) throws IOException {
                    saved.requireCurrent();
                    if (count > 0) received += count;
                    if (received > size) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                }
            };
            var digest = DigestUtils.getSha256Digest();
            // 输入流由HTTP适配器关闭；本组件只管理自己创建的私有文件。
            try (var target = Files.newOutputStream(content, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                long copied = IOUtils.copyLarge(new DigestInputStream(guarded, digest), target, new byte[segmentBytes]);
                if (copied != size || !sha256.equals(HexFormat.of().formatHex(digest.digest())))
                    throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            }
            saved.path(); complete = true; return saved;
        } finally { if (!complete) Files.delete(content); }
    }

    Path path() throws IOException {
        requireCurrent();
        if (Files.size(content) != size) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        try (var input = Files.newInputStream(content, LinkOption.NOFOLLOW_LINKS);
                var bounded = BoundedInputStream.builder().setInputStream(input).setMaxCount(size + 1).get()) {
            if (!sha256.equals(DigestUtils.sha256Hex(bounded)) || bounded.getCount() != size)
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
        requireCurrent(); return content;
    }

    private void requireCurrent() throws IOException {
        if (closed) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        admission.requireCurrent(); requireDirectory(directory, directoryIdentity);
        var current = attributes(content);
        if (!current.isRegularFile() || !sameIdentity(contentIdentity, current)
                || !current.owner().equals(directoryIdentity.owner())
                || !current.permissions().equals(PosixFilePermissions.fromString("rw-------"))) denied();
    }

    private static void requireDirectory(Path directory, PosixFileAttributes original) throws IOException {
        var current = attributes(directory);
        if (!current.isDirectory() || !sameIdentity(original, current)
                || !current.permissions().equals(PosixFilePermissions.fromString("rwx------"))
                || !directory.toRealPath().equals(directory)) denied();
    }
    private static PosixFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
    private static boolean sameIdentity(PosixFileAttributes original, PosixFileAttributes current) {
        return original.fileKey() != null && original.fileKey().equals(current.fileKey()) && original.owner().equals(current.owner());
    }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }

    @Override public void close() throws IOException {
        if (closed) return;
        if (!sameIdentity(contentIdentity, attributes(content))) denied();
        Files.delete(content); closed = true;
    }
}
