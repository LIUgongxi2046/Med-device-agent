package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Service-only historical launch recovery. This is not a session or execution admission.
 * Current membership/binding and mTLS are mandatory even when the original run is terminal. */
public final class FormalRunRecoveryAccess {
    private final JdbcTemplate db;
    private final TenantTransaction transactions;
    private final ServiceCertificateIdentity services;
    private final PermissionMapper permissions;
    private final RunTokenAuthoritySchema schema;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    private final JsonMapper reader = JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public FormalRunRecoveryAccess(DataSource data, TenantTransaction transactions,
            ServiceCertificateIdentity services, PermissionMapper permissions,
            RunTokenAuthoritySchema schema, PermissionSchema json, ResourceReadAuthority resources) {
        db = new JdbcTemplate(data); db.setQueryTimeout(5);
        this.transactions = Objects.requireNonNull(transactions); this.services = Objects.requireNonNull(services);
        this.permissions = Objects.requireNonNull(permissions); this.schema = Objects.requireNonNull(schema);
        this.json = Objects.requireNonNull(json); this.resources = Objects.requireNonNull(resources);
    }

    public <T> T execute(HttpServletRequest http, String tenant, String run,
            Function<EnterpriseAccess.Admission, T> operation) {
        Objects.requireNonNull(http); Objects.requireNonNull(operation);
        return ThrowingSupplier.of(() -> {
            WireValues.uuid(tenant); WireValues.uuid(run);
            if (TransactionSynchronizationManager.isActualTransactionActive()) deny();
            var service = services.require(http);
            var initial = original(tenant, run, false);
            validate(initial, tenant, run, service);
            var epochs = one(db.query("SELECT auth_epoch,governance_epoch FROM tenant WHERE tenant_id=? LIMIT 2",
                (rs, n) -> new TenantWatermarks(rs.getBigDecimal(1).toBigIntegerExact(),
                    rs.getBigDecimal(2).toBigIntegerExact()), tenant));
            try (var scope = TenantScope.open(new TenantScope.Identity(tenant, initial.actor(), BigInteger.ZERO,
                    epochs.authEpoch(), epochs.governanceEpoch()))) {
                return transactions.execute(current -> ThrowingSupplier.of(() -> {
                    var identity = identity(http, tenant, run, initial, service);
                    var proof = new Proof(identity);
                    try {
                        var context = new BusinessSessionProtocol.Context(tenant, "0",
                            current.authEpoch().toString(), current.governanceEpoch().toString());
                        var admission = new EnterpriseAccess.Admission(proof, context, current);
                        var taskScope = new PermissionProtocol.Scope(tenant, initial.project(), "TASK", initial.task());
                        var grant = resources.require(admission, taskScope, "run.read", "RUN_READING");
                        T result = operation.apply(admission);
                        proof.requireActive();
                        if (!identity.equals(identity(http, tenant, run, initial, service))
                                || !grant.equals(resources.require(admission, taskScope, "run.read", "RUN_READING"))) deny();
                        return result;
                    } finally { proof.active = false; }
                }).get());
            }
        }).get();
    }

