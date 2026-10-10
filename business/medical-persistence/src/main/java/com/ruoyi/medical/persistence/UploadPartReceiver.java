package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.UploadTransferProtocol.PartRow;
import com.ruoyi.medical.persistence.UploadTransferProtocol.SessionRow;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

final class UploadPartReceiver {
    interface Authority {
        String require(EnterpriseAccess.Admission admission, String uploadId, boolean writing) throws Exception;
        void stored(EnterpriseAccess.Admission admission, SessionRow previous, PartRow saved) throws Exception;
    }
    private final EnterpriseAccess access;
    private final UploadTransferStore uploads;
    private final UploadTransferMapper mapper;
    private final Authority authority;

    UploadPartReceiver(EnterpriseAccess access, UploadTransferStore uploads, UploadTransferMapper mapper, Authority authority) {
        this.access = Objects.requireNonNull(access); this.uploads = Objects.requireNonNull(uploads);
        this.mapper = Objects.requireNonNull(mapper); this.authority = Objects.requireNonNull(authority);
    }

    ObjectRef receiveStream(ApplicationSessionIdentity.Verified subject, String context, String uploadId, long partNo, String sha256,
            long length, InputStream content, Path directory, int segmentBytes, S3ObjectWriter writer,
            TransferConcurrency concurrency, TransferConcurrency.Limits limits) throws Exception {
        outside(subject); WireValues.revision(context); WireValues.uuid(uploadId); WireValues.hash(sha256);
        var initial = inspect(subject, context, uploadId, partNo, sha256);
        long expected = UploadTransferStore.range(initial.session().expectedSize(), partNo).size().longValueExact();
        if (length != expected) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        ready(initial);
        try (var lease = concurrency.acquire(initial.session().tenantId(), subject.user().user_id(), limits,
                () -> currentIncoming(subject, context, uploadId, partNo, sha256, initial));
                var saved = IncomingUploadPart.capture(directory, content, expected, sha256, segmentBytes, lease)) {
            var result = receive(subject, uploadId, partNo, sha256, saved.path(), writer, lease);
            lease.requireCurrent(); return result;
        }
    }

