package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real ledger -> transfer proof -> registration orchestration, explicit authority/S3/JDBC doubles. */
class TaskProfessionalObjectRegistrationTest extends TaskProfessionalObjectFixture {
    @TempDir Path directory;
    TaskProfessionalObjectRegistrationTest()throws Exception{}
    RuntimeObjectTransport transport(S3ObjectWriter writer)throws Exception{Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));return new RuntimeObjectTransport(()->writer,directory,67108864);}
    InterfaceProtocol.ObjectRef object(RuntimeObjectAttempt attempt){var w=attempt.writeIntent();return new InterfaceProtocol.ObjectRef(w.objectKey(),"exact-original-version",w.sha256(),w.sizeBytes(),w.mediaType());}
    RuntimeObjectTransport.VerifiedTransfer physical(TaskProfessionalObjectLedger.Attempt attempt)throws Exception{
        var writer=mock(S3ObjectWriter.class);when(writer.writeVerified(any(),any(),any())).thenAnswer(c->{assertArrayEquals(envelopeBytes,Files.readAllBytes(c.getArgument(1)));return object(attempt);});when(writer.recoverVerified(any(),any())).thenReturn(object(attempt));return transport(writer).transferVerified(attempt,()->{});
    }
    TaskProfessionalObjectRegistration.Registered complete()throws Exception{var attempt=initial();var transfer=physical(attempt);return tx(()->professionalRegistrations.register(leasedProducer,envelope,attempt,transfer));}
    @Test void genuineAttemptAndTransferRegisterPreparedEnvelopeWithoutSessionClaim()throws Exception{
        var registered=complete();assertEquals("STEP_ENVELOPE",registered.kind());assertEquals("PREPARED_BYTES",registered.receipt().required("provenance").required("phase").asText());
        assertEquals("2.0.0",only("runtime_object_intent").get("schema_version"));assertEquals("1.0.0",only("runtime_registered_object").get("schema_version"));assertEquals(run,only("artifact").get("run_id"));
        assertTrue(rows("professional_runtime_session").isEmpty());assertTrue(rows("runtime_professional_object_binding").isEmpty());assertTrue(rows("professional_runtime_supervision").isEmpty());
        assertEquals(1,rows("runtime_governance_manifest").size());assertEquals(1,rows("runtime_governance_member").size());assertEquals(2,rows("audit_event").size());assertEquals(2,rows("outbox_event").size());
    }
    @Test void unknownPutCanOnlyRecoverSameOriginalAttemptAndVersion()throws Exception{
        var attempt=initial();var writer=mock(S3ObjectWriter.class);when(writer.writeVerified(any(),any(),any())).thenThrow(new java.io.IOException("unknown PUT"));when(writer.recoverVerified(any(),any())).thenReturn(object(attempt));var io=transport(writer);
        assertThrows(java.io.IOException.class,()->io.transferVerified(attempt,()->{}));var retry=recovered();var transfer=io.transferVerified(retry,()->{});tx(()->professionalRegistrations.register(leasedProducer,envelope,retry,transfer));
        verify(writer,times(1)).writeVerified(any(),any(),any());verify(writer,times(1)).recoverVerified(any(),any());assertEquals(1,rows("runtime_object_attempt").size());
    }
    @Test void publicObjectOrOtherAttemptTransferNeverRegisters()throws Exception{
        var attempt=initial();assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,envelope,attempt,null)));
        var transfer=physical(attempt);var other=recovered();assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,envelope,other,transfer)));assertTrue(rows("artifact").isEmpty());
    }
    @Test void retryReadsExactReceiptAndRejectsChangedPhysicalVersion()throws Exception{
        var original=complete();var attempt=recovered();var transfer=physical(attempt);var reread=tx(()->professionalRegistrations.readVerified(leasedProducer,envelope,attempt,transfer));assertEquals(original.receipt(),reread.receipt());assertEquals(1,rows("artifact").size());
        var writer=mock(S3ObjectWriter.class);var w=attempt.writeIntent();when(writer.recoverVerified(any(),any())).thenReturn(new InterfaceProtocol.ObjectRef(w.objectKey(),"different-version",w.sha256(),w.sizeBytes(),w.mediaType()));var wrong=transport(writer).transferVerified(attempt,()->{});
        assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readVerified(leasedProducer,envelope,attempt,wrong)));
    }
    @Test void sourceRevocationStopsRegistrationAndTransportGuard()throws Exception{
        var attempt=initial();var transfer=physical(attempt);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(professionalContent).requireUnchanged();
        assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,envelope,attempt,transfer)));assertThrows(TenantAccessException.class,()->tx(()->{professionalRegistrations.requireTransferCurrent(leasedProducer,envelope,attempt);return null;}));assertTrue(rows("artifact").isEmpty());
    }
    @Test void originalGovernanceAndRegistryTamperingCannotBeHiddenByFilters()throws Exception{
        complete();var attempt=recovered();var transfer=physical(attempt);only("runtime_governance_member").put("classification","PUBLIC");assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readVerified(leasedProducer,envelope,attempt,transfer)));
        only("runtime_governance_member").put("classification","INTERNAL");only("artifact").put("run_id",id(999));assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readVerified(leasedProducer,envelope,attempt,transfer)));
    }
    @Test void missingPreparedAuditCannotCreateAttempt()throws Exception{
        tx(()->professionalLedger.prepare(leasedProducer,envelope));rows("audit_event").clear();assertThrows(TenantAccessException.class,this::initial);assertTrue(rows("runtime_object_attempt").isEmpty());
    }
    @Test void proofHasPrivateConstructorAndNoPublicRawByteFactory(){
        for(Class<?> type:List.of(TaskProfessionalObjectRegistration.Registered.class,TaskProfessionalObjectLedger.Intent.class,TaskProfessionalObjectLedger.Attempt.class))for(var ctor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
        for(var method:TaskProfessionalObjectRegistration.class.getMethods())if(method.getName().equals("register"))assertFalse(Arrays.asList(method.getParameterTypes()).contains(byte[].class));
    }
}
