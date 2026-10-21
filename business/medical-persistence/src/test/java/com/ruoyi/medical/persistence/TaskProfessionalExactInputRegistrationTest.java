package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real075 preparation/transport/registration, with explicit current input/session/inspection
 * proof doubles. This test does not assert a deployed session or invoke a model. */
class TaskProfessionalExactInputRegistrationTest extends TaskProfessionalObjectFixture {
    @TempDir Path directory;
    final TaskInvocationContext.Prepared exact=mock(TaskInvocationContext.Prepared.class);
    final TaskProfessionalRuntimeService.Session open=mock(TaskProfessionalRuntimeService.Session.class);
    final TaskProfessionalSupervisor.Admitted admitted=mock(TaskProfessionalSupervisor.Admitted.class);
    final byte[] body;
    TaskProfessionalExactInputRegistrationTest()throws Exception {
        var value=json.object();value.putArray("messages").addObject().put("role","user").put("content","literal approved prompt");value.putArray("tools");
        body=ContentReadAuthorization.canonicalBytes(value);new TaskInvocationContextSchema().readExactInput(body);
        var binding=json.object().put("tenant_id",tenant).put("run_id",run).put("step_id",step).put("action_id",action).put("lease_generation",1).put("input_sha256",hash('a')).put("step_envelope_sha256",hash('b'));
        when(open.admission()).thenReturn(admission);when(open.input()).thenReturn(professionalInput);when(open.state()).thenReturn("OPEN");when(open.sessionId()).thenReturn(session);when(open.binding()).thenReturn(binding);when(open.deployment()).thenReturn(deployment);
        when(admitted.sessionId()).thenReturn(session);when(admitted.binding()).thenReturn(binding);when(admitted.deploymentSha256()).thenReturn(hash('a'));when(admitted.receiptId()).thenReturn(id(95));when(admitted.sha256()).thenReturn(hash('e'));
        when(exact.admission()).thenReturn(admission);when(exact.input()).thenReturn(professionalInput);when(exact.session()).thenReturn(open);when(exact.admitted()).thenReturn(admitted);when(exact.supervision()).thenReturn(admitted);
        when(exact.bytes()).thenReturn(body);when(exact.payloadSha256()).thenReturn(WireValues.sha256(body));when(exact.kind()).thenReturn("TASK_EXACT_INPUT");when(exact.sessionId()).thenReturn(session);when(exact.runId()).thenReturn(run);when(exact.stepId()).thenReturn(step);when(exact.actionId()).thenReturn(action);when(exact.leaseGeneration()).thenReturn("1");when(exact.workerService()).thenReturn("spiffe://test.invalid/worker");when(exact.inputId()).thenReturn(id(83));when(exact.inputSha256()).thenReturn(hash('a'));
        var actualMembers=professionalContent.members();var actualRoots=professionalContent.roots();
        when(exact.modelRequestId()).thenReturn(id(96));when(exact.modelRequestSha256()).thenReturn(hash('f'));when(exact.contentMembers()).thenReturn(actualMembers);when(exact.contentRoots()).thenReturn(actualRoots);
        when(exact.provenance()).thenReturn(json.object().put("schema_version","1.0.0").put("phase","PRE_SEND").put("session_id",session).put("payload_sha256",WireValues.sha256(body)).put("supervision_receipt_id",id(95)).put("supervision_sha256",hash('e')).put("deployment_sha256",hash('a')));
    }
    TaskProfessionalObjectLedger.Attempt attempt()throws Exception{return tx(()->{var intent=professionalLedger.prepare(leasedProducer,exact);return professionalLedger.attempt(leasedProducer,exact,intent);});}
    RuntimeObjectTransport.VerifiedTransfer transfer(TaskProfessionalObjectLedger.Attempt attempt)throws Exception {
        Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));var writer=mock(S3ObjectWriter.class);var w=attempt.writeIntent();var object=new InterfaceProtocol.ObjectRef(w.objectKey(),"exact-provider-input-version",w.sha256(),w.sizeBytes(),w.mediaType());
        when(writer.writeVerified(any(),any(),any())).thenAnswer(c->{assertArrayEquals(body,Files.readAllBytes(c.getArgument(1)));return object;});when(writer.recoverVerified(any(),any())).thenReturn(object);
        return new RuntimeObjectTransport(()->writer,directory,67108864).transferVerified(attempt,()->{});
    }
    @Test void actualProviderBytesRegisterOnlyPreSendAdmittedNotClosed()throws Exception {
        var attempt=attempt();var physical=transfer(attempt);var registered=tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,physical));
        assertEquals("TASK_EXACT_INPUT",registered.kind());assertEquals("PRE_SEND",registered.receipt().required("provenance").required("phase").asText());
        assertEquals(TaskInvocationContextSchema.EXACT_INPUT_REF,only("runtime_object_intent").get("schema_ref"));assertArrayEquals(body,attempt.bytes());
        assertEquals(id(95),only("runtime_professional_object_binding").get("supervision_receipt_id"));assertEquals(session,only("runtime_professional_object_binding").get("session_id"));
        assertTrue(rows("invocation").isEmpty());assertTrue(rows("invocation_reservation").isEmpty());
    }
    @Test void closedOrSiblingSessionCannotBindPreSend()throws Exception {
        var attempt=attempt();var physical=transfer(attempt);when(open.state()).thenReturn("CLOSED");assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,physical)));assertTrue(rows("runtime_registered_object").isEmpty());
        when(open.state()).thenReturn("OPEN");when(admitted.sessionId()).thenReturn(id(999));assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,physical)));
    }
    @Test void changedObservationDoesNotRecoverAnOldAttemptEvenForIdenticalBytes()throws Exception {
        var attempt=attempt();when(exact.modelRequestSha256()).thenReturn(hash('9'));
        assertThrows(TenantAccessException.class,()->tx(()->professionalLedger.requireAttempt(leasedProducer,exact,attempt.intent())));
    }
    @Test void revokedAdmissionOrChangedSourceClosurePreventsMetadata()throws Exception {
        var attempt=attempt();var physical=transfer(attempt);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(admitted).requireUnchanged();
        assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,physical)));assertTrue(rows("artifact").isEmpty());
        doNothing().when(admitted).requireUnchanged();when(exact.contentMembers()).thenReturn(List.of());
        assertThrows(TenantAccessException.class,()->tx(()->{professionalRegistrations.requireTransferCurrent(leasedProducer,exact,attempt);return null;}));
    }
    @Test void rereadRequiresExactOriginalPreSendBinding()throws Exception {
        var attempt=attempt();var physical=transfer(attempt);tx(()->professionalRegistrations.register(leasedProducer,exact,attempt,physical));
        only("runtime_professional_object_binding").put("supervision_sha256",hash('9'));
        assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readVerified(leasedProducer,exact,attempt,physical)));
    }
}
