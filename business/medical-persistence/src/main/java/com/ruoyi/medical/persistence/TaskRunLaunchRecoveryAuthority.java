package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Read/reconciliation authority for an already requested workflow launch, never start authority.
 * Terminal runs and revoked execution principals retain their historical identity. Current actor
 * read access and current mTLS registration, not historical execution grants, authorize recovery. */
public final class TaskRunLaunchRecoveryAuthority {
    private final JdbcTemplate db;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    private final RunTokenAuthoritySchema tokens;
    private final RuntimeReplaySchema replays;
    private final ServiceCertificateIdentity services;
    private final JsonMapper reader = JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public TaskRunLaunchRecoveryAuthority(DataSource data, PermissionSchema json,
            ResourceReadAuthority resources, RunTokenAuthoritySchema tokens,
            RuntimeReplaySchema replays, ServiceCertificateIdentity services) {
        this.db = new JdbcTemplate(data); db.setQueryTimeout(5);
        this.json = Objects.requireNonNull(json); this.resources = Objects.requireNonNull(resources);
        this.tokens = Objects.requireNonNull(tokens); this.replays = Objects.requireNonNull(replays);
        this.services = Objects.requireNonNull(services);
    }

    public Verified require(EnterpriseAccess.Admission admission, HttpServletRequest http,
            String runId, String commandId) throws Exception {
        Objects.requireNonNull(admission); Objects.requireNonNull(http);
        WireValues.uuid(runId); WireValues.uuid(commandId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive() || admission.simulationWorker()) deny();
        var scope = TenantScope.requireCurrent();
        if (!scope.tenantId().equals(admission.context().tenant_id())
                || !scope.subjectId().equals(admission.actorId())) deny();
        return new Verified(admission, http, runId, commandId, read(admission, http, runId, commandId));
    }

    private Snapshot read(EnterpriseAccess.Admission admission, HttpServletRequest http,
            String run, String commandId) throws Exception {
        if (admission.recoveryWorker()) admission.recoveryProof().requireRun(run, commandId);
        var certificate = services.require(http);
        String tenant = admission.context().tenant_id(), actor = admission.actorId();
        var rows = db.query("""
            SELECT r.task_id,r.project_id,r.replay_manifest,r.classification,r.workflow_id,
                o.event_id,o.payload,p.principal_id,p.capability_manifest,e.scope_manifest,
                r.schema_manifest_sha256 AS run_manifest,p.schema_manifest_sha256 AS principal_manifest,
                e.schema_manifest_sha256 AS scope_manifest_hash
            FROM task_run r
            JOIN execution_registry e ON e.tenant_id=r.tenant_id AND e.project_id=r.project_id
                AND e.run_id=r.run_id AND e.created_by=r.created_by
            JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id
                AND p.run_id=r.run_id AND p.initiator_id=r.created_by
            JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id
                AND o.event_type='run.start.requested'
            WHERE r.tenant_id=? AND r.run_id=? AND r.created_by=?
                AND r.execution_kind='TASK' AND e.execution_kind='TASK'
                AND r.schema_version='1.0.0' AND e.schema_version='1.0.0' AND p.schema_version='1.0.0'
                AND o.payload->>'$.command.command_id'=? LIMIT 2 FOR UPDATE
            """, (rs, n) -> new Row(rs.getString("task_id"), rs.getString("project_id"),
                rs.getString("replay_manifest"), rs.getString("classification"), rs.getString("workflow_id"),
                rs.getString("event_id"), rs.getString("payload"), rs.getString("principal_id"),
                rs.getString("capability_manifest"), rs.getString("scope_manifest"),
                rs.getString("run_manifest"), rs.getString("principal_manifest"), rs.getString("scope_manifest_hash")),
            tenant, run, actor, commandId);
        if (rows.size() != 1) deny();
        var row = rows.get(0);
        WireValues.uuid(row.project()); WireValues.uuid(row.task()); WireValues.uuid(row.event());
        for (String manifest : List.of(row.runManifest(), row.principalManifest(), row.scopeManifest()))
            if (!tokens.manifestSha256().equals(manifest)) bad();
        var payload = reader.readTree(row.command());
        var command = payload.required("command");
        if (!(command instanceof ObjectNode)) bad();
        var withoutHash = payload.deepCopy();
        ((ObjectNode) withoutHash.required("command")).remove("command_sha256");
        WireValues.hash(text(command, "command_sha256"));
        if (!tokens.sha256(withoutHash).equals(text(command, "command_sha256"))
                || !"START_RUN".equals(text(command, "kind"))
                || !"1.0.0".equals(text(command, "schema_version"))
                || !commandId.equals(text(command, "command_id"))
                || !tenant.equals(text(command, "tenant_id"))
                || !row.project().equals(text(command, "project_id"))
                || !row.task().equals(text(command, "task_id"))
                || !run.equals(text(command, "run_id"))
                || !row.principal().equals(text(command, "principal_id"))
                || !actor.equals(text(payload.required("decision"), "actor_id"))
                || !(tenant + "/" + run).equals(text(command, "workflow_id"))
                || !row.workflow().equals(text(command, "workflow_id"))
                || !RuntimeReplaySchema.REF.equals(text(command, "replay_schema_ref"))) bad();
        var content = reader.readTree(row.replay());
        var replay = replays.read(json.canonical(content).getBytes(StandardCharsets.UTF_8),
            text(command, "replay_sha256"), text(command, "replay_schema_sha256"));
        if (admission.recoveryWorker()) admission.recoveryProof().requireOriginal(run, commandId,
            replay.rawSha256(), text(command, "command_sha256"));
        for (var field : Map.of("tenant_id", tenant, "project_id", row.project(), "task_id", row.task(),
                "run_id", run, "principal_id", row.principal(), "initiator_id", actor).entrySet())
            if (!field.getValue().equals(text(content, field.getKey()))) bad();
        var scope = tokens.scopeManifest(row.scope(), tenant, row.project(), actor, run, "TASK");
        var principal = tokens.principalManifest(row.capabilities(), tenant, row.project(), actor, run, row.principal(), scope);
        if (!scope.equals(content.required("scope_manifest")) || !principal.equals(content.required("principal_manifest"))) bad();
        var worker = content.required("execution_policy").required("worker");
        if (!certificate.serviceUri().equals(text(worker, "service_uri"))) deny();
        tokens.service(principal, certificate.serviceUri(), text(worker, "audience"));
        var grant = resources.require(admission,
            new PermissionProtocol.Scope(tenant, row.project(), "TASK", row.task()), "run.read", "RUN_READING");
        return new Snapshot(replay, payload.deepCopy(), row.event(), row.classification(), grant,
            certificate.serviceUri(), certificate.certificateSha256(), certificate.trustRevision());
    }