    private RecoveryIdentity identity(HttpServletRequest http, String tenant, String run, Original expected,
            ServiceCertificateIdentity.Verified expectedCertificate) throws Exception {
        var service = services.require(http);
        if (!expectedCertificate.serviceUri().equals(service.serviceUri())
                || !expectedCertificate.certificateSha256().equals(service.certificateSha256())
                || !expectedCertificate.trustRevision().equals(service.trustRevision())) deny();
        var original = original(tenant, run, true);
        if (!expected.equals(original)) deny();
        validate(original, tenant, run, service);
        String actor = original.actor();
        var binding = one(permissions.currentBinding(actor));
        if (!actor.equals(binding.userId()) || !"ACTIVE".equals(binding.status())) deny();
        List<String> accounts;
        if ("local".equals(binding.mode())) {
            if (!actor.equals(binding.localUserId())) deny();
            accounts = db.query("SELECT user_id FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                (rs, n) -> rs.getString(1), actor);
        } else if ("oidc".equals(binding.mode())) {
            if (binding.localUserId() != null) deny();
            accounts = db.query("""
                SELECT user_id FROM platform_identity WHERE identity_id=? AND user_id=? AND issuer=?
                    AND subject=? AND status='ACTIVE' FOR UPDATE
                """, (rs, n) -> rs.getString(1), binding.identityId(), actor, binding.issuer(), binding.subject());
        } else { deny(); return null; }
        if (!actor.equals(one(accounts))) deny();
        String member = one(db.query("""
            SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=? AND status='ACTIVE'
                AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6)) FOR UPDATE
            """, (rs, n) -> rs.getString(1), tenant, actor));
        var view = new AuthenticatedIdentityBinding.View(binding.mode(), actor, binding.issuer(), binding.subject());
        return new RecoveryIdentity("FORMAL_RUN_LAUNCH_RECOVERY", tenant, original.project(), run,
            original.task(), original.principal(), actor, member, view, service.serviceUri(),
            service.certificateSha256(), service.trustRevision(), schema.sha256(reader.readTree(original.replay())),
            text(reader.readTree(original.command()).required("command"), "command_id"),
            text(reader.readTree(original.command()).required("command"), "command_sha256"));
    }

    private Original original(String tenant, String run, boolean locked) throws Exception {
        var rows = db.query("""
            SELECT r.created_by,r.project_id,r.task_id,r.workflow_id,r.replay_manifest,p.principal_id,
                p.capability_manifest,e.scope_manifest,o.payload,
                r.schema_manifest_sha256 AS run_manifest,p.schema_manifest_sha256 AS principal_manifest,
                e.schema_manifest_sha256 AS scope_manifest_hash
            FROM task_run r JOIN execution_registry e ON e.tenant_id=r.tenant_id AND e.project_id=r.project_id
                AND e.run_id=r.run_id AND e.created_by=r.created_by
            JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id
                AND p.run_id=r.run_id AND p.initiator_id=r.created_by
            JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id
                AND o.event_type='run.start.requested'
            WHERE r.tenant_id=? AND r.run_id=? AND r.execution_kind='TASK' AND e.execution_kind='TASK'
                AND r.schema_version='1.0.0' AND p.schema_version='1.0.0' AND e.schema_version='1.0.0' LIMIT 2
            """ + (locked ? " FOR UPDATE" : ""), (rs, n) -> new Original(rs.getString("created_by"),
                rs.getString("project_id"), rs.getString("task_id"), rs.getString("workflow_id"),
                rs.getString("replay_manifest"), rs.getString("principal_id"), rs.getString("capability_manifest"),
                rs.getString("scope_manifest"), rs.getString("payload"), rs.getString("run_manifest"),
                rs.getString("principal_manifest"), rs.getString("scope_manifest_hash")), tenant, run);
        var row = one(rows);
        return new Original(row.actor(), row.project(), row.task(), row.workflow(), canonical(row.replay()),
            row.principal(), canonical(row.capabilities()), canonical(row.scope()), canonical(row.command()),
            row.runManifest(), row.principalManifest(), row.scopeManifest());
    }

    private void validate(Original original, String tenant, String run,
            ServiceCertificateIdentity.Verified service) throws Exception {
        WireValues.uuid(original.actor()); WireValues.uuid(original.project()); WireValues.uuid(original.task());
        for (String manifest : List.of(original.runManifest(), original.principalManifest(), original.scopeManifest()))
            if (!schema.manifestSha256().equals(manifest)) deny();
        var replay = reader.readTree(original.replay());
        var payload = reader.readTree(original.command()); var command = payload.required("command");
        var scope = schema.scopeManifest(original.scope(), tenant, original.project(), original.actor(), run, "TASK");
        var principal = schema.principalManifest(original.capabilities(), tenant, original.project(), original.actor(), run,
            original.principal(), scope);
        for (var entry : Map.of("tenant_id", tenant, "project_id", original.project(), "task_id", original.task(),
                "run_id", run, "principal_id", original.principal()).entrySet())
            if (!entry.getValue().equals(text(replay, entry.getKey())) || !entry.getValue().equals(text(command, entry.getKey()))) deny();
        if (!original.actor().equals(text(replay, "initiator_id"))
                || !original.actor().equals(text(payload.required("decision"), "actor_id"))
                || !scope.equals(replay.required("scope_manifest")) || !principal.equals(replay.required("principal_manifest"))
                || !"START_RUN".equals(text(command, "kind")) || !"1.0.0".equals(text(command, "schema_version"))
                || !RuntimeReplaySchema.REF.equals(text(command, "replay_schema_ref"))
                || !(tenant + "/" + run).equals(text(command, "workflow_id"))
                || !original.workflow().equals(text(command, "workflow_id"))
                || !schema.sha256(replay).equals(text(command, "replay_sha256"))) deny();
        WireValues.uuid(text(command, "command_id")); WireValues.hash(text(command, "replay_schema_sha256"));
        var without = payload.deepCopy(); ((ObjectNode) without.required("command")).remove("command_sha256");
        if (!schema.sha256(without).equals(text(command, "command_sha256"))) deny();
        var worker = replay.required("execution_policy").required("worker");
        if (!service.serviceUri().equals(text(worker, "service_uri"))) deny();
        schema.service(principal, service.serviceUri(), text(worker, "audience"));
    }

    static final class Proof {
        private final RecoveryIdentity identity;
        private final Thread thread = Thread.currentThread();
        private final TenantScope scope = TenantScope.requireCurrent();
        private final Map<Object, Object> transaction = Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private boolean active = true;
        private Proof(RecoveryIdentity identity) { this.identity = identity; if (transaction.isEmpty()) deny(); }
        void requireActive() {
            if (!active || thread != Thread.currentThread() || scope != TenantScope.requireCurrent()
                    || !TransactionSynchronizationManager.isActualTransactionActive()) deny();
            var actual = TransactionSynchronizationManager.getResourceMap();
            if (actual.size() != transaction.size()
                    || transaction.entrySet().stream().anyMatch(e -> actual.get(e.getKey()) != e.getValue())
                    || !identity.tenantId().equals(scope.tenantId()) || !identity.actorId().equals(scope.subjectId())) deny();
        }
        String actorId() { requireActive(); return identity.actorId(); }
        AuthenticatedIdentityBinding.View bindingView() { requireActive(); return identity.binding(); }
        RecoveryIdentity identity() { requireActive(); return identity; }
        void requireRun(String run, String command) {
            requireActive();
            if (!identity.runId().equals(run) || !identity.commandId().equals(command)) deny();
        }
        void requireOriginal(String run, String command, String replayHash, String commandHash) {
            requireRun(run, command);
            if (!identity.replaySha256().equals(replayHash) || !identity.commandSha256().equals(commandHash)) deny();
        }
        void require(PermissionProtocol.Scope resource, String action, String purpose) {
            requireActive();
            if (!new PermissionProtocol.Scope(identity.tenantId(), identity.projectId(), "TASK", identity.taskId()).equals(resource)
                    || !"run.read".equals(action) || !"RUN_READING".equals(purpose)) deny();
        }
    }
    record RecoveryIdentity(String kind, String tenantId, String projectId, String runId, String taskId,
        String principalId, String actorId, String memberId, AuthenticatedIdentityBinding.View binding,
        String serviceUri, String certificateSha256, String trustRevision, String replaySha256, String commandId, String commandSha256) { }
    private record Original(String actor, String project, String task, String workflow, String replay,
        String principal, String capabilities, String scope, String command, String runManifest,
        String principalManifest, String scopeManifest) { }
    private String canonical(String raw) throws Exception { return json.canonical(reader.readTree(raw)); }
    private static String text(JsonNode node, String field) {
        var value = node.required(field); if (!value.isTextual()) deny(); return value.textValue();
    }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) deny(); return rows.get(0); }
    private static void deny() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
