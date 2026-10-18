package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationCoverageTest.*;
/** Actual persistence service with explicitly mocked SQL and private authorities. Not a MySQL or end-to-end admission test. */
class FormalEvaluationExecutionRegistrationsTest {
 final PermissionSchema json=new PermissionSchema();final JdbcTemplate db=mock(JdbcTemplate.class);
 final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);
 final FormalEvaluationExecutionInputs.Prepared inputs=mock(FormalEvaluationExecutionInputs.Prepared.class);
 final FormalEvaluationExecutionAdmission.Prepared admitted=mock(FormalEvaluationExecutionAdmission.Prepared.class);
 final FormalEvaluationExecutionScopes scopes=mock(FormalEvaluationExecutionScopes.class);
 final FormalEvaluationExecutionScopes.Prepared snapshot=mock(FormalEvaluationExecutionScopes.Prepared.class);
 final FormalEvaluationModelPreparation modelPreparation=mock(FormalEvaluationModelPreparation.class);final FormalEvaluationModelPreparation.Prepared modelProof=mock(FormalEvaluationModelPreparation.Prepared.class);
 final FormalEvaluationExecutionRegistrations service=new FormalEvaluationExecutionRegistrations(null,null,scopes,modelPreparation,json,db);
 final Map<String,Object> row=new LinkedHashMap<>();final List<String> mutations=new ArrayList<>();String audit,event;TenantScope tenant;
 final String key="fixture-idempotency-key";
 FormalEvaluationExecutionRegistrationsTest()throws Exception{
  when(a.actorId()).thenReturn(id(90));when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(91),"1","1","1"));
  when(modelPreparation.require(eq(a),eq(inputs),eq(snapshot),any())).thenReturn(modelProof);when(modelProof.canonical()).thenReturn("{}");when(inputs.requireCurrent(a)).thenReturn(admitted);when(scopes.save(a,inputs)).thenReturn(snapshot);when(snapshot.snapshot()).thenReturn(b(92));when(snapshot.profileRevision()).thenReturn(id(93));
  var fixture=new FormalEvaluationMatrixTest();var request=fixture.start();var definition=fixture.definition(List.of(fixture.sample(20,"REAL")),1);var nodes=FormalEvaluationExecutionAdmission.matrix(definition,request,H,List.of(fixture.binding("N01")),json);
  when(admitted.request()).thenReturn(request);when(admitted.projectId()).thenReturn(id(94));when(admitted.classification()).thenReturn("INTERNAL");when(admitted.nodes()).thenReturn(nodes);
  var original=json.object();original.set("request",json.tree(request));original.set("definition",json.tree(definition));original.set("nodes",json.tree(nodes));original.set("authorizations",json.object());when(admitted.canonical()).thenReturn(json.canonical(original));
  when(db.query(anyString(),any(RowMapper.class))).thenReturn(List.of(LocalDateTime.of(2026,10,18,20,0)));
  doAnswer(i->{String sql=i.getArgument(0);if(sql.startsWith("SELECT user_id"))return List.of(Map.of("user_id",id(90)));if(sql.startsWith("SELECT execution_id"))return List.of();if(sql.startsWith("SELECT * FROM formal_evaluation_execution"))return row.isEmpty()?List.of():List.of(row);if(sql.startsWith("SELECT resource_ref"))return audit==null?List.of():List.of(Map.of("resource_ref",audit));if(sql.startsWith("SELECT payload"))return event==null?List.of():List.of(Map.of("payload",event));throw new AssertionError(sql);}).when(db).queryForList(anyString(),any(Object[].class));
  doAnswer(i->{String sql=i.getArgument(0);mutations.add(sql);if(sql.startsWith("INSERT INTO audit_event")){audit=i.getArgument(5);return 1;}if(sql.startsWith("INSERT INTO outbox_event")){event=i.getArgument(4);return 1;}if(sql.startsWith("INSERT INTO formal_evaluation_execution")){String[] columns=sql.substring(sql.indexOf('(')+1,sql.indexOf(')')).split(",");for(int n=0;n<columns.length;n++)row.put(columns[n],i.getArgument(n+1));return 1;}throw new AssertionError(sql);}).when(db).update(anyString(),any(Object[].class));
 }
 @BeforeEach void open(){tenant=TenantScope.open(new TenantScope.Identity(id(91),id(90),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));TransactionSynchronizationManager.setActualTransactionActive(true);}
 @AfterEach void close(){TransactionSynchronizationManager.clear();tenant.close();}
 @Test void registersOriginalAdmissionAndAuditOutboxOnly()throws Exception{var r=service.register(a,key,inputs);assertEquals(1,r.expectedCells());assertEquals(1,r.expectedNodes());assertEquals(3,mutations.size());assertTrue(audit.contains("EVALUATION_EXECUTION_PREPARED"));assertTrue(mutations.stream().filter(q->q.contains("audit_event")||q.contains("outbox_event")).allMatch(q->q.contains("evaluation.execution_prepared")));assertEquals(r.executionId(),row.get("execution_id"));assertEquals("INTERNAL",row.get("classification"));assertTrue(row.get("canonical_admission").toString().contains("source_snapshot"));assertTrue(mutations.stream().noneMatch(s->s.contains("invocation")||s.contains("run_step")));verify(snapshot,times(2)).requireCurrent(a);}
 @Test void exactRetryReadsOriginalWithoutNewWritesOrSnapshot()throws Exception{var first=service.register(a,key,inputs);var retry=service.register(a,key,inputs);assertEquals(first,retry);assertEquals(3,mutations.size());verify(scopes,times(1)).save(a,inputs);}
 @Test void recoveryRejectsMissingOriginalEvent()throws Exception{service.register(a,key,inputs);event=null;assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));assertEquals(3,mutations.size());}
 @Test void recoveryRejectsChangedAdmissionBytes()throws Exception{service.register(a,key,inputs);row.put("canonical_admission","{}");assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));}
 @Test void sameKeyDifferentRequestConflicts()throws Exception{service.register(a,key,inputs);var old=admitted.request();when(admitted.request()).thenReturn(new FormalEvaluationExecutionProtocol.Start("1.0.0",old.evaluationId(),H,"2",old.candidate(),old.executionPolicy(),old.enterpriseBudget(),old.parentBudget()));assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));assertEquals(3,mutations.size());}
 @Test void revokedAdmissionPreventsAnyMutation()throws Exception{when(inputs.requireCurrent(a)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));assertTrue(mutations.isEmpty());}
 @Test void transactionRequired(){TransactionSynchronizationManager.setActualTransactionActive(false);assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));assertTrue(mutations.isEmpty());}
 @Test void malformedKeyRejected(){assertThrows(TenantAccessException.class,()->service.register(a,"short",inputs));assertTrue(mutations.isEmpty());}
 @Test void missingIndependentModelSendProofPreventsExecutionRegistration()throws Exception{when(modelPreparation.require(eq(a),eq(inputs),eq(snapshot),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->service.register(a,key,inputs));assertTrue(mutations.isEmpty());}

}
