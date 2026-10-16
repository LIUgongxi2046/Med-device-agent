package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Component
@EnableConfigurationProperties(SourceUploadTransportPolicy.class)
final class SourceUploadTransport {
    private final EnterpriseAccess access;
    private final UploadTransferStore uploads;
    private final UploadTransferMapper mapper;
    private final SourceUploadService sources;
    private final DocumentUploadPartService documents;
    private final SourceUploadTransportPolicy policy;
    private final URI endpoint;
    private final String bucket;
    private final StaticCredentialsProvider objectCredentials;
    private final Path directory;
    private final RedissonClient redis;
    private final S3ObjectWriter writer;
    private final TransferConcurrency concurrency;
    private final TransferConcurrency.Limits limits;
    private final java.util.concurrent.ExecutorService receiving, checking;
    private final Scheduler deadlines;

    SourceUploadTransport(EnterpriseAccess access, UploadTransferStore uploads, UploadTransferMapper mapper,
            SourceUploadService sources, DocumentUploadPartService documents, SourceUploadTransportPolicy policy, RedisProperties redisSettings) throws IOException {
        this.sources = sources; this.documents = documents; this.policy = policy; this.access = access; this.uploads = uploads; this.mapper = mapper;
        limits = new TransferConcurrency.Limits(policy.revision(), policy.tenantPermits(), policy.userPermits(), policy.maximumDuration());
        Path storage = policy.storageDirectory().toRealPath(LinkOption.NOFOLLOW_LINKS), secret = storage.resolve("service-secret.json");
        if (!Files.getPosixFilePermissions(storage, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
                || !Files.isRegularFile(secret, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(secret, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                || !Files.getOwner(secret, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(storage, LinkOption.NOFOLLOW_LINKS)))
            throw new IllegalArgumentException("对象服务身份文件权限无效");
        var json = JsonMapper.builder().build();
        var deployment = json.readTree(Files.readAllBytes(storage.resolve("deployment.json")));
        var credentials = json.readTree(Files.readAllBytes(secret));
        if (!deployment.required("service_id").equals(credentials.required("service_id"))
                || !"LOCAL_PRIVATE_OBJECT_STORAGE".equals(deployment.required("scope").textValue()))
            throw new IllegalArgumentException("对象服务与接收环境身份不一致");
        directory = Files.createDirectories(storage.resolve("source-upload-spool"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("分段接收目录权限无效");
        endpoint = URI.create(deployment.required("endpoint").textValue()); bucket = deployment.required("bucket").textValue();
        objectCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
            credentials.required("access_key").textValue(), credentials.required("secret_key").textValue()));
        writer = new S3ObjectWriter(endpoint, bucket, policy.region(), S3ObjectReader.Network.LOCAL_LOOPBACK, objectCredentials,
            directory, S3ObjectReader.MAX_SEGMENT_BYTES, policy.segmentBytes());
        var config = new Config(); config.setThreads(2).setNettyThreads(2);
        config.useSingleServer().setAddress("redis://" + redisSettings.getHost() + ":" + redisSettings.getPort())
            .setPassword(redisSettings.getPassword()).setDatabase(redisSettings.getDatabase()).setRetryAttempts(0)
            .setConnectTimeout(3000).setTimeout(3000).setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);
        redis = Redisson.create(config); concurrency = new TransferConcurrency(redis, policy.namespace());
        receiving = pool(policy.tenantPermits(), "source-upload-receiver-%d");
        deadlines = Schedulers.newParallel("source-upload-deadlines", 2); checking = pool(4, "source-upload-admission-%d");
    }

    record PartResponse(int status, com.fasterxml.jackson.databind.JsonNode body) { }

    Mono<PartResponse> receive(ApplicationSessionIdentity.Verified subject, String context, String key,
            String uploadId, long partNo, String sha256, ServerHttpRequest request) {
        var mutation = new SourceUploadService.PartRequest(context, key, uploadId, partNo, sha256, request.getHeaders().getContentLength());
        var security = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        security.setAuthentication(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        // ExecutorService保留实际Future取消；两个共享有界线程池由本组件管理生命周期。
        var receivingSubject = Schedulers.fromExecutorService(new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(receiving, security));
        var checkingSubject = Schedulers.fromExecutorService(new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(checking, security));
        // 上传用途只读取当前企业中原会话；请求头不能选择领域或改变许可绑定。
        return Mono.fromCallable(() -> access.execute(subject, admission -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTEXT_CHANGED);
            return uploads.session(admission, uploadId).purpose();
        }).get())).subscribeOn(checkingSubject).flatMap(purpose -> {
            if ("DOCUMENT_BODY".equals(purpose)) return receiveDocument(subject,
                new DocumentUploadPartService.Request(context, key, uploadId, partNo, sha256, request.getHeaders().getContentLength()),
                request, receivingSubject, checkingSubject);
            if (!"SOURCE".equals(purpose)) return Mono.error(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
            return Mono.fromCallable(() -> sources.startPart(subject, mutation)).subscribeOn(checkingSubject)
            .flatMap(tenant -> Mono.using(() -> java.util.Optional.ofNullable(concurrency.tryPart(tenant, uploadId, partNo, limits)), slot -> {
                if (slot.isEmpty()) return Mono.fromCallable(() -> sources.partReplay(subject, mutation))
                    .map(replay -> new PartResponse(replay.status(), replay.body()));
                var receiver = new UploadPartReceiver(access, uploads, mapper, sources.partAuthority(mutation, slot.get()));
                return receiver.receiveRequest(subject, context, uploadId, partNo, sha256, request, directory, policy.segmentBytes(), policy.demand(),
                    writer, concurrency, limits, receivingSubject, deadlines, policy.admissionInterval(), checkingSubject)
                    .map(reference -> new PartResponse(200, sources.partReceipt(subject, mutation)));
            }, slot -> slot.ifPresent(TransferConcurrency.PartLease::close), true).subscribeOn(checkingSubject));
        });
    }

    private Mono<PartResponse> receiveDocument(ApplicationSessionIdentity.Verified subject, DocumentUploadPartService.Request mutation,
            ServerHttpRequest request, Scheduler receivingSubject, Scheduler checkingSubject) {
        return Mono.fromCallable(() -> documents.start(subject, mutation)).subscribeOn(checkingSubject)
            .flatMap(tenant -> Mono.using(() -> java.util.Optional.ofNullable(concurrency.tryPart(tenant, mutation.uploadId(), mutation.partNo(), limits)), slot -> {
                if (slot.isEmpty()) return Mono.fromCallable(() -> documents.replay(subject, mutation))
                    .map(replay -> new PartResponse(replay.status(), replay.body()));
                var receiver = new UploadPartReceiver(access, uploads, mapper, documents.receiving(mutation, slot.get()));
                return receiver.receiveRequest(subject, mutation.context(), mutation.uploadId(), mutation.partNo(), mutation.sha256(), request,
                    directory, policy.segmentBytes(), policy.demand(), writer, concurrency, limits, receivingSubject, deadlines,
                    policy.admissionInterval(), checkingSubject).map(reference -> new PartResponse(200, documents.receipt(subject, mutation)));
            }, slot -> slot.ifPresent(TransferConcurrency.PartLease::close), true).subscribeOn(checkingSubject));
    }

    int segmentBytes() { return policy.segmentBytes(); }

    com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef writeDocumentProjection(ApplicationSessionIdentity.Verified subject,
            String tenantId, S3ObjectWriter.WriteIntent intent, byte[] bytes, boolean recover, int maximumBytes,
            S3ObjectReader.Admission current) throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject); current.requireCurrent();
        if (bytes.length > maximumBytes) throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.FILE_TOO_LARGE);
        if (!Integer.toString(bytes.length).equals(intent.sizeBytes())
                || !com.ruoyi.medical.contracts.WireValues.sha256(bytes).equals(intent.sha256()))
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH);
        try (var lease = concurrency.acquire(tenantId, subject.user().user_id(), limits, current);
                var objectWriter = new S3ObjectWriter(endpoint, bucket, policy.region(), S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials, directory, maximumBytes, policy.segmentBytes())) {
            if (recover) return objectWriter.recoverVerified(intent, lease.guard(current));
            Path file = Files.createTempFile(directory, "document-projection-", ".json",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(file, bytes); lease.requireCurrent();
                return objectWriter.writeVerified(intent, file, lease.guard(current));
            } finally { Files.deleteIfExists(file); }
        }
    }

    @FunctionalInterface interface CompletedDocument<T> {
        T apply(com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef reference, byte[] bytes) throws Exception;
    }
    @FunctionalInterface interface DocumentRead<T> { T apply(S3ObjectReader reader, S3ObjectReader.TimedAdmission admission) throws Exception; }

    <T> T readSourceJob(String tenantId, String actorId, int maximumBytes, S3ObjectReader.Admission current,
            DocumentRead<T> reading) throws Exception {
        requireJobOutsideTransaction(tenantId,actorId); current.requireCurrent();
        try(var lease=concurrency.acquire(tenantId,actorId,limits,current);
                var reader=new S3ObjectReader(endpoint,bucket,policy.region(),S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials,directory,maximumBytes,policy.segmentBytes())) {
            return reading.apply(reader,lease.guard(current));
        }
    }

    com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef writeSourceJob(String tenantId, String actorId,
            S3ObjectWriter.WriteIntent intent, Path content, Path privateDirectory, boolean recover, int maximumBytes,
            S3ObjectReader.Admission current) throws Exception {
        requireJobOutsideTransaction(tenantId,actorId); current.requireCurrent();
        if(!intent.objectKey().startsWith("tenants/"+tenantId+"/source-parsed/"))
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
        try(var lease=concurrency.acquire(tenantId,actorId,limits,current);
                var output=new S3ObjectWriter(endpoint,bucket,policy.region(),S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials,privateDirectory,maximumBytes,policy.segmentBytes())) {
            if(recover) return output.recoverVerified(intent,lease.guard(current));
            return output.writeVerified(intent,content,lease.guard(current));
        }
    }

    byte[] assembleSourceJob(String tenantId,String actorId,String uploadId,SourceCompletionObjects.Manifest manifest,
            int maximumBytes,S3ObjectReader.Admission current) throws Exception {
        requireJobOutsideTransaction(tenantId,actorId); com.ruoyi.medical.contracts.WireValues.uuid(uploadId);
        if(!("tenants/"+tenantId+"/uploads/"+uploadId+"/parts").equals(manifest.objectPrefix()))
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
        return readSourceJob(tenantId,actorId,maximumBytes,current,(reader,admission) -> {
            try(var originals=new S3ObjectWriter(endpoint,bucket,policy.region(),S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials,directory,maximumBytes,policy.segmentBytes())) {
                return new SourceCompletionObjects().assemble(manifest,directory,maximumBytes,reader,originals,admission);
            }
        });
    }

    com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef writeSourceOriginalJob(String tenantId,String actorId,String uploadId,
            S3ObjectWriter.WriteIntent intent,Path content,Path privateDirectory,boolean recover,int maximumBytes,
            S3ObjectReader.Admission current) throws Exception {
        requireJobOutsideTransaction(tenantId,actorId); com.ruoyi.medical.contracts.WireValues.uuid(uploadId);
        if(!("tenants/"+tenantId+"/source-uploads/"+uploadId+"/original").equals(intent.objectKey()))
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
        try(var lease=concurrency.acquire(tenantId,actorId,limits,current);
                var writer=new S3ObjectWriter(endpoint,bucket,policy.region(),S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials,privateDirectory,maximumBytes,policy.segmentBytes())) {
            if(recover) return writer.recoverVerified(intent,lease.guard(current));
            return writer.writeVerified(intent,content,lease.guard(current));
        }
    }

    private static void requireJobOutsideTransaction(String tenantId,String actorId) {
        com.ruoyi.medical.contracts.WireValues.uuid(tenantId); com.ruoyi.medical.contracts.WireValues.uuid(actorId);
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
    }

    <T> T readDocument(ApplicationSessionIdentity.Verified subject, String tenantId, int maximumBytes,
            S3ObjectReader.Admission current, DocumentRead<T> reading) throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject); current.requireCurrent();
        try (var lease = concurrency.acquire(tenantId, subject.user().user_id(), limits, current);
                var reader = new S3ObjectReader(endpoint, bucket, policy.region(), S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials, directory, maximumBytes, policy.segmentBytes())) {
            return reading.apply(reader, lease);
        }
    }

    <T> T completeDocument(ApplicationSessionIdentity.Verified subject, UploadTransferProtocol.SessionRow original,
            DocumentUploadTransferService transfer, DocumentUploadPolicy capacity, S3ObjectReader.Admission current,
            CompletedDocument<T> completed) throws Exception {
        DocumentUploadTransferService.requireOutsideTransaction(subject); capacity.requireConfigured(); current.requireCurrent();
        long maximum = capacity.maximumBytes().longValueExact();
        // 分片与完整对象分别使用明确容量；原对象服务身份和共享传输名额保持一致。
        try (var reader = new S3ObjectReader(endpoint, bucket, policy.region(), S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials, directory, maximum, policy.segmentBytes());
                var completeWriter = new S3ObjectWriter(endpoint, bucket, policy.region(), S3ObjectReader.Network.LOCAL_LOOPBACK,
                    objectCredentials, directory, maximum, policy.segmentBytes())) {
            var reference = switch (original.state()) {
                case "ACTIVE" -> transfer.complete(subject, original.uploadId(), original.revision().toString(), directory,
                    maximum, reader, completeWriter, concurrency, limits, capacity.bodyLimits());
                case "COMPLETING" -> transfer.recoverCompletion(subject, original.uploadId(), maximum, reader,
                    completeWriter, concurrency, limits, capacity.bodyLimits());
                case "COMPLETED" -> UploadTransferObjects.decode(original.objectRef(), new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef(
                    original.objectKey(), original.objectVersion(), original.contentSha256(), original.expectedSize().toString(), original.mediaType()));
                default -> throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACTION_STALE);
            };
            current.requireCurrent();
            try (var lease = concurrency.acquire(original.tenantId(), subject.user().user_id(), limits, current::requireCurrent);
                    var verified = reader.readVerified(reference, lease.guard(current::requireCurrent))) {
                byte[] bytes = new byte[original.expectedSize().intValueExact()];
                for (int start = 0; start < bytes.length;) {
                    int length = Math.min(S3ObjectReader.MAX_SEGMENT_BYTES, bytes.length - start);
                    System.arraycopy(verified.chunk(start, length), 0, bytes, start, length); start += length;
                }
                lease.requireCurrent(); return completed.apply(reference, bytes);
            }
        }
    }

    private static java.util.concurrent.ExecutorService pool(int size, String name) {
        return new java.util.concurrent.ThreadPoolExecutor(size, size, 60, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(100), new org.apache.commons.lang3.concurrent.BasicThreadFactory.Builder()
                .namingPattern(name).daemon(true).build());
    }

    @PreDestroy void close() {
        receiving.shutdownNow(); deadlines.dispose(); checking.shutdownNow(); writer.close(); redis.shutdown();
    }
}