    private record Row(String task, String project, String replay, String classification, String workflow,
        String event, String command, String principal, String capabilities, String scope,
        String runManifest, String principalManifest, String scopeManifest) { }
    private record Snapshot(RuntimeReplaySchema.VerifiedInputs replay, JsonNode command, String event,
        String classification, PermissionProtocol.GrantRow grant, String service, String certificate, String trust) { }

    /** Unforgeable, short-transaction proof. It deliberately exposes no start permission. */
    public final class Verified {
        private final EnterpriseAccess.Admission admission;
        private final HttpServletRequest http;
        private final String run, commandId;
        private final Snapshot snapshot;
        private final Thread thread = Thread.currentThread();
        private final TenantScope scope = TenantScope.requireCurrent();
        private final Map<Object, Object> transaction = Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private boolean completed;
        private Verified(EnterpriseAccess.Admission admission, HttpServletRequest http, String run,
                String commandId, Snapshot snapshot) {
            this.admission = admission; this.http = http; this.run = run;
            this.commandId = commandId; this.snapshot = snapshot;
            if (transaction.isEmpty()) deny();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completed = true; }
            });
        }
        private void active() {
            if (completed || thread != Thread.currentThread() || scope != TenantScope.requireCurrent()
                    || !TransactionSynchronizationManager.isActualTransactionActive()) deny();
            var actual = TransactionSynchronizationManager.getResourceMap();
            if (actual.size() != transaction.size()
                    || transaction.entrySet().stream().anyMatch(e -> actual.get(e.getKey()) != e.getValue())) deny();
            if (!scope.tenantId().equals(admission.context().tenant_id()) || !scope.subjectId().equals(admission.actorId())) deny();
        }
        public String tenantId() { active(); return admission.context().tenant_id(); }
        public String projectId() { return text(replay().content(), "project_id"); }
        public String runId() { active(); return run; }
        public String taskId() { return replay().taskId(); }
        public String principalId() { return text(replay().content(), "principal_id"); }
        public String actorId() { active(); return admission.actorId(); }
        public String classification() { active(); return snapshot.classification(); }
        public String commandId() { active(); return commandId; }
        public String outboxEventId() { active(); return snapshot.event(); }
        public JsonNode command() { active(); return snapshot.command().deepCopy(); }
        public RuntimeReplaySchema.VerifiedInputs replay() { active(); return snapshot.replay(); }
        public void requireUnchanged() throws Exception {
            active();
            var fresh = read(admission, http, run, commandId);
            if (!snapshot.command().equals(fresh.command()) || !snapshot.event().equals(fresh.event())
                    || !snapshot.classification().equals(fresh.classification())
                    || !snapshot.replay().rawSha256().equals(fresh.replay().rawSha256())
                    || !snapshot.replay().schemaSha256().equals(fresh.replay().schemaSha256())
                    || !snapshot.grant().equals(fresh.grant()) || !snapshot.service().equals(fresh.service())
                    || !snapshot.certificate().equals(fresh.certificate()) || !snapshot.trust().equals(fresh.trust()))
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
    }
    private static String text(JsonNode value, String field) {
        var node = value.required(field); if (!node.isTextual()) bad(); return node.textValue();
    }
    private static void deny() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void bad() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
