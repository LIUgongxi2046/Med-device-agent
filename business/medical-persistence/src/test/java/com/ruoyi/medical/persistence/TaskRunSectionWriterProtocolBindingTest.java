package com.ruoyi.medical.persistence;
import com.ruoyi.medical.workflow.*;
import io.temporal.api.workflowservice.v1.*;
import io.temporal.common.converter.DefaultDataConverter;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.medical.persistence.TaskRunBootstrapSchemaTest.id;

/** Authored for the deferred test phase. No runtime/provider integration is implied. */
class TaskRunSectionWriterProtocolBindingTest {
    TaskRunWorkflowProtocolBindingTest f;
    @BeforeEach void setup()throws Exception{f=new TaskRunWorkflowProtocolBindingTest();f.setup();}
    @AfterEach void cleanup(){f.cleanup();}
    SectionWriterWorkflowExtensionV1 extension(String revision){return new SectionWriterWorkflowExtensionV1("1.0.0",revision,f.deployment.canonicalSha256(),"isolated-writer-v1","medical.task.section-writer.execute.v1","medical.run.section-writer.prepare.v1","medical.run.section-writer.inventory.v1","medical.run.section-writer.dispatch.v1","medical.run.section-writer.commit.v1","medical.run.section-writer.recover.v1",SectionWriterWorkflowExtensionV1.MARKER);}
    TaskRunWorkflowLedger.Intent prepare()throws Exception{var i=f.launches.prepare(f.f.start,f.f.registered,f.f.config);f.bindings.prepare(f.f.start,i,f.deployment,extension(id(98)));return i;}
    void sdk(TaskRunWorkflowLedger.Intent i,SectionWriterWorkflowExtensionV1 extension){f.sdk(i,i.commandId());var response=f.rpc.getWorkflowExecutionHistory(GetWorkflowExecutionHistoryRequest.getDefaultInstance());var history=response.getHistory().toBuilder();var event=history.getEvents(0).toBuilder();var start=event.getWorkflowExecutionStartedEventAttributes().toBuilder();var memo=start.getMemo().toBuilder();extension.memo().forEach((k,v)->memo.putFields(k,DefaultDataConverter.STANDARD_INSTANCE.toPayload(v).orElseThrow()));start.setMemo(memo);event.setWorkflowExecutionStartedEventAttributes(start);history.setEvents(0,event);when(f.rpc.getWorkflowExecutionHistory(any(GetWorkflowExecutionHistoryRequest.class))).thenReturn(response.toBuilder().setHistory(history).build());}
    @Test void cannotUpgradePriorBaseAttempt()throws Exception{var i=f.prepare();f.launches.attempt(f.f.start,i);assertThrows(TenantAccessException.class,()->f.bindings.prepare(f.f.start,i,f.deployment,extension(id(98))));assertEquals(1,f.db.operations.size());}
    @Test void originalExtensionCannotBeReplacedOrRemoved()throws Exception{var i=prepare();assertThrows(TenantAccessException.class,()->f.bindings.prepare(f.f.start,i,f.deployment,extension(id(99))));assertThrows(TenantAccessException.class,()->f.bindings.prepare(f.f.start,i,f.deployment,null));assertEquals(2,f.db.operations.size());}
    @Test void baseOnlyHistoryCannotCreateWriterAcceptance()throws Exception{var i=prepare();f.launches.attempt(f.f.start,i);var permit=f.bindings.authorizeRecovery(f.a,f.f.recovery);f.sdk(i,i.commandId());f.commit();assertThrows(IllegalArgumentException.class,()->f.delivery.recover(permit));assertTrue(f.f.sql.receipts.isEmpty());}
    @Test void recoveryUsesStoredExtensionAndActualSdkEvidence()throws Exception{var i=prepare();f.launches.attempt(f.f.start,i);var permit=f.bindings.authorizeRecovery(f.a,f.f.recovery);sdk(i,extension(id(98)));f.commit();var received=f.delivery.recover(permit);f.begin();f.bindings.record(f.a,f.f.recovery,received);var accepted=f.bindings.writers().requireAccepted(f.a,id(3));accepted.requireUnchanged();assertEquals(extension(id(98)),accepted.extension());assertEquals(id(90),accepted.temporalRunId());assertEquals(4,f.db.operations.size());verify(f.client,never()).newWorkflowStub(eq(TaskWorkflow.class),any(io.temporal.client.WorkflowOptions.class));}
    @Test void oldBaseLaunchRejectsUnboundExtensionMemo()throws Exception{var i=f.prepare();f.launches.attempt(f.f.start,i);var permit=f.bindings.authorizeRecovery(f.a,f.f.recovery);sdk(i,extension(id(98)));f.commit();assertThrows(IllegalArgumentException.class,()->f.delivery.recover(permit));}
    @Test void extensionActivationIsExplicitDefaultOff(){var condition=TaskRunSectionWriterWorkflowConfiguration.class.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);assertFalse(condition.matchIfMissing());assertTrue(java.util.List.of(condition.name()).contains("medical.task-run.section-writer-v1.enabled"));}
}
