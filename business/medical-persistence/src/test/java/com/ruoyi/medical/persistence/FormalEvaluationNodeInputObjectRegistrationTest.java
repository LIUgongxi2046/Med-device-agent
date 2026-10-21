package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual immutable ledger, actual transport proof construction and actual registration/readback.
 * S3 writer and original079/088 authority are explicit external-boundary doubles. */
class FormalEvaluationNodeInputObjectRegistrationTest extends NodeInputObjectFixture {
    @TempDir Path directory;
    FormalEvaluationNodeInputObjectRegistrationTest() throws Exception { }

    RuntimeObjectTransport transport(S3ObjectWriter writer) throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return new RuntimeObjectTransport(() -> writer, directory, 2 * 1024 * 1024);
    }
    InterfaceProtocol.ObjectRef object(RuntimeObjectAttempt attempt) {
        var w = attempt.writeIntent();
        return new InterfaceProtocol.ObjectRef(w.objectKey(), "actual-object-version", w.sha256(), w.sizeBytes(), w.mediaType());
    }
    RuntimeObjectTransport.VerifiedTransfer transfer(FormalEvaluationNodeInputObjectLedger.Attempt attempt) throws Exception {
        var writer = mock(S3ObjectWriter.class);
        when(writer.writeVerified(any(), any(), any())).thenAnswer(invocation -> {
            assertArrayEquals(checked.bytes(), Files.readAllBytes(invocation.getArgument(1)));
            return object(attempt);
        });
        when(writer.recoverVerified(any(), any())).thenReturn(object(attempt));
        return transport(writer).transferVerified(attempt, () -> { });
    }
    FormalEvaluationNodeInputObjectRegistration.Receipt complete() throws Exception {
        var attempt = prepare();
        var physical = transfer(attempt);
        return tx(() -> registrations.register(admission, proof, attempt, physical));
    }

    @Test void persistsRealObjectExactBytesFullAncestryAndOwnOperationOnly() throws Exception {
        var receipt = complete();
        assertEquals("actual-object-version", receipt.objectVersion());
        assertEquals(execution, receipt.executionId());
        assertEquals(WireValues.sha256(checked.bytes()), receipt.artifact().sha256());
        var artifact = only("artifact");
        assertNull(artifact.get("run_id"));
        assertNull(artifact.get("document_revision"));
        assertEquals("EVALUATION_NODE_INPUT", artifact.get("artifact_kind"));
        assertEquals("RESTRICTED", artifact.get("classification"));
        assertEquals("EVALUATION", only("artifact_content_binding").get("resource_kind"));
        assertEquals(evaluation, only("artifact_content_binding").get("resource_id"));
        assertEquals(checked.dependencies().members().size(), rows("dependency_access_member").size());
        assertEquals(checked.dependencies().roots().size(), rows("lineage_edge").size());
        assertEquals("COMPLETED", only("operation_record").get("state"));
        assertTrue(rows("formal_evaluation_execution").isEmpty());
        assertTrue(rows("task_run").isEmpty());
        assertTrue(rows("system_job_run").isEmpty());
    }

    @Test void completedRetryReturnsOriginalReceiptWithoutAnotherRegistration() throws Exception {
        var receipt = complete(); var recovered = recover();
        assertEquals(receipt, tx(() -> registrations.read(admission, proof, recovered)).orElseThrow());
        assertEquals(1, rows("artifact").size());
        assertEquals(3, rows("audit_event").size());
        assertEquals(3, rows("outbox_event").size());
    }

    @Test void publicObjectReferenceWithoutActualTransferCannotRegister() throws Exception {
        var attempt = prepare();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.register(admission, proof, attempt, null)));
        assertTrue(rows("artifact").isEmpty());
    }

    @Test void transferForAnotherPrivateAttemptCannotRegister() throws Exception {
        var first = prepare(); var physical = transfer(first); var recovered = recover();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.register(admission, proof, recovered, physical)));
        assertTrue(rows("artifact").isEmpty());
    }

    @Test void unknownInitialSendOnlyRecoversAndNeverSendsAgain() throws Exception {
        var first = prepare(); var writer = mock(S3ObjectWriter.class);
        when(writer.writeVerified(any(), any(), any())).thenThrow(new java.io.IOException("Unknown PUT result"));
        when(writer.recoverVerified(any(), any())).thenReturn(object(first));
        var transport = transport(writer);
        assertThrows(java.io.IOException.class, () -> transport.transferVerified(first, () -> { }));
        var recovered = recover(); var physical = transport.transferVerified(recovered, () -> { });
        tx(() -> registrations.register(admission, proof, recovered, physical));
        verify(writer, times(1)).writeVerified(any(), any(), any());
        verify(writer, times(1)).recoverVerified(any(), any());
        assertEquals(1, rows("formal_evaluation_node_input_attempt").size());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void noObjectAfterUnknownDoesNotRestartPut() throws Exception {
        prepare(); var recovered = recover(); var writer = mock(S3ObjectWriter.class);
        when(writer.recoverVerified(any(), any())).thenThrow(new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
        assertThrows(TenantAccessException.class, () -> transport(writer).transferVerified(recovered, () -> { }));
        verify(writer, never()).writeVerified(any(), any(), any());
        assertTrue(rows("artifact").isEmpty());
    }

    @Test void revokedProofAfterPhysicalReadbackPreventsRegistration() throws Exception {
        var attempt = prepare(); var physical = transfer(attempt);
        when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.register(admission, proof, attempt, physical)));
        assertTrue(rows("artifact").isEmpty());
    }

    @Test void changedObjectVersionOrClassificationCannotBeReadAsRegistered() throws Exception {
        complete(); only("artifact").put("object_version", "other-version"); var attempt = recover();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.read(admission, proof, attempt)));
    }

    @Test void missingDependencyMemberOrLineageCannotBeRecovered() throws Exception {
        complete(); rows("dependency_access_member").remove(0); var attempt = recover();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.read(admission, proof, attempt)));
    }

    @Test void attachedTaskRunCannotTurnEvaluationArtifactIntoGenericRuntimeArtifact() throws Exception {
        complete(); only("artifact").put("run_id", id(999)); var attempt = recover();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.read(admission, proof, attempt)));
    }

    @Test void missingRegisteredAuditOrChangedOperationResponsePreventsRecovery() throws Exception {
        complete(); only("operation_record").put("response_ref", "{}"); var attempt = recover();
        assertThrows(TenantAccessException.class, () -> tx(() -> registrations.read(admission, proof, attempt)));
    }

    @Test void transactionCannotRemainOpenDuringObjectIo() throws Exception {
        var attempt = prepare(); var writer = mock(S3ObjectWriter.class); var transport = transport(writer);
        assertThrows(TenantAccessException.class, () -> tx(() -> transport.transferVerified(attempt, () -> { })));
        verifyNoInteractions(writer);
    }
    @Test void actualEmittedSqlParsesAndCanBeCheckedAgainstTheCompleteMigrationColumns() throws Exception {
        complete();
        for (String sql : new LinkedHashSet<>(executedSql)) {
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql), sql);
        }
        String output = System.getProperty("medical.nodeInputSqlEvidence");
        if (output != null) {
            Files.writeString(Path.of(output), new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(new LinkedHashSet<>(executedSql)));
        }
    }
    @Test void deployedSourceTransportConsumesSeparateEvaluationAttemptWithRealPermitGuard() throws Exception {
        var attempt = prepare();
        var concurrency = mock(TransferConcurrency.class);
        var lease = mock(TransferConcurrency.Lease.class);
        var deployed = mock(SourceUploadTransport.class, CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(deployed, "concurrency", concurrency);
        org.springframework.test.util.ReflectionTestUtils.setField(deployed, "limits",
                new TransferConcurrency.Limits(id(999), 2, 1, java.time.Duration.ofSeconds(5)));
        var policy = mock(SourceUploadTransportPolicy.class);
        when(policy.region()).thenReturn("test-region");
        when(policy.segmentBytes()).thenReturn(65536);
        org.springframework.test.util.ReflectionTestUtils.setField(deployed, "policy", policy);
        when(concurrency.acquire(anyString(), anyString(), any(), any())).thenReturn(lease);
        when(lease.guard(any())).thenCallRealMethod();
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        var deployedTransport = new RuntimeObjectTransport(deployed, directory, 2 * 1024 * 1024);
        try (var writers = mockConstruction(S3ObjectWriter.class, (writer, construction) ->
                when(writer.writeVerified(any(), any(), any())).thenReturn(object(attempt)))) {
            var physical = deployedTransport.transferVerified(attempt, () -> { });
            assertSame(attempt, physical.originalAttempt());
            assertTrue(physical.reference().objectKey().startsWith("evaluation/" + tenant + "/" + execution + "/"));
            verify(writers.constructed().get(0)).writeVerified(eq(attempt.writeIntent()), any(), any());
            verify(writers.constructed().get(0)).close();
        }
        verify(concurrency).acquire(eq(tenant), eq(actor), any(), any());
        verify(lease).close();
    }
}
