package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import io.temporal.api.common.v1.*;
import io.temporal.api.history.v1.*;
import io.temporal.api.workflowservice.v1.*;
import io.temporal.client.*;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ruoyi.medical.persistence.TaskRunBootstrapSchemaTest.id;

/** SQL and gRPC responses are doubles. Exercises private permits and the actual
 * V2 SDK response verifier; real in-process SDK replay is covered in workflow. */
class TaskRunWorkflowProtocolBindingTest {
    TaskRunWorkflowLedgerTest f;MemorySql db;TaskRunWorkflowProtocolBinding bindings;TaskRunWorkflowLedger launches;
    EnterpriseAccess.Admission a;TenantScope scope;WaitingWorkflowDeploymentV2 deployment;WorkflowClient client;
    WorkflowServiceGrpc.WorkflowServiceBlockingStub rpc;TaskRunWorkflowDeliveryV2 delivery;Object resource=new Object();
    @BeforeEach void setup()throws Exception{
        f=new TaskRunWorkflowLedgerTest();scope=TenantScope.open(new TenantScope.Identity(id(1),id(6),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.bindResource(resource,new Object());
        a=mock(EnterpriseAccess.Admission.class);when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(a.actorId()).thenReturn(id(6));when(f.start.admission()).thenReturn(a);when(f.start.taskId()).thenReturn(id(5));when(f.start.principalId()).thenReturn(id(4));
        db=new MemorySql();launches=new TaskRunWorkflowLedger(db,f.schema);bindings=new TaskRunWorkflowProtocolBinding(db,f.json,f.schema,launches);
        deployment=new WaitingWorkflowDeploymentV2("2.0.0",id(96),f.config.namespace(),f.config.controlTaskQueue(),f.config.professionalTaskQueue(),"medical.task.run","medical.professional.step.v2","medical.run.commit-waiting.v2",WaitingWorkflowDeploymentV2.MARKER,"medical.run.load.v2","medical.run.recover-step.v2",1000,"medical.run.fail-undispatched.v2","medical.run.execute-stage.v2","medical.run.commit-stage.v2","medical.run.recover-stage.v2");
        client=mock(WorkflowClient.class);when(client.getOptions()).thenReturn(WorkflowClientOptions.newBuilder().setNamespace(f.config.namespace()).setDataConverter(DefaultDataConverter.STANDARD_INSTANCE).build());var service=mock(WorkflowServiceStubs.class);rpc=mock(WorkflowServiceGrpc.WorkflowServiceBlockingStub.class);when(client.getWorkflowServiceStubs()).thenReturn(service);when(service.blockingStub()).thenReturn(rpc);delivery=new TaskRunWorkflowDeliveryV2(client);
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clear();if(TransactionSynchronizationManager.hasResource(resource))TransactionSynchronizationManager.unbindResource(resource);if(scope!=null)scope.close();}
    TaskRunWorkflowLedger.Intent prepare()throws Exception{var i=launches.prepare(f.start,f.registered,f.config);bindings.prepare(f.start,i,deployment);return i;}
    void commit(){for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCommit();TransactionSynchronizationManager.clear();}
    void begin(){TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();}
    void sdk(TaskRunWorkflowLedger.Intent i,String command){var execution=WorkflowExecution.newBuilder().setWorkflowId(i.reference().workflowId()).setRunId(id(90)).build();var memo=Memo.newBuilder();deployment.memo(command).forEach((k,v)->memo.putFields(k,DefaultDataConverter.STANDARD_INSTANCE.toPayload(v).orElseThrow()));var start=WorkflowExecutionStartedEventAttributes.newBuilder().setOriginalExecutionRunId(id(90)).setWorkflowType(WorkflowType.newBuilder().setName("medical.task.run")).setTaskQueue(io.temporal.api.taskqueue.v1.TaskQueue.newBuilder().setName(i.reference().controlTaskQueue())).setMemo(memo).setInput(Payloads.newBuilder().addPayloads(DefaultDataConverter.STANDARD_INSTANCE.toPayload(i.reference()).orElseThrow()));
        when(rpc.describeWorkflowExecution(any(DescribeWorkflowExecutionRequest.class))).thenReturn(DescribeWorkflowExecutionResponse.newBuilder().setWorkflowExecutionInfo(io.temporal.api.workflow.v1.WorkflowExecutionInfo.newBuilder().setExecution(execution)).build());when(rpc.getWorkflowExecutionHistory(any(GetWorkflowExecutionHistoryRequest.class))).thenReturn(GetWorkflowExecutionHistoryResponse.newBuilder().setHistory(History.newBuilder().addEvents(HistoryEvent.newBuilder().setWorkflowExecutionStartedEventAttributes(start))).build());}
    TaskRunWorkflowLedger.Receipt accept()throws Exception{var i=prepare();launches.attempt(f.start,i);var permit=bindings.authorizeRecovery(a,f.recovery);sdk(i,i.commandId());commit();var received=delivery.recover(permit);begin();return bindings.record(a,f.recovery,received);}
    @Test void exactBindingIsImmutableAndDoesNotStartOrChangeRun()throws Exception{var first=prepare();prepare();assertEquals(1,db.operations.size());assertEquals("QUEUED",db.run.get("status"));assertEquals(0,f.sql.attempts.size());verifyNoInteractions(rpc);assertThrows(TenantAccessException.class,()->bindings.requireWaitingV2(a,id(3)));assertEquals(first.launchId(),f.sql.launches.get(0).get("launch_id"));}
    @Test void v1AttemptCannotBeUpgradedAndSameQueueIsNotAProtocolProof()throws Exception{var intent=launches.prepare(f.start,f.registered,f.config);launches.attempt(f.start,intent);assertThrows(TenantAccessException.class,()->bindings.prepare(f.start,intent,deployment));assertEquals(0,db.operations.size());assertThrows(TenantAccessException.class,()->bindings.requireWaitingV2(a,id(3)));verifyNoInteractions(rpc);}
    @Test void changedExplicitDeploymentCannotOverwriteOriginalBinding()throws Exception{var i=prepare();var changed=new WaitingWorkflowDeploymentV2("2.0.0",id(97),deployment.namespace(),deployment.controlTaskQueue(),deployment.professionalTaskQueue(),deployment.workflowType(),deployment.professionalActivity(),deployment.waitingActivity(),deployment.versionMarker(),deployment.loadActivity(),deployment.recoveryActivity(),deployment.recoveryPollMillis(),deployment.undispatchedFailureActivity(),deployment.executeStageActivity(),deployment.commitStageActivity(),deployment.recoverStageActivity());assertThrows(TenantAccessException.class,()->bindings.prepare(f.start,i,changed));assertEquals(1,db.operations.size());}
    @Test void originalMemoCheckedAcceptanceIsAtomicLinkedAndReadOnly()throws Exception{var receipt=accept();assertEquals(id(90),receipt.temporalRunId());assertEquals(2,db.operations.size());assertEquals(1,f.sql.receipts.size());var proof=bindings.requireWaitingV2(a,id(3));proof.requireUnchanged();assertEquals(deployment.canonicalSha256(),proof.protocolSha256());assertEquals(id(7),proof.originalStartCommandId());int updates=db.updates;doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(f.start).requireUnchanged();assertEquals(receipt.sha256(),bindings.read(a,f.recovery).orElseThrow().sha256());proof.requireUnchanged();assertEquals(updates,db.updates);verify(rpc,times(1)).describeWorkflowExecution(any(DescribeWorkflowExecutionRequest.class));}
    @Test void wrongOriginalCommandCannotCreateEitherAcceptanceWitness()throws Exception{var i=prepare();launches.attempt(f.start,i);var permit=bindings.authorizeRecovery(a,f.recovery);sdk(i,id(99));commit();assertThrows(IllegalArgumentException.class,()->delivery.recover(permit));assertEquals(1,db.operations.size());assertTrue(f.sql.receipts.isEmpty());}
    @Test void partialOrTamperedWitnessCannotMintWaitingProof()throws Exception{accept();var proof=bindings.requireWaitingV2(a,id(3));db.audits.values().stream().filter(r->TaskRunWorkflowProtocolBinding.ACCEPT.equals(r.get("action"))).findFirst().orElseThrow().put("request_id",id(99));assertThrows(TenantAccessException.class,proof::requireUnchanged);assertThrows(TenantAccessException.class,()->bindings.requireWaitingV2(a,id(3)));}
    @Test void acceptedProofIsTransactionBoundAndHasNoPublicConstructor()throws Exception{accept();var proof=bindings.requireWaitingV2(a,id(3));for(var c:proof.getClass().getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));for(var s:TransactionSynchronizationManager.getSynchronizations())s.afterCompletion(0);assertThrows(TenantAccessException.class,proof::requireUnchanged);for(var c:TaskRunWorkflowDeliveryV2.Received.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));}
    class MemorySql extends JdbcTemplate{
        final Map<String,Object> run=new HashMap<>(Map.of("tenant_id",id(1),"project_id",id(2),"run_id",id(3),"task_id",id(5),"created_by",id(6),"classification","INTERNAL","workflow_id",id(1)+"/"+id(3),"status","QUEUED"));
        final Map<String,Map<String,Object>> operations=new LinkedHashMap<>(),audits=new LinkedHashMap<>(),events=new LinkedHashMap<>();int updates;
        @Override public <T>List<T> query(String sql,RowMapper<T> mapper,Object...args){return f.sql.query(sql,mapper,args);}@Override public <T>List<T> query(String sql,RowMapper<T> mapper){return f.sql.query(sql,mapper);}
        @Override public List<Map<String,Object>> queryForList(String sql,Object...args){try{
            if(sql.contains("FROM task_run_workflow_launch"))return f.sql.launches;if(sql.contains("FROM task_run_workflow_attempt"))return f.sql.attempts;if(sql.contains("FROM task_run_workflow_acceptance"))return f.sql.receipts;
            if(sql.contains("FROM task_run_replay_registration")){var r=new HashMap<String,Object>();for(String name:List.of("tenant_id","project_id","run_id","classification"))r.put(name,run.get(name));r.put("created_by",id(6));r.put("schema_version","1.0.0");r.put("schema_manifest_sha256",f.schema.manifest());r.put("receipt_json",f.schema.canonical(f.registration));r.put("receipt_sha256",f.schema.hash(f.registration));return List.of(r);}
            if(sql.contains("FROM task_run WHERE"))return List.of(run);var table=sql.contains("FROM operation_record")?operations:sql.contains("FROM audit_event")?audits:sql.contains("FROM outbox_event")?events:null;if(table==null)throw new AssertionError(sql);var row=table.get(String.valueOf(args[1]));return row==null?List.of():List.of(row);
        }catch(Exception e){throw new IllegalStateException(e);}}
        @Override public int update(String sql,Object...args){updates++;if(sql.startsWith("INSERT INTO task_run_workflow_"))return f.sql.update(sql,args);var columns=sql.substring(sql.indexOf('(')+1,sql.indexOf(')')).split(",");var values=sql.substring(sql.indexOf("VALUES(")+7,sql.lastIndexOf(')')).split(",");assertEquals(columns.length,values.length);var row=new LinkedHashMap<String,Object>();int arg=0;for(int i=0;i<columns.length;i++){String token=values[i];Object v=token.equals("?")?args[arg++]:token.equals("NULL")?null:token.startsWith("'")?token.substring(1,token.length()-1):Integer.valueOf(token);row.put(columns[i],v);}assertEquals(args.length,arg);if(sql.startsWith("INSERT INTO operation_record"))operations.put((String)row.get("operation_id"),row);else if(sql.startsWith("INSERT INTO audit_event"))audits.put((String)row.get("audit_id"),row);else if(sql.startsWith("INSERT INTO outbox_event"))events.put((String)row.get("event_id"),row);else throw new AssertionError(sql);return 1;}
    }
}
