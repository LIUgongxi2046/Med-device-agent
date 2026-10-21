package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real commit writer and schemas with explicit current-authority/JDBC doubles. No DB claim. */
class TaskResearchCommitServiceTest {
 final PermissionSchema json=new PermissionSchema();final JdbcTemplate db=mock(JdbcTemplate.class);
 final FormalTaskNodeAuthority.Verified node=mock(FormalTaskNodeAuthority.Verified.class);
 final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);
 final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
 final TaskResearchCompletionAuthority authority=mock(TaskResearchCompletionAuthority.class);
 final TaskResearchCompletionAuthority.ProducedCompletion produced=mock(TaskResearchCompletionAuthority.ProducedCompletion.class);
 final TaskRuntimeJournal journal=mock(TaskRuntimeJournal.class);
 final TaskResearchCommitService service=new TaskResearchCommitService(mock(DataSource.class),json,authority,journal);
 final String tenant=id(1),project=id(2),run=id(3),step=id(4),actor=id(5),action=id(6),command=id(7),result=id(8),hash="a".repeat(64);
 final WorkflowProtocol.Dispatch dispatch=new WorkflowProtocol.Dispatch(run,step,action,command,hash,1,id(9),"b".repeat(64));
 final List<String> writes=new ArrayList<>();boolean casFails;String state="SUCCEEDED";String waiting;
 TaskResearchCommitServiceTest()throws Exception {
  ReflectionTestUtils.setField(service,"db",db);when(journal.requireExistingStep(eq(node),any())).thenReturn(dispatch);when(node.controlAdmission()).thenReturn(admission);when(node.currentInput()).thenReturn(input);
  when(admission.controlWorker()).thenReturn(true);when(admission.actorId()).thenReturn(actor);when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));
  when(input.projectId()).thenReturn(project);when(input.runId()).thenReturn(run);when(input.stepId()).thenReturn(step);when(input.inputSha256()).thenReturn(hash);
  when(authority.requireProduced(admission,run,step,result)).thenReturn(produced);when(produced.actionId()).thenReturn(action);when(produced.leaseGeneration()).thenReturn(1L);when(produced.stepRevision()).thenReturn(7L);when(produced.resultSha256()).thenReturn(hash);
  when(produced.outcome()).thenAnswer(c->state);when(produced.waitingItemId()).thenAnswer(c->waiting);
  when(produced.outputs()).thenReturn(List.of(new WorkflowProtocol.Output(result,hash,"https://example.test/result"),new WorkflowProtocol.Output(id(10),hash,"https://example.test/evidence"),new WorkflowProtocol.Output(id(11),hash,"https://example.test/export")));
  when(produced.canonicalCommitRequest()).thenAnswer(c->request());
  var attempt=new LinkedHashMap<String,Object>();attempt.put("command_id",command);attempt.put("action_id",action);attempt.put("input_sha256",hash);attempt.put("envelope_artifact_id",dispatch.stepEnvelopeArtifactId());attempt.put("envelope_sha256",dispatch.stepEnvelopeSha256());attempt.put("worker_service_uri","spiffe://medical/worker");JsonNode body=json.tree(attempt);attempt.put("canonical_dispatch",json.canonical(body));attempt.put("dispatch_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(body)));
  when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String sql=c.getArgument(0);if(sql.contains("FROM step_runtime_attempt"))return List.of(attempt);if(sql.contains("FROM run_runtime_receipt"))return List.of();if(sql.contains("FROM task_run"))return List.of(Map.of("classification","RESTRICTED"));throw new AssertionError(sql);});
  when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenReturn(List.of(LocalDateTime.of(2026,10,20,2,0)));
  when(db.update(anyString(),any(Object[].class))).thenAnswer(c->{String sql=c.getArgument(0);writes.add(sql);return casFails&&sql.startsWith("UPDATE run_step")?0:1;});
 }
 @BeforeEach void begin(){TransactionSynchronizationManager.setActualTransactionActive(true);}
 @AfterEach void end(){TransactionSynchronizationManager.setActualTransactionActive(false);}
 @Test void actualProducedCompletionWritesReceiptAndStepButNeverFinishesRun()throws Exception {var r=service.commit(node,dispatch,completion());assertEquals(WorkflowProtocol.State.SUCCEEDED,r.status());assertEquals(3,r.outputs().size());assertEquals(4,writes.size());assertTrue(writes.get(3).contains("lease_expires_at>UTC_TIMESTAMP(6)"));assertTrue(writes.stream().noneMatch(s->s.startsWith("UPDATE task_run")));verify(produced,atLeast(2)).requireUnchanged();}
 @Test void unresolvedChecksCommitWaitingNotSuccessfulReceipt()throws Exception {state="WAITING_INPUT";waiting=id(12);var r=service.commit(node,dispatch,completion());assertEquals(WorkflowProtocol.State.WAITING_INPUT,r.status());assertEquals(waiting,r.waitingItemId());}
 @Test void callerCannotTurnWaitingEvidenceIntoSuccess() {state="WAITING_INPUT";waiting=id(12);assertThrows(TenantAccessException.class,()->service.commit(node,dispatch,new WorkflowProtocol.Completion(run,step,hash,1,WorkflowProtocol.State.SUCCEEDED,result,hash)));assertTrue(writes.isEmpty());}
 @Test void revokedProducingAuthorityWritesNothing()throws Exception {doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(produced).requireUnchanged();assertThrows(TenantAccessException.class,()->service.commit(node,dispatch,completion()));assertTrue(writes.isEmpty());}
 @Test void staleLeaseCasThrowsForEnclosingTransactionRollback(){casFails=true;assertThrows(TenantAccessException.class,()->service.commit(node,dispatch,completion()));assertEquals(4,writes.size());/* JDBC double does not emulate rollback. */}
 @Test void siblingRunAndLeaseCannotBorrowReceipt(){assertThrows(TenantAccessException.class,()->service.commit(node,dispatch,new WorkflowProtocol.Completion(id(13),step,hash,1,WorkflowProtocol.State.SUCCEEDED,result,hash)));assertTrue(writes.isEmpty());assertThrows(TenantAccessException.class,()->service.commit(node,dispatch,new WorkflowProtocol.Completion(run,step,hash,2,WorkflowProtocol.State.SUCCEEDED,result,hash)));assertTrue(writes.isEmpty());}
 @Test void terminalOriginalRecoveryDoesNotMintFreshExecutionAuthority()throws Exception {
  var memory=recordCommit();var receipt=service.commit(node,dispatch,completion());prepareRecovery(memory,receipt);
  clearInvocations(node,authority,produced);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(node).requireUnchanged();
  assertEquals(Optional.of(receipt),service.loadCommitted(admission,run,step));verifyNoInteractions(node,authority,produced);
 }
 @Test void recoveryCannotBorrowSiblingRunOrChangedOriginalJournal()throws Exception {
  var memory=recordCommit();var receipt=service.commit(node,dispatch,completion());prepareRecovery(memory,receipt);
  assertThrows(TenantAccessException.class,()->service.loadCommitted(admission,id(99),step));
  memory.get("audit").put("classification","PUBLIC");assertThrows(TenantAccessException.class,()->service.loadCommitted(admission,run,step));
 }
 @Test void terminalRecoveryStillRejectsCurrentReadRevocation()throws Exception {
  var memory=recordCommit();var receipt=service.commit(node,dispatch,completion());var proof=prepareRecovery(memory,receipt);
  doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(proof).requireActive();assertThrows(TenantAccessException.class,()->service.loadCommitted(admission,run,step));
 }
 private Map<String,Map<String,Object>> recordCommit(){
  var memory=new HashMap<String,Map<String,Object>>();
  when(db.update(anyString(),any(Object[].class))).thenAnswer(c->{String sql=c.getArgument(0);Object[] args=c.getArguments().length==2&&c.getArgument(1)instanceof Object[]v?v:Arrays.copyOfRange(c.getArguments(),1,c.getArguments().length);
   if(sql.startsWith("INSERT INTO run_runtime_receipt")){var r=map("tenant_id",args[0],"project_id",args[1],"run_id",args[2],"receipt_id",args[3],"command_id",args[4],"command_kind","COMMIT","request_sha256",args[5],"canonical_request",args[6],"canonical_receipt",args[7],"receipt_sha256",args[8],"schema_version","1.0.0","schema_manifest_sha256",args[9],"created_by",args[10],"created_at",args[11],"classification",args[12]);memory.put("receipt",r);}
   else if(sql.startsWith("INSERT INTO audit_event"))memory.put("audit",map("tenant_id",args[0],"project_id",args[1],"audit_id",args[2],"subject_id",args[3],"action",args[4],"resource_ref",args[5],"result","SUCCESS","reason_code",null,"request_id",args[6],"trace_id",args[7],"schema_version","1.0.0","schema_manifest_sha256",args[8],"created_by",args[9],"created_at",args[10],"classification",args[11]));
   else if(sql.startsWith("INSERT INTO outbox_event"))memory.put("event",map("tenant_id",args[0],"event_id",args[1],"aggregate_id",args[2],"aggregate_revision",1,"event_type",args[3],"payload",args[4],"occurred_at",args[5]));return 1;});return memory;
 }
 private FormalRunRecoveryAccess.Proof prepareRecovery(Map<String,Map<String,Object>> memory,WorkflowProtocol.Receipt receipt)throws Exception {
  var proof=mock(FormalRunRecoveryAccess.Proof.class);var identity=new FormalRunRecoveryAccess.RecoveryIdentity("TASK_RUN_RECOVERY",tenant,project,run,id(30),id(31),actor,id(32),new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View("local",actor,null,null),"spiffe://medical/worker",hash,id(33),hash,id(34),hash);
  when(admission.recoveryWorker()).thenReturn(true);when(admission.controlWorker()).thenReturn(false);when(admission.recoveryProof()).thenReturn(proof);when(proof.identity()).thenReturn(identity);
  when(journal.readHistoricalStep(admission,run,step,1)).thenReturn(dispatch);when(journal.requireHistoricalStep(admission,dispatch)).thenReturn(dispatch);
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();JsonNode tree=mapper.valueToTree(receipt);
  when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->{String sql=c.getArgument(0);if(sql.contains("FROM run_runtime_receipt"))return List.of(memory.get("receipt"));if(sql.contains("FROM run_step"))return List.of(map("status",receipt.status().name(),"output_ref",json.canonical(tree.required("outputs")),"lease_generation",1));if(sql.contains("FROM audit_event"))return List.of(memory.get("audit"));if(sql.contains("FROM outbox_event"))return List.of(memory.get("event"));throw new AssertionError(sql);});
  when(db.query(startsWith("SELECT classification FROM task_run"),any(RowMapper.class),any(Object[].class))).thenReturn(List.of("RESTRICTED"));return proof;
 }
 private static Map<String,Object> map(Object...pairs){var out=new HashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
 private WorkflowProtocol.Completion completion(){return new WorkflowProtocol.Completion(run,step,hash,1,WorkflowProtocol.State.valueOf(state),result,hash);}
 private JsonNode request(){var r=json.object().put("schema_version","1.0.0").put("kind","TASK_RESEARCH_COMMIT").put("run_id",run).put("step_id",step).put("session_id",id(14)).put("lease_generation",1).put("input_sha256",hash).put("outcome",state);if(waiting==null)r.putNull("waiting_item_id");else r.put("waiting_item_id",waiting);for(String k:List.of("result","evidence_bundle","session_export"))r.putObject(k).put("artifact_id",result).put("sha256",hash).put("object_version","version-1").put("receipt_sha256",hash);for(String k:List.of("source_read_receipts","invocations","retrievals","completion_checks"))r.putArray(k);return r;}
 static String id(int i){return String.format("00000000-0000-4000-8000-%012d",i);}
}
