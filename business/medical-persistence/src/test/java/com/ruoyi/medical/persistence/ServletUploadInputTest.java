package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.util.function.ThrowingSupplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.adapter.JdkFlowAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class ServletUploadInputTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static Path root, directory, permission;
    private static JsonNode deployment, originals;
    private static Connection database;
    private static Properties credentials;
    private static RedissonClient redis;
    private static String actor;

    @BeforeAll static void actualServicesAndPublicOriginals() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        deployment = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")));
        originals = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertFalse(originals.required("customer_records_created").booleanValue());
        assertFalse(originals.required("formal_evidence_eligible").booleanValue());
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false); database.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); actor = rows.getString(1); assertFalse(rows.next());
        }
        var config = new Config(); config.setThreads(2).setNettyThreads(2);
        config.useSingleServer().setAddress("redis://127.0.0.1:16379").setPassword(secrets.required("redis_password").textValue())
            .setRetryAttempts(0).setConnectTimeout(3000).setTimeout(3000).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2);
        redis = Redisson.create(config);
        directory = Files.createTempDirectory(root.resolve(".implementation-work"), "servlet-input-check-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        permission = Files.createTempFile(directory, "technical-permission-", ".identity",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        requireTechnicalPermission(permission);
    }

    private static void requireTechnicalPermission(Path grantedPermission) {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        assertDoesNotThrow(() -> {
            try (var current = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials)) {
                current.setReadOnly(true); current.setAutoCommit(false); current.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try (var query = current.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
                    assertTrue(rows.next()); assertEquals(actor, rows.getString(1)); assertFalse(rows.next());
                }
            }
        });
        if (!assertDoesNotThrow(() -> Files.getPosixFilePermissions(grantedPermission, LinkOption.NOFOLLOW_LINKS)).equals(PosixFilePermissions.fromString("rw-------")))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }

    private static Path original(int index) { return root.resolve("ui-delivery/assets/public-evidence/" + (index == 0 ? "clinical-evaluation-2021.docx" : "safety-principles-2021.doc")); }
    private static String sha(int index) { return originals.required("objects").get(index).required("sha256").textValue(); }
    private Method implementation() {
        var method = Arrays.stream(IncomingUploadPart.class.getDeclaredMethods()).filter(item -> item.getName().equals("captureRequest")).findFirst().orElse(null);
        assertNotNull(method, "实际Servlet输入必须按原总期限解除等待，并保留全部原件与私有文件清理");
        return method;
    }
    private static long privateParts() throws Exception {
        try (var files = Files.list(directory)) { return files.filter(path -> path.getFileName().toString().startsWith("upload-input-")).count(); }
    }
    private static Throwable rootCause(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
    private void failed(Fixture server, ErrorCode expected) throws Exception {
        var failure = assertThrows(ExecutionException.class, () -> server.result.get(5, TimeUnit.SECONDS));
        var actual = rootCause(failure);
        assertEquals(expected, assertInstanceOf(TenantAccessException.class, actual, actual::toString).code());
        assertTrue(server.terminated.await(5, TimeUnit.SECONDS)); assertEquals(0, privateParts()); server.released();
    }

    @Test void actualHttpPreservesBothOriginalsAndReleasesBothRedisPermits() throws Exception {
        var method = implementation();
        for (int index = 0; index < 2; index++) {
            try (var server = new Fixture(method, index, sha(index), Duration.ofSeconds(10), 1024, 2)) {
                var response = server.send(HttpRequest.BodyPublishers.ofFile(original(index)), Files.size(original(index))).get(8, TimeUnit.SECONDS);
                assertEquals(200, response.statusCode()); assertEquals(sha(index), server.result.get(5, TimeUnit.SECONDS));
                assertEquals(Files.size(original(index)), server.received.get()); assertTrue(server.buffers.get() > 1);
                assertTrue(server.terminated.await(5, TimeUnit.SECONDS)); assertEquals(0, privateParts()); server.released();
            }
        }
    }

    @RepeatedTest(32) void stoppedActualSenderIsTerminatedByOriginalTotalDeadline() throws Exception {
        var method = implementation(); var resume = new CompletableFuture<Void>();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(1), 64, 2)) {
            var request = server.send(server.pacedOriginal(resume, Duration.ZERO, false), Files.size(original(0)));
            long started = System.nanoTime();
            try {
                failed(server, ErrorCode.LEASE_STALE); assertTrue(server.received.get() > 0);
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 4000);
            } finally { resume.complete(null); request.cancel(true); }
        }
    }

    @Test void continuousActualBytesDoNotExtendOriginalTotalDeadline() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(1), 64, 2)) {
            var request = server.send(server.pacedOriginal(null, Duration.ofMillis(120), false), Files.size(original(0)));
            long started = System.nanoTime();
            try {
                failed(server, ErrorCode.LEASE_STALE); assertTrue(server.received.get() >= 128); assertTrue(server.buffers.get() >= 2);
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 4000);
            } finally { request.cancel(true); }
        }
    }

    @Test void actualDifferentOriginalLengthIsRejectedBeforeBodyConsumption() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 1024, 2)) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(1)), Files.size(original(1)));
            try { failed(server, ErrorCode.CONTENT_MISMATCH); assertEquals(0, server.received.get()); }
            finally { request.cancel(true); }
        }
    }

    @Test void actualOtherOriginalDigestCannotValidateReceivedContent() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(1), Duration.ofSeconds(10), 1024, 2)) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(0)), Files.size(original(0)));
            try { failed(server, ErrorCode.CONTENT_MISMATCH); assertEquals(Files.size(original(0)), server.received.get()); }
            finally { request.cancel(true); }
        }
    }

    @Test void actualPermissionRevocationDuringHttpInputStopsReception() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(null, Duration.ofMillis(120), true), Files.size(original(0)));
            try { failed(server, ErrorCode.ACCESS_DENIED); }
            finally { request.cancel(true); server.stopSender(); }
        }
    }

    @Test void actualClientCancellationCleansPrivateContentAndOriginalPermits() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(null, Duration.ofMillis(120), false), Files.size(original(0)));
            try {
                assertTrue(server.firstBody.await(5, TimeUnit.SECONDS), "取消以前必须实际接收到原件内容");
                assertTrue(request.cancel(true), "实际网络请求必须仍在执行并接受取消");
                assertTrue(server.terminated.await(5, TimeUnit.SECONDS), "网络取消后必须完成本次接收和清理");
                assertEquals(0, privateParts()); server.released();
                assertFalse(server.result.isDone() && !server.result.isCompletedExceptionally());
            } finally { request.cancel(true); }
        }
    }

    @Test void stoppedActualSenderObservesRevocationWithoutAnotherByte() throws Exception {
        var resume = new CompletableFuture<Void>();
        try (var server = new Fixture(implementation(), 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(resume, Duration.ZERO, false), Files.size(original(0)));
            try {
                server.awaitStoppedRead();
                Files.setPosixFilePermissions(server.grantedPermission, PosixFilePermissions.fromString("r--------"));
                failed(server, ErrorCode.ACCESS_DENIED); assertEquals(64, server.received.get());
            } finally { request.cancel(true); resume.complete(null); }
        }
    }

    @Test void cancelledActualReceptionTerminatesWaitingInputAndReleasesOriginalPermits() throws Exception {
        var resume = new CompletableFuture<Void>();
        try (var server = new Fixture(implementation(), 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(resume, Duration.ZERO, false), Files.size(original(0)));
            try {
                server.awaitStoppedRead(); assertEquals(64, server.received.get());
                // 输入仍保持连接并等待剩余原件，取消服务端处理必须自行完成清理。
                java.util.Objects.requireNonNull(server.reception.get()).cancel();
                assertTrue(server.terminated.await(5, TimeUnit.SECONDS));
                assertEquals(0, privateParts()); server.released();
                assertEquals(64, server.received.get());
                assertFalse(server.result.isDone() && !server.result.isCompletedExceptionally());
            } finally { request.cancel(true); resume.complete(null); }
        }
    }

    @Test void stoppedActualSenderObservesActualPolicyRevisionChange() throws Exception {
        var resume = new CompletableFuture<Void>();
        try (var server = new Fixture(implementation(), 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(resume, Duration.ZERO, false), Files.size(original(0)));
            try {
                server.awaitStoppedRead();
                var policy = redis.<String>getBucket(server.prefix + ":tenant:" + server.tenant + ":policy", StringCodec.INSTANCE);
                var current = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(policy.get());
                current.put("revision", deployment.required("service_id").textValue());
                policy.set(JSON.writeValueAsString(current));
                failed(server, ErrorCode.SCOPE_STALE); assertEquals(64, server.received.get());
            } finally { request.cancel(true); resume.complete(null); }
        }
    }

    @Test void stoppedActualSenderObservesActualTenantCapacityChange() throws Exception {
        var resume = new CompletableFuture<Void>();
        try (var server = new Fixture(implementation(), 0, sha(0), Duration.ofSeconds(10), 64, 2)) {
            var request = server.send(server.pacedOriginal(resume, Duration.ZERO, false), Files.size(original(0)));
            try {
                server.awaitStoppedRead();
                var permits = redis.getPermitExpirableSemaphore(server.prefix + ":tenant:" + server.tenant + ":permits");
                permits.setPermits(2);
                var failure = assertThrows(ExecutionException.class, () -> server.result.get(5, TimeUnit.SECONDS));
                assertEquals(ErrorCode.SCOPE_STALE, ((TenantAccessException) rootCause(failure)).code());
                assertTrue(server.terminated.await(5, TimeUnit.SECONDS)); assertEquals(0, privateParts());
                assertEquals(2, permits.availablePermits());
                assertEquals(1, redis.getPermitExpirableSemaphore(server.prefix + ":user:" + actor + ":permits").availablePermits());
                assertEquals(64, server.received.get());
            } finally { request.cancel(true); resume.complete(null); }
        }
    }

    @Test void actualUnknownLengthIsRejectedBeforeBodyConsumption() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 1024, 2)) {
            var request = server.sendUnknownLength(server.pacedOriginal(null, Duration.ofMillis(120), false));
            try { failed(server, ErrorCode.CONTENT_MISMATCH); assertEquals(0, server.received.get()); }
            finally { request.cancel(true); server.stopSender(); }
        }
    }

    @Test void missingQueueDemandRejectsActualRequestBeforeConsumption() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 1024, 0)) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(0)), Files.size(original(0)));
            try { failed(server, ErrorCode.SCHEMA_INVALID); assertEquals(0, server.received.get()); }
            finally { request.cancel(true); }
        }
    }

    @Test void queueBudgetCannotExceedOriginalPartCapacity() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 3 * 1024 * 1024, 2)) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(0)), Files.size(original(0)));
            try { failed(server, ErrorCode.SCHEMA_INVALID); assertEquals(0, server.received.get()); }
            finally { request.cancel(true); }
        }
    }

    @Test void actualServletBufferCannotExceedConsumerConfiguration() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 64, 2, 1024)) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(0)), Files.size(original(0)));
            try { failed(server, ErrorCode.SCHEMA_INVALID); assertTrue(server.received.get() > 64); }
            finally { request.cancel(true); }
        }
    }

    @Test void actualNonBlockingReceiverRejectsFileConsumption() throws Exception {
        var method = implementation();
        try (var server = new Fixture(method, 0, sha(0), Duration.ofSeconds(10), 1024, 2, 1024,
                Schedulers.newSingle("actual-upload-event-loop"))) {
            var request = server.send(HttpRequest.BodyPublishers.ofFile(original(0)), Files.size(original(0)));
            try { failed(server, ErrorCode.SCHEMA_INVALID); assertEquals(0, server.received.get()); }
            finally { request.cancel(true); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Tomcat tomcat = new Tomcat();
        private final String prefix = "medical:engineering:servlet:" + UUID.randomUUID();
        private final String tenant = deployment.required("service_id").textValue();
        private final Scheduler receiver;
        private final Scheduler deadlines = Schedulers.newSingle("actual-upload-deadline");
        private final Scheduler checks = Schedulers.newBoundedElastic(1, 1, "actual-upload-admission");
        private final Scheduler clientTiming = Schedulers.newSingle("actual-upload-sender");
        private final java.util.concurrent.ExecutorService clientExecutor = Executors.newCachedThreadPool();
        private final HttpClient client = HttpClient.newBuilder().executor(clientExecutor).version(HttpClient.Version.HTTP_1_1).build();
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final CountDownLatch terminated = new CountDownLatch(1), firstBody = new CountDownLatch(1);
        private final CountDownLatch senderTerminated = new CountDownLatch(1);
        private final CompletableFuture<Void> senderStop = new CompletableFuture<>();
        private boolean paced;
        private final Path grantedPermission;
        private final AtomicLong received = new AtomicLong();
        private final AtomicInteger buffers = new AtomicInteger();
        private final AtomicReference<Thread> consumingThread = new AtomicReference<>();
        private final AtomicReference<org.reactivestreams.Subscription> reception = new AtomicReference<>();

        private Fixture(Method method, int index, String expectedSha, Duration duration, int segmentBytes, int demand) throws Exception {
            this(method, index, expectedSha, duration, segmentBytes, demand, segmentBytes);
        }
        private Fixture(Method method, int index, String expectedSha, Duration duration, int segmentBytes, int demand, int servletBufferBytes) throws Exception {
            this(method, index, expectedSha, duration, segmentBytes, demand, servletBufferBytes,
                Schedulers.newBoundedElastic(1, 1, "actual-upload-receiver"));
        }
        private Fixture(Method method, int index, String expectedSha, Duration duration, int segmentBytes, int demand, int servletBufferBytes, Scheduler receiver) throws Exception {
            this.receiver = receiver;
            grantedPermission = Files.createTempFile(directory, "request-permission-", ".identity",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            var limiter = new TransferConcurrency(redis, prefix);
            var limits = new TransferConcurrency.Limits(deployment.required("deployment_id").textValue(), 1, 1, duration);
            var base = Files.createTempDirectory(directory, "tomcat-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            tomcat.setBaseDir(base.toString()); tomcat.setHostname("127.0.0.1"); tomcat.setPort(0);
            tomcat.getConnector().setProperty("address", "127.0.0.1");
            var context = tomcat.addContext("", base.toString());
            var servlet = new ServletHttpHandlerAdapter((request, response) -> Mono.defer(() -> {
                var cancelled = reactor.core.publisher.Sinks.<Void>empty();
                return Mono.fromCallable(() -> {
                consumingThread.set(Thread.currentThread());
                try (var lease = limiter.acquire(tenant, actor, limits, () -> requireTechnicalPermission(grantedPermission))) {
                    var observed = new ServerHttpRequestDecorator(request) {
                        @Override public Flux<DataBuffer> getBody() {
                            return super.getBody().doOnNext(buffer -> { received.addAndGet(buffer.readableByteCount()); buffers.incrementAndGet(); firstBody.countDown(); });
                        }
                    };
                    try (var saved = (IncomingUploadPart) method.invoke(null, directory, observed, Files.size(original(index)), expectedSha,
                            segmentBytes, demand, lease, deadlines, Duration.ofMillis(100), checks, cancelled.asMono())) {
                        var actual = saved.path(); assertEquals(-1, Files.mismatch(original(index), actual));
                        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(actual));
                        lease.requireCurrent(); assertTrue(Thread.currentThread().getName().startsWith("actual-upload-receiver"));
                        return expectedSha;
                    }
                } finally { terminated.countDown(); }
                }).subscribeOn(receiver)
                    .doOnCancel(() -> cancelled.emitError(new TenantAccessException(ErrorCode.ACTION_STALE), reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST))
                    .doOnSubscribe(reception::set)
                    .doOnSuccess(result::complete).doOnError(result::completeExceptionally).then(response.setComplete());
            }));
            servlet.setBufferSize(servletBufferBytes);
            var wrapper = Tomcat.addServlet(context, "actual-upload-input", servlet); wrapper.setAsyncSupported(true);
            context.addServletMappingDecoded("/*", "actual-upload-input"); tomcat.start();
        }
        private CompletableFuture<HttpResponse<Void>> send(HttpRequest.BodyPublisher body, long size) {
            return client.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/"))
                .POST(HttpRequest.BodyPublishers.fromPublisher(body, size)).build(), HttpResponse.BodyHandlers.discarding());
        }
        private CompletableFuture<HttpResponse<Void>> sendUnknownLength(HttpRequest.BodyPublisher body) {
            return client.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/"))
                .POST(body).build(), HttpResponse.BodyHandlers.discarding());
        }
        private HttpRequest.BodyPublisher pacedOriginal(CompletableFuture<Void> resume, Duration delay, boolean revoke) {
            assertFalse(paced); paced = true;
            var bytes = DataBufferUtils.read(original(0), new DefaultDataBufferFactory(), 64, LinkOption.NOFOLLOW_LINKS)
                .map(buffer -> {
                    try { return buffer.toByteBuffer(); }
                    finally { DataBufferUtils.release(buffer); }
                }).index().concatMap(item -> Mono.defer(() -> {
                    if (revoke && item.getT1() >= 2)
                        ThrowingSupplier.of(() -> Files.setPosixFilePermissions(grantedPermission, PosixFilePermissions.fromString("r--------"))).get();
                    if (item.getT1() == 0) return Mono.just(item.getT2());
                    if (resume != null) return Mono.fromFuture(resume).thenReturn(item.getT2());
                    return Mono.delay(delay, clientTiming).thenReturn(item.getT2());
                }), 1).takeUntilOther(Mono.fromFuture(senderStop)).doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .doFinally(signal -> senderTerminated.countDown());
            return HttpRequest.BodyPublishers.fromPublisher(JdkFlowAdapter.publisherToFlowPublisher(bytes));
        }
        private void stopSender() throws Exception {
            if (paced) {
                senderStop.complete(null); assertTrue(senderTerminated.await(5, TimeUnit.SECONDS));
            }
        }
        private void released() {
            assertEquals(1, redis.getPermitExpirableSemaphore(prefix + ":tenant:" + tenant + ":permits").availablePermits());
            assertEquals(1, redis.getPermitExpirableSemaphore(prefix + ":user:" + actor + ":permits").availablePermits());
        }
        private void awaitStoppedRead() {
            // 必须观察实际消费线程正在等待原输入，并核查首段已保存，随后才能变更准入。
            await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(5)).until(() -> {
                var thread = consumingThread.get();
                if (thread == null || received.get() != 64) return false;
                boolean waiting = Arrays.stream(thread.getStackTrace()).anyMatch(frame ->
                    frame.getClassName().equals("org.springframework.core.io.buffer.SubscriberInputStream") && frame.getMethodName().equals("await"));
                try (var files = Files.list(directory)) {
                    var parts = files.filter(path -> path.getFileName().toString().startsWith("upload-input-")).toList();
                    return waiting && parts.size() == 1 && Files.size(parts.get(0)) == 64;
                }
            });
        }
        @Override public void close() throws Exception {
            try {
                stopSender();
                receiver.disposeGracefully().block(Duration.ofSeconds(12)); checks.disposeGracefully().block(Duration.ofSeconds(5));
                deadlines.disposeGracefully().block(Duration.ofSeconds(5));
                clientTiming.disposeGracefully().block(Duration.ofSeconds(5));
                tomcat.stop(); tomcat.destroy();
            }
            finally {
                receiver.dispose(); checks.dispose(); clientExecutor.shutdownNow(); assertTrue(clientExecutor.awaitTermination(5, TimeUnit.SECONDS));
                for (var key : redis.getKeys().getKeysByPattern(prefix + ":*")) redis.getKeys().delete(key);
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.filter(item -> !item.equals(directory) && !item.equals(permission)).sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }
    @AfterAll static void closeActualServicesAndTechnicalFiles() throws Exception {
        redis.shutdown(); database.close(); Files.delete(permission); Files.delete(directory);
    }
}
