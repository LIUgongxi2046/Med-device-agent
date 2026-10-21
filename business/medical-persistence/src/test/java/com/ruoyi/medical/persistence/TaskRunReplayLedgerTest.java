package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real ledger/control flow with synthetic current-proof and SQL fixtures only.
 * Does not prove publication acceptance, transaction rollback, S3 or runtime availability. */
class TaskRunReplayLedgerTest {
    JdbcTemplate db;TaskRunBootstrapSchema schema;PermissionSchema json;TaskRunReplayLedger ledger;
    TaskRunBootstrapAuthority.Verified proof;RuntimeReplaySchema.VerifiedInputs replay;TenantScope scope;
    TaskRunReplayLedger.Row stored;LocalDateTime attempted;String audit,event;ObjectNode command;
    LocalDateTime now=LocalDateTime.of(2026,10,19,12,0);List<String> writes;
    static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    @BeforeEach void setup()throws Exception{
        db=mock(JdbcTemplate.class);json=new PermissionSchema();schema=new TaskRunBootstrapSchema();
        var c=SchemaCatalogue.fromClasspath();var node=schema.read(new String(RuntimeReplaySchemaTest.fixture(),StandardCharsets.UTF_8));
        byte[] bytes=json.canonical(node).getBytes(StandardCharsets.UTF_8);
        replay=new RuntimeReplaySchema(json).read(bytes,DigestUtils.sha256Hex(bytes),c.schemaDigest("replay-manifest"));
        proof=mock(TaskRunBootstrapAuthority.Verified.class);when(proof.replay()).thenReturn(replay);
        when(proof.tenantId()).thenReturn(node.required("tenant_id").asText());when(proof.projectId()).thenReturn(node.required("project_id").asText());
        when(proof.runId()).thenReturn(node.required("run_id").asText());when(proof.taskId()).thenReturn(node.required("task_id").asText());
        when(proof.actorId()).thenReturn(node.required("initiator_id").asText());when(proof.principalId()).thenReturn(node.required("principal_id").asText());
        when(proof.classification()).thenReturn("INTERNAL");when(proof.outboxEventId()).thenReturn(id(81));when(proof.authorizationsSha256()).thenReturn("a".repeat(64));
        var inner=json.object().put("schema_version","1.0.0").put("kind","START_RUN").put("command_id",id(82))
            .put("tenant_id",proof.tenantId()).put("project_id",proof.projectId()).put("run_id",proof.runId())
            .put("task_id",proof.taskId()).put("principal_id",proof.principalId()).put("workflow_id",proof.tenantId()+"/"+proof.runId())
            .put("replay_sha256",replay.rawSha256()).put("replay_schema_sha256",replay.schemaSha256()).put("replay_schema_ref",RuntimeReplaySchema.REF);
        command=json.object();command.set("command",inner);command.set("decision",json.object().put("actor_id",proof.actorId()));inner.put("command_sha256",schema.hash(command));
        when(proof.command()).thenAnswer(x->command.deepCopy());ledger=new TaskRunReplayLedger(db,schema,json,bytes.length+1000);writes=new ArrayList<>();
        when(db.query(anyString(),any(RowMapper.class))).thenAnswer(call->{if(call.getArgument(0,String.class).equals("SELECT UTC_TIMESTAMP(6)"))return List.of(now);throw new AssertionError(call.getArgument(0,String.class));});
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);
            if(sql.contains("SELECT * FROM task_run_replay_intent"))return stored==null?List.of():List.of(stored);
            if(sql.contains("SELECT resource_ref FROM audit_event"))return audit==null?List.of():List.of(audit);
            if(sql.contains("SELECT payload FROM outbox_event"))return event==null?List.of():List.of(event);
            if(sql.contains("SELECT a.created_at FROM task_run_replay_attempt"))return attempted==null?List.of():List.of(attempted);
            throw new AssertionError(sql);
        });
        when(db.update(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);Object[] a=(Object[])call.getRawArguments()[1];writes.add(sql);
            if(sql.contains("INSERT INTO task_run_replay_intent"))stored=new TaskRunReplayLedger.Row((String)a[0],(String)a[1],(String)a[2],(String)a[3],(String)a[4],(String)a[5],(String)a[6],(String)a[7],(String)a[8],(String)a[9],(String)a[10],(String)a[11],(String)a[12],(String)a[13],a[14].toString(),(String)a[15],(String)a[16],(String)a[17],"1.0.0",(String)a[18],(String)a[19],(LocalDateTime)a[20],(String)a[21]);
            else if(sql.contains("INSERT INTO audit_event"))audit=(String)a[4];
            else if(sql.contains("INSERT INTO outbox_event"))event=(String)a[3];
            else if(sql.contains("INSERT INTO task_run_replay_attempt"))attempted=(LocalDateTime)a[6];
            else throw new AssertionError(sql);return 1;
        });
        TransactionSynchronizationManager.setActualTransactionActive(true);
        scope=TenantScope.open(new TenantScope.Identity(proof.tenantId(),proof.actorId(),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));
    }
    @AfterEach void cleanup(){if(scope!=null)scope.close();TransactionSynchronizationManager.clear();}
    @Test void preparePersistsExactRunOnlyBytesAndRecoversSameOriginalIntent()throws Exception{
        var first=ledger.prepare(proof);assertEquals(3,writes.size());var again=ledger.prepare(proof);
        assertEquals(first.sha256(),again.sha256());assertEquals(first.writeIntent(),again.writeIntent());assertEquals(3,writes.size());
        assertArrayEquals(schema.canonical(replay.content()).getBytes(StandardCharsets.UTF_8),first.bytes());
        assertFalse(writes.stream().anyMatch(sql->sql.contains("run_step")||sql.contains("UPDATE task_run")));
        ((ObjectNode)first.content()).put("planned_artifact_id",id(99));assertNotEquals(id(99),first.plannedArtifactId());
    }
    @Test void oneInitialTransferThenOnlyRecoveryInMemoryAndFromDatabase()throws Exception{
        var i=ledger.prepare(proof);var a=ledger.attempt(proof,i);assertFalse(a.priorAttempt());assertEquals(4,writes.size());
        assertEquals(RuntimeObjectAttempt.TransferMode.INITIAL_WRITE,a.claimTransferMode());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,a.claimTransferMode());
        var recovered=ledger.attempt(proof,i);assertTrue(recovered.priorAttempt());assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,recovered.claimTransferMode());assertEquals(4,writes.size());
        assertEquals(RuntimeObjectAttempt.TransferMode.RECOVER_ONLY,ledger.requireAttempt(proof,i).claimTransferMode());
    }
    @Test void readOnlyRecoveryNeverCreatesMissingAttempt()throws Exception{
        var i=ledger.prepare(proof);assertThrows(TenantAccessException.class,()->ledger.requireAttempt(proof,i));assertEquals(3,writes.size());
    }
    @Test void missingOrModifiedIndependentWitnessRejectsBeforeAttempt()throws Exception{
        var i=ledger.prepare(proof);event=null;assertThrows(TenantAccessException.class,()->ledger.attempt(proof,i));assertEquals(3,writes.size());
        event=audit;audit="{}";assertThrows(TenantAccessException.class,()->ledger.attempt(proof,i));assertEquals(3,writes.size());
    }
    @Test void alteredStartCommandOrRevokedPrivateAuthorityCannotWrite()throws Exception{
        ((ObjectNode)command.required("command")).put("command_sha256","f".repeat(64));assertThrows(TenantAccessException.class,()->ledger.prepare(proof));assertTrue(writes.isEmpty());
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(proof).requireUnchanged();assertThrows(TenantAccessException.class,()->ledger.prepare(proof));assertTrue(writes.isEmpty());
    }
    @Test void currentAuthorizationChangeDoesNotReplaceOriginalPreparedBytes()throws Exception{
        var i=ledger.prepare(proof);when(proof.authorizationsSha256()).thenReturn("f".repeat(64));assertThrows(TenantAccessException.class,()->ledger.attempt(proof,i));assertEquals(3,writes.size());
    }
    @Test void explicitCapacityAndRealTransactionAreRequired()throws Exception{
        assertThrows(IllegalArgumentException.class,()->new TaskRunReplayLedger(db,schema,json,0));
        var small=new TaskRunReplayLedger(db,schema,json,1);assertThrows(TenantAccessException.class,()->small.prepare(proof));assertTrue(writes.isEmpty());
        TransactionSynchronizationManager.setActualTransactionActive(false);assertThrows(TenantAccessException.class,()->ledger.prepare(proof));assertTrue(writes.isEmpty());
    }
    @Test void attemptHasPrivateConstructionAndNoRegistrationClaim()throws Exception{
        for(var constructor:TaskRunReplayLedger.Attempt.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        var i=ledger.prepare(proof);var a=ledger.attempt(proof,i);assertEquals(i.writeIntent().objectKey(),a.expectedObjectKey());
        assertEquals(proof.tenantId(),a.tenantId());assertEquals(proof.actorId(),a.actorId());
        assertFalse(writes.stream().anyMatch(sql->sql.contains("INSERT INTO artifact")||sql.contains("workflow_acceptance")));
    }
}
