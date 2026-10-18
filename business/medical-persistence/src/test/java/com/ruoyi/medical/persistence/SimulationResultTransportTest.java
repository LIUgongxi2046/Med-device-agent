package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Boundary doubles only: these tests do not claim a live S3/Redis write or acceptance. */
class SimulationResultTransportTest {
    @TempDir Path directory;
    final String tenant = UUID.randomUUID().toString(), actor = UUID.randomUUID().toString();
    final byte[] bytes = "{\"simulation\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String key() { return "tenants/" + tenant + "/simulation-results/" + UUID.randomUUID() + "/" + UUID.randomUUID(); }
    S3ObjectWriter.WriteIntent intent(String key) throws Exception {
        return new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(), key, WireValues.sha256(bytes), Integer.toString(bytes.length), "application/json");
    }
    @Test void rejectsForeignUnsafeAndSourceNamespacesBeforeWriterBoundary() throws Exception {
        var storage = mock(SourceUploadTransport.class);
        var transport = new VerifiedS3SimulationResultTransport(storage, 1024);
        String base = "tenants/" + tenant + "/simulation-results/";
        for (String key : java.util.List.of(key().replace(tenant, UUID.randomUUID().toString()),
                key().replace("simulation-results", "source-parsed"), base + "../artifact", base + "%2e%2e/a",
                base + UUID.randomUUID() + "/" + UUID.randomUUID() + "/suffix", base + UUID.randomUUID() + "/")) {
            var intent = intent(key);
            assertThrows(TenantAccessException.class, () -> transport.write(tenant, actor, intent, bytes, false, () -> {}));
        }
        verifyNoInteractions(storage);
    }
    @Test void rejectsTransactionRevocationAndContentMismatchBeforeIo() throws Exception {
        var storage = mock(SourceUploadTransport.class);
        var transport = new VerifiedS3SimulationResultTransport(storage, 1024);
        var intent = intent(key());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(TenantAccessException.class, () -> transport.write(tenant, actor, intent, bytes, false, () -> {})); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThrows(SecurityException.class, () -> transport.write(tenant, actor, intent, bytes, false, () -> { throw new SecurityException("revoked"); }));
        assertThrows(TenantAccessException.class, () -> transport.write(tenant, actor, intent, new byte[] {1}, false, () -> {}));
        assertThrows(TenantAccessException.class, () -> new VerifiedS3SimulationResultTransport(storage, 1).write(tenant, actor, intent, bytes, false, () -> {}));
        var media = new S3ObjectWriter.WriteIntent(intent.writeId(), intent.objectKey(), intent.sha256(), intent.sizeBytes(), "text/plain");
        assertThrows(TenantAccessException.class, () -> transport.write(tenant, actor, media, bytes, false, () -> {}));
        verifyNoInteractions(storage);
    }
    @Test void newWriteUsesPrivateSnapshotVerifiedWriterAndCleansUp() throws Exception {
        var writer = mock(S3ObjectWriter.class);
        var intent = intent(key()); var reference = new ObjectRef(intent.objectKey(), "exact-version", intent.sha256(), intent.sizeBytes(), intent.mediaType());
        var calls = new AtomicInteger(); S3ObjectReader.Admission current = calls::incrementAndGet;
        when(writer.writeVerified(eq(intent), any(Path.class), same(current))).thenAnswer(call -> {
            Path file = call.getArgument(1);
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
            assertEquals(directory, file.getParent());
            return reference;
        });
        assertEquals(reference, VerifiedS3SimulationResultTransport.writeVerified(writer, directory, intent, bytes, false, current));
        verify(writer, times(1)).writeVerified(eq(intent), any(Path.class), same(current));
        verify(writer, never()).recoverVerified(any(), any());
        assertTrue(calls.get() >= 2);
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void admissionLossAfterSnapshotPreventsWriteAndRemovesTemporaryFile() throws Exception {
        var writer = mock(S3ObjectWriter.class); var intent = intent(key()); var calls = new AtomicInteger();
        S3ObjectReader.Admission current = () -> {
            if (calls.incrementAndGet() == 2) throw new SecurityException("original authority revoked");
        };
        assertThrows(SecurityException.class, () -> VerifiedS3SimulationResultTransport.writeVerified(writer, directory, intent, bytes, false, current));
        verifyNoInteractions(writer);
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
    @Test void ambiguousWriteNeverRetriesPutAndRecoveryUsesOnlyVerifiedRecovery() throws Exception {
        var writer = mock(S3ObjectWriter.class); var intent = intent(key()); S3ObjectReader.Admission current = () -> {};
        when(writer.writeVerified(eq(intent), any(Path.class), same(current))).thenThrow(new IOException("response unknown"));
        assertThrows(IOException.class, () -> VerifiedS3SimulationResultTransport.writeVerified(writer, directory, intent, bytes, false, current));
        verify(writer, times(1)).writeVerified(eq(intent), any(Path.class), same(current));
        verify(writer, never()).recoverVerified(any(), any());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        clearInvocations(writer);
        var reference = new ObjectRef(intent.objectKey(), "recovered-exact-version", intent.sha256(), intent.sizeBytes(), intent.mediaType());
        when(writer.recoverVerified(intent, current)).thenReturn(reference);
        assertEquals(reference, VerifiedS3SimulationResultTransport.writeVerified(writer, directory, intent, bytes, true, current));
        verify(writer).recoverVerified(intent, current); verify(writer, never()).writeVerified(any(), any(), any());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }
}
