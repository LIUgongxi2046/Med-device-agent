package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.function.ThrowingSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual private control and transition proofs; SQL, mTLS and accepted Temporal
 * ledger are boundary doubles, not evidence of a real deployment or rollback. */
class TaskRunAcceptedStartTest {
    FormalRunControlAccessTest controls; FormalWorkerAccessTest f;
    TaskRunBootstrapSchema json; TaskRunAcceptedStart starts;
    TaskRunLaunchRecoveryAuthority recoveries; TaskRunWorkflowLedger ledger;
    TaskRunLaunchRecoveryAuthority.Verified recovery;
    TaskRunWorkflowLedger.Intent launch; TaskRunWorkflowLedger.Receipt acceptance;
    Reference reference; ObjectNode launchJson, acceptanceJson;
    final AtomicReference<RunTokenAuthorityStore.Run> current = new AtomicReference<>();
    MemorySql db;
    @BeforeEach void setup() throws Exception {
        controls=new FormalRunControlAccessTest();controls.setup();f=controls.f;
        json=new TaskRunBootstrapSchema();db=new MemorySql();
        current.set(copy("QUEUED"));when(f.store.run(f.run,f.actor)).thenAnswer(c->current.get());
        var fixture=new TaskRunBootstrapSchemaTest();launchJson=fixture.launch(fixture.registration(fixture.intent()));
        for(var n:List.of(launchJson,(ObjectNode)launchJson.required("reference")))
            n.put("tenant_id",f.tenant).put("project_id",f.project).put("run_id",f.run);
        launchJson.put("task_id",f.task).put("principal_id",f.principal).put("initiator_id",f.actor).put("command_id",controls.command)
            .put("workflow_id",f.tenant+"/"+f.run).put("reference_sha256",json.hash(launchJson.required("reference")));
        reference=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
            .treeToValue(launchJson.required("reference"),Reference.class);
        acceptanceJson=fixture.acceptance(launchJson);
        for(String field:List.of("tenant_id","project_id","run_id","task_id","principal_id","initiator_id","command_id","workflow_id"))
            acceptanceJson.set(field,launchJson.required(field));
        acceptanceJson.put("launch_intent_sha256",json.hash(launchJson));
        recoveries=mock(TaskRunLaunchRecoveryAuthority.class);ledger=mock(TaskRunWorkflowLedger.class);
        recovery=mock(TaskRunLaunchRecoveryAuthority.Verified.class);launch=mock(TaskRunWorkflowLedger.Intent.class);acceptance=mock(TaskRunWorkflowLedger.Receipt.class);
        when(recoveries.require(any(),eq(f.http),eq(f.run),eq(controls.command))).thenReturn(recovery);
        when(ledger.recover(recovery)).thenReturn(launch);when(ledger.read(recovery)).thenReturn(Optional.of(acceptance));
        when(launch.reference()).thenReturn(reference);when(launch.commandId()).thenReturn(controls.command);
        when(launch.launchId()).thenReturn(launchJson.required("launch_id").asText());when(launch.content()).thenAnswer(c->launchJson.deepCopy());
        when(acceptance.content()).thenAnswer(c->acceptanceJson.deepCopy());when(acceptance.sha256()).thenAnswer(c->json.hash(acceptanceJson));
        when(acceptance.temporalRunId()).thenReturn(acceptanceJson.required("temporal_run_id").asText());
        starts=new TaskRunAcceptedStart(db,recoveries,ledger,json);
    }
    @AfterEach void cleanup(){controls.cleanup();}
    RunTokenAuthorityStore.Run copy(String status){var r=f.persisted;return new RunTokenAuthorityStore.Run(r.tenant(),r.project(),r.id(),r.kind(),r.scope(),r.schema(),r.manifest(),r.principal(),r.actor(),r.capabilities(),r.principalStatus(),r.principalSchema(),r.principalManifest(),r.classification(),r.revision(),r.executionActor(),status,r.ended(),r.closed(),r.superseded(),r.task());}
    TaskNodeProductionBasis proof(EnterpriseAccess.Admission a,boolean running){
        TaskNodeProductionBasis p=running?mock(TaskNodeProductionAuthority.Verified.class):mock(TaskRunBootstrapAuthority.Verified.class);
        when(p.admission()).thenReturn(a);when(p.tenantId()).thenReturn(f.tenant);when(p.projectId()).thenReturn(f.project);
        when(p.taskId()).thenReturn(f.task);when(p.runId()).thenReturn(f.run);when(p.actorId()).thenReturn(f.actor);
        when(p.principalId()).thenReturn(f.principal);when(p.classification()).thenReturn("INTERNAL");when(p.command()).thenReturn(controls.base.start);
        return p;
    }
    com.fasterxml.jackson.databind.JsonNode accept() throws Exception{return controls.access.withReads(f.http,f.tenant,f.run,controls.command,reads->reads.execute(a->ThrowingSupplier.of(()->starts.accept((TaskRunBootstrapAuthority.Verified)proof(a,false),f.http,reference)).get()));}
    com.fasterxml.jackson.databind.JsonNode original() throws Exception{return controls.access.withReads(f.http,f.tenant,f.run,controls.command,reads->reads.execute(a->ThrowingSupplier.of(()->starts.require((TaskNodeProductionAuthority.Verified)proof(a,true),f.http,reference)).get()));}
    @Test void onlyOriginalAcceptedCommandAllowsOneAtomicStateAndJournal()throws Exception{
        var result=accept();assertEquals("RUNNING",current.get().status());assertEquals(4,db.updates);
        assertEquals(acceptance.sha256(),result.required("workflow_acceptance_sha256").asText());
        assertEquals(result,original());assertEquals(4,db.updates);
        verify(ledger,never()).record(any(),any());verify(ledger,never()).attempt(any(),any());
    }
    @Test void absentAcceptanceDoesNotMutateOrTreatLaunchIntentAsAcceptance()throws Exception{
        when(ledger.read(recovery)).thenReturn(Optional.empty());assertThrows(TenantAccessException.class,this::accept);
        assertEquals(0,db.updates);assertEquals("QUEUED",current.get().status());
    }
    @Test void changedWorkflowInputAndCommandAreNotAccepted()throws Exception{
        when(launch.commandId()).thenReturn(f.id(99));assertThrows(TenantAccessException.class,this::accept);assertEquals(0,db.updates);
        when(launch.commandId()).thenReturn(controls.command);
        acceptanceJson.put("reference_sha256","f".repeat(64));assertThrows(RuntimeException.class,this::accept);assertEquals(0,db.updates);
    }
    @Test void directStateMutationCannotUseGeneralControlProof()throws Exception{
        assertThrows(TenantAccessException.class,()->controls.access.withReads(f.http,f.tenant,f.run,controls.command,reads->reads.execute(a->{current.set(copy("RUNNING"));return null;})));
        assertEquals(0,db.updates);
    }
    @Test void currentCertificateChangeCannotBeRebasedWithStatus()throws Exception{
        db.changeCertificate=true;assertThrows(TenantAccessException.class,this::accept);
        assertEquals(1,db.updates);assertTrue(db.operations.isEmpty()); // SQL test double is not rollback evidence.
    }
    @Test void originalReadRejectsMissingOrModifiedJournalAndNeverRepairsIt()throws Exception{
        accept();db.audits.get(0).put("request_id",f.id(99));assertThrows(TenantAccessException.class,this::original);assertEquals(4,db.updates);
    }
    @Test void lostLiveGrantBlocksTransitionBeforeAnyWrite()throws Exception{
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(controls.base.grants).requireResource(any(),eq(f.actor),eq("local"),eq("task.run"),eq("TASK_EXECUTION"),any(),any());
        assertThrows(TenantAccessException.class,this::accept);assertEquals(0,db.updates);
    }
    private TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion question(){
        var q=mock(TaskRegisteredToolWaitingAnswerAuthority.CurrentQuestion.class);
        when(q.tenantId()).thenReturn(f.tenant);when(q.projectId()).thenReturn(f.project);when(q.taskId()).thenReturn(f.task);
        when(q.runId()).thenReturn(f.id(99));when(q.classification()).thenReturn("INTERNAL");return q;
    }
    private com.fasterxml.jackson.databind.JsonNode savedTarget(){
        var saved=JsonMapper.builder().build().createObjectNode();saved.putObject("replay_manifest").put("tenant_id",f.tenant)
            .put("project_id",f.project).put("task_id",f.task).put("run_id",f.run).put("initiator_id",f.actor);return saved;
    }
    @Test void humanReadSeparatesAcceptedWorkflowFromActualStartWithoutMutation()throws Exception {
        var q=question();var observed=new TaskRunWorkflowLedger.QuestionLaunch(launch,acceptance);
        assertNull(starts.questionStart(q,observed,savedTarget()));assertEquals(0,db.updates);
        var actual=accept();assertEquals(actual,starts.questionStart(q,observed,savedTarget()));assertEquals(4,db.updates);
        // STARTED is an immutable historical fact, not a claim the target is still running.
        db.run.put("status","SUCCEEDED");db.run.put("ended_at",db.now.plusSeconds(1));
        assertEquals(actual,starts.questionStart(q,observed,savedTarget()));assertEquals(4,db.updates);
        db.events.get(0).put("payload","{}");assertThrows(TenantAccessException.class,()->starts.questionStart(q,observed,savedTarget()));
    }
    @Test void claimedStartedTimestampWithoutActualReceiptsIsCorruption()throws Exception {
        db.run.put("started_at",db.now);
        assertThrows(TenantAccessException.class,()->starts.questionStart(question(),new TaskRunWorkflowLedger.QuestionLaunch(null,null),savedTarget()));
        assertThrows(TenantAccessException.class,()->starts.questionStart(question(),new TaskRunWorkflowLedger.QuestionLaunch(launch,acceptance),savedTarget()));
        assertEquals(0,db.updates);
    }
    class MemorySql extends JdbcTemplate {
        final Map<String,Object> run=new LinkedHashMap<>();
        final List<Map<String,Object>> operations=new ArrayList<>(),audits=new ArrayList<>(),events=new ArrayList<>();
        final LocalDateTime now=LocalDateTime.parse("2026-10-19T00:00:04");int updates;boolean changeCertificate;
        MemorySql(){run.put("status","QUEUED");run.put("revision",BigInteger.ONE);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){
            if(sql.contains("FROM task_run "))return List.of(new LinkedHashMap<>(run));
            if(sql.contains("FROM operation_record"))return operations;
            if(sql.contains("FROM audit_event"))return audits;
            if(sql.contains("FROM outbox_event"))return events;
            throw new AssertionError(sql);
        }
        @Override public <T>List<T> query(String sql,RowMapper<T> mapper){return query(sql,mapper,new Object[0]);}
        @Override public <T>List<T> query(String sql,RowMapper<T> mapper,Object...args){
            try{var rs=mock(java.sql.ResultSet.class);if(sql.contains("UTC_TIMESTAMP"))when(rs.getTimestamp(1)).thenReturn(Timestamp.valueOf(now));
                else if(sql.contains("FROM run_principal"))when(rs.getString(1)).thenReturn(f.principal);else throw new AssertionError(sql);
                return List.of(mapper.mapRow(rs,0));}catch(Exception e){throw new IllegalStateException(e);}
        }
        @Override public int update(String sql,Object...args){
            updates++;
            if(sql.startsWith("UPDATE task_run")){
                assertEquals("QUEUED",run.get("status"));run.put("status","RUNNING");run.put("started_at",now);run.put("revision",BigInteger.TWO);
                current.set(copy("RUNNING"));if(changeCertificate)when(controls.base.service.certificateSha256()).thenReturn("f".repeat(64));return 1;
            }
            if(sql.startsWith("INSERT INTO operation_record")){operations.add(row("tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at",args[0],args[1],args[2],args[3],args[4],args[5],"COMPLETED",200,args[6],args[7],"1.0.0",args[8],args[9],args[10],args[11],1,args[12]));return 1;}
            if(sql.startsWith("INSERT INTO audit_event")){audits.add(row("tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification",args[0],args[1],args[2],args[3],args[4],args[5],"SUCCESS",args[6],args[7],"1.0.0",args[8],args[9],args[10],args[11]));return 1;}
            if(sql.startsWith("INSERT INTO outbox_event")){events.add(row("tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at",args));return 1;}
            throw new AssertionError("Unexpected write: "+sql);
        }
        Map<String,Object> row(String names,Object...args){var result=new LinkedHashMap<String,Object>();var columns=names.split(",");assertEquals(columns.length,args.length);for(int i=0;i<columns.length;i++)result.put(columns[i],args[i]);return result;}
    }
}
