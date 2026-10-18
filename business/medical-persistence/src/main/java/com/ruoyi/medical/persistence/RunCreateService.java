package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RunCreateMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class RunCreateService {
    private static final String PURPOSE = "RUN_CREATION";
    private final EnterpriseAccess access;
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final RunCreateMapper mapper;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final RunCreateSchema schema;
    private final RunTokenAuthoritySchema authority;
    private final AgentReleaseStore releases;
    private final AgentReleaseScopeAuthority scopes;
    private final TemplateDependencyPolicy limits;
    private final IdentityDirectoryPolicy identity;
    private final RunCreateBudget budgets;
    private final ObjectProvider<RunCreateAdmission> providers;
    private final SchemaCatalogue shared;

    public RunCreateService(EnterpriseAccess access, TaskService tasks, TaskPlanService plans, RunCreateMapper mapper,
            ResourceReadAuthority resources, PermissionMapper permissions, PermissionSchema json, RunCreateSchema schema,
            RunTokenAuthoritySchema authority, AgentReleaseStore releases, AgentReleaseScopeAuthority scopes,
            TemplateDependencyPolicy limits, IdentityDirectoryPolicy identity, RunCreateBudget budgets,
            ObjectProvider<RunCreateAdmission> providers) throws IOException, java.security.NoSuchAlgorithmException {
        this.access = access; this.tasks = tasks; this.plans = plans; this.mapper = mapper; this.resources = resources;
        this.permissions = permissions; this.json = json; this.schema = schema; this.authority = authority;
        this.releases = releases; this.scopes = scopes; this.limits = limits; this.identity = identity;
        this.budgets = budgets; this.providers = providers; shared = SchemaCatalogue.fromClasspath();
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, String taskId, byte[] body) {
        WireValues.uuid(taskId); json.headers(context, key);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            var request = schema.request(body); var fixed = tasks.fixedTask(admission, taskId); var head = fixed.head();
            String tenant = admission.context().tenant_id(), project = head.projectId(), actor = subject.user().user_id();
            var taskScope = new PermissionProtocol.Scope(tenant, project, "TASK", taskId);
            var grant = resources.require(admission, taskScope, "task.run", PURPOSE);
            var values = new HashMap<String,Object>(); values.put("userId", actor); values.put("projectId", project); values.put("taskId", taskId);
            values.put("key", key); values.put("requestHash", schema.requestHash(taskId, request)); values.put("manifest", schema.manifestSha256());
            var previous = mapper.operation(values); if (previous.size() > 1) mismatch();
            if (!previous.isEmpty()) {
                var row = previous.get(0);
                if (!values.get("requestHash").equals(row.requestSha256())) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                if (!Integer.valueOf(202).equals(row.httpStatus()) || !"1.0.0".equals(row.schemaVersion())
                        || !schema.manifestSha256().equals(row.schemaManifestSha256()) || !Set.of("PROCESSING", "COMPLETED", "FAILED").contains(row.state())) mismatch();
                var response = schema.response(row.responseRef()); var data = response.required("data");
                if (!row.operationId().equals(text(data.required("operation"), "operation_id")) || !row.targetId().equals(text(data, "run_id"))) mismatch();
                validateReceipt(data, taskId, request); values.put("runId", row.targetId()); values.put("commandId", text(data, "command_id"));
                verifySaved(values, request, row.operationId());
                if (!grant.equals(resources.require(admission, taskScope, "task.run", PURPOSE))) fail(ErrorCode.SCOPE_STALE);
                return response;
            }
            RunCreateSchema.requireReady(head, request);
            if (!List.of("ACTIVE").equals(permissions.projectStatus(project))) fail(ErrorCode.ACCESS_DENIED);
            if (!mapper.activeRun(values).isEmpty()) fail(ErrorCode.STEP_NOT_READY);
            values.put("taskRevision", request.task_revision()); values.put("budgetRevision", request.budget_revision());
            values.put("expectedRevision", request.expected_revision());
            var attempts = mapper.attempts(values);
            // RN-05 owns all later attempts and their predecessor/unknown-invocation checks.
            if (!attempts.isEmpty()) fail(ErrorCode.STEP_NOT_READY);
            var plan = plans.readFixed(admission, fixed, request.plan_revision(), true);
            var planProof = plans.execution(admission, fixed, plan);
            var release = releases.read(admission, head.agentReleaseId(), PURPOSE);
            if (!"PUBLISHED".equals(release.agent().status())) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            if (!"COMPLETED".equals(release.evaluation().status())) fail(ErrorCode.EVALUATION_STALE);
            var releaseScope = scopes.require(release, taskScope, "task.run", identity.classification(), limits.limits());
            var now = Objects.requireNonNull(permissions.databaseTime());
            var budget = budgets.require(admission, values, now, release.content().manifest().limits().currency());
            String runId = id(), principalId = id(), operationId = id(), commandId = id(), requestId = id();
            var provider = requireProvider(providers);
            var proof = Objects.requireNonNull(provider.require(admission, fixed, plan, releaseScope, request, runId, principalId));
            var replay = Objects.requireNonNull(proof.replayManifest());
            // The missing C06 consumer must publish the complete replay contract, not label an unrelated valid schema as replay evidence.
            if (!"urn:medical:replay-manifest:1.0.0".equals(replay.schemaRef())) mismatch();
            var rebound = shared.bindPublished(replay.schemaRef(), replay.schemaSha256(), replay.canonicalBytes());
            if (!rebound.equals(replay)) mismatch();
            var replayNode = schema.read(replay.canonicalJson());
            requireReplay(replayNode, tenant, project, actor, taskId, runId, head.agentReleaseId(), request);
            var scopeManifest = authority.scopeManifest(json.canonical(proof.scopeManifest()), tenant, project, actor, runId, "TASK");
            var principal = authority.principalManifest(json.canonical(proof.principalManifest()), tenant, project, actor, runId, principalId, scopeManifest);
            for (var permission : authority.permissions(principal)) resources.require(admission, permission.scope(), permission.action(), permission.purpose());
            String retention = proof.retentionPolicyRevision(); WireValues.uuid(retention);
            var retentionVersion = one(permissions.basis(retention), ErrorCode.RETENTION_UNCONFIGURED);
            if (!retention.equals(retentionVersion.id()) || retentionVersion.ownerProjectId() != null && !project.equals(retentionVersion.ownerProjectId())) mismatch();
            WireValues.hash(retentionVersion.sha256());
            values.put("runId", runId); values.put("principalId", principalId); values.put("operationId", operationId); values.put("commandId", commandId);
            values.put("attempt", "1"); values.put("workflowId", tenant + "/" + runId); values.put("now", now);
            values.put("classification", identity.classification()); values.put("streamId", id()); values.put("retentionRevision", retention);
            values.put("authorityManifest", authority.manifestSha256()); values.put("scopeManifest", authority.canonical(scopeManifest));
            values.put("principalManifest", authority.canonical(principal)); values.put("replayManifest", replay.canonicalJson());
            var command = json.object().put("schema_version", "1.0.0").put("kind", "START_RUN").put("command_id", commandId)
                .put("tenant_id", tenant).put("project_id", project).put("task_id", taskId).put("run_id", runId)
                .put("principal_id", principalId).put("operation_id", operationId).put("workflow_id", tenant + "/" + runId)
                .put("replay_schema_ref", replay.schemaRef()).put("replay_schema_sha256", replay.schemaSha256())
                .put("replay_sha256", DigestUtils.sha256Hex(replay.canonicalBytes()));
            var decision = json.object().put("actor_id", actor).put("authority_grant_id", grant.grantId())
                .put("authority_revision", grant.revision().toString()).put("request_sha256", (String)values.get("requestHash"))
                .put("auth_epoch", admission.context().auth_epoch()).put("governance_epoch", admission.context().governance_epoch());
            decision.set("request", json.tree(request));
            var payload = json.object(); payload.set("command", command); payload.set("decision", decision);
            command.put("command_sha256", DigestUtils.sha256Hex(json.canonical(payload)));
            values.put("commandPayload", json.canonical(payload)); values.put("commandEventId", id());
            proof.requireUnchanged();
            changed(mapper.advanceTask(values)); changed(mapper.insertRegistry(values)); changed(mapper.insertRun(values));
            changed(mapper.insertPrincipal(values)); changed(mapper.insertActiveRun(values)); changed(mapper.insertStream(values)); changed(mapper.saveCommand(values));
            var data = json.object().put("run_id", runId).put("task_id", taskId).put("task_revision", request.task_revision())
                .put("plan_revision", request.plan_revision()).put("snapshot_id", request.snapshot_id()).put("budget_revision", request.budget_revision())
                .put("command_id", commandId).put("status", "QUEUED").put("revision", "1");
            data.set("operation", json.object().put("operation_id", operationId).put("resource_id", runId).put("status", "PROCESSING")
                .put("status_url", "/api/operations/" + operationId));
            var response = schema.receipt(data, requestId); values.put("response", json.canonical(response)); changed(mapper.saveOperation(values));
            var audit = json.object(); audit.set("request", json.tree(request)); audit.set("result", data); audit.set("decision", decision);
            changed(permissions.saveAudit(actor, project, id(), "run.create.accepted", json.canonical(audit), requestId, schema.manifestSha256(), identity.classification()));
            verifySaved(values, request, operationId);
            var after = tasks.fixedTask(admission, taskId).head();
            if (!RunCreateSchema.next(head.revision().toString()).equals(after.revision().toString()) || !head.taskRevision().equals(after.taskRevision())
                    || !"READY".equals(after.status()) || !List.of(runId).equals(mapper.activeRun(values))) mismatch();
            plans.requireUnchanged(admission, fixed, plan, planProof); releases.requireUnchanged(release); scopes.requireUnchanged(releaseScope);
            if (!budget.equals(budgets.require(admission, values, Objects.requireNonNull(permissions.databaseTime()), release.content().manifest().limits().currency()))
                    || !List.of("ACTIVE").equals(permissions.projectStatus(project)) || !grant.equals(resources.require(admission, taskScope, "task.run", PURPOSE))) fail(ErrorCode.SCOPE_STALE);
            for (var permission : authority.permissions(principal)) resources.require(admission, permission.scope(), permission.action(), permission.purpose());
            proof.requireUnchanged();
            return response;
        }).get());
    }

    static RunCreateAdmission requireProvider(ObjectProvider<RunCreateAdmission> providers) {
        var provider = providers.getIfAvailable(); if (provider == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE); return provider;
    }
    private void verifySaved(HashMap<String,Object> values, RunCreateSchema.Create request, String operationId) throws IOException {
        var saved = schema.read(one(mapper.created(values), ErrorCode.CONTENT_MISMATCH));
        if (!values.get("runId").equals(text(saved, "run_id")) || !values.get("taskId").equals(text(saved, "task_id"))
                || !request.task_revision().equals(text(saved, "task_revision")) || !request.budget_revision().equals(text(saved, "budget_revision"))
                || !request.plan_revision().equals(text(saved, "plan_revision")) || !request.snapshot_id().equals(text(saved, "snapshot_id"))
                || !values.get("userId").equals(text(saved, "created_by")) || !authority.manifestSha256().equals(text(saved, "schema_manifest"))) mismatch();
        String tenant = TenantScope.requireCurrent().tenantId(), actor = (String)values.get("userId"), project = (String)values.get("projectId");
        requireReplay(saved.required("replay_manifest"), tenant, project, actor, (String)values.get("taskId"), (String)values.get("runId"),
                text(saved, "agent_release_id"), request);
        var scope = authority.scopeManifest(json.canonical(saved.required("scope_manifest")), tenant, project, actor, (String)values.get("runId"), "TASK");
        var principal = saved.required("principal_manifest");
        authority.principalManifest(json.canonical(principal), tenant, project, actor, (String)values.get("runId"), text(principal, "principal_id"), scope);
        var command = saved.required("command").required("command"); var decision = saved.required("command").required("decision");
        if (!values.get("commandId").equals(text(command, "command_id")) || !values.get("runId").equals(text(command, "run_id"))
                || !operationId.equals(text(command, "operation_id")) || !"START_RUN".equals(text(command, "kind"))
                || !tenant.equals(text(command, "tenant_id")) || !project.equals(text(command, "project_id"))
                || !values.get("taskId").equals(text(command, "task_id")) || !actor.equals(text(decision, "actor_id"))
                || !text(principal, "principal_id").equals(text(command, "principal_id"))
                || !(tenant + "/" + values.get("runId")).equals(text(command, "workflow_id"))
                || !values.get("requestHash").equals(text(decision, "request_sha256")) || !json.tree(request).equals(decision.required("request"))
                || !DigestUtils.sha256Hex(json.canonical(saved.required("replay_manifest"))).equals(text(command, "replay_sha256"))) mismatch();
        var original = saved.required("command").deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)original.required("command")).remove("command_sha256");
        if (!DigestUtils.sha256Hex(json.canonical(original)).equals(text(command, "command_sha256"))) mismatch();
        if (values.containsKey("replayManifest") && (!schema.read((String)values.get("replayManifest")).equals(saved.required("replay_manifest"))
                || !schema.read((String)values.get("scopeManifest")).equals(saved.required("scope_manifest"))
                || !schema.read((String)values.get("principalManifest")).equals(saved.required("principal_manifest"))
                || !values.get("retentionRevision").equals(text(saved, "retention_policy_revision")))) mismatch();
    }
    private static void validateReceipt(JsonNode data, String task, RunCreateSchema.Create request) {
        if (!task.equals(text(data, "task_id")) || !request.task_revision().equals(text(data, "task_revision"))
                || !request.plan_revision().equals(text(data, "plan_revision")) || !request.snapshot_id().equals(text(data, "snapshot_id"))
                || !request.budget_revision().equals(text(data, "budget_revision"))) mismatch();
    }
    private static void requireReplay(JsonNode replay, String tenant, String project, String actor, String task, String run,
            String release, RunCreateSchema.Create request) {
        if (!tenant.equals(text(replay, "tenant_id")) || !project.equals(text(replay, "project_id")) || !actor.equals(text(replay, "initiator_id"))
                || !task.equals(text(replay, "task_id")) || !run.equals(text(replay, "run_id")) || !release.equals(text(replay, "agent_release_id"))
                || !request.task_revision().equals(text(replay, "task_revision")) || !request.plan_revision().equals(text(replay, "plan_revision"))
                || !request.snapshot_id().equals(text(replay, "snapshot_id")) || !request.budget_revision().equals(text(replay, "budget_revision"))) mismatch();
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String text(JsonNode row, String name) { var value = row.required(name); if (!value.isTextual()) mismatch(); return value.textValue(); }
    private static <T> T one(List<T> rows, ErrorCode error) { if (rows.size() != 1) fail(error); return rows.get(0); }
    private static void changed(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
