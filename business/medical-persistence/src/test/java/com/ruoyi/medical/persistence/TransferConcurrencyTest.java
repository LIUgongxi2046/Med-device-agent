package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.math.BigInteger;
import java.net.URI;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.util.function.ThrowingSupplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import static org.junit.jupiter.api.Assertions.*;

class TransferConcurrencyTest {
    private static final String NAME = "com.ruoyi.medical.persistence.TransferConcurrency";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Connection database;
    private static RedissonClient first, second;
    private static Path permission, directory;
    private static String actor, tenant, otherTenant, policy;
    private static JsonNode deployment;
    private static Path root;

    @BeforeAll static void actualServicesAndOperator() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); database.setAutoCommit(false);
        try (var statement = database.createStatement(); var rows = statement.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); actor = rows.getString(1); assertFalse(rows.next());
        }
        deployment = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")));
        tenant = deployment.required("service_id").textValue(); policy = deployment.required("deployment_id").textValue();
        otherTenant = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-identity.json"))).required("server_uuid").textValue();
        directory = Files.createTempDirectory(root.resolve(".implementation-work"), "transfer-concurrency-check-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        permission = Files.createTempFile(directory, "technical-permission-", ".identity",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        first = client(secrets.required("redis_password").textValue()); second = client(secrets.required("redis_password").textValue());
        requireTechnicalPermission();
    }

    private static RedissonClient client(String password) {
        var config = new Config(); config.setThreads(2).setNettyThreads(2);
        config.useSingleServer().setAddress("redis://127.0.0.1:16379").setPassword(password).setDatabase(0)
            .setRetryAttempts(0).setConnectTimeout(3000).setTimeout(3000).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
        return Redisson.create(config);
    }
    private static void requireTechnicalPermission() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        assertDoesNotThrow(() -> {
            try (var statement = database.createStatement();
                    var rows = statement.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                assertTrue(rows.next()); assertEquals(actor, rows.getString(1)); assertFalse(rows.next());
            }
        });
        if (!assertDoesNotThrow(() -> Files.getPosixFilePermissions(permission)).equals(PosixFilePermissions.fromString("rw-------")))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private Class<?> implementation() throws Exception {
        assertNotNull(getClass().getClassLoader().getResource(NAME.replace('.', '/') + ".class"),
            "实际Redis客户端必须共享企业和用户传输名额并固定配置与租期");
        return Class.forName(NAME);
    }
    private Object limiter(Class<?> type, RedissonClient client, String prefix) throws Exception {
        return type.getDeclaredConstructor(RedissonClient.class, String.class).newInstance(client, prefix);
    }
    private Object limits(Class<?> type, int tenantPermits, int userPermits, Duration duration) throws Exception {
        return Class.forName(NAME + "$Limits").getDeclaredConstructor(String.class, int.class, int.class, Duration.class)
            .newInstance(policy, tenantPermits, userPermits, duration);
    }
    private AutoCloseable acquire(Class<?> type, Object limiter, String target, Object limits) throws Exception {
        return (AutoCloseable) type.getDeclaredMethod("acquire", String.class, String.class, Class.forName(NAME + "$Limits"), S3ObjectReader.Admission.class)
            .invoke(limiter, target, actor, limits, (S3ObjectReader.Admission) TransferConcurrencyTest::requireTechnicalPermission);
    }
    private String prefix() { return "medical:engineering:transfer:" + UUID.randomUUID(); }
    private void code(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(InvocationTargetException.class, action);
        assertEquals(code, ((TenantAccessException) failure.getCause()).code());
    }
    private void cleanup(String prefix) {
        for (var key : first.getKeys().getKeysByPattern(prefix + ":*")) first.getKeys().delete(key);
    }

    private S3ObjectReader.Admission guarded(AutoCloseable lease, S3ObjectReader.Admission stage) throws Exception {
        var method = java.util.Arrays.stream(lease.getClass().getDeclaredMethods())
            .filter(item -> item.getName().equals("guard")).findFirst().orElse(null);
        assertNotNull(method, "恢复及完成的领域准入必须保留当前企业和用户的原租期与容量检查");
        return (S3ObjectReader.Admission) method.invoke(lease, stage);
    }

    private Duration remaining(Object admission) throws Exception {
        var method = java.util.Arrays.stream(admission.getClass().getDeclaredMethods())
            .filter(item -> item.getName().equals("remainingTime")).findFirst().orElse(null);
        assertNotNull(method, "实际对象请求必须采用原传输租期的剩余时间，阶段包装不能延长期限");
        return (Duration) method.invoke(admission);
    }

    private StaticCredentialsProvider objectCredentials() throws Exception {
        var secret = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/service-secret.json")));
        assertEquals(deployment.required("service_id"), secret.required("service_id"));
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(secret.required("access_key").textValue(), secret.required("secret_key").textValue()));
    }

    private ObjectRef publicOriginal() throws Exception {
        var objects = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertFalse(objects.required("customer_records_created").booleanValue()); assertFalse(objects.required("formal_evidence_eligible").booleanValue());
        var item = objects.required("objects").get(0);
        return new ObjectRef(item.required("object_key").textValue(), item.required("object_version").textValue(),
            item.required("sha256").textValue(), item.required("size_bytes").textValue(), item.required("media_type").textValue());
    }

    private void signal(String signal) throws Exception {
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        assertEquals(0, new ProcessBuilder("/bin/kill", "-" + signal, deployment.required("pid").asText()).start().waitFor());
    }

    private void stalledActualService(Object admission, String operation, org.junit.jupiter.api.function.Executable request) throws Exception {
        assertTrue(remaining(admission).compareTo(Duration.ZERO) > 0); requireTechnicalPermission();
        for (String table : java.util.List.of("tenant", "project", "object_upload_session")) {
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertFalse(rows.next());
            }
        }
        var protection = new java.util.concurrent.ScheduledThreadPoolExecutor(1); protection.setRemoveOnCancelPolicy(true);
        var resume = protection.schedule(() -> ThrowingSupplier.of(() -> { signal("CONT"); return null; }).get(), 5, TimeUnit.SECONDS);
        try {
            signal("STOP");
            var state = new ProcessBuilder("/bin/ps", "-o", "state=", "-p", deployment.required("pid").asText()).start();
            assertTrue(new String(state.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim().startsWith("T"));
            assertEquals(0, state.waitFor());
            long started = System.nanoTime();
            var failure = assertThrows(SdkException.class, request);
            assertTrue(failure instanceof ApiCallTimeoutException || failure instanceof ApiCallAttemptTimeoutException, failure.getClass().getName());
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsed < 4000, "请求必须在保护恢复以前由SDK截止终止");
            assertFalse(Thread.currentThread().isInterrupted());
            var result = JSON.createObjectNode(); result.put("operation", operation); result.put("elapsed_ms", elapsed);
            result.put("service_id", deployment.required("service_id").textValue()); result.put("pid", deployment.required("pid").longValue());
            System.out.println(JSON.writeValueAsString(result));
        } finally {
            try { signal("CONT"); if (!resume.cancel(false)) resume.get(); }
            finally { protection.shutdown(); assertTrue(protection.awaitTermination(3, TimeUnit.SECONDS)); }
        }
        requireTechnicalPermission();
    }

    @Test void originalRemainingTimeDecreasesAcrossCurrentStageChecks() throws Exception {
        var type = implementation(); String prefix = prefix();
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(3)))) {
                Duration original = remaining(lease); var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                TimeUnit.MILLISECONDS.sleep(150); Duration current = remaining(stage);
                assertTrue(current.compareTo(Duration.ZERO) > 0); assertTrue(current.compareTo(original) < 0); assertTrue(current.compareTo(Duration.ofSeconds(3)) < 0);
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualStoppedServiceGetIsAbortedByOriginalTransferDeadline() throws Exception {
        var type = implementation(); String prefix = prefix(); var original = publicOriginal(); var credentials = objectCredentials();
        try {
            try (var reader = new S3ObjectReader(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                    "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, directory, Long.parseLong(original.sizeBytes()), 4096);
                    var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(1)))) {
                var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                stalledActualService(stage, "GetObject", () -> {
                    try (var content = reader.readVerified(original, stage)) { content.chunk(0, 1); }
                });
                try (var content = reader.readVerified(original, TransferConcurrencyTest::requireTechnicalPermission)) {
                    assertArrayEquals(Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx")),
                        content.chunk(0, Integer.parseInt(original.sizeBytes())));
                }
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualStoppedServiceHeadIsAbortedByOriginalTransferDeadline() throws Exception {
        var type = implementation(); String prefix = prefix(); var original = publicOriginal(); var credentials = objectCredentials();
        var intent = new S3ObjectWriter.WriteIntent(policy, original.objectKey(), original.sha256(), original.sizeBytes(), original.mediaType());
        try {
            try (var writer = new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                    "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, directory, Long.parseLong(original.sizeBytes()), 4096);
                    var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(1)))) {
                var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                stalledActualService(stage, "HeadObject", () -> writer.recoverVerified(intent, stage));
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualStoppedServicePutIsAbortedWithoutAnotherWriteAttempt() throws Exception {
        var type = implementation(); String prefix = prefix(); String objectPrefix = "technical-transfer-deadline/" + UUID.randomUUID() + "/";
        var original = publicOriginal(); var credentials = objectCredentials();
        var intent = new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(), objectPrefix + "original", original.sha256(), original.sizeBytes(), original.mediaType());
        Path content = Files.createTempFile(directory, "deadline-original-", ".content", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(content, Files.readAllBytes(root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx")));
        try (var storage = S3Client.builder().endpointOverride(URI.create(deployment.required("endpoint").textValue()))
                .region(Region.US_EAST_1).credentialsProvider(credentials).forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder().proxyConfiguration(ProxyConfiguration.builder()
                    .useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()))
                .overrideConfiguration(options -> options.retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())).build()) {
            try {
                try (var writer = new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                        "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, directory, Long.parseLong(original.sizeBytes()), 4096);
                        var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(1)))) {
                    var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                    stalledActualService(stage, "PutObject", () -> writer.writeVerified(intent, content, stage));
                }
                long versions = 0;
                for (var page : storage.listObjectVersionsPaginator(ListObjectVersionsRequest.builder()
                        .bucket(deployment.required("bucket").textValue()).prefix(objectPrefix).build())) versions += page.versions().size();
                assertTrue(versions <= 1, "原条件写入发生超时后不得产生第二个版本");
            } finally {
                for (var page : storage.listObjectVersionsPaginator(ListObjectVersionsRequest.builder()
                        .bucket(deployment.required("bucket").textValue()).prefix(objectPrefix).build())) {
                    for (var version : page.versions()) storage.deleteObject(DeleteObjectRequest.builder()
                        .bucket(deployment.required("bucket").textValue()).key(version.key()).versionId(version.versionId()).build());
                }
            }
        } finally { cleanup(prefix); Files.delete(content); }
    }

    @Test void savedStageCannotContinueAfterOriginalCapacityIsReleased() throws Exception {
        var type = implementation(); String prefix = prefix();
        try {
            S3ObjectReader.Admission stage;
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)))) {
                stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission); stage.requireCurrent();
            }
            assertEquals(ErrorCode.LEASE_STALE, assertThrows(TenantAccessException.class, stage::requireCurrent).code());
            try (var current = acquire(type, limiter(type, second, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)))) {
                assertEquals(ErrorCode.LEASE_STALE, assertThrows(TenantAccessException.class, stage::requireCurrent).code());
            }
        } finally { cleanup(prefix); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant", "user"})
    void originalPermitCannotUseCapacityReacquiredByAnotherRequest(String kind) throws Exception {
        var type = implementation(); String prefix = prefix();
        try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)))) {
            var field = lease.getClass().getDeclaredField(kind + "Permit"); field.setAccessible(true);
            String original = (String) field.get(lease);
            String identity = kind.equals("tenant") ? tenant : actor;
            var semaphore = second.getPermitExpirableSemaphore(prefix + ":" + kind + ":" + identity + ":permits");
            assertTrue(semaphore.tryRelease(original));
            String replacement = semaphore.tryAcquire(0, 30, TimeUnit.SECONDS);
            assertNotNull(replacement); assertNotEquals(original, replacement);
            try {
                var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                assertEquals(ErrorCode.LEASE_STALE, assertThrows(TenantAccessException.class, stage::requireCurrent).code());
                assertEquals(1, semaphore.getPermits()); assertEquals(0, semaphore.availablePermits());
                assertTrue(semaphore.getLeaseTime(replacement) > 0);
            } finally { assertTrue(semaphore.tryRelease(replacement)); }
        } finally { cleanup(prefix); }
    }

    @Test void originalPartPermitCannotUseCapacityReacquiredByAnotherRequest() throws Exception {
        String prefix = prefix(); String upload = deployment.required("deployment_id").textValue();
        var limiter = new TransferConcurrency(first, prefix);
        try (var lease = limiter.tryPart(tenant, upload, 1, new TransferConcurrency.Limits(policy, 1, 1, Duration.ofSeconds(30)))) {
            assertNotNull(lease); lease.requireCurrent();
            var field = lease.getClass().getDeclaredField("permit"); field.setAccessible(true);
            String original = (String) field.get(lease);
            var semaphore = second.getPermitExpirableSemaphore(prefix + ":part:" + tenant + ":" + upload + ":1");
            assertTrue(semaphore.tryRelease(original));
            String replacement = semaphore.tryAcquire(0, 30, TimeUnit.SECONDS);
            assertNotNull(replacement); assertNotEquals(original, replacement);
            try {
                assertEquals(ErrorCode.LEASE_STALE, assertThrows(TenantAccessException.class, lease::requireCurrent).code());
                assertEquals(1, semaphore.getPermits()); assertEquals(0, semaphore.availablePermits());
                assertTrue(semaphore.getLeaseTime(replacement) > 0);
            } finally { assertTrue(semaphore.tryRelease(replacement)); }
        } finally { cleanup(prefix); }
    }

    @Test void changedActualCapacityStopsHeldStageBeforeFurtherTransmission() throws Exception {
        var type = implementation(); String prefix = prefix();
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)))) {
                var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                second.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").setPermits(2);
                assertEquals(ErrorCode.SCOPE_STALE, assertThrows(TenantAccessException.class, stage::requireCurrent).code());
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualPermissionChangeDuringStageAdmissionStopsTransmission() throws Exception {
        var type = implementation(); String prefix = prefix();
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)))) {
                var stage = guarded(lease, () -> {
                    requireTechnicalPermission();
                    assertDoesNotThrow(() -> Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("r--------")));
                });
                try { assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, stage::requireCurrent).code()); }
                finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualObjectRecoveryAndCompleteAssemblyUseSameOriginalRedisPermits() throws Exception {
        var type = implementation(); String prefix = prefix(); String objectPrefix = "technical-transfer-completion/" + UUID.randomUUID() + "/";
        var original = root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx");
        var objects = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        String sha256 = objects.required("objects").get(0).required("sha256").textValue(); long size = Files.size(original);
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/service-secret.json")));
        assertEquals(deployment.required("service_id"), secrets.required("service_id"));
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(), secrets.required("secret_key").textValue()));
        var intent = new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(), objectPrefix + "part", sha256, Long.toString(size), "application/octet-stream");
        Path content = Files.createTempFile(directory, "completion-original-", ".content",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(content, Files.readAllBytes(original));
        try (var storage = S3Client.builder().endpointOverride(URI.create(deployment.required("endpoint").textValue()))
                .region(Region.US_EAST_1).credentialsProvider(credentials).forcePathStyle(true)
                .httpClientBuilder(ApacheHttpClient.builder().proxyConfiguration(ProxyConfiguration.builder()
                    .useSystemPropertyValues(false).useEnvironmentVariableValues(false).build()))
                .overrideConfiguration(options -> options.retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())).build()) {
            try {
                try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30)));
                        var writer = new S3ObjectWriter(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                            "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, directory, size, 4096);
                        var reader = new S3ObjectReader(URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
                            "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, credentials, directory, size, 4096)) {
                    var stage = guarded(lease, TransferConcurrencyTest::requireTechnicalPermission);
                    ObjectRef part = writer.writeVerified(intent, content, stage);
                    assertEquals(part, writer.recoverVerified(intent, stage));
                    try (var assembly = new UploadObjectAssembly(directory, size, BigInteger.valueOf(size), sha256, stage)) {
                        assembly.append(1, part, reader); assertEquals(-1, Files.mismatch(original, assembly.finish()));
                        assertEquals(1, first.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").acquiredPermits());
                        assertEquals(1, second.getPermitExpirableSemaphore(prefix + ":user:" + actor + ":permits").acquiredPermits());
                        code(ErrorCode.RATE_LIMITED, () -> acquire(type, limiter(type, second, prefix), tenant, limits(type, 1, 1, Duration.ofSeconds(30))));
                    }
                }
                assertEquals(0, first.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").acquiredPermits());
                assertEquals(0, second.getPermitExpirableSemaphore(prefix + ":user:" + actor + ":permits").acquiredPermits());
            } finally {
                for (var page : storage.listObjectVersionsPaginator(ListObjectVersionsRequest.builder()
                        .bucket(deployment.required("bucket").textValue()).prefix(objectPrefix).build())) {
                    for (var version : page.versions()) storage.deleteObject(DeleteObjectRequest.builder()
                        .bucket(deployment.required("bucket").textValue()).key(version.key()).versionId(version.versionId()).build());
                }
            }
        } finally { cleanup(prefix); Files.delete(content); }
    }

    @Test void independentActualClientsShareUserCapacityAndReleasePartialEnterpriseAcquisition() throws Exception {
        var type = implementation(); String prefix = prefix();
        var one = limiter(type, first, prefix); var two = limiter(type, second, prefix);
        var limits = limits(type, 2, 1, Duration.ofSeconds(30));
        try {
            try (var lease = acquire(type, one, tenant, limits)) {
                code(ErrorCode.RATE_LIMITED, () -> acquire(type, two, otherTenant, limits));
                code(ErrorCode.RATE_LIMITED, () -> acquire(type, two, tenant, limits));
                assertEquals(1, first.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").acquiredPermits());
                assertEquals(0, second.getPermitExpirableSemaphore(prefix + ":tenant:" + otherTenant + ":permits").acquiredPermits());
            }
            try (var lease = acquire(type, two, otherTenant, limits)) {
                lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease);
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualEnterpriseCapacityAppliesBeforeUserCapacity() throws Exception {
        var type = implementation(); String prefix = prefix(); var limits = limits(type, 1, 2, Duration.ofSeconds(30));
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits)) {
                code(ErrorCode.RATE_LIMITED, () -> acquire(type, limiter(type, second, prefix), tenant, limits));
            }
            try (var lease = acquire(type, limiter(type, second, prefix), tenant, limits)) {
                lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease);
            }
        } finally { cleanup(prefix); }
    }

    @Test void changedConfigurationCannotCreateAnotherCapacityPool() throws Exception {
        var type = implementation(); String prefix = prefix(); var one = limiter(type, first, prefix);
        try {
            try (var lease = acquire(type, one, tenant, limits(type, 2, 1, Duration.ofSeconds(30)))) {
                code(ErrorCode.SCOPE_STALE, () -> acquire(type, limiter(type, second, prefix), tenant, limits(type, 3, 1, Duration.ofSeconds(30))));
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualRedisLeaseExpiryStopsExistingTransferAndReleasesCapacity() throws Exception {
        var type = implementation(); String prefix = prefix(); var limits = limits(type, 1, 1, Duration.ofMillis(500));
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits)) {
                TimeUnit.MILLISECONDS.sleep(650);
                code(ErrorCode.LEASE_STALE, () -> lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease));
                try (var current = acquire(type, limiter(type, second, prefix), tenant, limits(type, 1, 1, Duration.ofMillis(500)))) {
                    current.getClass().getDeclaredMethod("requireCurrent").invoke(current);
                }
            }
        } finally { cleanup(prefix); }
    }

    @Test void realPermissionRevocationStopsHeldLeaseWithoutAllocatingNewCapacity() throws Exception {
        var type = implementation(); String prefix = prefix(); var limits = limits(type, 1, 1, Duration.ofSeconds(30));
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits)) {
                Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("r--------"));
                try {
                    code(ErrorCode.ACCESS_DENIED, () -> lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease));
                    code(ErrorCode.ACCESS_DENIED, () -> acquire(type, limiter(type, second, prefix), otherTenant, limits));
                } finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
            }
            try (var lease = acquire(type, limiter(type, second, prefix), tenant, limits)) {
                lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease);
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualCapacityModificationInvalidatesHeldTransfer() throws Exception {
        var type = implementation(); String prefix = prefix(); var limits = limits(type, 1, 1, Duration.ofSeconds(30));
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits)) {
                second.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").setPermits(2);
                code(ErrorCode.SCOPE_STALE, () -> lease.getClass().getDeclaredMethod("requireCurrent").invoke(lease));
            }
        } finally { cleanup(prefix); }
    }

    @Test void actualOriginalInputRequiresBothRedisCapacityPermits() throws Exception {
        var type = implementation(); String prefix = prefix(); var limits = limits(type, 1, 1, Duration.ofSeconds(30));
        var original = root.resolve("ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx");
        var objects = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        try {
            try (var lease = acquire(type, limiter(type, first, prefix), tenant, limits);
                    var input = Files.newInputStream(original);
                    var saved = IncomingUploadPart.capture(directory, input, Files.size(original),
                        objects.required("objects").get(0).required("sha256").textValue(), 16384, (S3ObjectReader.Admission) lease)) {
                assertEquals(-1, Files.mismatch(original, saved.path()));
                assertEquals(1, first.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").acquiredPermits());
                assertEquals(1, second.getPermitExpirableSemaphore(prefix + ":user:" + actor + ":permits").acquiredPermits());
                code(ErrorCode.RATE_LIMITED, () -> acquire(type, limiter(type, second, prefix), tenant, limits));
            }
            assertEquals(0, first.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").acquiredPermits());
            assertEquals(0, second.getPermitExpirableSemaphore(prefix + ":user:" + actor + ":permits").acquiredPermits());
        } finally { cleanup(prefix); }
    }

    @AfterAll static void closeActualClientsAndTechnicalInputs() throws Exception {
        first.shutdown(); second.shutdown(); database.rollback(); database.close(); Files.delete(permission); Files.delete(directory);
    }
}
