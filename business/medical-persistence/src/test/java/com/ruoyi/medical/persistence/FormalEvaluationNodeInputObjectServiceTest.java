package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual service/ledger/registration/transport with explicit access, original proof and S3 boundaries. */
class FormalEvaluationNodeInputObjectServiceTest extends NodeInputObjectFixture {
    @TempDir Path directory;
    final EnterpriseAccess access = mock(EnterpriseAccess.class);
    final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    final FormalEvaluationNodeInputObjectAuthority authority = mock(FormalEvaluationNodeInputObjectAuthority.class);
    final S3ObjectWriter writer = mock(S3ObjectWriter.class);

    FormalEvaluationNodeInputObjectServiceTest() throws Exception {
        when(access.execute(eq(subject), any())).thenAnswer(invocation -> tx(() -> {
            Function<EnterpriseAccess.Admission, Object> work = invocation.getArgument(1);
            return work.apply(admission);
        }));
        when(authority.prepare(eq(subject), eq("1"), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return proof;
        });
        when(writer.writeVerified(any(), any(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            S3ObjectWriter.WriteIntent intent = invocation.getArgument(0);
            assertArrayEquals(checked.bytes(), Files.readAllBytes(invocation.getArgument(1)));
            return new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef(intent.objectKey(), "object-v1",
                    intent.sha256(), intent.sizeBytes(), intent.mediaType());
        });
    }

    FormalEvaluationNodeInputObjectService service() throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return new FormalEvaluationNodeInputObjectService(access, schema, authority, ledger,
                new RuntimeObjectTransport(() -> writer, directory, 2 * 1024 * 1024), registrations, json);
    }

    @Test void exactCoordinatorUsesOutsideIoAndFreshFinalTransactionThenOriginalRetry() throws Exception {
        var service = service(); byte[] body = checked.original().body().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var receipt = service.register(subject, "1", key, body);
        assertEquals(receipt, service.recover(subject, "1", key, body));
        verify(writer, times(1)).writeVerified(any(), any(), any());
        verify(writer, never()).recoverVerified(any(), any());
        verify(authority, times(2)).prepare(eq(subject), eq("1"), any());
        verify(proof, atLeast(10)).current(admission);
        assertEquals(3, rows("audit_event").size());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void recoveryMissingIntentNeverCallsObjectTransport() throws Exception {
        var service = service();
        assertThrows(TenantAccessException.class, () -> service.recover(subject, "1", key,
                checked.original().body().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        verifyNoInteractions(writer);
    }
}
