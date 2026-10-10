package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.apache.commons.io.input.BoundedInputStream;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public final class S3ObjectReader implements AutoCloseable {
    public static final int MAX_SEGMENT_BYTES = 8 * 1024 * 1024;
    public enum Network { LOCAL_LOOPBACK, PRIVATE_TLS }
    @FunctionalInterface public interface Admission { void requireCurrent(); }
    public interface TimedAdmission extends Admission { Duration remainingTime(); }
    private static final ExecutionAttribute<Admission> ADMISSION = new ExecutionAttribute<>("MedicalObjectReadAdmission");
    private final S3Client client;
    private final CircuitBreaker breaker;
    private final Path privateDirectory;
    private final String bucket;
    private final long maxObjectBytes;
    private final int segmentBytes;

    public S3ObjectReader(URI endpoint, String bucket, String region, Network network,
            AwsCredentialsProvider credentials, Path privateDirectory, long maxObjectBytes, int segmentBytes) throws IOException {
        Objects.requireNonNull(endpoint); Objects.requireNonNull(network); Objects.requireNonNull(credentials);
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !endpoint.getPath().isEmpty() && !endpoint.getPath().equals("/")) throw new IllegalArgumentException("对象端点必须使用完整批准地址");
        if (network == Network.LOCAL_LOOPBACK && !(endpoint.getScheme().equals("http") && endpoint.getHost().equals("127.0.0.1")
                && endpoint.getPort() > 0) || network == Network.PRIVATE_TLS && !endpoint.getScheme().equals("https"))
            throw new IllegalArgumentException("对象网络与批准模式不一致");
        if (bucket == null || bucket.isBlank() || region == null || region.isBlank() || maxObjectBytes <= 0
                || segmentBytes <= 0 || segmentBytes > MAX_SEGMENT_BYTES)
            throw new IllegalArgumentException("对象桶、区域与读取容量必须完整配置");
        this.privateDirectory = Objects.requireNonNull(privateDirectory).toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(this.privateDirectory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(this.privateDirectory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("对象读取目录必须使用私有权限");
        this.bucket = bucket; this.maxObjectBytes = maxObjectBytes; this.segmentBytes = segmentBytes;
        breaker = CircuitBreaker.of("medical-object-read", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED).slidingWindowSize(5).minimumNumberOfCalls(5)
            .failureRateThreshold(100).waitDurationInOpenState(Duration.ofSeconds(30)).permittedNumberOfCallsInHalfOpenState(1)
            .ignoreException(error -> !transportFailure(error)).build());
        client = S3Client.builder().endpointOverride(endpoint).region(Region.of(region)).credentialsProvider(credentials)
            .forcePathStyle(true).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                .connectionAcquisitionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30))
                .proxyConfiguration(ProxyConfiguration.builder().useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()))
            .overrideConfiguration(options -> options.apiCallTimeout(ObjectRequestDeadline.MAXIMUM_CALL).apiCallAttemptTimeout(ObjectRequestDeadline.MAXIMUM_ATTEMPT)
                .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(3).build()).addExecutionInterceptor(new ExecutionInterceptor() {
                    @Override public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes attributes) {
                        Objects.requireNonNull(attributes.getAttribute(ADMISSION), "对象读取必须重新核查当前授权").requireCurrent();
                    }
                })).build();
    }

    public VerifiedObject readStored(String stored, ObjectRef columns, Admission admission) throws Exception {
        return readVerified(StoredObjectReferences.decode(stored, columns), admission);
    }

    public VerifiedObject readVerified(ObjectRef reference, Admission admission) throws Exception {
        Objects.requireNonNull(reference); Objects.requireNonNull(admission);
        long size = WireValues.revision(reference.sizeBytes()).longValueExact();
        if (reference.objectKey().isEmpty() || reference.objectVersion().isEmpty() || reference.mediaType().isEmpty()
                || size <= 0 || size > maxObjectBytes) throw new IllegalArgumentException("对象引用超出已配置读取范围");
        admission.requireCurrent();
        Path temporary = Files.createTempFile(privateDirectory, "verified-object-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        boolean transferred = false;
        FileChannel channel = null;
        try {
            channel = FileChannel.open(temporary, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE, LinkOption.NOFOLLOW_LINKS);
            var digest = MessageDigest.getInstance("SHA-256");
            for (long start = 0; start < size;) {
                int length = (int) Math.min(segmentBytes, size - start);
                long offset = start;
                byte[] bytes = breaker.executeCallable(() -> readSegment(reference, offset, length, size, admission));
                admission.requireCurrent();
                digest.update(bytes);
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                start += length;
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            if (channel.size() != size || !sha256.equals(reference.sha256())) mismatch();
            channel.force(true);
            admission.requireCurrent();
            var verified = new VerifiedObject(reference, sha256, channel, admission);
            transferred = true;
            return verified;
        } finally {
            if (!transferred) {
                if (channel != null) channel.close();
                Files.deleteIfExists(temporary);
            }
        }
    }

    private byte[] readSegment(ObjectRef reference, long start, int length, long size, Admission admission) {
        admission.requireCurrent();
        long end = start + length - 1;
        var request = GetObjectRequest.builder().bucket(bucket).key(reference.objectKey()).versionId(reference.objectVersion())
            .range("bytes=" + start + "-" + end).overrideConfiguration(options -> {
                ObjectRequestDeadline.apply(options, admission); options.putExecutionAttribute(ADMISSION, admission);
            }).build();
        return client.getObject(request, (GetObjectResponse response, software.amazon.awssdk.http.AbortableInputStream stream) -> {
            boolean consumed = false;
            try {
                if (!reference.objectVersion().equals(response.versionId()) || !reference.mediaType().equals(response.contentType())
                        || response.contentLength() == null || response.contentLength() != length
                        || !Objects.equals("bytes " + start + "-" + end + "/" + size, response.contentRange())) mismatch();
                // 多读一个字节核查完整长度；异常分段不继续消耗剩余响应。
                var bounded = BoundedInputStream.builder().setInputStream(stream).setMaxCount(length + 1L).setPropagateClose(false).get();
                byte[] bytes = bounded.readAllBytes();
                if (bytes.length != length) mismatch();
                consumed = true;
                return bytes;
            } finally { if (!consumed) stream.abort(); }
        });
    }

    private static boolean transportFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause())
            if (current instanceof TenantAccessException) return false;
        for (Throwable current = error; current != null; current = current.getCause())
            if (current instanceof IOException || current instanceof ApiCallTimeoutException || current instanceof ApiCallAttemptTimeoutException
                    || current instanceof SdkServiceException service && service.statusCode() >= 500) return true;
        return false;
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }

    @Override public void close() { client.close(); }

    public static final class VerifiedObject implements AutoCloseable {
        private final ObjectRef reference;
        private final String fullSha256;
        private final FileChannel channel;
        private final Admission admission;
        private VerifiedObject(ObjectRef reference, String fullSha256, FileChannel channel, Admission admission) {
            this.reference = reference; this.fullSha256 = fullSha256; this.channel = channel; this.admission = admission;
        }
        public ObjectRef reference() { return reference; }
        public String fullSha256() { return fullSha256; }
        public synchronized byte[] chunk(long start, int length) throws IOException {
            if (!channel.isOpen()) throw new IllegalStateException("对象读取已经关闭");
            long size = WireValues.revision(reference.sizeBytes()).longValueExact();
            if (start < 0 || length <= 0 || length > MAX_SEGMENT_BYTES || start > size - length)
                throw new IllegalArgumentException("对象分段超出已核验范围");
            admission.requireCurrent();
            var buffer = ByteBuffer.allocate(length);
            channel.position(start);
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) mismatch();
            admission.requireCurrent();
            return buffer.array();
        }
        @Override public synchronized void close() throws IOException { channel.close(); }
    }
}
