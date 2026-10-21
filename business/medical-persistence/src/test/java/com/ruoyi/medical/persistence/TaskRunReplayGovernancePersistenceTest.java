package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real governance ledger and real run intent/attempt with stateful SQL doubles.
 * Exercises immutable persisted-row verification, not a live MySQL/S3 transaction. */
class TaskRunReplayGovernancePersistenceTest {
 TaskRunReplayLedgerTest fixture;TaskRunReplayLedger.Intent intent;TaskRunReplayGovernanceLedger ledger;JdbcTemplate db;
 TaskRunReplayGovernanceLedger.Stored stored;List<TaskRunReplayGovernanceLedger.Member> members;
 List<TaskRunReplayGovernanceLedger.Edge> edges;List<String> writes;int hiddenMembers,hiddenEdges;
 @BeforeEach void setup()throws Exception{
  fixture=new TaskRunReplayLedgerTest();fixture.setup();intent=fixture.ledger.prepare(fixture.proof);fixture.ledger.attempt(fixture.proof,intent);
  db=mock(JdbcTemplate.class);ledger=new TaskRunReplayGovernanceLedger(db,fixture.json);members=new ArrayList<>();edges=new ArrayList<>();writes=new ArrayList<>();
  when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);Object[] args=(Object[])call.getRawArguments()[2];
   assertEquals(fixture.proof.tenantId(),args[0]);assertEquals(fixture.proof.projectId(),args[1]);assertEquals(fixture.proof.runId(),args[2]);assertEquals(intent.writeIntent().writeId(),args[3]);
   if(sql.startsWith("SELECT COUNT(*) FROM task_run_replay_governance_")){
    assertEquals(4,args.length);assertFalse(sql.contains("created_by"));assertFalse(sql.contains("schema_version"));assertFalse(sql.contains("classification"));assertFalse(sql.contains("created_at"));
    return List.of(sql.contains("governance_member")?members.size()+hiddenMembers:edges.size()+hiddenEdges);
   }
   if(sql.contains("SELECT classification FROM task_run_replay_intent"))return List.of("INTERNAL");
   if(sql.contains("FROM task_run_replay_governance_manifest")){assertTrue(sql.contains("replay_sha256=? AND classification=?"));return stored==null?List.of():List.of(stored);}
   if(sql.contains("FROM task_run_replay_governance_member"))return List.copyOf(members);
   if(sql.contains("FROM task_run_replay_governance_edge"))return List.copyOf(edges);
   throw new AssertionError(sql);
  });
  when(db.update(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);Object[] a=(Object[])call.getRawArguments()[1];writes.add(sql);
   assertEquals(fixture.proof.tenantId(),a[0]);assertEquals(fixture.proof.projectId(),a[1]);assertEquals(fixture.proof.runId(),a[2]);assertEquals(intent.writeIntent().writeId(),a[3]);
   if(sql.contains("INSERT INTO task_run_replay_governance_manifest")){
    var ctor=TaskRunReplayGovernanceLedger.Stored.class.getDeclaredConstructors()[0];ctor.setAccessible(true);stored=(TaskRunReplayGovernanceLedger.Stored)ctor.newInstance(a[3],a[6],a[7],a[8]);
   }else if(sql.contains("INSERT INTO task_run_replay_governance_member"))members.add(new TaskRunReplayGovernanceLedger.Member((Integer)a[4],(String)a[5],(String)a[6],(String)a[7],(String)a[8]));
   else if(sql.contains("INSERT INTO task_run_replay_governance_edge"))edges.add(new TaskRunReplayGovernanceLedger.Edge((Integer)a[4],(String)a[5],(String)a[6],(String)a[7]));
   else throw new AssertionError(sql);return 1;
  });
 }
 @AfterEach void cleanup(){fixture.cleanup();}
 TaskRunReplayGovernanceLedger.Stored prepare()throws Exception{return ledger.prepare(fixture.proof,intent,fixture.replay,"INTERNAL");}
 @Test void persistsCompleteFixedGraphAndRetryNeverAppends()throws Exception{
  var first=prepare();int count=writes.size();assertEquals(1+fixture.replay.content().required("dependency_versions").size()+fixture.replay.content().required("dependency_edges").size(),count);
  assertEquals(first,prepare());assertEquals(first,ledger.requireStored(fixture.proof,intent,fixture.replay));assertEquals(count,writes.size());
  assertTrue(writes.stream().allMatch(sql->sql.contains("INSERT INTO task_run_replay_governance_")));
 }
 @Test void missingStoredManifestIsNotCreatedByReader(){assertThrows(TenantAccessException.class,()->ledger.requireStored(fixture.proof,intent,fixture.replay));assertTrue(writes.isEmpty());}
 @Test void missingMemberCannotBeRepairedByRetry()throws Exception{prepare();assertFalse(members.isEmpty());members.remove(0);int count=writes.size();assertThrows(TenantAccessException.class,this::prepare);assertEquals(count,writes.size());}
 @Test void changedHashCannotBeRepairedByRetry()throws Exception{prepare();var original=members.get(0);members.set(0,new TaskRunReplayGovernanceLedger.Member(original.ordinal(),original.id(),original.project(),original.kind(),"f".repeat(64)));int count=writes.size();assertThrows(TenantAccessException.class,this::prepare);assertEquals(count,writes.size());}
 @Test void extraMemberHiddenByForeignActorSchemaOrFutureTimestampIsRejected()throws Exception{
  prepare();hiddenMembers=1;int count=writes.size();assertThrows(TenantAccessException.class,this::prepare);assertThrows(TenantAccessException.class,()->ledger.requireStored(fixture.proof,intent,fixture.replay));assertEquals(count,writes.size());
 }
 @Test void extraEdgeHiddenByForeignClassificationOrFutureTimestampIsRejected()throws Exception{
  prepare();hiddenEdges=1;int count=writes.size();assertThrows(TenantAccessException.class,this::prepare);assertThrows(TenantAccessException.class,()->ledger.requireStored(fixture.proof,intent,fixture.replay));assertEquals(count,writes.size());
 }
 @Test void revokedCurrentAuthorityNeverPersistsGraph()throws Exception{doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(fixture.proof).requireUnchanged();assertThrows(TenantAccessException.class,this::prepare);assertTrue(writes.isEmpty());}
 @Test void wrongClassificationNeverPersistsGraph(){assertThrows(TenantAccessException.class,()->ledger.prepare(fixture.proof,intent,fixture.replay,"PUBLIC"));assertTrue(writes.isEmpty());}
 @Test void runOwnedDdlHasAttemptForeignKeyAndNoSyntheticStep()throws Exception{
  var root=java.nio.file.Path.of(System.getProperty("medical.projectRoot","../.."));
  String ddl=java.nio.file.Files.readString(root.resolve("business/medical-data/src/main/resources/db/migration/094-task-run-replay-governance.sql"));
  assertEquals(3,ddl.split("CREATE TABLE ",-1).length-1);assertTrue(ddl.contains("REFERENCES task_run_replay_attempt(tenant_id,project_id,run_id,write_id)"));
  assertFalse(ddl.contains("runtime_object_intent"));assertFalse(ddl.contains("step_id"));assertFalse(ddl.contains("UPDATE task_run"));
 }
}
