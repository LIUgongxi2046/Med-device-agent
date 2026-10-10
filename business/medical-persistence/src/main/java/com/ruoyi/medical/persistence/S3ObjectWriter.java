package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.input.BoundedInputStream;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

public final class S3ObjectWriter implements AutoCloseable {
    private static final ExecutionAttribute<S3ObjectReader.Admission> ADMISSION = new ExecutionAttribute<>("MedicalObjectWriteAdmission");
    private final S3Client client;
    private final S3ObjectReader reader;
    private final Path privateDirectory;
    private final String bucket;
    private final long maxObjectBytes;

    public record WriteIntent(String writeId, String objectKey, String sha256, String sizeBytes, String mediaType) {
        public WriteIntent {
            WireValues.uuid(writeId); WireValues.hash(sha256);
            if (WireValues.revision(sizeBytes).signum() <= 0 || objectKey == null || objectKey.isBlank()
                    || mediaType == null || mediaType.isBlank()) throw new IllegalArgumentException("对象写入身份与完整内容要求无效");
            WireValues.boundedString(objectKey, 1024); WireValues.boundedString(mediaType, 128);
            org.springframework.util.MimeTypeUtils.parseMimeType(mediaType);
        }
        Map<String, String> metadata() { return Map.of("write-id", writeId, "sha256", sha256, "size-bytes", sizeBytes); }
    }

    public S3ObjectWriter(URI endpoint, String bucket, String region, S3ObjectReader.Network network,
            AwsCredentialsProvider credentials, Path privateDirectory, long maxObjectBytes, int segmentBytes) throws IOException {
        // 上传完成采用已知完整大小的PutObject；领域容量必须显式配置。
        if (maxObjectBytes <= 0 || maxObjectBytes > 5_000_000_000L) throw new IllegalArgumentException("完整对象写入容量超出PutObject范围");
        reader = new S3ObjectReader(endpoint, bucket, region, network, credentials, privateDirectory, maxObjectBytes, segmentBytes);
        this.privateDirectory = privateDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        this.bucket = bucket; this.maxObjectBytes = maxObjectBytes;
        client = S3Client.builder().endpointOverride(endpoint).region(Region.of(region)).credentialsProvider(credentials)
            .forcePathStyle(true).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(5))
                .connectionAcquisitionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(30))
                .proxyConfiguration(ProxyConfiguration.builder().useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()))
            .overrideConfiguration(options -> options.apiCallTimeout(ObjectRequestDeadline.MAXIMUM_CALL).apiCallAttemptTimeout(ObjectRequestDeadline.MAXIMUM_ATTEMPT)
                .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())
                .addExecutionInterceptor(new ExecutionInterceptor() {
                    @Override public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes attributes) {
                        Objects.requireNonNull(attributes.getAttribute(ADMISSION), "对象写入必须重新核查当前授权").requireCurrent();
                    }
                })).build();
    }

    public ObjectRef writeVerified(WriteIntent intent, Path content, S3ObjectReader.Admission admission) throws Exception {
        Objects.requireNonNull(admission).requireCurrent();
        long size = requireIntent(intent);
        Objects.requireNonNull(content);
        Path original = content.toAbsolutePath().normalize();
        if (!Files.getPosixFilePermissions(privateDirectory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
                || !privateDirectory.equals(original.getParent()) || !Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(original, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                || !Files.getOwner(privateDirectory, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(original, LinkOption.NOFOLLOW_LINKS))) denied();
        if (Files.size(original) != size) mismatch();
        Path snapshot = Files.createTempFile(privateDirectory, "object-write-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            // 私有快照固定实际发送字节，原上传文件随后变化不改变该请求。
            try (var source = Files.newInputStream(original, LinkOption.NOFOLLOW_LINKS);
                    var bounded = BoundedInputStream.builder().setInputStream(source).setMaxCount(size + 1).get();
                    var target = Files.newOutputStream(snapshot, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                bounded.transferTo(target);
            }
            if (Files.size(snapshot) != size) mismatch();
            try (var input = Files.newInputStream(snapshot, LinkOption.NOFOLLOW_LINKS)) {
                if (!intent.sha256().equals(DigestUtils.sha256Hex(input))) mismatch();
            }
            admission.requireCurrent();
            var request = PutObjectRequest.builder().bucket(bucket).key(intent.objectKey()).ifNoneMatch("*")
                .contentLength(size).contentType(intent.mediaType()).metadata(intent.metadata()).serverSideEncryption(ServerSideEncryption.AES256)
                .overrideConfiguration(options -> {
                    ObjectRequestDeadline.apply(options, admission); options.putExecutionAttribute(ADMISSION, admission);
                }).build();
            var written = client.putObject(request, RequestBody.fromFile(snapshot));
            if (written.versionId() == null || written.versionId().isEmpty() || "null".equals(written.versionId())
                    || written.serverSideEncryption() != ServerSideEncryption.AES256) mismatch();
            return verifyRemote(intent, written.versionId(), admission);
        } finally { Files.deleteIfExists(snapshot); }
    }

    public ObjectRef recoverVerified(WriteIntent intent, S3ObjectReader.Admission admission) throws Exception {
        Objects.requireNonNull(admission).requireCurrent(); requireIntent(intent);
        // 响应未知时明确重读原对象，不进行第二次写入。
        return verifyRemote(intent, null, admission);
    }

    private ObjectRef verifyRemote(WriteIntent intent, String version, S3ObjectReader.Admission admission) throws Exception {
        admission.requireCurrent();
        var request = HeadObjectRequest.builder().bucket(bucket).key(intent.objectKey()).versionId(version)
            .overrideConfiguration(options -> {
                ObjectRequestDeadline.apply(options, admission); options.putExecutionAttribute(ADMISSION, admission);
            }).build();
        var stored = client.headObject(request);
        if (stored.versionId() == null || stored.versionId().isEmpty() || "null".equals(stored.versionId())
                || version != null && !version.equals(stored.versionId()) || !intent.metadata().equals(stored.metadata())
                || !intent.mediaType().equals(stored.contentType()) || stored.contentLength() == null
                || stored.contentLength() != WireValues.revision(intent.sizeBytes()).longValueExact()
                || stored.serverSideEncryption() != ServerSideEncryption.AES256) mismatch();
        var reference = new ObjectRef(intent.objectKey(), stored.versionId(), intent.sha256(), intent.sizeBytes(), intent.mediaType());
        try (var verified = reader.readVerified(reference, admission)) {
            if (!reference.equals(verified.reference()) || !intent.sha256().equals(verified.fullSha256())) mismatch();
            admission.requireCurrent(); return reference;
        }
    }

    private long requireIntent(WriteIntent intent) throws IOException {
        Objects.requireNonNull(intent);
        long size = WireValues.revision(intent.sizeBytes()).longValueExact();
        if (size > maxObjectBytes) throw new IllegalArgumentException("对象写入超出已配置容量");
        int keyBytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(intent.objectKey())).remaining();
        if (keyBytes > 1024) throw new IllegalArgumentException("对象键超出S3字节容量");
        StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(intent.mediaType()));
        return size;
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    @Override public void close() { try { client.close(); } finally { reader.close(); } }
}
