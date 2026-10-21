package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Connected actual service -> authority -> deterministic expansion -> immutable ledger -> transport -> registration.
 * Original079/083/088/template/profile permission readers and the external S3 writer are named doubles.
 * No professional runtime, live DB isolation, provider call or approval claim. */
class FormalEvaluationPlanPreparationServiceTest {
    @TempDir Path directory;
    FormalEvaluationPlanPreparationService service(FormalEvaluationPlanPreparationAuthorityTest f,S3ObjectWriter writer)throws Exception {
        var beans=new StaticListableBeanFactory();if(writer!=null){Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));beans.addBean("transport",new RuntimeObjectTransport(()->writer,directory,1048576));}
        return new FormalEvaluationPlanPreparationService(f.access,f.inputs,f.bounds,mock(FormalEvaluationExecutionRegistrations.class),f.authority,f.ledger,f.registrations,beans.getBeanProvider(RuntimeObjectTransport.class),f.schema,f.resources,f.json);
    }
    InterfaceProtocol.ObjectRef object(S3ObjectWriter.WriteIntent w){return new InterfaceProtocol.ObjectRef(w.objectKey(),"explicit-test-object-version",w.sha256(),w.sizeBytes(),w.mediaType());}
    byte[] request(FormalEvaluationPlanPreparationAuthorityTest f)throws Exception{return f.request(null).body().getBytes(StandardCharsets.UTF_8);}
    byte[] recovery(FormalEvaluationPlanPreparationAuthorityTest f)throws Exception{return f.schema.canonical(new FormalEvaluationPlanPreparationSchema.Recovery("1.0.0",f.key,f.request(null).request())).getBytes(StandardCharsets.UTF_8);}
    @Test void normalPreparationConnectsActualMethodsAndRetryNeverRepeatsPut()throws Exception{
        var f=new FormalEvaluationPlanPreparationAuthorityTest();var writer=mock(S3ObjectWriter.class);
        when(writer.writeVerified(any(),any(),any())).thenAnswer(i->{S3ObjectWriter.WriteIntent w=i.getArgument(0);byte[] content=Files.readAllBytes(i.getArgument(1));assertEquals(w.sha256(),WireValues.sha256(content));assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return object(w);});
        var service=service(f,writer);var first=service.prepare(f.subject,"1",f.key,request(f));var second=service.prepare(f.subject,"1",f.key,request(f));
        assertEquals(first,second);assertEquals("PREPARED",first.required("state").asText());assertEquals(2,first.required("preview").required("plan_candidate").required("sections").size());assertFalse(first.required("runnable").asBoolean());assertFalse(first.required("professional_completion").asBoolean());
        assertEquals(f.schema.hash(first.required("preview").required("plan_candidate")),first.required("artifact_sha256").asText());verify(writer,times(1)).writeVerified(any(),any(),any());verify(writer,never()).recoverVerified(any(),any());assertEquals(1,f.rows("artifact").size());assertTrue(f.rows("task_run").isEmpty());assertTrue(f.rows("system_job_run").isEmpty());assertTrue(f.rows("formal_evaluation_node_result").isEmpty());
    }
    @Test void unknownPutUsesExplicitRecoveryGetWithOriginalBytesAndNoSecondAttempt()throws Exception{
        var f=new FormalEvaluationPlanPreparationAuthorityTest();var writer=mock(S3ObjectWriter.class);when(writer.writeVerified(any(),any(),any())).thenThrow(new java.io.IOException("fixture uncertain PUT"));when(writer.recoverVerified(any(),any())).thenAnswer(i->object(i.getArgument(0)));
        var service=service(f,writer);assertThrows(java.io.IOException.class,()->service.prepare(f.subject,"1",f.key,request(f)));var recovered=service.recover(f.subject,"1",recovery(f));assertEquals("PREPARED",recovered.required("state").asText());verify(writer,times(1)).writeVerified(any(),any(),any());verify(writer,times(1)).recoverVerified(any(),any());assertEquals(1,f.rows("formal_evaluation_plan_preparation_attempt").size());
    }
    @Test void previewNeedsNoObjectTransportAndCreatesNoImmutableOperations()throws Exception{
        var f=new FormalEvaluationPlanPreparationAuthorityTest();var service=service(f,null);assertEquals("SELECTED",service.preview(f.subject,"1",request(f)).required("selection_state").asText());assertTrue(f.rows("operation_record").isEmpty());assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.prepare(f.subject,"1",f.key,request(f))).code());assertTrue(f.rows("formal_evaluation_plan_preparation_intent").isEmpty());
    }
    @Test void unsupportedNodesCannotReachOriginalBoundPreparation()throws Exception{
        var f=new FormalEvaluationPlanPreparationAuthorityTest();when(f.binding.nodeKind()).thenReturn("N02");var service=service(f,null);var body=f.schema.canonical(new FormalEvaluationContextProtocol.BoundStart("1.0.0",f.checked.original().request().start(),PlanPreparationFixture.basis(40))).getBytes(StandardCharsets.UTF_8);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->service.prepareExecution(f.subject,"1",f.key,body)).code());verify(f.bounds,never()).prepare(any(EnterpriseAccess.Admission.class),anyString(),any(FormalEvaluationExecutionInputs.Prepared.class),any(PermissionProtocol.Basis.class));assertTrue(f.rows("formal_evaluation_execution").isEmpty());
    }
    @Test void supportedOriginalBoundStartDelegatesActualAdmissionInputWithFullMatrixReceipt()throws Exception{
        var f=new FormalEvaluationPlanPreparationAuthorityTest();when(f.bounds.prepare(f.admission,f.key,f.source,PlanPreparationFixture.basis(40))).thenReturn(new FormalEvaluationExecutionRegistrations.Registration(f.execution,f.schema.hash(f.checked.original().request().start()),2,2));var body=f.schema.canonical(new FormalEvaluationContextProtocol.BoundStart("1.0.0",f.checked.original().request().start(),PlanPreparationFixture.basis(40))).getBytes(StandardCharsets.UTF_8);
        var r=service(f,null).prepareExecution(f.subject,"1",f.key,body);assertEquals(2,r.required("expected_cells").asInt());assertEquals(2,r.required("expected_nodes").asInt());verify(f.bounds).prepare(f.admission,f.key,f.source,PlanPreparationFixture.basis(40));
    }
}
