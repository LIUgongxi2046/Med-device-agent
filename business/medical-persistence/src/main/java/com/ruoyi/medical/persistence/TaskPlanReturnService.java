package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskPlanReturnService {
    private static final String PURPOSE = "PLAN_MAINTENANCE";
    private final EnterpriseAccess access;
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final TaskPlanWriteMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final TaskPlanDecisionSchema schema;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy identity;
    private final SchemaCatalogue shared;
    private final TaskPlanConfirmationStore confirmations;

    public TaskPlanReturnService(EnterpriseAccess access, TaskService tasks, TaskPlanService plans, TaskPlanWriteMapper mapper,
            PermissionMapper permissions, ResourceReadAuthority resources, TaskPlanDecisionSchema schema,
            PermissionSchema json, IdentityDirectoryPolicy identity, TaskPlanConfirmationStore confirmations) throws IOException, java.security.NoSuchAlgorithmException {
        this.access = access; this.tasks = tasks; this.plans = plans; this.mapper = mapper; this.permissions = permissions;
        this.resources = resources; this.schema = schema; this.json = json; this.identity = identity; this.confirmations = confirmations;
        shared = SchemaCatalogue.fromClasspath();
    }

    JsonNode basis(ApplicationSessionIdentity.Verified subject, String context, String taskId, String decision) {
        WireValues.uuid(taskId); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var prepared = prepare(admission, taskId, decision);
            var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(),
                    prepared.head().revision().toString(), json.tree(prepared.baseline())));
            shared.validate("interfaces", result, "Envelope"); return result;
        }).get());
    }

    JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key, String taskId, byte[] body) {
        json.headers(context, key); WireValues.uuid(taskId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var request = schema.request(body); var fixed = tasks.fixedTask(admission, taskId);
            var values = values(admission, fixed.head()); resources.require(admission, scope(fixed.head()), "plan.confirm", PURPOSE);
            var canonical = json.object(); canonical.put("operation_id", "TK-04"); canonical.put("task_id", taskId);
            canonical.set("input", json.tree(request)); values.put("requestHash", hash(json.canonical(canonical))); values.put("key", key);
            var operations = mapper.operation(values); if (operations.size() > 1) mismatch();
            if (!operations.isEmpty()) {
                var original = operations.get(0);
                if (!original.requestSha256().equals(values.get("requestHash"))) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                return replay(admission, fixed, values, original, request);
            }
            var prepared = prepare(admission, taskId, request.decision()); var head = prepared.head();
            if (!head.revision().toString().equals(request.expectedRevision())) fail(ErrorCode.REVISION_CONFLICT);
            if (!prepared.plan().planRevision().equals(request.targetRevision())) fail(ErrorCode.PLAN_STALE);
            if (!prepared.baseline().equals(request.baselineManifest())) fail(ErrorCode.SCOPE_STALE);
            String revision = PermissionSchema.next(request.expectedRevision()); var now = Objects.requireNonNull(permissions.databaseTime());
            String operationId = UUID.randomUUID().toString(), decisionId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            values.put("operationId", operationId); values.put("decisionId", decisionId); values.put("basePlanRevision", request.targetRevision());
            values.put("basePlanHash", prepared.plan().contentSha256()); values.put("basisTaskRevision", head.taskRevision());
            values.put("taskRevision", head.taskRevision()); values.put("resultingPlanRevision", null); values.put("decision", request.decision());
            values.put("fromStatus", head.status()); values.put("taskStatus", "CONFIRM".equals(request.decision()) ? "READY" : head.status());
            values.put("expectedRevision", request.expectedRevision());
            values.put("resultingRevision", revision); values.put("reason", request.reason()); values.put("request", json.canonicalObject(request));
            values.put("baseline", json.canonicalObject(prepared.baseline())); values.put("baselineHash", hash((String) values.get("baseline")));
            values.put("authorizations", json.canonical(prepared.authorizations()));
            values.put("authorityGrant", prepared.grant().grantId()); values.put("authorityRevision", prepared.grant().revision().toString());
            values.put("authEpoch", admission.watermarks().authEpoch().toString()); values.put("governanceEpoch", admission.watermarks().governanceEpoch().toString());
            values.put("now", now); values.put("classification", identity.classification()); values.put("httpStatus", 200);
            one(mapper.beginOperation(values));
            if (prepared.confirmation() != null) confirmations.save(admission, prepared.confirmation(), request, values);
            var receipt = new TaskPlanDecisionProtocol.Receipt("1.0.0", operationId, decisionId, head.tenantId(), head.projectId(), taskId,
                    request.targetRevision(), prepared.plan().contentSha256(), head.taskRevision(), (String) values.get("taskRevision"),
                    (String) values.get("resultingPlanRevision"), request.decision(), head.status(), (String) values.get("taskStatus"),
                    request.expectedRevision(), revision, subject.user().user_id(), now.toInstant(ZoneOffset.UTC).toString(), request.reason());
            var response = schema.envelope(receipt, requestId); String saved = json.canonical(response);
            values.put("response", saved); values.put("responseHash", hash(saved));
            one(mapper.insertDecision(values));
            if (prepared.confirmation() != null) one(mapper.bindDecision(values));
            one(mapper.advanceTaskHead(values));
            var after = tasks.fixedTask(admission, taskId);
            if (!after.head().taskRevision().equals(values.get("taskRevision")) || !after.head().status().equals(values.get("taskStatus"))
                    || !after.head().revision().toString().equals(revision) || !after.input().equals(fixed.input())
                    || !samePlan(prepared.plan(), plans.readFixed(admission, after, request.targetRevision(), false))
                    || !prepared.commonAuthorizations().equals(authorizations(admission, head, prepared.grant()))) mismatch();
            if (prepared.confirmation() == null) {
                if (!prepared.planHead().equals(plans.planHead(values))) mismatch();
            } else {
                var planHead = plans.planHead(values); var confirmed = plans.readFixed(admission, after, receipt.resultingPlanRevision(), true);
                if (!receipt.resultingPlanRevision().equals(planHead.planRevision()) || !values.get("resultingPlanHeadRevision").equals(planHead.revision().toString())
                        || !"CONFIRMED".equals(confirmed.status()) || !receipt.resultingTaskRevision().equals(confirmed.taskRevision())
                        || !prepared.plan().content().equals(confirmed.content()) || !prepared.plan().contentSha256().equals(confirmed.contentSha256())) mismatch();
                confirmations.requireUnchanged(admission, prepared.confirmation());
            }
            if (!prepared.grant().equals(resources.require(admission, scope(head), "plan.confirm", PURPOSE))) fail(ErrorCode.SCOPE_STALE);
            var audit = audit(receipt, request.baselineManifest()); values.put("requestId", requestId);
            String action = "CONFIRM".equals(request.decision()) ? "task.plan.confirmed" : "task.plan.returned"; values.put("action", action);
            one(permissions.saveAudit(subject.user().user_id(), head.projectId(), UUID.randomUUID().toString(), action,
                    json.canonical(audit), requestId, schema.manifestSha256(), identity.classification()));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), taskId, revision, action, saved));
            one(mapper.completeOperation(values));
            if (!response.equals(replay(admission, after, values, single(mapper.operation(values)), request))) mismatch();
            return response;
        }).get());
    }

    JsonNode original(ApplicationSessionIdentity.Verified subject, String context, String key, String taskId) {
        json.headers(context, key); WireValues.uuid(taskId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var fixed = tasks.fixedTask(admission, taskId);
            resources.require(admission, scope(fixed.head()), "plan.confirm", PURPOSE);
            var values = values(admission, fixed.head()); values.put("key", key); var rows = mapper.operation(values);
            if (rows.size() > 1) mismatch();
            if (rows.isEmpty()) {
                var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(), fixed.head().revision().toString(), json.tree(null)));
                shared.validate("interfaces", response, "Envelope"); return response;
            }
            return replay(admission, fixed, values, rows.get(0), null);
        }).get());
    }

    private Prepared prepare(EnterpriseAccess.Admission admission, String taskId, String decision) throws IOException {
        if (!Set.of("RETURN", "CONFIRM").contains(decision)) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var fixed = tasks.fixedTask(admission, taskId); var head = fixed.head();
        var grant = resources.require(admission, scope(head), "plan.confirm", PURPOSE);
        if (!Set.of("NEEDS_MATERIALS", "PLAN_PENDING").contains(head.status())) fail(ErrorCode.REVISION_CONFLICT);
        var planHead = plans.planHead(values(admission, head)); if (planHead == null) fail(ErrorCode.PLAN_STALE);
        var plan = plans.readFixed(admission, fixed, planHead.planRevision(), false);
        if (!"DRAFT".equals(plan.status()) || !head.taskRevision().equals(plan.taskRevision())) fail(ErrorCode.PLAN_STALE);
        var common = authorizations(admission, head, grant);
        var confirmation = "CONFIRM".equals(decision) ? confirmations.prepare(admission, fixed, plan, common) : null;
        var authorizations = confirmation == null ? common : confirmation.authorizations();
        var input = fixed.input(); var baseline = new TaskPlanDecisionProtocol.Baseline("1.0.0", head.tenantId(), head.projectId(), taskId,
                head.taskRevision(), plan.planRevision(), plan.contentSha256(), head.snapshotId(), head.templateVersion(), head.profileRevision(),
                head.contentHash(), input.requestHash(), input.scopeHash(), input.requirementsHash(), head.status(), head.revision().toString(),
                planHead.revision().toString(), admission.context().context_revision(), admission.watermarks().authEpoch().toString(),
                admission.watermarks().governanceEpoch().toString(), hash(json.canonical(authorizations)));
        schema.baseline(baseline); return new Prepared(head, planHead, plan, baseline, grant, authorizations, common, confirmation);
    }

    private JsonNode authorizations(EnterpriseAccess.Admission admission, TaskProtocol.Head head, PermissionProtocol.GrantRow grant) throws IOException {
        var values = json.object().arrayNode(); values.add(proof("plan.confirm", grant));
        values.add(proof("task.read", resources.require(admission, scope(head), "task.read", "TASK_READING")));
        for (var source : plans.sourceProof(admission, head.projectId(), head.taskId(), head.taskRevision(), head.snapshotId(), head.profileRevision(), false)) {
            var value = proof("source.read", source.sourceGrant()); value.put("source_version", source.source().sourceVersion());
            value.put("rights_revision", source.rights().rightsRevision()); value.put("rights_sha256", source.rights().contentSha256()); values.add(value);
        }
        return values;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode proof(String action, PermissionProtocol.GrantRow grant) {
        var value = json.object(); value.put("action", action); value.put("grant_id", grant.grantId());
        value.put("grant_revision", grant.revision().toString()); value.put("basis_version", grant.basisVersion()); return value;
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, TaskService.VerifiedTask fixed, Map<String,Object> values,
            SelectionOperationRow operation, TaskPlanDecisionProtocol.Request expected) throws IOException {
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !fixed.head().taskId().equals(operation.targetId()) || !"1.0.0".equals(operation.schemaVersion())
                || !schema.manifestSha256().equals(operation.schemaManifestSha256())) mismatch();
        values.put("operationId", operation.operationId()); var row = single(mapper.decision(values));
        var request = schema.request(row.requestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var baseline = request.baselineManifest(); var response = schema.response(row.receiptJson());
        var plan = plans.readFixed(admission, fixed, row.targetPlanRevision(), false);
        WireValues.uuid(row.authorityGrant()); WireValues.revision(row.authorityRevision().toString()); Objects.requireNonNull(row.createdAt());
        var canonical = json.object(); canonical.put("operation_id", "TK-04"); canonical.put("task_id", fixed.head().taskId()); canonical.set("input", json.tree(request));
        var receipt = new TaskPlanDecisionProtocol.Receipt("1.0.0", operation.operationId(), row.decisionId(), row.tenantId(), row.projectId(), row.taskId(),
                row.targetPlanRevision(), row.targetPlanSha256(), row.basisTaskRevision(), row.resultingTaskRevision(), row.resultingPlanRevision(),
                row.decision(), row.fromStatus(), row.toStatus(), row.expectedRevision().toString(), row.resultingRevision().toString(), row.createdBy(),
                row.createdAt().toInstant(ZoneOffset.UTC).toString(), row.reason());
        if (!row.tenantId().equals(admission.context().tenant_id()) || !row.projectId().equals(fixed.head().projectId())
                || !row.taskId().equals(fixed.head().taskId()) || !row.createdBy().equals(values.get("userId"))
                || !operation.operationId().equals(row.operationId()) || !operation.requestSha256().equals(hash(json.canonical(canonical)))
                || expected != null && !expected.equals(request) || !row.targetPlanRevision().equals(request.targetRevision())
                || !row.targetPlanSha256().equals(plan.contentSha256()) || !row.basisTaskRevision().equals(plan.taskRevision())
                || !row.reason().equals(request.reason()) || !row.decision().equals(request.decision())
                || !row.expectedRevision().toString().equals(request.expectedRevision())
                || !row.schemaVersion().equals("1.0.0") || !row.schemaManifest().equals(schema.manifestSha256())
                || !row.classification().equals(identity.classification()) || row.authorityRevision().signum() <= 0
                || !row.authEpoch().toString().equals(baseline.authEpoch()) || !row.governanceEpoch().toString().equals(baseline.governanceEpoch())
                || !baseline.tenantId().equals(row.tenantId()) || !baseline.projectId().equals(row.projectId()) || !baseline.taskId().equals(row.taskId())
                || !baseline.taskRevision().equals(row.basisTaskRevision()) || !baseline.planSha256().equals(row.targetPlanSha256())
                || !baseline.snapshotId().equals(plan.snapshotId()) || !baseline.templateVersion().equals(plan.templateVersion())
                || !baseline.profileRevision().equals(plan.profileRevision()) || !baseline.taskStatus().equals(row.fromStatus())
                || !json.tree(baseline).equals(schema.read(row.baselineManifest())) || !hash(json.canonicalObject(baseline)).equals(row.baselineSha256())
                || !schema.read(row.dependencyAuthorizations()).isArray()
                || !hash(json.canonical(schema.read(row.dependencyAuthorizations()))).equals(baseline.dependencyAuthorizationsSha256())
                || !schema.envelope(receipt, response.required("request_id").textValue()).equals(response)
                || !hash(json.canonical(response)).equals(row.receiptSha256()) || !schema.response(operation.responseRef()).equals(response)) mismatch();
        values.put("decisionId", row.decisionId()); values.put("requestId", response.required("request_id").textValue()); values.put("resultingRevision", row.resultingRevision().toString());
        values.put("action", "CONFIRM".equals(row.decision()) ? "task.plan.confirmed" : "task.plan.returned");
        if ("CONFIRM".equals(row.decision())) confirmations.verifyResult(admission, fixed, plan, row, baseline);
        if (!audit(receipt, baseline).equals(schema.read(single(mapper.decisionAudit(values))))
                || !response.equals(schema.read(single(mapper.decisionEvent(values))))) mismatch();
        resources.require(admission, scope(fixed.head()), "plan.confirm", PURPOSE); return response;
    }
    private JsonNode audit(TaskPlanDecisionProtocol.Receipt receipt, TaskPlanDecisionProtocol.Baseline baseline) {
        var value = json.object(); value.put("operation_id", receipt.operationId()); value.put("decision_id", receipt.decisionId());
        value.set("receipt", json.tree(receipt)); value.set("baseline_manifest", json.tree(baseline)); return value;
    }
    private record Prepared(TaskProtocol.Head head, TaskPlanWriteProtocol.Head planHead, TaskPlanWriteProtocol.View plan,
            TaskPlanDecisionProtocol.Baseline baseline, PermissionProtocol.GrantRow grant, JsonNode authorizations,
            JsonNode commonAuthorizations, TaskPlanConfirmationStore.Prepared confirmation) { }
    private boolean samePlan(TaskPlanWriteProtocol.View expected, TaskPlanWriteProtocol.View actual) {
        var first = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(expected);
        var second = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(actual);
        first.remove(List.of("current_plan_revision", "current_plan_head_revision")); second.remove(List.of("current_plan_revision", "current_plan_head_revision"));
        return first.equals(second);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission, TaskProtocol.Head head) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("projectId", head.projectId());
        values.put("taskId", head.taskId()); values.put("operationName", "TK-04"); values.put("operationManifest", schema.manifestSha256());
        values.put("classification", identity.classification()); return values;
    }
    private static PermissionProtocol.Scope scope(TaskProtocol.Head head) { return new PermissionProtocol.Scope(head.tenantId(), head.projectId(), "TASK", head.taskId()); }
    private static String hash(String content) { return DigestUtils.sha256Hex(content); }
    private static <T> T single(List<T> values) { if (values.size() != 1) mismatch(); return values.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void current(EnterpriseAccess.Admission admission, String context) { if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
