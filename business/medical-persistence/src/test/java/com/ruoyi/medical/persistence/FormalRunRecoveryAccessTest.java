package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual private recovery proofs and real read-authority scope gate; JDBC, replay schema and
 * certificate verifier are explicit doubles. No live database/TLS/Temporal acceptance claim. */
class FormalRunRecoveryAccessTest {
    FormalWorkerAccessTest f;
    JdbcTemplate db;
    TenantTransaction transactions;
    ServiceCertificateIdentity services;
    ServiceCertificateIdentity.Verified certificate;
    PermissionAuthority grants;
    ResourceReadAuthority resources;
    FormalRunRecoveryAccess access;
    TaskRunLaunchRecoveryAuthority authority;
    ObjectNode replay, command;
    PermissionProtocol.GrantRow grant;
    String commandId, eventId;
    final TenantWatermarks epochs = new TenantWatermarks(BigInteger.ONE, BigInteger.ONE);

    @BeforeEach void setup() throws Exception {
        f = new FormalWorkerAccessTest(); f.setUp(); f.cleanup(); f.scope = null;
        db = mock(JdbcTemplate.class); transactions = mock(TenantTransaction.class);
        services = mock(ServiceCertificateIdentity.class);
        certificate = certificate("spiffe://medical/runtime", "a".repeat(64));
        when(services.require(f.http)).thenReturn(certificate);
        grants = mock(PermissionAuthority.class); grant = mock(PermissionProtocol.GrantRow.class);
        resources = new ResourceReadAuthority(f.permissions, grants, new PermissionSchema());
        when(f.permissions.resourceOwner("TASK", f.task)).thenReturn(List.of(new PermissionProtocol.OwnerRow(f.task, f.project)));
        when(f.permissions.databaseTime()).thenReturn(LocalDateTime.of(2026, 10, 19, 0, 0));
        when(grants.findResource(eq(f.resource), eq(f.actor), eq("local"), eq("run.read"), eq("RUN_READING"), anyList(), any()))
            .thenReturn(Optional.of(grant));
        commandId = f.id(61); eventId = f.id(62);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // No task.run permission in the original principal, either.
        var principal = (ObjectNode) mapper.readTree(f.persisted.capabilities());
        principal.putArray("permissions").addObject().put("action", "task.read").put("purpose", "TASK_READING")
            .set("scope", f.schema.tree(f.resource));
        when(f.persisted.capabilities()).thenReturn(f.schema.canonical(principal));
        replay = f.schema.object().put("tenant_id", f.tenant).put("project_id", f.project).put("task_id", f.task)
            .put("run_id", f.run).put("initiator_id", f.actor).put("principal_id", f.principal);
        replay.set("principal_manifest", principal); replay.set("scope_manifest", mapper.readTree(f.persisted.scope()));
        replay.putObject("execution_policy").putObject("worker").put("service_uri", "spiffe://medical/runtime").put("audience", "medical-runtime");
        command = f.schema.object(); command.putObject("decision").put("actor_id", f.actor);
        command.putObject("command").put("schema_version", "1.0.0").put("kind", "START_RUN")
            .put("tenant_id", f.tenant).put("project_id", f.project).put("task_id", f.task).put("run_id", f.run)
            .put("principal_id", f.principal).put("workflow_id", f.tenant + "/" + f.run).put("command_id", commandId)
            .put("replay_schema_ref", RuntimeReplaySchema.REF).put("replay_schema_sha256", "b".repeat(64));
        rehash();
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0); var rs = mock(java.sql.ResultSet.class);
            if (sql.contains("FROM task_run")) {
                // Terminal and revoked historical rows are valid; no live-state filter may hide them.
                assertFalse(sql.contains("r.status")); assertFalse(sql.contains("p.status"));
                when(rs.getString("created_by")).thenReturn(f.actor); when(rs.getString("project_id")).thenReturn(f.project);
                when(rs.getString("task_id")).thenReturn(f.task); when(rs.getString("workflow_id")).thenReturn(f.tenant + "/" + f.run);
                when(rs.getString("replay_manifest")).thenReturn(f.schema.canonical(replay)); when(rs.getString("principal_id")).thenReturn(f.principal);
                String capabilities = f.persisted.capabilities(), scopeJson = f.persisted.scope();
                when(rs.getString("capability_manifest")).thenReturn(capabilities); when(rs.getString("scope_manifest")).thenReturn(scopeJson);
                when(rs.getString("payload")).thenReturn(f.schema.canonical(command)); when(rs.getString("event_id")).thenReturn(eventId);
                when(rs.getString("classification")).thenReturn("INTERNAL");
                for (String name : List.of("run_manifest", "principal_manifest", "scope_manifest_hash")) when(rs.getString(name)).thenReturn(f.schema.manifestSha256());
            } else if (sql.contains("SELECT auth_epoch")) {
                when(rs.getBigDecimal(1)).thenReturn(java.math.BigDecimal.ONE); when(rs.getBigDecimal(2)).thenReturn(java.math.BigDecimal.ONE);
            } else if (sql.contains("platform_local_account") || sql.contains("tenant_member")) {
                when(rs.getString(1)).thenReturn(f.actor);
            } else throw new AssertionError("Unexpected SQL: " + sql);
            return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(rs, 0));
        });
        when(transactions.execute(any())).thenAnswer(call -> {
            var scope = TenantScope.requireCurrent(); Object key = new Object();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization(); TransactionSynchronizationManager.bindResource(key, new Object());
            scope.verifyTransaction(epochs);
            try { return ((Function<TenantWatermarks, ?>) call.getArgument(0)).apply(epochs); }
            finally {
                for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                TransactionSynchronizationManager.clearSynchronization(); scope.finishTransaction();
                TransactionSynchronizationManager.unbindResource(key); TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        });
        var data = mock(DataSource.class); var json = new PermissionSchema();
        access = new FormalRunRecoveryAccess(data, transactions, services, f.permissions, f.schema, json, resources);
        ReflectionTestUtils.setField(access, "db", db);
        var replays = mock(RuntimeReplaySchema.class); var verified = mock(RuntimeReplaySchema.VerifiedInputs.class);
        when(verified.content()).thenAnswer(c -> replay.deepCopy()); when(verified.taskId()).thenReturn(f.task);
        when(verified.rawSha256()).thenAnswer(c -> f.schema.sha256(replay)); when(verified.schemaSha256()).thenReturn("b".repeat(64));
        when(replays.read(any(byte[].class), anyString(), anyString())).thenReturn(verified);
        authority = new TaskRunLaunchRecoveryAuthority(data, json, resources, f.schema, replays, services);
        ReflectionTestUtils.setField(authority, "db", db);
    }
    private ServiceCertificateIdentity.Verified certificate(String uri, String hash) {
        var value = mock(ServiceCertificateIdentity.Verified.class);
        when(value.serviceUri()).thenReturn(uri); when(value.certificateSha256()).thenReturn(hash); when(value.trustRevision()).thenReturn(f.id(90)); return value;
    }
    private void rehash() throws Exception {
        var c = (ObjectNode) command.required("command"); c.put("replay_sha256", f.schema.sha256(replay));
        c.remove("command_sha256"); c.put("command_sha256", f.schema.sha256(command));
    }
    @Test void terminalHistoricalRunMintsReadOnlyProofWithoutStartOrPublicationAuthority() throws Exception {
        var retained = new AtomicReference<TaskRunLaunchRecoveryAuthority.Verified>();
        assertEquals("read", access.execute(f.http, f.tenant, f.run, a -> {
            assertTrue(a.recoveryWorker()); assertThrows(TenantAccessException.class, a::subject);
            a.recoveryProof().require(f.resource, "run.read", "RUN_READING");
            assertThrows(TenantAccessException.class, () -> a.recoveryProof().require(f.resource, "task.run", "RUN_CREATION"));
            try { var proof = authority.require(a, f.http, f.run, commandId); retained.set(proof); proof.requireUnchanged(); }
            catch (Exception e) { throw new AssertionError(e); }
            return "read";
        }));
        assertThrows(TenantAccessException.class, () -> retained.get().runId());
        verify(grants, never()).findResource(any(), anyString(), anyString(), eq("task.run"), anyString(), anyList(), any());
    }
    @Test void revokedReadGrantBlocksBeforeCallback() throws Exception {
        when(grants.findResource(any(), anyString(), anyString(), eq("run.read"), eq("RUN_READING"), anyList(), any())).thenReturn(Optional.empty());
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> fail("read denied")));
    }
    @Test void grantRevocationDuringCallbackCannotReturnResult() {
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> {
            try { when(grants.findResource(any(), anyString(), anyString(), eq("run.read"), eq("RUN_READING"), anyList(), any())).thenReturn(Optional.empty()); }
            catch (java.io.IOException e) { throw new AssertionError(e); } return "blocked";
        }));
    }
    @Test void wrongMtlsServiceAndRotatedCertificateDenied() throws Exception {
        var wrong = certificate("spiffe://other/worker", "a".repeat(64));
        when(services.require(f.http)).thenReturn(wrong);
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> fail("wrong service")));
        when(services.require(f.http)).thenReturn(certificate);
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> {
            try { var rotated = certificate("spiffe://medical/runtime", "c".repeat(64)); when(services.require(f.http)).thenReturn(rotated); }
            catch (Exception e) { throw new AssertionError(e); } return null;
        }));
    }
    @Test void mismatchedActorAndTenantCannotBorrowHistoricalCommand() throws Exception {
        replay.put("initiator_id", f.id(98)); rehash();
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> fail("wrong actor")));
        replay.put("initiator_id", f.actor).put("tenant_id", f.id(99)); rehash();
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> fail("wrong tenant")));
    }
    @Test void replacedTransactionAndCrossThreadCannotUseProof() {
        access.execute(f.http, f.tenant, f.run, a -> {
            Object key = new Object(); TransactionSynchronizationManager.bindResource(key, new Object());
            try { assertThrows(TenantAccessException.class, a::actorId); }
            finally { TransactionSynchronizationManager.unbindResource(key); }
            var failure = new AtomicReference<Throwable>(); var thread = new Thread(() -> { try { a.actorId(); } catch (Throwable e) { failure.set(e); } });
            thread.start(); try { thread.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            assertInstanceOf(TenantAccessException.class, failure.get()); return null;
        });
    }
    @Test void sameTaskCallbackCannotBorrowAnotherRunOrCommand() {
        access.execute(f.http,f.tenant,f.run,a->{
            clearInvocations(db);
            assertThrows(TenantAccessException.class,()->authority.require(a,f.http,f.id(77),commandId));
            assertThrows(TenantAccessException.class,()->authority.require(a,f.http,f.run,f.id(78)));
            verifyNoInteractions(db);
            a.recoveryProof().requireRun(f.run,commandId);
            assertThrows(TenantAccessException.class,()->a.recoveryProof().requireOriginal(f.run,commandId,"0".repeat(64),"1".repeat(64)));
            return null;
        });
    }
    @Test void missingCurrentActorBindingDenied() {
        when(f.permissions.currentBinding(f.actor)).thenReturn(List.of());
        assertThrows(TenantAccessException.class, () -> access.execute(f.http, f.tenant, f.run, a -> fail("missing actor")));
    }
}
