package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class S3ObjectReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ObjectRef> REFERENCES = new ArrayList<>();
    private static final List<Path> ORIGINALS = new ArrayList<>();
    private static final AtomicInteger ADMISSIONS = new AtomicInteger();
    private static Path root;
    private static Path spool;
    private static JsonNode deployment;
    private static S3ObjectReader reader;

    @BeforeAll static void actualPrivateStorageAndOriginals() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path directory = root.resolve(".implementation-work/runtime/object-storage");
        deployment = JSON.readTree(Files.readAllBytes(directory.resolve("deployment.json")));
        var secrets = JSON.readTree(Files.readAllBytes(directory.resolve("service-secret.json")));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(directory.resolve("service-secret.json"), LinkOption.NOFOLLOW_LINKS));
        assertEquals(deployment.required("service_id"), secrets.required("service_id"));
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
            assertEquals(reference.sizeBytes(), Long.toString(Files.size(original)));
            ORIGINALS.add(original); REFERENCES.add(reference); maximum = Math.max(maximum, Files.size(original));
        }
        assertEquals(2, REFERENCES.size());
        spool = Files.createTempDirectory(root.resolve(".implementation-work"), "object-reader-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        reader = new S3ObjectReader(java.net.URI.create(deployment.required("endpoint").textValue()),
            deployment.required("bucket").textValue(), "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK,
            StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(),
                secrets.required("secret_key").textValue())), spool, maximum, Math.toIntExact(Files.size(ORIGINALS.get(0)) / 2));
        requireCurrentNativeDeployment();
    }

    private static void requireCurrentNativeDeployment() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        ADMISSIONS.incrementAndGet();
    }

    @AfterAll static void releasePrivateResources() throws Exception {
        reader.close();
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
        Files.delete(spool);
    }

    @Test void bothActualVersionsRemainReadableAndEveryReturnedChunkMatchesOriginal() throws Exception {
        assertEquals(REFERENCES.get(0).objectKey(), REFERENCES.get(1).objectKey());
        assertNotEquals(REFERENCES.get(0).objectVersion(), REFERENCES.get(1).objectVersion());
        for (int index = 0; index < REFERENCES.size(); index++) {
            var reference = REFERENCES.get(index);
            byte[] original = Files.readAllBytes(ORIGINALS.get(index));
            int before = ADMISSIONS.get();
            try (var verified = reader.readVerified(reference, S3ObjectReaderTest::requireCurrentNativeDeployment)) {
                assertEquals(reference, verified.reference());
                assertEquals(reference.sha256(), verified.fullSha256());
                int middle = original.length / 2;
                assertArrayEquals(Arrays.copyOfRange(original, 0, middle), verified.chunk(0, middle));
                assertArrayEquals(Arrays.copyOfRange(original, middle, original.length), verified.chunk(middle, original.length - middle));
                assertArrayEquals(original, verified.chunk(0, original.length));
                assertTrue(ADMISSIONS.get() >= before + 9);
            }
        }
    }

    @Test void actualOtherVersionCannotSatisfyOriginalReference() throws Exception {
        var original = REFERENCES.get(0);
        var changed = new ObjectRef(original.objectKey(), REFERENCES.get(1).objectVersion(), original.sha256(), original.sizeBytes(), original.mediaType());
        requireMismatch(changed);
    }

    @Test void actualBytesCannotSatisfyDifferentDigestSizeOrMediaType() throws Exception {
        for (var reference : REFERENCES) {
            String changedDigest = (reference.sha256().charAt(0) == '0' ? "1" : "0") + reference.sha256().substring(1);
            requireMismatch(new ObjectRef(reference.objectKey(), reference.objectVersion(), changedDigest, reference.sizeBytes(), reference.mediaType()));
            requireMismatch(new ObjectRef(reference.objectKey(), reference.objectVersion(), reference.sha256(),
                Long.toString(Long.parseLong(reference.sizeBytes()) - 1), reference.mediaType()));
            requireMismatch(new ObjectRef(reference.objectKey(), reference.objectVersion(), reference.sha256(), reference.sizeBytes(), reference.mediaType() + ";charset=UTF-8"));
        }
    }

    private static void requireMismatch(ObjectRef reference) throws Exception {
        Exception error = assertThrows(Exception.class, () -> {
            try (var ignored = reader.readVerified(reference, S3ObjectReaderTest::requireCurrentNativeDeployment)) { fail("不匹配的实际对象不得返回内容"); }
        });
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        assertInstanceOf(TenantAccessException.class, cause);
        assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) cause).code());
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void chunkBoundaryAndClosedReadFailBeforeReturningContent() throws Exception {
        var reference = REFERENCES.get(0);
        var verified = reader.readVerified(reference, S3ObjectReaderTest::requireCurrentNativeDeployment);
        try (verified) {
            assertThrows(IllegalArgumentException.class, () -> verified.chunk(-1, 1));
            assertThrows(IllegalArgumentException.class, () -> verified.chunk(0, 0));
            assertThrows(IllegalArgumentException.class, () -> verified.chunk(Long.parseLong(reference.sizeBytes()), 1));
            assertThrows(IllegalArgumentException.class, () -> verified.chunk(0, S3ObjectReader.MAX_SEGMENT_BYTES + 1));
        }
        assertThrows(IllegalStateException.class, () -> verified.chunk(0, 1));
    }
}