    Mono<ObjectRef> receiveRequest(ApplicationSessionIdentity.Verified subject, String context, String uploadId, long partNo, String sha256,
            ServerHttpRequest request, Path directory, int segmentBytes, int demand, S3ObjectWriter writer,
            TransferConcurrency concurrency, TransferConcurrency.Limits limits, Scheduler receiver, Scheduler deadlines,
            Duration admissionInterval, Scheduler checks) {
        Objects.requireNonNull(receiver); Objects.requireNonNull(deadlines); Objects.requireNonNull(checks);
        Objects.requireNonNull(request); Objects.requireNonNull(writer); Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        if (receiver == deadlines || receiver == checks || deadlines == checks || admissionInterval.isNegative() || admissionInterval.toMillis() < 1)
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        admissionInterval.toNanos();
        return Mono.defer(() -> {
            var cancelled = reactor.core.publisher.Sinks.<Void>empty();
            return Mono.<ObjectRef, Reception>using(() -> {
                if (Schedulers.isInNonBlockingThread()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
                outside(subject); WireValues.revision(context); WireValues.uuid(uploadId); WireValues.hash(sha256);
                var initial = inspect(subject, context, uploadId, partNo, sha256);
                long expected = UploadTransferStore.range(initial.session().expectedSize(), partNo).size().longValueExact();
                if (request.getHeaders().getContentLength() != expected) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                ready(initial);
                // 准入不能等待接收线程；容量耗尽时立即报告，原租期从取得名额开始。
                return new Reception(expected, concurrency.acquire(initial.session().tenantId(), subject.user().user_id(), limits,
                    () -> currentIncoming(subject, context, uploadId, partNo, sha256, initial)));
            }, reception -> Mono.fromCallable(() -> {
                if (Schedulers.isInNonBlockingThread()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
                var lease = reception.lease(); lease.requireCurrent();
                try (var saved = IncomingUploadPart.captureRequest(directory, request, reception.expected(), sha256, segmentBytes, demand,
                        lease, deadlines, admissionInterval, checks, cancelled.asMono())) {
                    var result = receive(subject, uploadId, partNo, sha256, saved.path(), writer, lease);
                    lease.requireCurrent(); return result;
                }
            }).subscribeOn(receiver), reception -> reception.lease().close(), true).subscribeOn(checks)
                // 取消必须终止实际输入订阅，唤醒等待字节的InputStream并执行资源清理。
                .doOnCancel(() -> cancelled.emitError(new TenantAccessException(ErrorCode.ACTION_STALE), reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST));
        });
    }

    ObjectRef receive(ApplicationSessionIdentity.Verified subject, String uploadId, long partNo, String sha256, Path content,
            S3ObjectWriter writer, TransferConcurrency.Lease lease) throws Exception {
        outside(subject); WireValues.uuid(uploadId); WireValues.hash(sha256); lease.requireCurrent();
        var initial = transaction(subject, admission -> inspect(admission, uploadId, partNo, sha256));
        verify(content, UploadTransferStore.range(initial.session().expectedSize(), partNo).size(), sha256);
        lease.requireCurrent();
        if (initial.part() != null && "STORED".equals(initial.part().state())) return reference(initial.part());
        if (initial.part() != null && "WRITING".equals(initial.part().state())) return recoverHeld(subject, initial, writer, lease);
        var work = transaction(subject, admission -> {
            var current = active(admission, uploadId, partNo, sha256); same(initial.authorization(), current.authorization());
            var row = current.part();
            if (row != null && "STORED".equals(row.state())) return current;
            if (row != null && !"RESERVED".equals(row.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            if (row == null) {
                var session = current.session(); var range = UploadTransferStore.range(session.expectedSize(), partNo);
                String write = UUID.randomUUID().toString(), key = "tenants/" + session.tenantId() + "/uploads/" + uploadId + "/parts/" + partNo + "/" + write;
                var now = mapper.databaseTime(Map.of("userId", admission.subject().user().user_id(), "uploadId", uploadId));
                row = uploads.reserve(admission, new PartRow(session.tenantId(), session.projectId(), uploadId, partNo, write,
                    session.expectedSize(), session.maxPartBytes(), range.start(), range.end(), range.size(), sha256, key,
                    WireValues.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RESERVED", null, null,
                    session.schemaVersion(), session.schemaManifestSha256(), session.createdBy(), now, session.classification(), BigInteger.ONE, now));
            }
            return new Admission(current.session(), uploads.beginPart(admission, uploadId, partNo, row.revision().toString(), row.writeId()), current.authorization());
        });
        if ("STORED".equals(work.part().state())) return reference(work.part());
        var actual = writer.writeVerified(intent(work.part()), content, lease.guard(() -> currentWriting(subject, work)));
        lease.requireCurrent(); return store(subject, work, actual);
    }

    ObjectRef recover(ApplicationSessionIdentity.Verified subject, String uploadId, long partNo, String sha256,
            S3ObjectWriter writer, TransferConcurrency concurrency, TransferConcurrency.Limits limits) throws Exception {
        outside(subject); WireValues.uuid(uploadId); WireValues.hash(sha256);
        var work = transaction(subject, admission -> inspect(admission, uploadId, partNo, sha256));
        if (work.part() == null) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if ("STORED".equals(work.part().state())) return reference(work.part());
        if (!"WRITING".equals(work.part().state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        try (var lease = concurrency.acquire(work.session().tenantId(), subject.user().user_id(), limits, () -> currentWriting(subject, work))) {
            return recoverHeld(subject, work, writer, lease);
        }
    }

    private ObjectRef recoverHeld(ApplicationSessionIdentity.Verified subject, Admission work, S3ObjectWriter writer,
            TransferConcurrency.Lease lease) throws Exception {
        // 原响应未知时只核查原write_id与固定对象，沿用本次实际接收的名额和期限。
        var actual = writer.recoverVerified(intent(work.part()), lease.guard(() -> currentWriting(subject, work)));
        lease.requireCurrent(); return store(subject, work, actual);
    }

    private Admission inspect(ApplicationSessionIdentity.Verified subject, String context, String id, long part, String sha) {
        return transaction(subject, admission -> {
            if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            return inspect(admission, id, part, sha);
        });
    }
    private Admission inspect(EnterpriseAccess.Admission admission, String id, long number, String sha) throws Exception {
        var session = uploads.session(admission, id); var part = uploads.partIfPresent(admission, id, number).orElse(null);
        boolean writing = part == null || !"STORED".equals(part.state());
        String permission = authority.require(admission, id, writing);
        if (writing && !"ACTIVE".equals(session.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        if (part != null && !sha.equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return new Admission(session, part, permission);
    }
    private Admission active(EnterpriseAccess.Admission admission, String id, long number, String sha) throws Exception {
        String permission = authority.require(admission, id, true);
        var session = uploads.session(admission, id); if (!"ACTIVE".equals(session.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        var part = uploads.partIfPresent(admission, id, number).orElse(null);
        if (part != null && !sha.equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return new Admission(session, part, permission);
    }
    private void currentIncoming(ApplicationSessionIdentity.Verified subject, String context, String id, long number, String sha, Admission original) {
        outside(subject); var current = inspect(subject, context, id, number, sha);
        if (!Identity.of(original.session()).equals(Identity.of(current.session()))) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if (current.part() == null || !"STORED".equals(current.part().state())) same(original.authorization(), current.authorization());
    }
    private void currentWriting(ApplicationSessionIdentity.Verified subject, Admission original) {
        outside(subject); transaction(subject, admission -> { currentWriting(admission, original); return null; });
    }
    private void currentWriting(EnterpriseAccess.Admission admission, Admission original) throws Exception {
        var current = active(admission, original.session().uploadId(), original.part().partNo(), original.part().sha256());
        same(original.authorization(), current.authorization());
        if (!original.part().equals(current.part()) || !"WRITING".equals(current.part().state())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }
    private ObjectRef store(ApplicationSessionIdentity.Verified subject, Admission work, ObjectRef actual) {
        return transaction(subject, admission -> {
            currentWriting(admission, work); var previous = uploads.session(admission, work.session().uploadId()); var row = work.part();
            var saved = uploads.storePart(admission, row.uploadId(), row.partNo(), row.revision().toString(), row.writeId(), actual);
            authority.stored(admission, previous, saved); return reference(saved);
        });
    }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject, Operation<T> action) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> action.apply(admission)).get());
    }
    @FunctionalInterface private interface Operation<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    private static void outside(ApplicationSessionIdentity.Verified subject) {
        if (subject == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static void ready(Admission admission) {
        if (admission.part() != null && !java.util.Set.of("RESERVED", "WRITING", "STORED").contains(admission.part().state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
    }
    private static void same(String before, String after) { if (!Objects.equals(before, after)) throw new TenantAccessException(ErrorCode.SCOPE_STALE); }
    private static ObjectRef reference(PartRow row) throws IOException {
        return UploadTransferObjects.decode(row.objectRef(), new ObjectRef(row.objectKey(), row.objectVersion(), row.sha256(), row.sizeBytes().toString(), "application/octet-stream"));
    }
    private static S3ObjectWriter.WriteIntent intent(PartRow row) {
        return new S3ObjectWriter.WriteIntent(row.writeId(), row.objectKey(), row.sha256(), row.sizeBytes().toString(), "application/octet-stream");
    }
    private static void verify(Path content, BigInteger size, String sha) throws IOException {
        Path file = Objects.requireNonNull(content).toAbsolutePath().normalize(), directory = file.getParent();
        if (directory == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
                || !Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS))) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (Files.size(file) != size.longValueExact()) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
                var bounded = BoundedInputStream.builder().setInputStream(input).setMaxCount(size.longValueExact() + 1).get()) {
            if (!sha.equals(DigestUtils.sha256Hex(bounded)) || bounded.getCount() != size.longValueExact()) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
    }
    private record Reception(long expected, TransferConcurrency.Lease lease) { }
    private record Admission(SessionRow session, PartRow part, String authorization) { }
    private record Identity(String tenant, String project, String id, String subject, String purpose, String source, String grant,
            BigInteger expectedSize, String sha, String media, String key, String keySha, BigInteger maximumPart,
            java.time.LocalDateTime expires, String version, String manifest, String createdBy, java.time.LocalDateTime createdAt, String classification) {
        static Identity of(SessionRow row) {
            return new Identity(row.tenantId(), row.projectId(), row.uploadId(), row.subjectId(), row.purpose(), row.sourceUploadId(), row.grantId(),
                row.expectedSize(), row.expectedSha256(), row.mediaType(), row.objectKey(), row.objectKeySha256(), row.maxPartBytes(), row.expiresAt(),
                row.schemaVersion(), row.schemaManifestSha256(), row.createdBy(), row.createdAt(), row.classification());
        }
    }
}
