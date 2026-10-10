package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.SchemaCatalogue;
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
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public final class DocumentUploadTransferService {
    private final EnterpriseAccess access;
    private final UploadAuthorizationAuthority authority;
    private final UploadTransferStore uploads;
    private final UploadTransferMapper mapper;
    private final SchemaCatalogue schemas;

    public DocumentUploadTransferService(EnterpriseAccess access, UploadAuthorizationAuthority authority,
            UploadTransferStore uploads, UploadTransferMapper mapper) throws IOException, NoSuchAlgorithmException {
        this.access = Objects.requireNonNull(access); this.authority = Objects.requireNonNull(authority);
        this.uploads = Objects.requireNonNull(uploads); this.mapper = Objects.requireNonNull(mapper);
        schemas = SchemaCatalogue.fromClasspath();
    }

    ObjectRef receiveStream(ApplicationSessionIdentity.Verified subject, String context, String uploadId,
            long partNo, String sha256, long contentLength, InputStream content, Path privateDirectory,
            int segmentBytes, S3ObjectWriter writer, TransferConcurrency concurrency, TransferConcurrency.Limits limits) throws Exception {
        requireOutsideTransaction(subject); WireValues.revision(context); WireValues.uuid(uploadId); WireValues.hash(sha256);
        Objects.requireNonNull(content); Objects.requireNonNull(writer); Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        var initial = inspectIncoming(subject, context, uploadId, partNo, sha256);
        long expected = UploadTransferStore.range(initial.session().expectedSize(), partNo).size().longValueExact();
        if (contentLength != expected) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        requireIncomingReady(initial);
        try (var lease = concurrency.acquire(initial.session().tenantId(), subject.user().user_id(), limits,
                () -> requireIncomingCurrent(subject, context, uploadId, partNo, sha256, initial))) {
            try (var saved = IncomingUploadPart.capture(privateDirectory, content, expected, sha256, segmentBytes, lease)) {
                var actual = receive(subject, uploadId, partNo, sha256, saved.path(), writer, lease);
                lease.requireCurrent(); return actual;
            }
        }
    }

    Mono<ObjectRef> receiveRequest(ApplicationSessionIdentity.Verified subject, String context, String uploadId,
            long partNo, String sha256, ServerHttpRequest content, Path privateDirectory, int segmentBytes, int demand,
            S3ObjectWriter writer, TransferConcurrency concurrency, TransferConcurrency.Limits limits, Scheduler receiver, Scheduler deadlines,
            java.time.Duration admissionInterval, Scheduler checks) {
        Objects.requireNonNull(receiver); Objects.requireNonNull(deadlines); Objects.requireNonNull(content); Objects.requireNonNull(writer);
        Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        Objects.requireNonNull(checks); Objects.requireNonNull(admissionInterval);
        if (receiver == deadlines || receiver == checks || deadlines == checks
                || admissionInterval.isNegative() || admissionInterval.toMillis() < 1)
            throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        admissionInterval.toNanos();
        // 领域查询及文件消费在服务器提供的接收线程执行，Servlet读取回调只传递实际buffer。
        return Mono.defer(() -> {
            var cancelled = reactor.core.publisher.Sinks.<Void>empty();
            return Mono.fromCallable(() -> {
                if (Schedulers.isInNonBlockingThread()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
                requireOutsideTransaction(subject); WireValues.revision(context); WireValues.uuid(uploadId); WireValues.hash(sha256);
                var initial = inspectIncoming(subject, context, uploadId, partNo, sha256);
                long expected = UploadTransferStore.range(initial.session().expectedSize(), partNo).size().longValueExact();
                if (content.getHeaders().getContentLength() != expected) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
                requireIncomingReady(initial);
                try (var lease = concurrency.acquire(initial.session().tenantId(), subject.user().user_id(), limits,
                        () -> requireIncomingCurrent(subject, context, uploadId, partNo, sha256, initial));
                        var saved = IncomingUploadPart.captureRequest(privateDirectory, content, expected, sha256, segmentBytes, demand,
                            lease, deadlines, admissionInterval, checks, cancelled.asMono())) {
                    var actual = receive(subject, uploadId, partNo, sha256, saved.path(), writer, lease);
                    lease.requireCurrent(); return actual;
                }
            }).subscribeOn(receiver)
                .doOnCancel(() -> cancelled.emitError(new TenantAccessException(ErrorCode.ACTION_STALE), reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST));
        });
    }

    private PartAdmission inspectIncoming(ApplicationSessionIdentity.Verified subject, String context,
            String uploadId, long partNo, String sha256) {
        return transaction(subject, admission -> {
            if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            return inspectPart(admission, uploadId, partNo, sha256);
        });
    }

    private void requireIncomingCurrent(ApplicationSessionIdentity.Verified subject, String context,
            String uploadId, long partNo, String sha256, PartAdmission original) {
        requireOutsideTransaction(subject);
        var current = inspectIncoming(subject, context, uploadId, partNo, sha256);
        if (!UploadIdentity.of(original.session()).equals(UploadIdentity.of(current.session())))
            throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if (current.part() == null || !"STORED".equals(current.part().state()))
            requireSameAuthorization(original.authorization(), current.authorization());
    }

    private static void requireIncomingReady(PartAdmission current) {
        if (current.part() != null && !"RESERVED".equals(current.part().state()) && !"STORED".equals(current.part().state()))
            throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
    }

    // 文件与对象客户端由服务器传输适配器提供，公开请求不能选择对象地址或授权清单。
    ObjectRef receive(ApplicationSessionIdentity.Verified subject, String uploadId, long partNo, String sha256,
            Path privateContent, S3ObjectWriter writer, TransferConcurrency.Lease transport) throws Exception {
        requireOutsideTransaction(subject); WireValues.uuid(uploadId); WireValues.hash(sha256); Objects.requireNonNull(writer);
        Objects.requireNonNull(transport).requireCurrent();
        var initial = inspectPart(subject, uploadId, partNo, sha256);
        var range = UploadTransferStore.range(initial.session().expectedSize(), partNo);
        verifyContent(privateContent, range.size(), sha256);
        transport.requireCurrent();
        if (initial.part() != null && "STORED".equals(initial.part().state())) return reference(initial.part());
        var work = transaction(subject, admission -> {
            var current = activePart(admission, uploadId, partNo, sha256);
            requireSameAuthorization(initial.authorization(), current.authorization());
            var row = current.part();
            if (row != null && "STORED".equals(row.state())) return current;
            if (row != null && !"RESERVED".equals(row.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            if (row == null) {
                var upload = current.session(); String writeId = UUID.randomUUID().toString();
                String key = "tenants/" + upload.tenantId() + "/uploads/" + uploadId + "/parts/" + partNo + "/" + writeId;
                var now = mapper.databaseTime(Map.of("userId", admission.subject().user().user_id(), "uploadId", uploadId));
                row = uploads.reserve(admission, new PartRow(upload.tenantId(), upload.projectId(), uploadId, partNo, writeId,
                    upload.expectedSize(), upload.maxPartBytes(), range.start(), range.end(), range.size(), sha256, key,
                    WireValues.sha256(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "RESERVED", null, null,
                    upload.schemaVersion(), upload.schemaManifestSha256(), upload.createdBy(), now, upload.classification(), BigInteger.ONE, now));
            }
            row = uploads.beginPart(admission, uploadId, partNo, row.revision().toString(), row.writeId());
            return new PartAdmission(current.session(), row, current.authorization());
        });
        if ("STORED".equals(work.part().state())) return reference(work.part());
        var actual = writer.writeVerified(intent(work.part()), privateContent, transport.guard(() -> requirePartCurrent(subject, work)));
        transport.requireCurrent();
        return storePart(subject, work, actual);
    }

    ObjectRef recoverPart(ApplicationSessionIdentity.Verified subject, String uploadId, long partNo, String sha256,
            S3ObjectWriter writer, TransferConcurrency concurrency, TransferConcurrency.Limits limits) throws Exception {
        requireOutsideTransaction(subject); WireValues.uuid(uploadId); WireValues.hash(sha256); Objects.requireNonNull(writer);
        Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        var work = inspectPart(subject, uploadId, partNo, sha256);
        if (work.part() == null) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        if ("STORED".equals(work.part().state())) return reference(work.part());
        if (!"WRITING".equals(work.part().state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
        try (var lease = concurrency.acquire(work.session().tenantId(), subject.user().user_id(), limits,
                () -> requirePartCurrent(subject, work))) {
            // 未知响应只核查原write_id对应的对象；缺少对象会明确返回读取错误。
            var actual = writer.recoverVerified(intent(work.part()), lease.guard(() -> requirePartCurrent(subject, work)));
            lease.requireCurrent(); return storePart(subject, work, actual);
        }
    }

    ObjectRef complete(ApplicationSessionIdentity.Verified subject, String uploadId, String expectedRevision,
            Path privateDirectory, long maximumBytes, S3ObjectReader reader, S3ObjectWriter writer,
            TransferConcurrency concurrency, TransferConcurrency.Limits limits, DocumentBodySemantics.Limits bodyLimits) throws Exception {
        requireOutsideTransaction(subject); WireValues.uuid(uploadId); WireValues.revision(expectedRevision);
        Objects.requireNonNull(reader); Objects.requireNonNull(writer); Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        Objects.requireNonNull(bodyLimits);
        var initial = inspectCompletion(subject, uploadId, maximumBytes, false);
        if ("COMPLETED".equals(initial.session().state())) return reference(initial.session());
        try (var lease = concurrency.acquire(initial.session().tenantId(), subject.user().user_id(), limits,
                () -> requireCompletionTransportCurrent(subject, initial))) {
            // 容量拒绝不能将尚未发送的上传改为COMPLETING。
            var work = transaction(subject, admission -> {
                requireCompletionTransportCurrent(admission, initial);
                var saved = uploads.beginCompletion(admission, uploadId, expectedRevision);
                return new CompletionAdmission(saved, initial.authorization());
            });
            return completeHeld(subject, work, privateDirectory, maximumBytes, reader, writer, lease, bodyLimits);
        }
    }

    private ObjectRef completeHeld(ApplicationSessionIdentity.Verified subject, CompletionAdmission work,
            Path privateDirectory, long maximumBytes, S3ObjectReader reader, S3ObjectWriter writer,
            TransferConcurrency.Lease lease, DocumentBodySemantics.Limits bodyLimits) throws Exception {
        String original = work.authorization(); String id = work.session().uploadId();
        var stage = lease.guard(() -> requireCompletionCurrent(subject, work));
        try (var assembly = new UploadObjectAssembly(privateDirectory, maximumBytes, work.session().expectedSize(), work.session().expectedSha256(),
                stage)) {
            long after = 0;
            while (true) {
                long previous = after;
                var page = transaction(subject, admission -> {
                    requireCompletionCurrent(admission, work); return uploads.parts(admission, id, previous, 100);
                });
                if (page.isEmpty()) break;
                for (var row : page) {
                    if (!"STORED".equals(row.state())) throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
                    var partReference = reference(row);
                    assembly.append(row.partNo(), partReference, reader); after = row.partNo();
                }
            }
            Path content = assembly.finish();
            stage.requireCurrent();
            // 完整正文核验结构和规范字节；专业资格和Artifact登记继续由领域消费者核验。
            var required = transaction(subject, admission -> {
                var current = authority.requireBoundDocument(admission, id); requireSameAuthorization(original, current.authorizationSha256());
                return current.publishedSchema();
            });
            DocumentBodySemantics.published(schemas, required, Files.readAllBytes(content), bodyLimits);
            stage.requireCurrent();
            var actual = writer.writeVerified(intent(work.session()), content, stage);
            stage.requireCurrent();
            return storeCompletion(subject, work, actual);
        }
    }

    ObjectRef recoverCompletion(ApplicationSessionIdentity.Verified subject, String uploadId, long maximumBytes,
            S3ObjectReader reader, S3ObjectWriter writer, TransferConcurrency concurrency, TransferConcurrency.Limits limits,
            DocumentBodySemantics.Limits bodyLimits) throws Exception {
        requireOutsideTransaction(subject); WireValues.uuid(uploadId); Objects.requireNonNull(reader); Objects.requireNonNull(writer);
        Objects.requireNonNull(concurrency); Objects.requireNonNull(limits);
        Objects.requireNonNull(bodyLimits);
        var work = inspectCompletion(subject, uploadId, maximumBytes, true);
        if ("COMPLETED".equals(work.session().state())) return reference(work.session());
        try (var lease = concurrency.acquire(work.session().tenantId(), subject.user().user_id(), limits,
                () -> requireCompletionTransportCurrent(subject, work))) {
            var stage = lease.guard(() -> requireCompletionCurrent(subject, work));
            var actual = writer.recoverVerified(intent(work.session()), stage);
            try (var verified = reader.readVerified(actual, stage)) {
                var required = transaction(subject, admission -> {
                    var current = authority.requireBoundDocument(admission, uploadId); requireSameAuthorization(work.authorization(), current.authorizationSha256());
                    return current.publishedSchema();
                });
                var bytes = new byte[work.session().expectedSize().intValueExact()];
                for (int start = 0; start < bytes.length;) {
                    int length = Math.min(S3ObjectReader.MAX_SEGMENT_BYTES, bytes.length - start);
                    System.arraycopy(verified.chunk(start, length), 0, bytes, start, length); start += length;
                }
                stage.requireCurrent(); DocumentBodySemantics.published(schemas, required, bytes, bodyLimits);
            }
            stage.requireCurrent(); return storeCompletion(subject, work, actual);
        }
    }

    private CompletionAdmission inspectCompletion(ApplicationSessionIdentity.Verified subject, String uploadId,
            long maximumBytes, boolean recovering) {
        return transaction(subject, admission -> {
            var upload = uploads.session(admission, uploadId);
            if ("COMPLETED".equals(upload.state())) {
                authority.requireVisibleBoundDocument(admission, uploadId); return new CompletionAdmission(upload, null);
            }
            var current = authority.requireBoundDocument(admission, uploadId);
            if (!(recovering ? "COMPLETING" : "ACTIVE").equals(upload.state()))
                throw new TenantAccessException(recovering ? ErrorCode.ACTION_STALE : ErrorCode.STEP_NOT_READY);
            requireJsonCapacity(upload, maximumBytes); return new CompletionAdmission(upload, current.authorizationSha256());
        });
    }

    private void requireCompletionTransportCurrent(ApplicationSessionIdentity.Verified subject, CompletionAdmission original) {
        requireOutsideTransaction(subject);
        transaction(subject, admission -> { requireCompletionTransportCurrent(admission, original); return null; });
    }

    private void requireCompletionTransportCurrent(EnterpriseAccess.Admission admission, CompletionAdmission original) throws Exception {
        var upload = uploads.session(admission, original.session().uploadId());
        if (!UploadIdentity.of(original.session()).equals(UploadIdentity.of(upload))) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        var current = authority.requireBoundDocument(admission, upload.uploadId());
        requireSameAuthorization(original.authorization(), current.authorizationSha256());
    }

    private PartAdmission inspectPart(ApplicationSessionIdentity.Verified subject, String uploadId, long partNo, String sha256) {
        return transaction(subject, admission -> inspectPart(admission, uploadId, partNo, sha256));
    }

    private PartAdmission inspectPart(EnterpriseAccess.Admission admission, String uploadId, long partNo, String sha256) throws Exception {
        var upload = uploads.session(admission, uploadId); var part = uploads.partIfPresent(admission, uploadId, partNo).orElse(null);
        var current = part != null && "STORED".equals(part.state())
            ? authority.requireVisibleBoundDocument(admission, uploadId) : authority.requireBoundDocument(admission, uploadId);
        if (part == null || !"STORED".equals(part.state())) requireActive(upload);
        if (part != null && !sha256.equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return new PartAdmission(upload, part, current.authorizationSha256());
    }

    private PartAdmission activePart(EnterpriseAccess.Admission admission, String uploadId, long partNo, String sha256) throws Exception {
        var current = authority.requireBoundDocument(admission, uploadId); var upload = uploads.session(admission, uploadId); requireActive(upload);
        var part = uploads.partIfPresent(admission, uploadId, partNo).orElse(null);
        if (part != null && !sha256.equals(part.sha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        return new PartAdmission(upload, part, current.authorizationSha256());
    }

    private void requirePartCurrent(ApplicationSessionIdentity.Verified subject, PartAdmission original) {
        requireOutsideTransaction(subject);
        transaction(subject, admission -> { requirePartCurrent(admission, original); return null; });
    }

    private void requirePartCurrent(EnterpriseAccess.Admission admission, PartAdmission original) throws Exception {
        var current = activePart(admission, original.session().uploadId(), original.part().partNo(), original.part().sha256());
        requireSameAuthorization(original.authorization(), current.authorization());
        if (!original.part().equals(current.part()) || !"WRITING".equals(current.part().state())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }

    private ObjectRef storePart(ApplicationSessionIdentity.Verified subject, PartAdmission work, ObjectRef actual) {
        return transaction(subject, admission -> {
            requirePartCurrent(admission, work); var row = work.part();
            return reference(uploads.storePart(admission, row.uploadId(), row.partNo(), row.revision().toString(), row.writeId(), actual));
        });
    }

    private void requireCompletionCurrent(ApplicationSessionIdentity.Verified subject, CompletionAdmission original) {
        requireOutsideTransaction(subject);
        transaction(subject, admission -> { requireCompletionCurrent(admission, original); return null; });
    }

    private void requireCompletionCurrent(EnterpriseAccess.Admission admission, CompletionAdmission original) throws Exception {
        var current = authority.requireBoundDocument(admission, original.session().uploadId());
        requireSameAuthorization(original.authorization(), current.authorizationSha256());
        if (!original.session().equals(uploads.session(admission, original.session().uploadId()))) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
    }

    private ObjectRef storeCompletion(ApplicationSessionIdentity.Verified subject, CompletionAdmission work, ObjectRef actual) {
        return transaction(subject, admission -> {
            requireCompletionCurrent(admission, work);
            return reference(uploads.complete(admission, work.session().uploadId(), work.session().revision().toString(), actual));
        });
    }

    private <T> T transaction(ApplicationSessionIdentity.Verified subject, CheckedOperation<T> operation) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> operation.apply(admission)).get());
    }
    @FunctionalInterface private interface CheckedOperation<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    static void requireOutsideTransaction(ApplicationSessionIdentity.Verified subject) {
        if (subject == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static void requireActive(SessionRow upload) {
        if (!"ACTIVE".equals(upload.state())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
    }
    private static void requireSameAuthorization(String original, String current) {
        if (!Objects.equals(original, current)) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }
    private static void requireJsonCapacity(SessionRow upload, long maximumBytes) {
        if (maximumBytes <= 0 || maximumBytes > Integer.MAX_VALUE) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if (upload.expectedSize().compareTo(BigInteger.valueOf(maximumBytes)) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (!"application/json".equals(upload.mediaType())) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
    }
    private static ObjectRef reference(PartRow row) throws IOException {
        return UploadTransferObjects.decode(row.objectRef(), new ObjectRef(row.objectKey(), row.objectVersion(), row.sha256(), row.sizeBytes().toString(), "application/octet-stream"));
    }
    private static ObjectRef reference(SessionRow row) throws IOException {
        return UploadTransferObjects.decode(row.objectRef(), new ObjectRef(row.objectKey(), row.objectVersion(), row.contentSha256(), row.expectedSize().toString(), row.mediaType()));
    }
    private static S3ObjectWriter.WriteIntent intent(PartRow row) {
        return new S3ObjectWriter.WriteIntent(row.writeId(), row.objectKey(), row.sha256(), row.sizeBytes().toString(), "application/octet-stream");
    }
    private static S3ObjectWriter.WriteIntent intent(SessionRow row) {
        return new S3ObjectWriter.WriteIntent(row.uploadId(), row.objectKey(), row.expectedSha256(), row.expectedSize().toString(), row.mediaType());
    }
    private static void verifyContent(Path content, BigInteger size, String sha256) throws IOException {
        Path file = Objects.requireNonNull(content).toAbsolutePath().normalize(); Path directory = file.getParent();
        if (directory == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
                || !Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS)))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if (Files.size(file) != size.longValueExact()) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
                var bounded = BoundedInputStream.builder().setInputStream(input).setMaxCount(size.longValueExact() + 1).get()) {
            if (!sha256.equals(DigestUtils.sha256Hex(bounded)) || bounded.getCount() != size.longValueExact())
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
    }
    private record PartAdmission(SessionRow session, PartRow part, String authorization) { }
    private record CompletionAdmission(SessionRow session, String authorization) { }
    private record UploadIdentity(String tenantId, String projectId, String uploadId, String subjectId, String purpose,
            String sourceUploadId, String grantId, BigInteger expectedSize, String expectedSha256, String mediaType,
            String objectKey, String objectKeySha256, BigInteger maxPartBytes, java.time.LocalDateTime expiresAt,
            String schemaVersion, String schemaManifestSha256, String createdBy, java.time.LocalDateTime createdAt,
            String classification) {
        static UploadIdentity of(SessionRow row) {
            // 其他片段的进度变化不改变本片段的固定上传身份。
            return new UploadIdentity(row.tenantId(), row.projectId(), row.uploadId(), row.subjectId(), row.purpose(),
                row.sourceUploadId(), row.grantId(), row.expectedSize(), row.expectedSha256(), row.mediaType(),
                row.objectKey(), row.objectKeySha256(), row.maxPartBytes(), row.expiresAt(), row.schemaVersion(),
                row.schemaManifestSha256(), row.createdBy(), row.createdAt(), row.classification());
        }
    }
}
