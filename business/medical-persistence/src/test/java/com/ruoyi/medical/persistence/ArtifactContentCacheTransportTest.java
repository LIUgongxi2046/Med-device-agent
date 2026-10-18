package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactContentCacheTransportTest {
    private final byte[] bytes = new byte[257];
    private final AtomicLong backingBytes = new AtomicLong();
    private final AtomicBoolean allowed = new AtomicBoolean(true), corrupt = new AtomicBoolean();
    private final String keyId = UUID.randomUUID().toString();
    private final ArtifactContentPageCache.Owner owner = owner(UUID.randomUUID().toString());
    private HttpServer server;
    private Path root;
    private S3ObjectReader reader;
    private ArtifactContentPageCache cache;
    private SecretKey key;
    private ObjectRef object;
    private String authorization;
    private volatile CountDownLatch entered, release;

    @BeforeEach void fixture() throws Exception {
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) index;
        root = Files.createTempDirectory("artifact-cache-contract-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        object = new ObjectRef("private/fixed", "fixed-version", WireValues.sha256(bytes), Integer.toString(bytes.length), "application/octet-stream");
        authorization = WireValues.sha256("fixed-full-tenant-subject-document-authorization".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var generator = KeyGenerator.getInstance("AES"); generator.init(256); key = generator.generateKey();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fixture/private/fixed", exchange -> {
            if (entered != null) entered.countDown();
            if (release != null) try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Fixture timeout"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new java.io.IOException(failure); }
            var range = exchange.getRequestHeaders().getFirst("Range").substring(6).split("-");
            int first = Integer.parseInt(range[0]), last = Integer.parseInt(range[1]);
            var part = Arrays.copyOfRange(bytes, first, last + 1); if (corrupt.get()) part[0] ^= 1;
            exchange.getResponseHeaders().set("Content-Type", object.mediaType());
            exchange.getResponseHeaders().set("Content-Range", "bytes " + first + "-" + last + "/" + bytes.length);
            exchange.getResponseHeaders().set("x-amz-version-id", object.objectVersion());
            exchange.sendResponseHeaders(206, part.length); backingBytes.addAndGet(part.length);
            try (var output = exchange.getResponseBody()) { output.write(part); } finally { exchange.close(); }
        });
        server.start(); reader = reader();
        cache = new ArtifactContentPageCache(root, 20L * bytes.length, 20, 10L * bytes.length, 5L * bytes.length, 2);
    }

    @Test void completePaginationIncludingOneByteLimitReadsBackingObjectExactlyOncePerSequence() throws Exception {
        for (int limit : new int[]{1, 7, 65536}) {
            long before = backingBytes.get(); var assembly = new ObjectContentAssembly(object, bytes.length);
            String cursor = null;
            do {
                var page = paging(owner, Duration.ofMinutes(1)).read(object, this::current, limit, cursor, transport());
                assembly.accept(page); cursor = page.coverage().nextCursor();
            } while (cursor != null);
            assertArrayEquals(bytes, assembly.content());
            assertEquals(bytes.length, backingBytes.get() - before, "Backing I/O must be O(object size), including limit=1");
        }
        assertEquals(3L * bytes.length, cache.reservedBytes());
    }

    @Test void freshReaderAndTransportLeaseReuseVerifiedSpoolWithoutNetwork() throws Exception {
        String cursor = paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, null, transport()).coverage().nextCursor();
        reader.close(); reader = reader();
        long before = backingBytes.get();
        var page = paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, cursor, transport());
        assertEquals("7", page.startByte()); assertEquals(before, backingBytes.get());
        allowed.set(false);
        assertCode(ErrorCode.ACCESS_DENIED, () -> paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, cursor, transport()));
        assertEquals(before, backingBytes.get());
    }

    @Test void lostOrDifferentOwnerContinuationFailsClosedWithoutRefetch() throws Exception {
        String cursor = paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, null, transport()).coverage().nextCursor();
        long before = backingBytes.get();
        assertCode(ErrorCode.CURSOR_EXPIRED, () -> paging(owner(owner.tenantId()), Duration.ofMinutes(1))
            .read(object, this::current, 7, cursor, transport()));
        cache.close(); cache = new ArtifactContentPageCache(root, 1024, 2, 1024, 1024, 1);
        assertCode(ErrorCode.CURSOR_EXPIRED, () -> paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, cursor, transport()));
        assertEquals(before, backingBytes.get()); assertEquals(0, cache.reservedBytes());
    }

    @Test void globalTenantAndSubjectQuotasRejectBeforeBackingIo() throws Exception {
        cache.close(); cache = new ArtifactContentPageCache(root, 3L * bytes.length, 4, 2L * bytes.length, bytes.length, 2);
        first(owner);
        assertCode(ErrorCode.CONTEXT_LIMIT, () -> first(owner));
        first(owner(owner.tenantId()));
        assertCode(ErrorCode.CONTEXT_LIMIT, () -> first(owner(owner.tenantId())));
        first(owner(UUID.randomUUID().toString()));
        assertCode(ErrorCode.CONTEXT_LIMIT, () -> first(owner(UUID.randomUUID().toString())));
        assertEquals(3L * bytes.length, backingBytes.get()); assertEquals(3L * bytes.length, cache.reservedBytes());
    }

    @Test void entryQuotaIsIndependentOfByteCapacity() throws Exception {
        cache.close(); cache = new ArtifactContentPageCache(root, 10000, 1, 10000, 10000, 1);
        first(owner);
        assertCode(ErrorCode.CONTEXT_LIMIT, () -> first(owner(UUID.randomUUID().toString())));
        assertEquals(bytes.length, backingBytes.get());
    }

    @Test void concurrentTransfersAreFailFastAndNeverDownloadDuplicateObjects() throws Exception {
        cache.close(); cache = new ArtifactContentPageCache(root, 10000, 4, 10000, 10000, 1);
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> { first(owner); return true; });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertCode(ErrorCode.RATE_LIMITED, () -> first(owner));
            release.countDown(); assertTrue(first.get(5, TimeUnit.SECONDS));
            assertEquals(bytes.length, backingBytes.get()); assertEquals(bytes.length, cache.reservedBytes());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void digestFailureReleasesReservationAndCannotPopulateCache() throws Exception {
        corrupt.set(true);
        assertCode(ErrorCode.CONTENT_MISMATCH, () -> first(owner));
        assertEquals(0, cache.reservedBytes());
        corrupt.set(false); first(owner); assertEquals(bytes.length, cache.reservedBytes());
        try (var paths = Files.walk(root)) { assertEquals(0, paths.filter(Files::isRegularFile).count(), "Spools must be unlinked/private"); }
    }

    @Test void idleExpiryReclaimsCapacityAndShutdownCleansDirectories() throws Exception {
        String cursor = paging(owner, Duration.ofSeconds(1)).read(object, this::current, 7, null, transport()).coverage().nextCursor();
        assertEquals(bytes.length, cache.reservedBytes());
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(4)).until(() -> cache.reservedBytes() == 0);
        long before = backingBytes.get();
        assertCode(ErrorCode.CURSOR_EXPIRED, () -> paging(owner, Duration.ofSeconds(1)).read(object, this::current, 7, cursor, transport()));
        assertEquals(before, backingBytes.get());
        cache.close(); try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
    }

    private void first(ArtifactContentPageCache.Owner owner) throws Exception {
        paging(owner, Duration.ofMinutes(1)).read(object, this::current, 7, null, transport());
    }
    private ObjectContentPaging paging(ArtifactContentPageCache.Owner owner, Duration lifetime) {
        return new ObjectContentPaging(reader, key, keyId, lifetime, 65536, cache, owner);
    }
    private S3ObjectReader reader() throws Exception {
        return new S3ObjectReader(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture", "us-east-1",
            S3ObjectReader.Network.LOCAL_LOOPBACK, StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-local-id", "synthetic-local-secret")),
            root, bytes.length, 64);
    }
    private static ArtifactContentPageCache.Owner owner(String tenant) { return new ArtifactContentPageCache.Owner(tenant, UUID.randomUUID().toString()); }
    private String current() { if (!allowed.get()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return authorization; }
    private S3ObjectReader.TimedAdmission transport() {
        return new S3ObjectReader.TimedAdmission() {
            @Override public void requireCurrent() { current(); }
            @Override public Duration remainingTime() { return Duration.ofSeconds(5); }
        };
    }
    private static void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        Throwable failure = assertThrows(Exception.class, action);
        while (!(failure instanceof TenantAccessException) && failure.getCause() != null) failure = failure.getCause();
        assertInstanceOf(TenantAccessException.class, failure); assertEquals(code, ((TenantAccessException) failure).code());
    }
    @AfterEach void cleanup() throws Exception {
        if (release != null) release.countDown(); if (reader != null) reader.close(); if (server != null) server.stop(0);
        if (cache != null) cache.close();
        if (root != null) { try (var files = Files.list(root)) { assertEquals(0, files.count()); } Files.delete(root); }
    }
}
