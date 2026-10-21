package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.*;
import org.springframework.util.function.ThrowingSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual private Pending/actual control lifetime; node/input/SQL boundaries are
 * doubles. Rollback here is a deterministic transaction model, not MySQL isolation
 * acceptance. No real lease, credential, object or provider operation is executed. */
class TaskRunDispatchReservationTest {
    FormalRunControlAccessTest controls;FormalWorkerAccessTest f;MemorySql db;TaskRunDispatchReservation reservations;
    TaskRunActivityPreparation.Current current;TaskNodeProductionAuthority.Verified production;TaskNodeInputAuthority inputAuthority;
    TaskNodeInputAuthority.Verified input;TaskRunGraphService.Verified graph;FormalTaskNodeAuthority.Verified node;
    PermissionSchema json;WorkflowProtocol.Reference reference;String command;boolean failCommit;
    @BeforeEach void setup()throws Exception{
        json=new PermissionSchema();
        controls=new FormalRunControlAccessTest();controls.setup();f=controls.f;db=new MemorySql();command=f.id(81);
        reference=new WorkflowProtocol.Reference("1.0.0",f.tenant,f.project,f.run,WorkflowProtocol.ExecutionKind.TASK,f.id(82),"e".repeat(64),f.id(83),9999999999999L,10,10,1,1000,1000,1000,"control","professional");
        current=mock(TaskRunActivityPreparation.Current.class);production=mock(TaskNodeProductionAuthority.Verified.class);inputAuthority=mock(TaskNodeInputAuthority.class);input=mock(TaskNodeInputAuthority.Verified.class);graph=mock(TaskRunGraphService.Verified.class);node=mock(FormalTaskNodeAuthority.Verified.class);
        when(current.production()).thenReturn(production);when(current.graph()).thenReturn(graph);when(production.tenantId()).thenReturn(f.tenant);when(production.projectId()).thenReturn(f.project);when(production.runId()).thenReturn(f.run);
        when(graph.nodes()).thenReturn(List.of(new TaskRunGraphService.Node(f.id(84),"q",f.step,"fixed question",f.id(85),List.of())));
        when(inputAuthority.require(any(),eq(f.run),eq(f.step),any(),any())).thenReturn(input);
        when(input.tenantId()).thenReturn(f.tenant);when(input.projectId()).thenReturn(f.project);when(input.taskId()).thenReturn(f.task);when(input.runId()).thenReturn(f.run);when(input.stepId()).thenReturn(f.step);
        when(input.graphId()).thenReturn(f.id(86));when(input.graphSha256()).thenReturn("b".repeat(64));when(input.inputId()).thenReturn(f.id(87));when(input.inputSha256()).thenReturn("a".repeat(64));when(input.receiptSha256()).thenReturn("c".repeat(64));
        when(input.objectReference()).thenReturn(new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef("fixed/input","version-1","a".repeat(64),"10","application/json"));when(input.manifest()).thenReturn(json.object().put("node_kind","N02"));when(node.currentInput()).thenReturn(input);
        reservations=new TaskRunDispatchReservation(db,inputAuthority,controls.base.services,json);
        doAnswer(call->{
            var scope=TenantScope.requireCurrent();Object key=new Object(),resource=new Object();TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(key,resource);TransactionSynchronizationManager.initSynchronization();scope.verifyTransaction(controls.epochs);
            var before=db.copy();boolean committed=false;
            try{Object result=((Function<TenantWatermarks,?>)call.getArgument(0)).apply(controls.epochs);if(failCommit)throw new IllegalStateException("crash before commit");for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCommit();committed=true;return result;}
            finally{if(!committed)db.restore(before);for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(committed?TransactionSynchronization.STATUS_COMMITTED:TransactionSynchronization.STATUS_ROLLED_BACK);TransactionSynchronizationManager.clearSynchronization();scope.finishTransaction();TransactionSynchronizationManager.unbindResource(key);TransactionSynchronizationManager.setActualTransactionActive(false);}
        }).when(controls.tx).execute(any());
    }
    @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();controls.cleanup();}
    <T>T tx(Checked<T> callback)throws Exception{return controls.access.withReads(f.http,f.tenant,f.run,controls.command,reads->reads.execute(a->ThrowingSupplier.of(()->{when(current.admission()).thenReturn(a);when(node.controlAdmission()).thenReturn(a);return callback.apply(a);}).get()));}
    @FunctionalInterface interface Checked<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
    TaskRunDispatchReservation.Pending reserve()throws Exception{return tx(a->reservations.reserve(current,f.http,reference,f.step,command));}
    RunTokenAuthority.ClaimRequest claim(){return new RunTokenAuthority.ClaimRequest(f.tenant,f.actor,f.run,f.step,BigInteger.ONE,Duration.ofSeconds(30));}
    RunTokenAuthorityStore.Step ready(){var step=mock(RunTokenAuthorityStore.Step.class);when(step.status()).thenReturn("READY");when(step.revision()).thenReturn(BigInteger.ONE);when(step.generation()).thenReturn(BigInteger.ZERO);when(step.input()).thenReturn("a".repeat(64));return step;}
    void before(TaskRunDispatchReservation.Pending p)throws Exception{p.before(node,claim(),controls.base.service,f.persisted,ready());}
    RunTokenAuthority.Lease lease(){var lease=mock(RunTokenAuthority.Lease.class);when(lease.runId()).thenReturn(f.run);when(lease.stepId()).thenReturn(f.step);when(lease.owner()).thenReturn("spiffe://medical/runtime");when(lease.generation()).thenReturn(BigInteger.ONE);when(lease.revision()).thenReturn(BigInteger.TWO);when(lease.expiresAt()).thenReturn(db.now.plusSeconds(30).toInstant(ZoneOffset.UTC));return lease;}
    void mutateLease(){db.step.put("status","RUNNING");db.step.put("lease_owner","spiffe://medical/runtime");db.step.put("lease_generation",1);db.step.put("revision",2);db.step.put("lease_expires_at",db.now.plusSeconds(30));}
    @Test void originalReservationIsImmutableAndDoesNotClaimLeaseOrMoney()throws Exception{
        var first=reserve();assertEquals(f.actor,first.actor());assertEquals(BigInteger.ONE,first.revision());assertEquals(3,db.updates);assertEquals("READY",db.step.get("status"));
        reserve();assertEquals(3,db.updates);assertEquals(1,db.operations.size());assertTrue(db.writes.stream().noneMatch(s->s.contains("budget")||s.contains("run_token")||s.startsWith("UPDATE")));
    }
    @Test void pendingCannotBeUsedBeforeCommitOrAfterRollback()throws Exception{
        var escaped=new AtomicReference<TaskRunDispatchReservation.Pending>();
        assertThrows(IllegalStateException.class,()->tx(a->{var p=reservations.reserve(current,f.http,reference,f.step,command);escaped.set(p);assertThrows(TenantAccessException.class,p::actor);failCommit=true;return null;}));
        assertEquals(0,db.operations.size());assertNotNull(escaped.get(),"The reservation proof must exist before the simulated rollback");assertThrows(TenantAccessException.class,()->escaped.get().actor());
    }
    @Test void crossCommandAndChangedInputBudgetOrProducerPinsCannotClaim()throws Exception{
        var p=reserve();command=f.id(99);assertThrows(TenantAccessException.class,this::reserve);command=f.id(81);
        db.intent.put("producer_decision_id",f.id(98));assertThrows(TenantAccessException.class,()->tx(a->{before(p);return null;}));assertEquals("READY",db.step.get("status"));assertEquals(1,db.operations.size());
    }
    @Test void currentNodeRevocationBeforeClaimWritesNothing()throws Exception{
        var p=reserve();doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(node).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(a->{before(p);return null;}));assertEquals("READY",db.step.get("status"));assertEquals(1,db.operations.size());
    }
    @Test void crashAfterLeaseBeforeReceiptRollsBackBothAndPermitsOnlyOriginalReadyClaim()throws Exception{
        var p=reserve();assertThrows(IllegalStateException.class,()->tx(a->{before(p);mutateLease();throw new IllegalStateException("crash after lease before receipt");}));
        assertEquals("READY",db.step.get("status"));assertEquals(1,db.operations.size());var retry=reserve();tx(a->{before(retry);mutateLease();retry.after(node,lease());return null;});assertEquals("RUNNING",db.step.get("status"));assertEquals(2,db.operations.size());
    }
    @Test void crashAfterReceiptBeforeCommitRollsBackLeaseAndClaimedReceipt()throws Exception{
        var p=reserve();assertThrows(IllegalStateException.class,()->tx(a->{before(p);mutateLease();p.after(node,lease());assertEquals(2,db.operations.size(),"Claim receipt exists before simulated commit failure");failCommit=true;return null;}));assertEquals("READY",db.step.get("status"));assertEquals(1,db.operations.size());
    }
    @Test void claimedCommandCannotReclaimExpiredLeaseOrReusePending()throws Exception{
        var p=reserve();tx(a->{before(p);mutateLease();p.after(node,lease());assertThrows(TenantAccessException.class,()->p.after(node,lease()));return null;});
        db.step.put("lease_expires_at",db.now.minusSeconds(1));assertThrows(TenantAccessException.class,this::reserve);assertEquals(2,db.operations.size());
    }
    @Test void twoCommittedPendingProofsCannotBothClaimSameReadyGeneration()throws Exception{
        var first=reserve();var second=reserve();tx(a->{before(first);mutateLease();first.after(node,lease());return null;});
        assertThrows(TenantAccessException.class,()->tx(a->{before(second);return null;}));assertEquals(2,db.operations.size());assertEquals("1",String.valueOf(db.step.get("lease_generation")));
    }
    @Test void tamperedOrPartialOriginalJournalNeverBecomesNewDispatch()throws Exception{
        reserve();db.audits.values().iterator().next().put("request_id",f.id(99));assertThrows(TenantAccessException.class,this::reserve);assertEquals(1,db.operations.size());
    }
    @Test void privateProofIsNotAPublicGenericCallback()throws Exception{
        for(var ctor:TaskRunDispatchReservation.Pending.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
        var method=RunTokenAuthority.class.getDeclaredMethod("claimReserved",jakarta.servlet.http.HttpServletRequest.class,RunTokenAuthority.ClaimRequest.class,TaskRunDispatchReservation.Pending.class);assertFalse(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }
    class MemorySql extends JdbcTemplate {
        final LocalDateTime now=LocalDateTime.parse("2026-10-19T00:00:04");final Map<String,Object> step=new LinkedHashMap<>(),intent=new LinkedHashMap<>(),run=new LinkedHashMap<>();
        final Map<String,Map<String,Object>> operations=new LinkedHashMap<>(),audits=new LinkedHashMap<>(),events=new LinkedHashMap<>();final List<String>writes=new ArrayList<>();int updates;
        MemorySql(){step.put("status","READY");step.put("revision",1);step.put("lease_generation",0);step.put("input_sha256","a".repeat(64));step.put("run_classification","INTERNAL");run.put("project_id",f.project);run.put("task_id",f.task);run.put("parent_run_id",null);run.put("status","RUNNING");run.put("principal_id",f.principal);run.put("replay_manifest","{\"budget_revision\":\""+f.id(90)+"\"}");intent.put("producer_rule_id",f.id(91));intent.put("producer_sha256","d".repeat(64));intent.put("producer_decision_id",f.id(92));intent.put("producer_decision_sha256","f".repeat(64));}
        @Override public List<Map<String,Object>> queryForList(String sql,Object... args){
            if(sql.equals("SELECT project_id,task_id,parent_run_id,replay_manifest FROM task_run WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE")){
                assertArrayEquals(new Object[]{f.tenant,f.run},args);
                var originalRun=new LinkedHashMap<String,Object>();
                for(String column:List.of("project_id","task_id","parent_run_id","replay_manifest"))originalRun.put(column,run.get(column));
                return List.of(originalRun);
            }
            if(sql.contains("FROM run_step"))return List.of(new LinkedHashMap<>(step));if(sql.contains("FROM task_node_input_intent"))return List.of(new LinkedHashMap<>(intent));if(sql.contains("FROM task_run r JOIN run_principal"))return List.of(new LinkedHashMap<>(run));if(sql.contains("FROM step_runtime_attempt"))return List.of();
            Map<String,Map<String,Object>> table=sql.contains("FROM operation_record")?operations:sql.contains("FROM audit_event")?audits:sql.contains("FROM outbox_event")?events:null;if(table==null)throw new AssertionError(sql);var row=table.get(String.valueOf(args[1]));return row==null?List.of():List.of(new LinkedHashMap<>(row));
        }
        @Override public <T>List<T> query(String sql,RowMapper<T> mapper){try{var rs=mock(java.sql.ResultSet.class);when(rs.getTimestamp(1)).thenReturn(Timestamp.valueOf(now));return List.of(mapper.mapRow(rs,0));}catch(Exception e){throw new IllegalStateException(e);}}
        @Override public int update(String sql,Object...a){updates++;writes.add(sql);
            if(sql.startsWith("INSERT INTO operation_record")){operations.put((String)a[1],row("tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at",a[0],a[1],a[2],a[3],a[4],a[5],"COMPLETED",200,a[6],a[7],"1.0.0",a[8],a[9],a[10],a[11],1,a[12]));return 1;}
            if(sql.startsWith("INSERT INTO audit_event")){audits.put((String)a[2],row("tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification",a[0],a[1],a[2],a[3],a[4],a[5],"SUCCESS",a[6],a[7],"1.0.0",a[8],a[9],a[10],a[11]));return 1;}
            if(sql.startsWith("INSERT INTO outbox_event")){events.put((String)a[1],row("tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at",a[0],a[1],a[2],1,a[3],a[4],a[5]));return 1;}throw new AssertionError(sql);
        }
        Map<String,Object> row(String columns,Object...values){var out=new LinkedHashMap<String,Object>();var names=columns.split(",");assertEquals(names.length,values.length);for(int i=0;i<names.length;i++)out.put(names[i],values[i]);return out;}
        List<Object> copy(){return List.of(new LinkedHashMap<>(step),copyRows(operations),copyRows(audits),copyRows(events));}
        Map<String,Map<String,Object>> copyRows(Map<String,Map<String,Object>> source){var out=new LinkedHashMap<String,Map<String,Object>>();source.forEach((k,v)->out.put(k,new LinkedHashMap<>(v)));return out;}
        @SuppressWarnings("unchecked")void restore(List<Object> copy){step.clear();step.putAll((Map<String,Object>)copy.get(0));operations.clear();operations.putAll((Map<String,Map<String,Object>>)copy.get(1));audits.clear();audits.putAll((Map<String,Map<String,Object>>)copy.get(2));events.clear();events.putAll((Map<String,Map<String,Object>>)copy.get(3));}
    }
}
