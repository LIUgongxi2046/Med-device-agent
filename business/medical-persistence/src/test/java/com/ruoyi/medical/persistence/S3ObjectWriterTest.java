package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.springframework.util.function.ThrowingSupplier;
import static org.junit.jupiter.api.Assertions.*;

class S3ObjectWriterTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ObjectRef> ORIGINAL_REFS = new ArrayList<>();
    private static final List<Path> ORIGINALS = new ArrayList<>();
    private static final List<Path> PRIVATE_FILES = new ArrayList<>();
    private static final List<ObjectRef> WRITTEN = new ArrayList<>();
    private static final AtomicInteger ADMISSIONS = new AtomicInteger();
    private static final String PREFIX = "technical-object-write/" + UUID.randomUUID() + "/";
    private static Path root;
    private static Path spool;
    private static Path permission;
    private static JsonNode deployment;
    private static StaticCredentialsProvider credentials;
    private static S3ObjectWriter writer;
    private static S3Client storage;
    private static long maximum;

    @BeforeAll static void actualPrivateStorageAndOriginals() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var directory = root.resolve(".implementation-work/runtime/object-storage");
        deployment = JSON.readTree(Files.readAllBytes(directory.resolve("deployment.json")));
        var secrets = JSON.readTree(Files.readAllBytes(directory.resolve("service-secret.json")));
        assertEquals(deployment.required("service_id"), secrets.required("service_id"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("service-secret.json"), LinkOption.NOFOLLOW_LINKS));
        var stored = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertEquals("LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS", stored.required("scope").textValue());
        assertFalse(stored.required("formal_evidence_eligible").booleanValue()); assertFalse(stored.required("customer_records_created").booleanValue());
        for (var object : stored.required("objects")) {
            var original = root.resolve(object.required("source_ref").textValue()).toRealPath();
            var reference = new ObjectRef(object.required("object_key").textValue(), object.required("object_version").textValue(),
                object.required("sha256").textValue(), object.required("size_bytes").textValue(), object.required("media_type").textValue());
            assertEquals(reference.sha256(), WireValues.sha256(Files.readAllBytes(original)));
            assertEquals(reference.sizeBytes(), Long.toString(Files.size(original)));
            ORIGINALS.add(original); ORIGINAL_REFS.add(reference); maximum = Math.max(maximum, Files.size(original));
        }
        assertEquals(2, ORIGINALS.size());
        spool = Files.createTempDirectory(root.resolve(".implementation-work"), "object-writer-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        permission = Files.createTempFile(spool, "write-permission-", ".identity",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(), secrets.required("secret_key").textValue()));
        writer = newWriter();
        storage = S3Client.builder().endpointOverride(URI.create(deployment.required("endpoint").textValue())).region(Region.US_EAST_1)
            .credentialsProvider(credentials).forcePathStyle(true).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(ApacheHttpClient.builder().proxyConfiguration(ProxyConfiguration.builder()
                .useEnvironmentVariableValues(false).useSystemPropertyValues(false).build()))
            .overrideConfiguration(options -> options.retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())).build();
        requireCurrentNativeDeployment();
    }

    private static S3ObjectWriter newWriter() throws Exception {
        return new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(), "us-east-1",
            S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, spool, maximum, Math.toIntExact(Files.size(ORIGINALS.get(0)) / 2));
    }

    private static void requireCurrentNativeDeployment() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        if (!assertDoesNotThrow(() -> Files.getPosixFilePermissions(permission, LinkOption.NOFOLLOW_LINKS)).equals(PosixFilePermissions.fromString("rw-------")))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        ADMISSIONS.incrementAndGet();
    }

    private static S3ObjectWriter.WriteIntent intent(int index) {
        var original = ORIGINAL_REFS.get(index); String identity = UUID.randomUUID().toString();
        return new S3ObjectWriter.WriteIntent(identity, PREFIX + identity, original.sha256(), original.sizeBytes(), original.mediaType());
    }

    private static Path privateContent(int index) throws Exception {
        Path content = Files.createTempFile(spool, "upload-", ".content", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(content, Files.readAllBytes(ORIGINALS.get(index))); PRIVATE_FILES.add(content); return content;
    }

    private static void actualContent(ObjectRef reference, int index) throws Exception {
        var response = storage.getObject(GetObjectRequest.builder().bucket(deployment.required("bucket").textValue()).key(reference.objectKey())
            .versionId(reference.objectVersion()).build(), ResponseTransformer.toBytes());
        assertArrayEquals(Files.readAllBytes(ORIGINALS.get(index)), response.asByteArray());
        assertEquals(reference.objectVersion(), response.response().versionId());
        assertEquals(reference.sha256(), WireValues.sha256(response.asByteArray()));
        assertEquals("AES256", response.response().serverSideEncryptionAsString());
    }

    private static Class<?> assemblyType() throws Exception {
        String name = "com.ruoyi.medical.persistence.UploadObjectAssembly";
        assertNotNull(S3ObjectWriterTest.class.getClassLoader().getResource(name.replace('.', '/') + ".class"),
            "上传完成必须按原连续范围重组实际分片并核查完整SHA-256");
        return Class.forName(name);
    }

    @Test void actualOriginalPartsAreAssembledWithFullSizeDigestAndPrivatePermissions() throws Exception {
        var type = assemblyType();
        for (int index = 0; index < ORIGINALS.size(); index++) {
            var original = ORIGINAL_REFS.get(index); String identity = UUID.randomUUID().toString();
            var intent = new S3ObjectWriter.WriteIntent(identity, PREFIX + identity, original.sha256(), original.sizeBytes(), "application/octet-stream");
            var part = writer.writeVerified(intent, privateContent(index), S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(part);
            try (var reader = new S3ObjectReader(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                    "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, spool, maximum, 4096);
                    var assembly = (AutoCloseable) type.getDeclaredConstructor(Path.class, long.class, java.math.BigInteger.class, String.class,
                        S3ObjectReader.Admission.class).newInstance(spool, maximum, new java.math.BigInteger(original.sizeBytes()), original.sha256(),
                            (S3ObjectReader.Admission) S3ObjectWriterTest::requireCurrentNativeDeployment)) {
                type.getDeclaredMethod("append", long.class, ObjectRef.class, S3ObjectReader.class).invoke(assembly, 1L, part, reader);
                Path complete = (Path) type.getDeclaredMethod("finish").invoke(assembly);
                assertArrayEquals(Files.readAllBytes(ORIGINALS.get(index)), Files.readAllBytes(complete));
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(complete, LinkOption.NOFOLLOW_LINKS));
                assertEquals(spool.toRealPath(), complete.getParent());
                assertEquals(complete, type.getDeclaredMethod("finish").invoke(assembly));
            }
        }
        try (var entries = Files.list(spool)) { assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("upload-assembly-"))); }
    }

    @Test void uploadAssemblyRejectsMissingPartsAndUnapprovedPrivateDirectory() throws Exception {
        var type = assemblyType(); var original = ORIGINAL_REFS.get(0);
        var constructor = type.getDeclaredConstructor(Path.class, long.class, java.math.BigInteger.class, String.class, S3ObjectReader.Admission.class);
        try (var assembly = (AutoCloseable) constructor.newInstance(spool, maximum, new java.math.BigInteger(original.sizeBytes()), original.sha256(),
                (S3ObjectReader.Admission) S3ObjectWriterTest::requireCurrentNativeDeployment)) {
            var incomplete = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> type.getDeclaredMethod("finish").invoke(assembly));
            assertEquals(ErrorCode.STEP_NOT_READY, ((TenantAccessException) incomplete.getCause()).code());
        }
        var privateDirectory = Files.createTempDirectory(root.resolve(".implementation-work"), "upload-assembly-permissions-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        try {
            var denied = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> constructor.newInstance(privateDirectory, maximum,
                new java.math.BigInteger(original.sizeBytes()), original.sha256(), (S3ObjectReader.Admission) S3ObjectWriterTest::requireCurrentNativeDeployment));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) denied.getCause()).code());
        } finally { Files.delete(privateDirectory); }
    }

    @Test void uploadAssemblyRequiresBoundedCapacityBeforeCreatingFiles() throws Exception {
        var type = assemblyType(); var original = ORIGINAL_REFS.get(1);
        var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> type.getDeclaredConstructor(Path.class, long.class,
            java.math.BigInteger.class, String.class, S3ObjectReader.Admission.class).newInstance(spool, Long.parseLong(original.sizeBytes()) - 1,
                new java.math.BigInteger(original.sizeBytes()), original.sha256(), (S3ObjectReader.Admission) S3ObjectWriterTest::requireCurrentNativeDeployment));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ((TenantAccessException) failure.getCause()).code());
        try (var entries = Files.list(spool)) { assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("upload-assembly-"))); }
    }

    @Test void deployedBinaryIsReassembledFromEveryActualEightMiBPartAndFinalShortPart() throws Exception {
        var binary = root.resolve(".implementation-work/toolchains/seaweedfs-4.48/weed").toRealPath();
        long total = Files.size(binary), partBytes = UploadTransferProtocol.PART_BYTES.longValueExact();
        assertTrue(total > partBytes); assertTrue(total % partBytes > 0);
        try (var input = Files.newInputStream(binary)) {
            assertEquals(deployment.required("binary_sha256").textValue(), org.apache.commons.codec.digest.DigestUtils.sha256Hex(input));
        }
        Path partContent = Files.createTempFile(spool, "deployed-binary-part-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); PRIVATE_FILES.add(partContent);
        try (var input = Files.newInputStream(binary);
                var partWriter = new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                    "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, spool, partBytes, Math.toIntExact(partBytes));
                var reader = new S3ObjectReader(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                    "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, spool, partBytes, Math.toIntExact(partBytes));
                var assembly = new UploadObjectAssembly(spool, total, java.math.BigInteger.valueOf(total), deployment.required("binary_sha256").textValue(),
                    S3ObjectWriterTest::requireCurrentNativeDeployment)) {
            long received = 0, number = 1;
            while (received < total) {
                byte[] bytes = input.readNBytes(Math.toIntExact(Math.min(partBytes, total - received)));
                assertEquals(Math.min(partBytes, total - received), bytes.length);
                Files.write(partContent, bytes); String identity = UUID.randomUUID().toString();
                var required = new S3ObjectWriter.WriteIntent(identity, PREFIX + identity,
                    WireValues.sha256(bytes), Integer.toString(bytes.length), "application/octet-stream");
                var reference = partWriter.writeVerified(required, partContent, S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(reference);
                assertEquals(reference, partWriter.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment));
                assembly.append(number, reference, reader); received += bytes.length; number++;
            }
            assertEquals(-1, input.read());
            Path complete = assembly.finish(); assertEquals(total, Files.size(complete));
            assertEquals(-1L, Files.mismatch(binary, complete));
            try (var content = Files.newInputStream(complete)) {
                assertEquals(deployment.required("binary_sha256").textValue(), org.apache.commons.codec.digest.DigestUtils.sha256Hex(content));
            }
            assertEquals(UploadTransferStore.partCount(java.math.BigInteger.valueOf(total)).longValueExact(), number - 1);
        }
        try (var entries = Files.list(spool)) { assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("upload-assembly-"))); }
    }

    @Test void originalPartSequenceFullDigestAndCurrentFilesystemAuthorityAreEnforced() throws Exception {
        var original = ORIGINAL_REFS.get(0); String identity = UUID.randomUUID().toString();
        var required = new S3ObjectWriter.WriteIntent(identity, PREFIX + identity, original.sha256(), original.sizeBytes(), "application/octet-stream");
        var part = writer.writeVerified(required, privateContent(0), S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(part);
        try (var reader = new S3ObjectReader(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, spool, maximum, 4096)) {
            try (var assembly = new UploadObjectAssembly(spool, maximum, new java.math.BigInteger(original.sizeBytes()), ORIGINAL_REFS.get(1).sha256(),
                    S3ObjectWriterTest::requireCurrentNativeDeployment)) {
                assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> assembly.append(2, part, reader)).code());
                assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> assembly.append(1, original, reader)).code());
                assembly.append(1, part, reader);
                assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> assembly.append(1, part, reader)).code());
                assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, assembly::finish).code());
                assertEquals(ErrorCode.ACTION_STALE, assertThrows(TenantAccessException.class, assembly::finish).code());
            }
            try (var assembly = new UploadObjectAssembly(spool, maximum, new java.math.BigInteger(original.sizeBytes()), original.sha256(),
                    S3ObjectWriterTest::requireCurrentNativeDeployment)) {
                assembly.append(1, part, reader);
                Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("r--------"));
                try { assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, assembly::finish).code()); }
                finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
                Path complete = assembly.finish();
                Files.write(complete, Files.readAllBytes(ORIGINALS.get(1)));
                assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, assembly::finish).code());
            }
        }
        try (var entries = Files.list(spool)) { assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("upload-assembly-"))); }
    }

    @Test void bothOriginalsAreWrittenVerifiedAndRecoveredWithSameActualVersion() throws Exception {
        for (int index = 0; index < ORIGINALS.size(); index++) {
            var required = intent(index); int before = ADMISSIONS.get();
            var reference = writer.writeVerified(required, privateContent(index), S3ObjectWriterTest::requireCurrentNativeDeployment);
            WRITTEN.add(reference); actualContent(reference, index);
            assertEquals(reference, writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment));
            try (var reopened = newWriter()) { assertEquals(reference, reopened.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment)); }
            assertTrue(ADMISSIONS.get() > before + 10);
        }
    }

    @Test void sameKeyCannotBeWrittenAgainOrOverwrittenWithOtherActualOriginal() throws Exception {
        var required = intent(0); Path content = privateContent(0);
        var reference = writer.writeVerified(required, content, S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(reference);
        assertEquals(412, assertThrows(S3Exception.class, () -> writer.writeVerified(required, content, S3ObjectWriterTest::requireCurrentNativeDeployment)).statusCode());
        var other = ORIGINAL_REFS.get(1);
        var changed = new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), other.sha256(), other.sizeBytes(), other.mediaType());
        assertEquals(412, assertThrows(S3Exception.class, () -> writer.writeVerified(changed, privateContent(1), S3ObjectWriterTest::requireCurrentNativeDeployment)).statusCode());
        assertEquals(reference, writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment));
        actualContent(reference, 0);
        assertEquals(1, storage.listObjectVersions(ListObjectVersionsRequest.builder().bucket(deployment.required("bucket").textValue()).prefix(required.objectKey()).build()).versions().size());
    }

    @Test void concurrentDifferentOriginalsCreateExactlyOneActualVersion() throws Exception {
        var firstIntent = intent(0); var secondOriginal = ORIGINAL_REFS.get(1);
        var secondIntent = new S3ObjectWriter.WriteIntent(firstIntent.writeId(), firstIntent.objectKey(), secondOriginal.sha256(), secondOriginal.sizeBytes(), secondOriginal.mediaType());
        var intents = List.of(firstIntent, secondIntent); var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<CompletableFuture<ObjectRef>>();
            for (int index = 0; index < intents.size(); index++) {
                var required = intents.get(index); var content = privateContent(index); var calls = new AtomicInteger();
                futures.add(CompletableFuture.supplyAsync(ThrowingSupplier.of(() -> writer.writeVerified(required, content, () -> {
                    if (calls.incrementAndGet() == 3) assertDoesNotThrow(() -> barrier.await(15, TimeUnit.SECONDS));
                    requireCurrentNativeDeployment();
                })), executor));
            }
            assertThrows(ExecutionException.class, () -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS));
            int accepted = 0; int rejected = 0;
            for (int index = 0; index < futures.size(); index++) {
                var future = futures.get(index);
                if (future.isCompletedExceptionally()) {
                    Throwable failure = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
                    while (failure.getCause() != null) failure = failure.getCause();
                    assertInstanceOf(S3Exception.class, failure); assertEquals(412, ((S3Exception) failure).statusCode()); rejected++;
                } else {
                    var reference = future.get(1, TimeUnit.SECONDS); WRITTEN.add(reference); actualContent(reference, index);
                    assertEquals(reference, writer.recoverVerified(intents.get(index), S3ObjectWriterTest::requireCurrentNativeDeployment)); accepted++;
                }
            }
            assertEquals(1, accepted); assertEquals(1, rejected);
            assertEquals(1, storage.listObjectVersions(ListObjectVersionsRequest.builder().bucket(deployment.required("bucket").textValue()).prefix(firstIntent.objectKey()).build()).versions().size());
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void wrongActualDigestOrSizeDoesNotCreateRemoteObject() throws Exception {
        var required = intent(0); Path content = privateContent(0);
        var wrongDigest = new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), ORIGINAL_REFS.get(1).sha256(), required.sizeBytes(), required.mediaType());
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> writer.writeVerified(wrongDigest, content, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        var wrongSize = new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), required.sha256(), Long.toString(Files.size(content) - 1), required.mediaType());
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> writer.writeVerified(wrongSize, content, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        assertEquals(404, assertThrows(S3Exception.class, () -> storage.headObject(HeadObjectRequest.builder()
            .bucket(deployment.required("bucket").textValue()).key(required.objectKey()).build())).statusCode());
        try (var entries = Files.list(spool)) { assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith("object-write-"))); }
    }

    @Test void recoveryRejectsOtherIdentityDigestSizeAndMediaType() throws Exception {
        var required = intent(0); var reference = writer.writeVerified(required, privateContent(0), S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(reference);
        var other = ORIGINAL_REFS.get(1);
        for (var changed : List.of(
                new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(), required.objectKey(), required.sha256(), required.sizeBytes(), required.mediaType()),
                new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), other.sha256(), required.sizeBytes(), required.mediaType()),
                new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), required.sha256(), Long.toString(Files.size(ORIGINALS.get(0)) - 1), required.mediaType()),
                new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), required.sha256(), required.sizeBytes(), other.mediaType()))) {
            assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                () -> writer.recoverVerified(changed, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        }
        actualContent(reference, 0);
    }

    @Test void privateSnapshotPreservesOriginalWhenUploadFileChangesBeforeTransmission() throws Exception {
        var required = intent(0); var content = privateContent(0); var calls = new AtomicInteger();
        var reference = writer.writeVerified(required, content, () -> {
            if (calls.incrementAndGet() == 3) assertDoesNotThrow(() -> Files.write(content, Files.readAllBytes(ORIGINALS.get(1))));
            requireCurrentNativeDeployment();
        });
        WRITTEN.add(reference); assertArrayEquals(Files.readAllBytes(ORIGINALS.get(1)), Files.readAllBytes(content)); actualContent(reference, 0);
    }

    @Test void actualPermissionRevocationAfterWriteRequiresExplicitOriginalRecovery() throws Exception {
        var required = intent(0); var content = privateContent(0);
        try {
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> writer.writeVerified(required, content, () -> {
                var versions = storage.listObjectVersions(ListObjectVersionsRequest.builder()
                    .bucket(deployment.required("bucket").textValue()).prefix(required.objectKey()).build()).versions();
                // 实际原对象已经保存后撤销技术权限，检查次数不决定撤销时机。
                if (!versions.isEmpty()) {
                    assertEquals(1, versions.size()); assertEquals(required.objectKey(), versions.get(0).key());
                    assertNotNull(versions.get(0).versionId());
                    assertDoesNotThrow(() -> Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("---------")));
                }
                requireCurrentNativeDeployment();
            })).code());
        } finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
        var reference = writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment); WRITTEN.add(reference); actualContent(reference, 0);
        assertEquals(412, assertThrows(S3Exception.class, () -> writer.writeVerified(required, content, S3ObjectWriterTest::requireCurrentNativeDeployment)).statusCode());
        assertEquals(reference, writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment));
    }

    @Test void publicFileOrRevokedTechnicalPermissionCannotStartWrite() throws Exception {
        var required = intent(0); var content = privateContent(0);
        Files.setPosixFilePermissions(content, PosixFilePermissions.fromString("rw-r--r--"));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
            () -> writer.writeVerified(required, content, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        try {
            Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("---------"));
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        } finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
        assertEquals(404, assertThrows(S3Exception.class, () -> writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment)).statusCode());
    }

    @Test void linkedContentAndConfiguredCapacityAreRejectedBeforeWriting() throws Exception {
        var required = intent(0); var content = privateContent(0); var link = spool.resolve(UUID.randomUUID() + ".content");
        Files.createSymbolicLink(link, content); PRIVATE_FILES.add(link);
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
            () -> writer.writeVerified(required, link, S3ObjectWriterTest::requireCurrentNativeDeployment)).code());
        var excessive = new S3ObjectWriter.WriteIntent(required.writeId(), required.objectKey(), required.sha256(), Long.toString(maximum + 1), required.mediaType());
        assertThrows(IllegalArgumentException.class, () -> writer.writeVerified(excessive, content, S3ObjectWriterTest::requireCurrentNativeDeployment));
        assertEquals(404, assertThrows(S3Exception.class, () -> writer.recoverVerified(required, S3ObjectWriterTest::requireCurrentNativeDeployment)).statusCode());
    }

    @AfterAll static void releasePrivateResources() throws Exception {
        System.out.println(JSON.writeValueAsString(Map.of("check_id", "IC-05-OBJECT-WRITE", "scope", "LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS_AND_DEPLOYED_BINARY",
            "formal_evidence_eligible", false, "customer_records_created", false, "references", WRITTEN)));
        try {
            for (var page : storage.listObjectVersionsPaginator(ListObjectVersionsRequest.builder().bucket(deployment.required("bucket").textValue()).prefix(PREFIX).build()))
                for (var version : page.versions()) {
                    assertTrue(version.key().startsWith(PREFIX));
                    storage.deleteObject(DeleteObjectRequest.builder().bucket(deployment.required("bucket").textValue()).key(version.key()).versionId(version.versionId()).build());
                }
        } finally { try { writer.close(); } finally { storage.close(); } }
        for (var content : PRIVATE_FILES) Files.delete(content);
        Files.delete(permission);
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
        Files.delete(spool);
    }
}
