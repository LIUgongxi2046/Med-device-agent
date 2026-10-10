package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.util.Base64URL;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class ObjectContentPagingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ObjectRef> REFERENCES = new ArrayList<>();
    private static final List<Path> ORIGINALS = new ArrayList<>();
    private static Path root, spool, deploymentFile;
    private static JsonNode deployment;
    private static S3ObjectReader reader;
    private static SecretKey key;
    private static String keyId;
    private static int chunkBytes;

    @BeforeAll static void actualStorageAndCryptographicKey() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        deploymentFile = root.resolve(".implementation-work/runtime/object-storage/deployment.json");
        deployment = JSON.readTree(Files.readAllBytes(deploymentFile));
        var secrets = JSON.readTree(Files.readAllBytes(deploymentFile.resolveSibling("service-secret.json")));
        assertEquals(deployment.required("service_id"), secrets.required("service_id"));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(deploymentFile.resolveSibling("service-secret.json")));
        var stored = JSON.readTree(Files.readAllBytes(root.resolve(
            ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertEquals("LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS", stored.required("scope").textValue());
        assertFalse(stored.required("customer_records_created").booleanValue());
        assertFalse(stored.required("formal_evidence_eligible").booleanValue());
        long maximum = 0;
        for (var object : stored.required("objects")) {
            Path original = root.resolve(object.required("source_ref").textValue()).toRealPath();
            var reference = new ObjectRef(object.required("object_key").textValue(), object.required("object_version").textValue(),
                object.required("sha256").textValue(), object.required("size_bytes").textValue(), object.required("media_type").textValue());
            assertEquals(reference.sha256(), WireValues.sha256(Files.readAllBytes(original)));
            ORIGINALS.add(original); REFERENCES.add(reference); maximum = Math.max(maximum, Files.size(original));
        }
        assertEquals(2, REFERENCES.size());
        spool = Files.createTempDirectory(root.resolve(".implementation-work"), "content-paging-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        chunkBytes = Math.toIntExact(Files.size(ORIGINALS.get(0)) / 2);
        reader = new S3ObjectReader(java.net.URI.create(deployment.required("endpoint").textValue()),
            deployment.required("bucket").textValue(), "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK,
            StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(),
                secrets.required("secret_key").textValue())), spool, maximum, chunkBytes);
        var generator = KeyGenerator.getInstance("AES"); generator.init(256); key = generator.generateKey(); keyId = UUID.randomUUID().toString();
    }

    // 技术准入核查实际服务与目录权限，不建立客户身份或读取回执。
    private static String currentTechnicalScope() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(deploymentFile))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        if (!assertDoesNotThrow(() -> Files.getPosixFilePermissions(spool, LinkOption.NOFOLLOW_LINKS))
                .equals(PosixFilePermissions.fromString("rwx------"))) throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED);
        return assertDoesNotThrow(() -> WireValues.sha256(Files.readAllBytes(deploymentFile)));
    }

    private static ObjectContentPaging paging() {
        return new ObjectContentPaging(reader, key, keyId, Duration.ofMinutes(1), chunkBytes);
    }

    private static JsonNode read(ObjectContentPaging paging, ObjectRef reference, Supplier<String> scope, int size, String cursor) throws Exception {
        return JSON.valueToTree(paging.read(reference, scope, size, cursor));
    }

    @Test void actualOriginalsAreCompletelyReassembledFromAuthenticatedPages() throws Exception {
        var paging = paging();
        for (int index = 0; index < REFERENCES.size(); index++) {
            var reference = REFERENCES.get(index);
            var result = new ByteArrayOutputStream(); String cursor = null, selection = null;
            int pages = 0;
            do {
                var page = read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor);
                byte[] bytes = Base64.getDecoder().decode(page.required("content").textValue());
                assertEquals("BASE64", page.required("encoding").textValue());
                assertEquals(Long.toString(result.size()), page.required("startByte").textValue());
                assertEquals(Long.toString(result.size() + bytes.length), page.required("endByte").textValue());
                assertEquals(WireValues.sha256(bytes), page.required("chunkSha256").textValue());
                assertEquals(reference.sha256(), page.required("fullSha256").textValue());
                assertEquals(reference.mediaType(), page.required("mediaType").textValue());
                assertTrue(bytes.length > 0 && bytes.length <= chunkBytes);
                var coverage = page.required("coverage");
                if (selection == null) selection = coverage.required("selectionSha256").textValue();
                assertEquals(selection, coverage.required("selectionSha256").textValue());
                result.write(bytes); pages++;
                assertEquals(result.size() == Long.parseLong(reference.sizeBytes()), coverage.required("complete").booleanValue());
                cursor = coverage.required("nextCursor").isNull() ? null : coverage.required("nextCursor").textValue();
                if (cursor != null) assertFalse(cursor.contains(reference.objectKey()));
            } while (cursor != null);
            assertTrue(pages > 1);
            assertArrayEquals(Files.readAllBytes(ORIGINALS.get(index)), result.toByteArray());
            assertEquals(reference.sha256(), WireValues.sha256(result.toByteArray()));
        }
    }

    @Test void repeatedCursorReturnsTheSameActualRangeAndDifferentBindingsAreRejected() throws Exception {
        var paging = paging(); var reference = REFERENCES.get(0);
        String cursor = read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null)
            .required("coverage").required("nextCursor").textValue();
        var first = read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor);
        var repeated = read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor);
        for (String field : List.of("content", "chunkSha256", "fullSha256", "startByte", "endByte", "coverage")) assertEquals(first.required(field), repeated.required(field));
        requireCursorInvalid(() -> read(paging, REFERENCES.get(1), ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor));
        Supplier<String> otherActualScope = () -> assertDoesNotThrow(() -> WireValues.sha256(Files.readAllBytes(ORIGINALS.get(0))));
        requireCursorInvalid(() -> read(paging, reference, otherActualScope, chunkBytes, cursor));
        requireCursorInvalid(() -> read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes - 1, cursor));
    }

    @Test void actualDirectoryPermissionWithdrawalStopsFurtherContentAndCleansFiles() throws Exception {
        var paging = paging(); var reference = REFERENCES.get(0);
        String cursor = read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null)
            .required("coverage").required("nextCursor").textValue();
        Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwxr-x---"));
        try {
            var error = assertThrows(TenantAccessException.class, () -> read(paging, reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor));
            assertEquals(ErrorCode.ACCESS_DENIED, error.code());
        }
        finally { Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwx------")); }
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    private static void requireCursorInvalid(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, action).code());
    }

    @Test void authenticatedEncryptionRejectsChangedTagAndActualKeyRotation() throws Exception {
        var paging = paging(); var reference = REFERENCES.get(0);
        String cursor = paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null).coverage().nextCursor();
        var original = JWEObject.parse(cursor); byte[] tag = original.getAuthTag().decode(); tag[0] ^= 1;
        var changed = new JWEObject(original.getParsedParts()[0], original.getEncryptedKey(), original.getIV(),
            original.getCipherText(), Base64URL.encode(tag));
        assertThrows(JOSEException.class, () -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, changed.serialize()));
        var generator = KeyGenerator.getInstance("AES"); generator.init(256);
        var rotated = new ObjectContentPaging(reader, generator.generateKey(), keyId, Duration.ofMinutes(1), chunkBytes);
        assertThrows(JOSEException.class, () -> rotated.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor));
        var newVersion = new ObjectContentPaging(reader, key, UUID.randomUUID().toString(), Duration.ofMinutes(1), chunkBytes);
        requireCursorInvalid(() -> newVersion.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor));
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void actualCursorExpiresWithoutExtendingItsOriginalLifetime() throws Exception {
        var paging = new ObjectContentPaging(reader, key, keyId, Duration.ofSeconds(2), chunkBytes);
        var reference = REFERENCES.get(0);
        String cursor = paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null).coverage().nextCursor();
        var encrypted = JWEObject.parse(cursor); encrypted.decrypt(new DirectDecrypter(key));
        var value = JSON.readTree(encrypted.getPayload().toBytes());
        assertEquals(Duration.ofSeconds(2), Duration.between(WireValues.utcTime(value.required("issued_at").textValue()),
            WireValues.utcTime(value.required("expires_at").textValue())));
        Thread.sleep(2100);
        assertEquals(ErrorCode.CURSOR_EXPIRED, assertThrows(TenantAccessException.class,
            () -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor)).code());
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void authenticatedCursorStillRequiresClosedFieldsAndValidByteBoundaries() throws Exception {
        var paging = paging(); var reference = REFERENCES.get(0);
        String cursor = paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null).coverage().nextCursor();
        var original = JWEObject.parse(cursor); original.decrypt(new DirectDecrypter(key));
        var value = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(original.getPayload().toBytes());
        var extra = value.deepCopy(); extra.put("object_key", reference.objectKey());
        requireCursorInvalid(() -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, encrypt(original, extra)));
        for (String start : List.of("0", "1", reference.sizeBytes())) {
            var boundary = value.deepCopy(); boundary.put("start_byte", start);
            requireCursorInvalid(() -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, encrypt(original, boundary)));
        }
        requireCursorInvalid(() -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, ""));
        requireCursorInvalid(() -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, "A".repeat(8193)));
        assertThrows(IllegalArgumentException.class, () -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, 0, null));
        assertThrows(IllegalArgumentException.class, () -> paging.read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes + 1, null));
    }

    private static String encrypt(JWEObject original, JsonNode value) throws Exception {
        var changed = new JWEObject(original.getHeader(), new Payload(JSON.writeValueAsBytes(value)));
        changed.encrypt(new DirectEncrypter(key)); return changed.serialize();
    }

    @Test void productionCompleteReadReturnsBothActualFixedOriginals() throws Exception {
        for (int index = 0; index < REFERENCES.size(); index++) {
            byte[] result = paging().readComplete(REFERENCES.get(index),
                ObjectContentPagingTest::currentTechnicalScope, chunkBytes,
                Math.toIntExact(Files.size(ORIGINALS.get(index))));
            assertArrayEquals(Files.readAllBytes(ORIGINALS.get(index)), result);
        }
    }

    @Test void productionAssemblerRequiresEveryPageBeforeReturningContent() throws Exception {
        var reference = REFERENCES.get(0);
        var assembly = new ObjectContentAssembly(reference, Math.toIntExact(Files.size(ORIGINALS.get(0))));
        var page = paging().read(reference, ObjectContentPagingTest::currentTechnicalScope, chunkBytes, null);
        assembly.accept(page);
        requireContentMismatch(assembly::content);
    }

    @Test void capacityRejectionPrecedesActualStorageAdmission() throws Exception {
        var reference = REFERENCES.get(0);
        Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwxr-x---"));
        try {
            var error = assertThrows(TenantAccessException.class, () -> paging().readComplete(reference,
                ObjectContentPagingTest::currentTechnicalScope, chunkBytes, Math.toIntExact(Files.size(ORIGINALS.get(0))) - 1));
            assertEquals(ErrorCode.CONTEXT_LIMIT, error.code());
            assertThrows(IllegalArgumentException.class, () -> paging().readComplete(reference,
                ObjectContentPagingTest::currentTechnicalScope, chunkBytes, 0));
        }
        finally { Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwx------")); }
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void completeReadStopsWhenActualDirectoryAdmissionIsWithdrawn() throws Exception {
        Supplier<String> withdrawal = () -> {
            String current = currentTechnicalScope();
            assertDoesNotThrow(() -> Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwxr-x---")));
            return current;
        };
        try {
            var error = assertThrows(TenantAccessException.class, () -> paging().readComplete(REFERENCES.get(0),
                withdrawal, chunkBytes, Math.toIntExact(Files.size(ORIGINALS.get(0)))));
            assertEquals(ErrorCode.ACCESS_DENIED, error.code());
        }
        finally { Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwx------")); }
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void repeatedMissingAndOutOfOrderActualPagesCannotBecomeCompleteContent() throws Exception {
        var pages = actualPages(1); var reference = REFERENCES.get(1);
        var repeated = assembly(reference); repeated.accept(pages.get(0));
        requireContentMismatch(() -> repeated.accept(pages.get(0)));
        requireContentMismatch(() -> repeated.accept(pages.get(1)));
        requireContentMismatch(repeated::content);
        var missing = assembly(reference); missing.accept(pages.get(0));
        requireContentMismatch(() -> missing.accept(pages.get(2)));
        requireContentMismatch(missing::content);
        var outOfOrder = assembly(reference);
        requireContentMismatch(() -> outOfOrder.accept(pages.get(pages.size() - 1)));
        requireContentMismatch(outOfOrder::content);
    }

    @Test void alteredActualPageMetadataRangesAndChunkDigestAreRejected() throws Exception {
        var pages = actualPages(0); var first = pages.get(0); var second = pages.get(1);
        var other = actualPages(1).get(0);
        for (var changed : List.of(
            new ObjectContentPaging.Page(com.ruoyi.medical.contracts.InterfaceProtocol.Encoding.UTF8, first.content(), first.mediaType(), first.chunkSha256(), first.fullSha256(), first.startByte(), first.endByte(), first.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), other.mediaType(), first.chunkSha256(), first.fullSha256(), first.startByte(), first.endByte(), first.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), first.mediaType(), first.chunkSha256(), other.fullSha256(), first.startByte(), first.endByte(), first.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), first.mediaType(), second.chunkSha256(), first.fullSha256(), first.startByte(), first.endByte(), first.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), first.mediaType(), first.chunkSha256(), first.fullSha256(), first.startByte(), second.endByte(), first.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), first.mediaType(), first.chunkSha256(), first.fullSha256(), first.startByte(), first.endByte(), other.coverage()),
            new ObjectContentPaging.Page(first.encoding(), first.content(), first.mediaType(), first.chunkSha256(), first.fullSha256(), first.startByte(), first.endByte(), second.coverage()))) {
            var assembly = assembly(REFERENCES.get(0));
            requireContentMismatch(() -> assembly.accept(changed));
            requireContentMismatch(assembly::content);
        }
    }

    @Test void actualByteCorruptionCannotPassUsingRecomputedChunkDigest() throws Exception {
        var pages = actualPages(0); var assembly = assembly(REFERENCES.get(0));
        assembly.accept(pages.get(0));
        var last = pages.get(1); byte[] changed = Base64.getDecoder().decode(last.content()); changed[0] ^= 1;
        var corrupted = new ObjectContentPaging.Page(last.encoding(), Base64.getEncoder().encodeToString(changed),
            last.mediaType(), WireValues.sha256(changed), last.fullSha256(), last.startByte(), last.endByte(), last.coverage());
        requireContentMismatch(() -> assembly.accept(corrupted));
        requireContentMismatch(assembly::content);
    }

    @Test void completeContentUsesIndependentReturnedBytesAndCannotAcceptFurtherPages() throws Exception {
        var pages = actualPages(0); var assembly = assembly(REFERENCES.get(0));
        for (var page : pages) assembly.accept(page);
        byte[] result = assembly.content(); assertArrayEquals(Files.readAllBytes(ORIGINALS.get(0)), result);
        result[0] ^= 1;
        assertArrayEquals(Files.readAllBytes(ORIGINALS.get(0)), assembly.content());
        requireContentMismatch(() -> assembly.accept(pages.get(0)));
    }

    private static ObjectContentAssembly assembly(ObjectRef reference) throws Exception {
        return new ObjectContentAssembly(reference, Integer.parseInt(reference.sizeBytes()));
    }

    private static List<ObjectContentPaging.Page> actualPages(int index) throws Exception {
        var result = new ArrayList<ObjectContentPaging.Page>(); var paging = paging(); String cursor = null;
        do {
            var page = paging.read(REFERENCES.get(index), ObjectContentPagingTest::currentTechnicalScope, chunkBytes, cursor);
            result.add(page); cursor = page.coverage().nextCursor();
        } while (cursor != null);
        return List.copyOf(result);
    }

    private static void requireContentMismatch(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, action).code());
    }

    @AfterAll static void closeActualStorageReader() throws Exception {
        reader.close();
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
        Files.delete(spool);
    }
}
