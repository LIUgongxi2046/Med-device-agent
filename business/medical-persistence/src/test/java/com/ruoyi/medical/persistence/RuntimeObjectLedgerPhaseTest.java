package com.ruoyi.medical.persistence;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** Actual ledger phase checks with mocked SQL. No live transaction/rollback claim. */
class RuntimeObjectLedgerPhaseTest {
 FormalWorkerAccessTest f;RuntimeObjectLedger ledger;RuntimeObjectLedger.Intent intent;org.springframework.jdbc.core.JdbcTemplate db;
 ResourceReadAuthority resources;
 String write="00000000-0000-4000-8000-000000000091";
 @BeforeEach void setup()throws Exception{
  f=new FormalWorkerAccessTest();f.setUp();db=mock(org.springframework.jdbc.core.JdbcTemplate.class);var json=new PermissionSchema();resources=mock(ResourceReadAuthority.class);ledger=new RuntimeObjectLedger(db,json,resources,1024,mock(RuntimeReplayOriginalAuthority.class));
  var constructor=RuntimeObjectLedger.Intent.class.getDeclaredConstructors()[0];constructor.setAccessible(true);intent=(RuntimeObjectLedger.Intent)constructor.newInstance("key",f.id(92),RuntimeObjectLedger.Kind.REPLAY_MANIFEST,new S3ObjectWriter.WriteIntent(write,"runtime/fixed","a".repeat(64),"2","application/json"),"{}","urn:medical:replay-manifest:1.0.0","b".repeat(64),f.tenant,f.actor,f.run);
  when(db.query(contains("SELECT task_id"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(f.actor))).thenReturn(List.of(f.task));
  when(db.query(contains("SELECT write_id,planned_artifact_id"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq("key"),eq(f.actor))).thenReturn(List.of(intent));
  var witness=json.object().put("schema_version","1.0.0").put("tenant_id",f.tenant).put("project_id",f.project).put("run_id",f.run).put("actor_id",f.actor).put("write_key","key").put("artifact_id",f.id(92)).put("kind","REPLAY_MANIFEST").put("schema_ref","urn:medical:replay-manifest:1.0.0").put("payload_schema_sha256","b".repeat(64));witness.set("write_intent",json.tree(intent.writeIntent()));
  when(db.query(contains("SELECT a.resource_ref"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor))).thenReturn(List.of(json.canonical(witness)));
  when(db.query(contains("SELECT e.payload"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor))).thenReturn(List.of(json.canonical(witness)));
 }
 void state(String value){when(db.query(contains("SELECT stage"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor),eq("b".repeat(64)))).thenReturn(List.of(value));}
 void attempts(boolean exists){when(db.query(contains("SELECT a.write_id FROM runtime_object_attempt"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor))).thenReturn(exists?List.of(write):List.of());}
 RuntimeObjectLedger.Attempt attempt()throws Exception{return f.access.execute(f.http,"actual-token-fixture",f.request,a->{try{return ledger.attempt(a,intent);}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}});}
 @AfterEach void cleanup(){f.cleanup();}
 @Test void missingAttemptCannotReopenPersistedAttemptedPhase(){state("ATTEMPTED");attempts(false);assertThrows(TenantAccessException.class,this::attempt);verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void rolledBackPhaseCannotIgnoreIndependentAttempt(){state("PREPARED");attempts(true);assertThrows(TenantAccessException.class,this::attempt);}
 @Test void actualPriorAttemptOnlyYieldsRecoveryProof()throws Exception{state("ATTEMPTED");attempts(true);assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,attempt().claimTransferMode());verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void missingIndependentPreparedEventBlocksRecovery(){state("ATTEMPTED");attempts(true);when(db.query(contains("SELECT e.payload"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor))).thenReturn(List.of());assertThrows(TenantAccessException.class,this::attempt);verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void changedPreparedAuditBlocksRecovery(){state("ATTEMPTED");attempts(true);when(db.query(contains("SELECT a.resource_ref"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor))).thenReturn(List.of("{}"));assertThrows(TenantAccessException.class,this::attempt);}
 RuntimeObjectLedger.Attempt readOnly()throws Exception{return f.access.execute(f.http,"actual-token-fixture",f.request,a->{try{return ledger.requireAttempt(a,intent);}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}});}
 @Test void readOnlyNeverCreatesAnUnattemptedWrite(){state("PREPARED");attempts(false);assertThrows(TenantAccessException.class,this::readOnly);verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void readOnlyUsesCurrentTaskReadWithoutRegistrationGrant()throws Exception{state("ATTEMPTED");attempts(true);assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,readOnly().claimTransferMode());verify(resources).require(any(),eq(new PermissionProtocol.Scope(f.tenant,f.project,"TASK",f.task)),eq("task.read"),eq("TASK_EXECUTION"));verify(resources,never()).require(any(),any(),eq("artifact.register"),any());verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void missingPhaseCannotBeTreatedAsNew(){attempts(false);when(db.query(contains("SELECT stage"),any(RowMapper.class),eq(f.tenant),eq(f.project),eq(f.run),eq(write),eq(f.actor),eq("b".repeat(64)))).thenReturn(List.of());assertThrows(TenantAccessException.class,this::attempt);}
}
