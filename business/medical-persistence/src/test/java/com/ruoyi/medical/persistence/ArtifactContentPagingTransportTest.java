package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.KeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP + production S3 reader; synthetic bytes/credentials, no external account or database. */
class ArtifactContentPagingTransportTest {
    private final byte[] bytes = "{\"label\":\"受控内容\",\"nodes\":[],\"note\":\"bounded byte pages\"}".getBytes(StandardCharsets.UTF_8);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private final AtomicBoolean revokeOnRead = new AtomicBoolean();
    private final AtomicBoolean corrupt = new AtomicBoolean();
    private HttpServer server;
    private Path spool;
    private S3ObjectReader reader;
    private ObjectContentPaging paging;
    private ObjectRef reference;
    private String authorization;

    @BeforeEach void startPrivateFixture() throws Exception {
        spool = Files.createTempDirectory("artifact-paging-contract-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        authorization = WireValues.sha256("test-session-tenant-project-domain-and-sources".getBytes(StandardCharsets.UTF_8));
        reference = new ObjectRef("private/test-document", "fixed-version", WireValues.sha256(bytes), Integer.toString(bytes.length), "application/json");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fixture/private/test-document", exchange -> {
            requests.incrementAndGet();
            if (!"versionId=fixed-version".equals(exchange.getRequestURI().getRawQuery())) {
                exchange.sendResponseHeaders(400, -1); exchange.close(); return;
            }
            String range = exchange.getRequestHeaders().getFirst("Range");
            var parts = range.substring("bytes=".length()).split("-");
            int start = Integer.parseInt(parts[0]), end = Integer.parseInt(parts[1]);
            byte[] selected = Arrays.copyOfRange(bytes, start, end + 1);
            if (corrupt.get()) selected[0] ^= 1;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + bytes.length);
            exchange.getResponseHeaders().set("x-amz-version-id", "fixed-version");
            if (revokeOnRead.get()) allowed.set(false);
            exchange.sendResponseHeaders(206, selected.length);
            try (var output = exchange.getResponseBody()) { output.write(selected); }
            finally { exchange.close(); }
        });
        server.start();
        reader = new S3ObjectReader(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture", "us-east-1",
            S3ObjectReader.Network.LOCAL_LOOPBACK, StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-local-id", "synthetic-local-secret")),
            spool, bytes.length, 16);
        var generator = KeyGenerator.getInstance("AES"); generator.init(256);
        paging = new ObjectContentPaging(reader, generator.generateKey(), UUID.randomUUID().toString(), Duration.ofMinutes(1), 65536);
    }

    @Test void realBytesReassembleAcrossPagesWithoutPublishingObjectIdentity() throws Exception {
        var assembly = new ObjectContentAssembly(reference, bytes.length); String cursor = null; int pages = 0;
        do {
            var page = paging.read(reference, this::current, 7, cursor, transport(Duration.ofSeconds(5)));
            assembly.accept(page); pages++;
            assertEquals(WireValues.sha256(Base64.getDecoder().decode(page.content())), page.chunkSha256());
            assertEquals(reference.sha256(), page.fullSha256());
            cursor = page.coverage().nextCursor();
            if (cursor != null) assertFalse(cursor.contains(reference.objectKey()));
        } while (cursor != null);
        assertTrue(pages > 1); assertTrue(requests.get() >= pages);
        assertArrayEquals(bytes, assembly.content());
    }

    @Test void cursorCannotMoveAcrossIdentityObjectOrPageSizeAndReplayIsExact() throws Exception {
        String cursor = paging.read(reference, this::current, 7, null, transport(Duration.ofSeconds(5))).coverage().nextCursor();
        var page = paging.read(reference, this::current, 7, cursor, transport(Duration.ofSeconds(5)));
        var replay = paging.read(reference, this::current, 7, cursor, transport(Duration.ofSeconds(5)));
        assertEquals(page.content(), replay.content()); assertEquals(page.startByte(), replay.startByte());
        assertEquals(page.endByte(), replay.endByte()); assertEquals(page.coverage().deliveredSha256(), replay.coverage().deliveredSha256());
        int before = requests.get();
        assertCode(ErrorCode.CURSOR_INVALID, () -> paging.read(reference, () -> "a".repeat(64), 7, cursor, transport(Duration.ofSeconds(5))));
        assertCode(ErrorCode.CURSOR_INVALID, () -> paging.read(reference, this::current, 8, cursor, transport(Duration.ofSeconds(5))));
        var other = new ObjectRef(reference.objectKey(), "other-version", reference.sha256(), reference.sizeBytes(), reference.mediaType());
        assertCode(ErrorCode.CURSOR_INVALID, () -> paging.read(other, this::current, 7, cursor, transport(Duration.ofSeconds(5))));
        assertEquals(before, requests.get(), "Rejected cursors must not contact object storage");
    }

    @Test void lastPageAloneCannotEstablishCompleteConsumption() throws Exception {
        String cursor = null; ObjectContentPaging.Page page;
        do {
            page = paging.read(reference, this::current, 7, cursor, transport(Duration.ofSeconds(5)));
            cursor = page.coverage().nextCursor();
        } while (cursor != null);
        assertTrue(page.coverage().complete());
        var partial = new ObjectContentAssembly(reference, bytes.length);
        ObjectContentPaging.Page last = page;
        assertCode(ErrorCode.CONTENT_MISMATCH, () -> partial.accept(last));
    }

    @Test void expiredTransportDeadlineStopsBeforeActualNetworkRequest() {
        assertCode(ErrorCode.LEASE_STALE, () -> paging.read(reference, this::current, 7, null, transport(Duration.ZERO)));
        assertEquals(0, requests.get());
    }

    @Test void sourceRevocationDuringActualReadReleasesFilesAndNeverReturnsAPage() {
        revokeOnRead.set(true);
        assertCode(ErrorCode.ACCESS_DENIED, () -> paging.read(reference, this::current, 7, null, transport(Duration.ofSeconds(5))));
        assertEquals(1, requests.get());
    }

    @Test void modifiedStoredBytesNeverReturnEvenAFirstPage() {
        corrupt.set(true);
        assertCode(ErrorCode.CONTENT_MISMATCH, () -> paging.read(reference, this::current, 7, null, transport(Duration.ofSeconds(5))));
        assertTrue(requests.get() > 0);
    }

    private String current() {
        if (!allowed.get()) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return authorization;
    }

    private S3ObjectReader.TimedAdmission transport(Duration remaining) {
        return new S3ObjectReader.TimedAdmission() {
            @Override public void requireCurrent() { current(); }
            @Override public Duration remainingTime() { return remaining; }
        };
    }

    private static void assertCode(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        // The AWS SDK may wrap an interceptor/admission failure; the denial still must be preserved.
        Throwable failure = assertThrows(Exception.class, action);
        while (!(failure instanceof TenantAccessException) && failure.getCause() != null) failure = failure.getCause();
        assertInstanceOf(TenantAccessException.class, failure);
        assertEquals(expected, ((TenantAccessException) failure).code());
    }

    @AfterEach void stopPrivateFixture() throws Exception {
        if (reader != null) reader.close(); if (server != null) server.stop(0);
        if (spool != null) {
            try (var files = Files.list(spool)) { assertEquals(0, files.count(), "Partial bytes must be cleaned after every outcome"); }
            Files.delete(spool);
        }
    }
}
