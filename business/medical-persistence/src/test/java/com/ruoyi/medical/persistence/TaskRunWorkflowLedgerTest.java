package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.workflow.WorkflowDelivery;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskRunBootstrapSchemaTest.id;

/** Explicit SQL/authority/Temporal boundary doubles. These are not MySQL or real Temporal evidence. */
class TaskRunWorkflowLedgerTest {
    final TaskRunBootstrapSchemaTest fixture=new TaskRunBootstrapSchemaTest();
    final PermissionSchema json=new PermissionSchema();
    final TaskRunBootstrapSchema schema=new TaskRunBootstrapSchema();
    final ObjectNode registration=fixture.registration(fixture.intent());
    final MemorySql sql=new MemorySql();
    final TaskRunWorkflowLedger ledger=new TaskRunWorkflowLedger(sql,schema);
    final TaskRunBootstrapAuthority.Verified start=mock(TaskRunBootstrapAuthority.Verified.class);
    final TaskRunLaunchRecoveryAuthority.Verified recovery=mock(TaskRunLaunchRecoveryAuthority.Verified.class);
    final TaskRunReplayRegistration.Registered registered=mock(TaskRunReplayRegistration.Registered.class);
    final RuntimeReplaySchema.VerifiedInputs replay=mock(RuntimeReplaySchema.VerifiedInputs.class);
    final TaskRunWorkflowConfiguration config=new TaskRunWorkflowConfiguration(id(80),"test-only","spiffe://fixture/worker","fixture-control","fixture-professional",60000,10,3,2,1000,2000,3000);
    TaskRunWorkflowLedgerTest()throws Exception{
        var payload=fixture.identity();payload.remove("command_id");
        payload.set("execution_policy",json.object().set("worker",json.object().put("service_uri",config.serviceUri())));
        when(replay.content()).thenAnswer(a->payload.deepCopy());
        var command=json.object().put("command_id",id(7)).put("command_sha256","a".repeat(64)).put("replay_sha256",registration.required("object_ref").required("sha256").asText());
        var envelope=json.object().set("command",command);
        when(start.authorizationsSha256()).thenReturn("d".repeat(64));when(start.tenantId()).thenReturn(id(1));when(start.projectId()).thenReturn(id(2));when(start.runId()).thenReturn(id(3));when(start.actorId()).thenReturn(id(6));when(start.classification()).thenReturn("INTERNAL");when(start.command()).thenReturn(envelope);when(start.replay()).thenReturn(replay);
        when(recovery.tenantId()).thenReturn(id(1));when(recovery.projectId()).thenReturn(id(2));when(recovery.runId()).thenReturn(id(3));when(recovery.actorId()).thenReturn(id(6));when(recovery.classification()).thenReturn("INTERNAL");when(recovery.command()).thenReturn(envelope);when(recovery.replay()).thenReturn(replay);
        when(registered.receipt()).thenAnswer(a->registration.deepCopy());when(registered.receiptSha256()).thenReturn(schema.hash(registration));when(registered.artifactId()).thenReturn(id(10));
        var o=registration.required("object_ref");when(registered.reference()).thenReturn(new ObjectRef(o.required("object_key").asText(),o.required("object_version").asText(),o.required("sha256").asText(),"2","application/json"));
        begin();
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clear();}
    void begin(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
    void commit(){for(var s:TransactionSynchronizationManager.getSynchronizations())s.afterCommit();TransactionSynchronizationManager.clear();}
    TaskRunWorkflowLedger.Attempt attempt()throws Exception{return ledger.attempt(start,ledger.prepare(start,registered,config));}
    TaskRunWorkflowDelivery.Boundary transport(){var t=mock(TaskRunWorkflowDelivery.Boundary.class);when(t.namespace()).thenReturn(config.namespace());return t;}
    @Test void intentAndAttemptCommitBeforeSingleStartAndReceiptNeedsActualAck()throws Exception{
        var attempt=attempt();assertEquals(1,sql.launches.size());assertEquals(1,sql.attempts.size());
        var permit=ledger.authorizeStart(start,attempt,config);var t=transport();var delivery=new TaskRunWorkflowDelivery(t);
        var ack=new WorkflowDelivery.Acceptance(attempt.intent().reference().workflowId(),id(90),attempt.intent().reference().inputSha256());
        when(t.start(any(),eq(id(7)))).thenReturn(ack);when(t.recover(any(),eq(id(7)))).thenReturn(ack);
        assertThrows(IllegalStateException.class,()->delivery.deliver(permit));verify(t,never()).start(any(),any());
        commit();var actual=delivery.deliver(permit);verify(t,times(1)).start(eq(attempt.intent().reference()),eq(id(7)));
        assertTrue(sql.receipts.isEmpty());begin();var receipt=ledger.record(recovery,actual);assertEquals(id(90),receipt.temporalRunId());commit();
        assertThrows(IllegalStateException.class,()->delivery.deliver(permit));verify(t,times(1)).start(any(),any());verify(t,never()).recover(any(),any());
        begin();assertEquals(receipt.sha256(),ledger.read(recovery).orElseThrow().sha256());
    }
    @Test void unknownOutcomeRecoversOriginalCommandWithoutReplacementStartOrNewExecutionProof()throws Exception{
        var original=attempt();var permit=ledger.authorizeStart(start,original,config);commit();
        var t=transport();when(t.start(any(),any())).thenThrow(new IllegalStateException("synthetic lost acknowledgement"));
        var delivery=new TaskRunWorkflowDelivery(t);assertThrows(IllegalStateException.class,()->delivery.deliver(permit));
        begin();var recovered=ledger.authorizeRecovery(recovery);commit();
        when(t.recover(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(recovered.intent().reference().workflowId(),id(91),recovered.intent().reference().inputSha256()));
        var result=delivery.recover(recovered);begin();assertEquals(id(91),ledger.record(recovery,result).temporalRunId());
        verify(t,times(1)).start(any(),eq(id(7)));verify(t,times(1)).recover(eq(original.intent().reference()),eq(id(7)));
        assertEquals(1,sql.launches.size());assertEquals(1,sql.attempts.size());
    }
    @Test void queryNotFoundRemainsUnresolvedAndDoesNotStart()throws Exception{
        var original=attempt();commit();begin();var intent=ledger.authorizeRecovery(recovery);commit();
        var t=transport();when(t.recover(any(),any())).thenThrow(new IllegalStateException("NOT_FOUND remains ambiguous"));
        assertThrows(IllegalStateException.class,()->new TaskRunWorkflowDelivery(t).recover(intent));verify(t,never()).start(any(),any());assertTrue(sql.receipts.isEmpty());
    }
    @Test void wrongNamespaceOrActualInputHashCannotBecomeReceipt()throws Exception{
        var attempt=attempt();var permit=ledger.authorizeStart(start,attempt,config);commit();var t=transport();when(t.namespace()).thenReturn("wrong");
        var delivery=new TaskRunWorkflowDelivery(t);assertThrows(IllegalArgumentException.class,()->delivery.deliver(permit));verify(t,never()).start(any(),any());
        when(t.namespace()).thenReturn(config.namespace());when(t.start(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(attempt.intent().reference().workflowId(),id(92),"0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->delivery.deliver(permit));assertTrue(sql.receipts.isEmpty());
    }
    @Test void staleAuthorityOrCrossActorRegistrationFailsBeforeStart()throws Exception{
        doThrow(new IllegalStateException("stale authority")).when(start).requireUnchanged();
        assertThrows(IllegalStateException.class,()->ledger.prepare(start,registered,config));assertTrue(sql.launches.isEmpty());
        doNothing().when(start).requireUnchanged();registration.put("initiator_id",id(99));
        assertThrows(RuntimeException.class,()->ledger.prepare(start,registered,config));assertTrue(sql.launches.isEmpty());
    }
    @Test void changedPinnedConfigurationAndTamperedIntentAreRejected()throws Exception{
        var attempt=attempt();var changed=new TaskRunWorkflowConfiguration(config.revision(),"other",config.serviceUri(),config.controlTaskQueue(),config.professionalTaskQueue(),60000,10,3,2,1000,2000,3000);
        assertThrows(RuntimeException.class,()->ledger.prepare(start,registered,changed));
        sql.launches.get(0).put("intent_sha256","0".repeat(64));assertThrows(RuntimeException.class,()->ledger.recover(recovery));
    }
    @Test void tamperedReceiptAndDifferentActualRunCannotOverwriteAcceptance()throws Exception{
        var attempt=attempt();var permit=ledger.authorizeStart(start,attempt,config);commit();var t=transport();
        when(t.start(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(attempt.intent().reference().workflowId(),id(93),attempt.intent().reference().inputSha256()));
        var delivery=new TaskRunWorkflowDelivery(t);var actual=delivery.deliver(permit);begin();ledger.record(recovery,actual);commit();
        when(t.recover(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(attempt.intent().reference().workflowId(),id(94),attempt.intent().reference().inputSha256()));
        begin();var recoveryPermit=ledger.authorizeRecovery(recovery);commit();
        var other=delivery.recover(recoveryPermit);begin();assertThrows(RuntimeException.class,()->ledger.record(recovery,other));
        sql.receipts.get(0).put("receipt_sha256","0".repeat(64));assertThrows(RuntimeException.class,()->ledger.read(recovery));assertEquals(1,sql.receipts.size());
    }
    @Test void recoveryRequiresCommittedFreshOneUsePermitAndNoRetainedIntentOverload()throws Exception{
        attempt();var permit=ledger.authorizeRecovery(recovery);var t=transport();var delivery=new TaskRunWorkflowDelivery(t);
        assertThrows(IllegalStateException.class,()->delivery.recover(permit));verify(t,never()).recover(any(),any());
        commit();when(t.recover(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(permit.intent().reference().workflowId(),id(95),permit.intent().reference().inputSha256()));
        delivery.recover(permit);assertThrows(IllegalStateException.class,()->delivery.recover(permit));verify(t,times(1)).recover(any(),any());
        assertThrows(NoSuchMethodException.class,()->TaskRunWorkflowDelivery.class.getMethod("recover",TaskRunWorkflowLedger.Intent.class));
    }
    @Test void rollbackCannotUnlockPhysicalStartPermit()throws Exception{
        var attempt=attempt();var permit=ledger.authorizeStart(start,attempt,config);
        TransactionSynchronizationManager.clear();var t=transport();
        assertThrows(RuntimeException.class,()->new TaskRunWorkflowDelivery(t).deliver(permit));verify(t,never()).start(any(),any());
    }
    @Test void expiredAndCrossThreadRecoveryPermitsCannotQuery()throws Exception{
        attempt();var permit=ledger.authorizeRecovery(recovery);commit();var t=transport();var delivery=new TaskRunWorkflowDelivery(t);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var otherThread=new Thread(()->{try{delivery.recover(permit);}catch(Throwable e){failure.set(e);}});
        otherThread.start();otherThread.join();assertInstanceOf(IllegalStateException.class,failure.get());
        // Explicit test-clock boundary: advance the commit age without sleeping or external work.
        org.springframework.test.util.ReflectionTestUtils.setField(permit,"committedNanos",
            System.nanoTime()-java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(config.readTimeoutMillis())-1);
        assertThrows(IllegalStateException.class,()->delivery.recover(permit));verify(t,never()).recover(any(),any());verify(t,never()).start(any(),any());
    }
    private TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion question(){
        var q=mock(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion.class);
        when(q.tenantId()).thenReturn(id(1));when(q.projectId()).thenReturn(id(2));when(q.runId()).thenReturn(id(99));
        when(q.taskId()).thenReturn(id(5));when(q.classification()).thenReturn("INTERNAL");return q;
    }
    private JsonNode savedQuestionTarget(){
        var saved=json.object().put("created_by",id(6));saved.set("replay_manifest",replay.content());saved.set("command",start.command());return saved;
    }
    @Test void humanContinuationDoesNotTurnCreationIntentOrAttemptIntoAcceptance()throws Exception {
        var q=question();var saved=savedQuestionTarget();
        assertNull(ledger.questionLaunch(q,saved).acceptance());assertTrue(sql.launches.isEmpty());
        ledger.prepare(start,registered,config);assertNull(ledger.questionLaunch(q,saved).acceptance());assertTrue(sql.attempts.isEmpty());
        attempt();assertNull(ledger.questionLaunch(q,saved).acceptance());assertTrue(sql.receipts.isEmpty());
        assertEquals(1,sql.launches.size());assertEquals(1,sql.attempts.size());
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(q).requireUnchanged();
        assertThrows(TenantAccessException.class,()->ledger.questionLaunch(q,saved));
    }
    @Test void humanContinuationRequiresTheOriginalReceiptHashAndCannotRepairIt()throws Exception {
        var attempt=attempt();var permit=ledger.authorizeStart(start,attempt,config);commit();var t=transport();
        when(t.start(any(),any())).thenReturn(new WorkflowDelivery.Acceptance(attempt.intent().reference().workflowId(),id(96),attempt.intent().reference().inputSha256()));
        var received=new TaskRunWorkflowDelivery(t).deliver(permit);begin();var receipt=ledger.record(recovery,received);
        var q=question();var saved=savedQuestionTarget();assertEquals(receipt.sha256(),ledger.questionLaunch(q,saved).acceptance().sha256());
        sql.receipts.get(0).put("receipt_sha256","0".repeat(64));assertThrows(RuntimeException.class,()->ledger.questionLaunch(q,saved));
        assertEquals(1,sql.receipts.size());verify(t,times(1)).start(any(),any());verify(t,never()).recover(any(),any());
    }
    class MemorySql extends JdbcTemplate {
        final List<Map<String,Object>> launches=new ArrayList<>(),attempts=new ArrayList<>(),receipts=new ArrayList<>();
        @Override public int update(String query,Object...args){
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            String table=query.substring("INSERT INTO ".length(),query.indexOf('('));String[] columns=query.substring(query.indexOf('(')+1,query.indexOf(')')).split(",");
            var row=new HashMap<String,Object>();for(int i=0;i<columns.length;i++)row.put(columns[i],args[i]);
            switch(table){case "task_run_workflow_launch"->launches.add(row);case "task_run_workflow_attempt"->attempts.add(row);case "task_run_workflow_acceptance"->receipts.add(row);default->throw new AssertionError(table);}return 1;
        }
        @Override public <T>List<T> query(String query,RowMapper<T> mapper){return query(query,mapper,new Object[0]);}
        @Override public <T>List<T> query(String query,RowMapper<T> mapper,Object...args){
            try{
                List<List<Object>> rows;
                if(query.equals("SELECT UTC_TIMESTAMP(6)"))rows=List.of(List.of(LocalDateTime.parse("2026-10-19T00:00:02")));
                else if(query.contains("FROM task_run_replay_registration"))rows=List.of(List.of(schema.canonical(registration),schema.hash(registration),id(10),registration.required("object_ref").required("sha256").asText(),"actual-version-fixture",LocalDateTime.parse("2026-10-19T00:00:01")));
                else if(query.contains("FROM task_run_workflow_launch"))rows=select(launches,"launch_id,command_id,command_sha256,workflow_id,reference_json,reference_sha256,intent_json,intent_sha256,created_at");
                else if(query.contains("FROM task_run_workflow_attempt"))rows=select(attempts,"created_at");
                else if(query.contains("FROM task_run_workflow_acceptance"))rows=select(receipts,"launch_id,temporal_run_id,receipt_json,receipt_sha256,created_at");
                else throw new AssertionError(query);
                var result=new ArrayList<T>();for(var row:rows){var rs=mock(ResultSet.class);when(rs.getString(anyInt())).thenAnswer(a->row.get(a.<Integer>getArgument(0)-1).toString());when(rs.getTimestamp(anyInt())).thenAnswer(a->Timestamp.valueOf((LocalDateTime)row.get(a.<Integer>getArgument(0)-1)));result.add(mapper.mapRow(rs,result.size()));}return result;
            }catch(Exception e){throw new RuntimeException(e);}
        }
        List<List<Object>> select(List<Map<String,Object>> rows,String columns){return rows.stream().map(r->Arrays.stream(columns.split(",")).map(r::get).toList()).toList();}
    }
}
