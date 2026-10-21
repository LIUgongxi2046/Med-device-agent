package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine session-export producer, ledger, transfer and registration with explicitly doubled
 * independent closure/current authorities. No host FINISH or data-only reference is a proof. */
class TaskProfessionalClosedRegistrationTest extends TaskProfessionalObjectFixture {
    @TempDir Path directory;
    final TaskProfessionalRuntimeService.Session closedSession=mock(TaskProfessionalRuntimeService.Session.class);
    final TaskProfessionalSupervisor.Closed closed=mock(TaskProfessionalSupervisor.Closed.class);
    final TaskResearchExecutionService factory=new TaskResearchExecutionService(json,professionalSchema);
    TaskProfessionalClosedRegistrationTest()throws Exception{
        var binding=json.object().put("tenant_id",tenant).put("run_id",run).put("step_id",step).put("action_id",action).put("lease_generation",1).put("input_sha256",hash('a')).put("step_envelope_sha256",hash('b'));
        when(closedSession.admission()).thenReturn(admission);when(closedSession.input()).thenReturn(professionalInput);when(closedSession.state()).thenReturn("CLOSED");when(closedSession.sessionId()).thenReturn(session);when(closedSession.runId()).thenReturn(run);when(closedSession.stepId()).thenReturn(step);when(closedSession.actionId()).thenReturn(action);when(closedSession.generation()).thenReturn("1");when(closedSession.workerService()).thenReturn("spiffe://test.invalid/worker");when(closedSession.binding()).thenReturn(binding);when(closedSession.deployment()).thenReturn(deployment);when(closedSession.launch()).thenReturn(json.object().put("runtime_manifest_sha256",hash('a')));
        when(closed.sessionId()).thenReturn(session);when(closed.binding()).thenReturn(binding);when(closed.receiptId()).thenReturn(id(95));when(closed.sha256()).thenReturn(hash('e'));when(closed.deploymentSha256()).thenReturn(hash('a'));
        var archive=json.object();archive.putArray("files").addObject().put("path","runtime-inventory.json").put("sha256",WireValues.sha256("{}".getBytes())).put("size_bytes","2").put("content_base64","e30=");
        archive.set("observation",json.object().put("session_id",session).set("binding",binding));when(closed.sessionExport()).thenReturn(ContentReadAuthorization.canonicalBytes(archive));
    }
    TaskResearchExecutionService.Produced produced()throws Exception{return tx(()->factory.sessionExport(closedSession,closed));}
    TaskProfessionalObjectLedger.Attempt attempt(TaskResearchExecutionService.Produced produced)throws Exception{return tx(()->{var intent=professionalLedger.prepare(leasedProducer,produced);return professionalLedger.attempt(leasedProducer,produced,intent);});}
    RuntimeObjectTransport.VerifiedTransfer transfer(TaskProfessionalObjectLedger.Attempt attempt)throws Exception{
        Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));var writer=mock(S3ObjectWriter.class);var w=attempt.writeIntent();var object=new InterfaceProtocol.ObjectRef(w.objectKey(),"closed-export-version",w.sha256(),w.sizeBytes(),w.mediaType());when(writer.writeVerified(any(),any(),any())).thenReturn(object);when(writer.recoverVerified(any(),any())).thenReturn(object);return new RuntimeObjectTransport(()->writer,directory,67108864).transferVerified(attempt,()->{});
    }
    @Test void actualExportProducerBindsClosedReceiptBeforeRegistration()throws Exception{
        var output=produced();var attempt=attempt(output);var physical=transfer(attempt);var registered=tx(()->professionalRegistrations.register(leasedProducer,output,attempt,physical));
        assertEquals("SESSION_EXPORT",registered.kind());assertEquals("POST_CLOSE",registered.receipt().required("provenance").required("phase").asText());assertEquals(id(95),only("runtime_professional_object_binding").get("supervision_receipt_id"));assertEquals(hash('e'),only("runtime_professional_object_binding").get("supervision_sha256"));
        int binding=-1,registry=-1;for(int n=0;n<executedSql.size();n++){if(executedSql.get(n).startsWith("INSERT INTO runtime_professional_object_binding"))binding=n;if(executedSql.get(n).startsWith("INSERT INTO runtime_registered_object"))registry=n;}assertTrue(binding>=0&&registry>binding);
    }
    @Test void merelyClosedStatusWithoutPrivateClosureCannotProduce()throws Exception{
        assertThrows(NullPointerException.class,()->tx(()->factory.sessionExport(closedSession,null)));when(closedSession.state()).thenReturn("RUNNING");assertThrows(TenantAccessException.class,this::produced);assertTrue(rows("runtime_object_intent").isEmpty());
    }
    @Test void alteredSessionOrArchiveIsRejectedByActualFactory()throws Exception{
        when(closed.sessionId()).thenReturn(id(999));assertThrows(TenantAccessException.class,this::produced);when(closed.sessionId()).thenReturn(session);when(closed.sessionExport()).thenReturn("{}".getBytes());assertThrows(TenantAccessException.class,this::produced);
    }
    @Test void closureRevocationBetweenTransferAndRegistrationCannotCreateArtifacts()throws Exception{
        var output=produced();var attempt=attempt(output);var physical=transfer(attempt);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(closed).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.register(leasedProducer,output,attempt,physical)));assertTrue(rows("artifact").isEmpty());assertTrue(rows("runtime_professional_object_binding").isEmpty());
    }
    @Test void changed099OriginalCannotReadRegisteredExport()throws Exception{
        var output=produced();var attempt=attempt(output);var physical=transfer(attempt);tx(()->professionalRegistrations.register(leasedProducer,output,attempt,physical));only("runtime_professional_object_binding").put("input_receipt_sha256",hash('f'));assertThrows(TenantAccessException.class,()->tx(()->professionalRegistrations.readVerified(leasedProducer,output,attempt,physical)));
    }
}
