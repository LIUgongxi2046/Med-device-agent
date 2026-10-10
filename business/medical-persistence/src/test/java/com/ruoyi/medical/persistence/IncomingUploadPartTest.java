package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IncomingUploadPartTest {
    private static final String NAME = "com.ruoyi.medical.persistence.IncomingUploadPart";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger admissions = new AtomicInteger();
    private static Path root, directory, permission;
    private static JsonNode deployment, objects;

    @BeforeAll static void actualOriginalsAndDeployment() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        deployment = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")));
        objects = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        directory = Files.createTempDirectory(root.resolve(".implementation-work"), "incoming-part-check-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        permission = Files.createTempFile(directory, "technical-permission-", ".identity",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        requireTechnicalPermission();
    }

    private static void requireTechnicalPermission() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json")))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
        if (!assertDoesNotThrow(() -> Files.getPosixFilePermissions(permission, LinkOption.NOFOLLOW_LINKS)).equals(PosixFilePermissions.fromString("rw-------")))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        admissions.incrementAndGet();
    }

    private Path original(int index) {
        return root.resolve("ui-delivery/assets/public-evidence/" + (index == 0 ? "clinical-evaluation-2021.docx" : "safety-principles-2021.doc"));
    }

    private Class<?> implementation() throws Exception {
        assertNotNull(getClass().getClassLoader().getResource(NAME.replace('.', '/') + ".class"),
            "上传输入需要完整读取实际片段并核查原范围、摘要、私有文件和当前准入");
        return Class.forName(NAME);
    }

    private Object capture(Class<?> type, InputStream source, long size, String sha, S3ObjectReader.Admission admission) throws Exception {
        return type.getDeclaredMethod("capture", Path.class, InputStream.class, long.class, String.class, int.class,
            S3ObjectReader.Admission.class).invoke(null, directory, source, size, sha, Math.toIntExact(Files.size(original(0)) / 2), admission);
    }

    private long fileCount() throws Exception { try (var files = Files.list(directory)) { return files.count(); } }

    @Test void bothActualPublicOriginalsRetainEveryByteAndPrivateFileIdentity() throws Exception {
        var type = implementation();
        for (int index = 0; index < 2; index++) {
            var original = original(index); String sha = objects.required("objects").get(index).required("sha256").textValue();
            try (var stream = Files.newInputStream(original); var saved = (AutoCloseable) capture(type, stream, Files.size(original), sha,
                    IncomingUploadPartTest::requireTechnicalPermission)) {
                var actual = (Path) type.getDeclaredMethod("path").invoke(saved);
                assertEquals(-1, Files.mismatch(original, actual)); assertEquals(directory, actual.getParent());
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(actual, LinkOption.NOFOLLOW_LINKS));
                assertEquals(Files.getOwner(directory), Files.getOwner(actual));
            }
            assertEquals(1, fileCount());
        }
        assertTrue(admissions.get() > 4);
    }

    @Test void actualSizeAndDigestConflictsNeverKeepPartialContent() throws Exception {
        var type = implementation(); var original = original(0); long size = Files.size(original);
        String sha = objects.required("objects").get(0).required("sha256").textValue();
        String otherSha = objects.required("objects").get(1).required("sha256").textValue();
        for (long expected : List.of(size - 1, size + 1)) {
            try (var stream = Files.newInputStream(original)) {
                var failure = assertThrows(InvocationTargetException.class,
                    () -> capture(type, stream, expected, sha, IncomingUploadPartTest::requireTechnicalPermission));
                assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
            }
            assertEquals(1, fileCount());
        }
        try (var stream = Files.newInputStream(original)) {
            var failure = assertThrows(InvocationTargetException.class,
                () -> capture(type, stream, size, otherSha, IncomingUploadPartTest::requireTechnicalPermission));
            assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
        }
        assertEquals(1, fileCount());
    }

    @Test void additionalActualOriginalBytesAreRejectedBeforeTheyCanBecomeAnotherPart() throws Exception {
        var type = implementation(); var original = original(0); String sha = objects.required("objects").get(0).required("sha256").textValue();
        try (var stream = new SequenceInputStream(Files.newInputStream(original), Files.newInputStream(original))) {
            var failure = assertThrows(InvocationTargetException.class,
                () -> capture(type, stream, Files.size(original), sha, IncomingUploadPartTest::requireTechnicalPermission));
            assertEquals(ErrorCode.CONTENT_MISMATCH, ((TenantAccessException) failure.getCause()).code());
        }
        assertEquals(1, fileCount());
    }

    @Test void realTechnicalPermissionRevocationStopsInputAndCleansPrivateContent() throws Exception {
        var type = implementation(); var original = original(0); String sha = objects.required("objects").get(0).required("sha256").textValue();
        var checks = new AtomicInteger();
        try (var stream = Files.newInputStream(original)) {
            var failure = assertThrows(InvocationTargetException.class, () -> capture(type, stream, Files.size(original), sha, () -> {
                if (checks.incrementAndGet() == 3) assertDoesNotThrow(() -> Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("r--------")));
                requireTechnicalPermission();
            }));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        } finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
        assertEquals(1, fileCount());
    }

    @Test void publicDirectoryPermissionsCannotReceivePrivateContent() throws Exception {
        var type = implementation(); var original = original(0); String sha = objects.required("objects").get(0).required("sha256").textValue();
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        try (var stream = Files.newInputStream(original)) {
            var failure = assertThrows(InvocationTargetException.class,
                () -> capture(type, stream, Files.size(original), sha, IncomingUploadPartTest::requireTechnicalPermission));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
        } finally { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
        assertEquals(1, fileCount());
    }

    @Test void actualDeploymentBinaryCannotBeAcceptedAsOneOversizedFragment() throws Exception {
        var type = implementation(); var original = root.resolve(".implementation-work/toolchains/seaweedfs-4.48/weed");
        String sha;
        try (var content = Files.newInputStream(original)) { sha = DigestUtils.sha256Hex(content); }
        try (var stream = Files.newInputStream(original)) {
            var failure = assertThrows(InvocationTargetException.class,
                () -> capture(type, stream, Files.size(original), sha, IncomingUploadPartTest::requireTechnicalPermission));
            assertEquals(ErrorCode.FILE_TOO_LARGE, ((TenantAccessException) failure.getCause()).code());
        }
        assertEquals(1, fileCount());
    }

    @Test void completedPrivateInputStillRequiresCurrentTechnicalPermission() throws Exception {
        var type = implementation(); var original = original(0); String sha = objects.required("objects").get(0).required("sha256").textValue();
        try (var stream = Files.newInputStream(original); var saved = (AutoCloseable) capture(type, stream, Files.size(original), sha,
                IncomingUploadPartTest::requireTechnicalPermission)) {
            Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("r--------"));
            try {
                var failure = assertThrows(InvocationTargetException.class, () -> type.getDeclaredMethod("path").invoke(saved));
                assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) failure.getCause()).code());
            } finally { Files.setPosixFilePermissions(permission, PosixFilePermissions.fromString("rw-------")); }
        }
        assertEquals(1, fileCount());
    }

    @Test void closedPrivateInputCannotReturnAReusablePath() throws Exception {
        var type = implementation(); var original = original(0); String sha = objects.required("objects").get(0).required("sha256").textValue();
        try (var stream = Files.newInputStream(original)) {
            var saved = (AutoCloseable) capture(type, stream, Files.size(original), sha, IncomingUploadPartTest::requireTechnicalPermission);
            saved.close();
            var failure = assertThrows(InvocationTargetException.class, () -> type.getDeclaredMethod("path").invoke(saved));
            assertEquals(ErrorCode.ACTION_STALE, ((TenantAccessException) failure.getCause()).code());
            saved.close();
        }
        assertEquals(1, fileCount());
    }

    @AfterAll static void removeOnlyTechnicalFiles() throws Exception {
        Files.delete(permission); Files.delete(directory);
    }
}
