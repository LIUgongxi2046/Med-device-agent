package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real ledger, transport-proof construction and registration. JDBC, S3 and current authority
 * boundaries are explicit doubles; these tests do not claim a deployed database/provider run. */
class TaskNodeInputRegistrationTest extends TaskNodeInputFixture {
    @TempDir Path directory;
    TaskNodeInputRegistrationTest()throws Exception{}
    RuntimeObjectTransport transport(S3ObjectWriter writer)throws Exception {Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));return new RuntimeObjectTransport(()->writer,directory,TaskNodeInputSchema.MAXIMUM_BYTES);}
    InterfaceProtocol.ObjectRef object(RuntimeObjectAttempt attempt){var w=attempt.writeIntent();return new InterfaceProtocol.ObjectRef(w.objectKey(),"actual-input-object-version",w.sha256(),w.sizeBytes(),w.mediaType());}
    RuntimeObjectTransport.VerifiedTransfer transfer(TaskNodeInputLedger.Attempt attempt)throws Exception {
        var writer=mock(S3ObjectWriter.class);when(writer.writeVerified(any(),any(),any())).thenAnswer(i->{assertArrayEquals(schema.bytes(input),Files.readAllBytes(i.getArgument(1)));return object(attempt);});
        when(writer.recoverVerified(any(),any())).thenReturn(object(attempt));return transport(writer).transferVerified(attempt,()->{});
    }
    TaskNodeInputRegistration.Registered complete()throws Exception{var attempt=prepare();var physical=transfer(attempt);return tx(()->registrations.register(proof,attempt,physical));}
    @Test void actualInputArtifactHasRunOwnershipAndFullReceiptJournal()throws Exception {
        var receipt=complete();assertEquals("actual-input-object-version",receipt.object().objectVersion());assertEquals(WireValues.sha256(schema.bytes(input)),receipt.object().sha256());
        assertEquals(run,only("artifact").get("run_id"));assertEquals("TASK_NODE_INPUT",only("artifact").get("artifact_kind"));assertEquals("TASK",only("artifact_content_binding").get("resource_kind"));assertEquals(task,only("artifact_content_binding").get("resource_id"));
        assertEquals(3,rows("dependency_access_member").size());assertEquals(3,rows("lineage_edge").size());assertEquals(3,rows("audit_event").size());assertEquals(3,rows("outbox_event").size());
        assertEquals("COMPLETED",only("operation_record").get("state"));assertTrue(rows("run_step").isEmpty());assertTrue(rows("runtime_node_definition").isEmpty());
    }
    @Test void completedRetryVerifiesOriginalReceiptWithoutAnotherRegistration()throws Exception {
        var receipt=complete();var recovered=recover();var read=tx(()->registrations.readStored(admission,recovered)).orElseThrow();
        assertEquals(receipt.receipt(),read.receipt());assertEquals(1,rows("artifact").size());assertEquals(1,rows("task_node_input_registered").size());
    }
    @Test void publicReferenceOrOtherAttemptTransferCannotRegister()throws Exception {
        var first=prepare();assertThrows(TenantAccessException.class,()->tx(()->registrations.register(proof,first,null)));
        var physical=transfer(first);var second=recover();assertThrows(TenantAccessException.class,()->tx(()->registrations.register(proof,second,physical)));assertTrue(rows("artifact").isEmpty());
    }
    @Test void uncertainPutCanOnlyRecoverOriginalObject()throws Exception {
        var first=prepare();var writer=mock(S3ObjectWriter.class);when(writer.writeVerified(any(),any(),any())).thenThrow(new java.io.IOException("Unknown PUT result"));when(writer.recoverVerified(any(),any())).thenReturn(object(first));var objects=transport(writer);
        assertThrows(java.io.IOException.class,()->objects.transferVerified(first,()->{}));var recovered=recover();var physical=objects.transferVerified(recovered,()->{});tx(()->registrations.register(proof,recovered,physical));
        verify(writer,times(1)).writeVerified(any(),any(),any());verify(writer,times(1)).recoverVerified(any(),any());assertEquals(1,rows("task_node_input_attempt").size());
    }
    @Test void missingObjectAfterUnknownNeverRestartsPut()throws Exception {
        prepare();var recovered=recover();var writer=mock(S3ObjectWriter.class);when(writer.recoverVerified(any(),any())).thenThrow(new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
        assertThrows(TenantAccessException.class,()->transport(writer).transferVerified(recovered,()->{}));verify(writer,never()).writeVerified(any(),any(),any());assertTrue(rows("artifact").isEmpty());
    }
    @Test void changedArtifactMetadataAndMissingReceiptJournalAreRejected()throws Exception {
        complete();var recovered=recover();only("artifact").put("run_id",id(999));assertThrows(TenantAccessException.class,()->tx(()->registrations.readStored(admission,recovered)));
        only("artifact").put("run_id",run);rows("audit_event").remove(2);assertThrows(TenantAccessException.class,()->tx(()->registrations.readStored(admission,recovered)));
    }
    @Test void tamperedDependencyRightsCannotBeReadAsOriginalReceipt()throws Exception {
        complete();var recovered=recover();rows("dependency_access_member").get(0).put("rights_revision",id(999));assertThrows(TenantAccessException.class,()->tx(()->registrations.readStored(admission,recovered)));
    }
}
