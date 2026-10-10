package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RunControlMapper;
import com.ruoyi.medical.persistence.mapper.RunReadMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class RunCancellationService {
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private final EnterpriseAccess access;
    private final RunControlMapper writes;
    private final RunReadMapper reads;
    private final TaskMapper tasks;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy identity;
    private final SchemaCatalogue shared;
    private final com.networknt.schema.JsonSchema output;
    private final String manifest;
    private final JsonMapper reader = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public RunCancellationService(EnterpriseAccess access, RunControlMapper writes, RunReadMapper reads, TaskMapper tasks,
            ResourceReadAuthority resources, PermissionMapper permissions, PermissionSchema json, IdentityDirectoryPolicy identity)
            throws IOException, NoSuchAlgorithmException {
        this.access = access; this.writes = writes; this.reads = reads; this.tasks = tasks; this.resources = resources;
        this.permissions = permissions; this.json = json; this.identity = identity; shared = SchemaCatalogue.fromClasspath();
        byte[] definition;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/run-control.schema.json"))) { definition = input.readAllBytes(); }
        output = shared.compileApiSchema(reader.readTree(definition)); output.initializeValidators();
        var metadata = json.object().put("schema_version", "1.0.0").put("run-control.schema.json", DigestUtils.sha256Hex(definition))
            .put("permission_manifest", json.manifestSha256()); manifest = DigestUtils.sha256Hex(json.canonical(metadata));
    }

    public JsonNode cancel(ApplicationSessionIdentity.Verified subject, String context, String key, String runId, byte[] body) {
        json.requireId(runId); json.headers(context, key);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            var input = reader.readTree(body); if (input == null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            shared.validate("interfaces", input, "CancelMessage");
            String expected = text(input, "expected_revision"), reason = text(input, "reason");
            PermissionSchema.revision(expected);
            if (reason.isBlank() || reason.codePointCount(0, reason.length()) > 2048) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("runId", runId); values.put("key", key);
            var head = reader.readTree(one(reads.head(values), ErrorCode.ACCESS_DENIED));
            String tenant = admission.context().tenant_id(), project = text(head, "project_id"), task = text(head, "task_id");
            json.requireId(project); json.requireId(task); json.requireId(text(head, "task_revision"));
            if (!tenant.equals(text(head, "tenant_id")) || !runId.equals(text(head, "run_id"))
                    || !"TASK".equals(text(head, "execution_kind")) || !"TASK".equals(text(head, "registry_kind"))
                    || !project.equals(text(head, "registry_project_id")) || !"1.0.0".equals(text(head, "schema_version"))
                    || !(tenant + "/" + runId).equals(text(head, "workflow_id"))) mismatch();
            WireValues.hash(text(head, "schema_manifest"));
            var scope = new PermissionProtocol.Scope(tenant, project, "TASK", task);
            var grant = resources.require(admission, scope, "task.run", "RUN_CANCELLATION");
            String projectStatus = one(permissions.projectStatus(project), ErrorCode.ACCESS_DENIED);
            if (!Set.of("ACTIVE", "PAUSED").contains(projectStatus)) fail(ErrorCode.ACCESS_DENIED);
            var taskHead = one(tasks.head(subject.user().user_id(), task), ErrorCode.ACCESS_DENIED);
            if (!tenant.equals(taskHead.tenantId()) || !project.equals(taskHead.projectId()) || !task.equals(taskHead.taskId())
                    || !taskHead.contentHash().equals(taskHead.registryHash())) mismatch();
            values.put("projectId", project); values.put("taskId", task); values.put("manifest", manifest);
            var request = json.object().put("operation_id", "RN-04").put("run_id", runId); request.set("input", input);
            String requestHash = DigestUtils.sha256Hex(json.canonical(request)); values.put("requestHash", requestHash);
            var previous = writes.operation(values);
            if (previous.size() > 1) mismatch();
            if (!previous.isEmpty()) {
                var saved = previous.get(0);
                if (!requestHash.equals(saved.requestSha256())) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                if (!runId.equals(saved.targetId()) || !"COMPLETED".equals(saved.state()) || !Integer.valueOf(200).equals(saved.httpStatus())
                        || !"1.0.0".equals(saved.schemaVersion()) || !manifest.equals(saved.schemaManifestSha256())) mismatch();
                var response = reader.readTree(saved.responseRef()); validate(response, runId, task, saved.operationId());
                unchanged(admission, scope, grant, projectStatus); return response;
            }
            String status = text(head, "status"), commandId = null, resulting = text(head, "revision");
            String operationId = id(), requestId = id(); var now = Objects.requireNonNull(permissions.databaseTime());
            values.put("operationId", operationId); values.put("now", now); values.put("classification", identity.classification());
            values.put("expectedRevision", expected); values.put("previousStatus", status);
            if (!Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status)) {
                if (!resulting.equals(expected)) fail(ErrorCode.REVISION_CONFLICT);
                if (status.equals("CANCEL_REQUESTED")) {
                    var original = reader.readTree(one(writes.originalCancellation(values), ErrorCode.CONTENT_MISMATCH));
                    shared.validate("interfaces", original, "Envelope"); if (!output.validate(original.required("data")).isEmpty()) mismatch();
                    if (!runId.equals(text(original.required("data").required("run"), "run_id"))) mismatch();
                    commandId = text(original.required("data"), "command_id"); json.requireId(commandId);
                } else {
                    if (!Set.of("QUEUED", "RUNNING", "WAITING_INPUT").contains(status)) mismatch();
                    var active = writes.activeRun(values); if (active.size() > 1) mismatch();
                    boolean current = active.size() == 1 && runId.equals(active.get(0));
                    if (!current && head.required("dispatch_closed_at").isNull()) fail(ErrorCode.STEP_NOT_READY);
                    resulting = next(resulting); values.put("resultingRevision", resulting);
                    changed(writes.requestCancellation(values));
                    if (current) {
                        values.put("taskRevision", taskHead.revision().toString()); next(taskHead.revision().toString());
                        changed(writes.requestTaskCancellation(values));
                    }
                    // 这里只登记请求与停止派发；外部终态和进程退出由独立回执证明。
                    commandId = id(); var command = json.object().put("schema_version", "1.0.0").put("tenant_id", tenant).put("project_id", project)
                        .put("run_id", runId).put("command_id", commandId).put("kind", "REQUEST_CANCEL").put("decision_receipt_id", operationId);
                    var decision = json.object(); decision.set("request", request); decision.put("actor_id", subject.user().user_id())
                        .put("authority_grant_id", grant.grantId()).put("authority_revision", grant.revision().toString())
                        .put("auth_epoch", admission.watermarks().authEpoch().toString()).put("governance_epoch", admission.watermarks().governanceEpoch().toString())
                        .put("created_at", now.toInstant(java.time.ZoneOffset.UTC).toString()).put("schema_manifest_sha256", manifest);
                    var commandBody = json.object(); commandBody.set("command", command); commandBody.set("decision", decision);
                    command.put("command_sha256", DigestUtils.sha256Hex(json.canonical(commandBody)));
                    values.put("commandPayload", json.canonical(commandBody)); values.put("commandEventId", id()); changed(writes.saveCommand(values));
                    appendEvent(admission, values, resulting, now); status = "CANCEL_REQUESTED";
                }
            }
            var after = reader.readTree(one(reads.head(values), ErrorCode.CONTENT_MISMATCH));
            if (!status.equals(text(after, "status")) || !resulting.equals(text(after, "revision"))) mismatch();
            var run = json.object(); for (String name : List.of("run_id", "task_id", "status", "reason_code", "revision", "dispatch_closed_at", "ended_at")) run.set(name, after.required(name));
            var operation = json.object().put("operation_id", operationId).put("resource_id", runId).put("status", "COMPLETED").put("status_url", "/api/operations/" + operationId);
            var view = json.object(); view.set("operation", operation); if (commandId == null) view.putNull("command_id"); else view.put("command_id", commandId); view.set("run", run);
            var response = json.object().put("schema_version", "1.0.0").put("request_id", requestId).put("resource_revision", resulting); response.set("data", view);
            validate(response, runId, task, operationId); unchanged(admission, scope, grant, projectStatus);
            values.put("response", json.canonical(response)); changed(writes.saveOperation(values));
            var audit = json.object(); audit.set("scope", json.tree(scope)); audit.set("request", request); audit.set("result", view);
            audit.put("operation_id", operationId).put("authority_grant_id", grant.grantId()).put("authority_revision", grant.revision().toString())
                .put("auth_epoch", admission.watermarks().authEpoch().toString()).put("governance_epoch", admission.watermarks().governanceEpoch().toString());
            changed(permissions.saveAudit(subject.user().user_id(), project, id(), "run.cancel.requested", json.canonical(audit), requestId, manifest, identity.classification()));
            var saved = one(writes.operation(values), ErrorCode.CONTENT_MISMATCH);
            if (!operationId.equals(saved.operationId()) || !requestHash.equals(saved.requestSha256()) || !response.equals(reader.readTree(saved.responseRef()))) mismatch();
            return response;
        }).get());
    }

    private void appendEvent(EnterpriseAccess.Admission admission, HashMap<String,Object> values, String revision, java.time.LocalDateTime now) throws IOException {
        var stream = reader.readTree(one(reads.stream(values), ErrorCode.CONTENT_MISMATCH));
        String streamId = text(stream, "stream_id"), sequence = text(stream, "sequence"), next = next(sequence), eventId = id(); json.requireId(streamId);
        var payload = json.object().put("business_event_type", "RUN_CANCEL_REQUESTED").put("status", "CANCEL_REQUESTED").put("reason_code", "USER_CANCELLED").put("read_operation_id", "RN-02");
        var event = json.object().put("schema_version", "1.0.0").put("event_id", eventId).put("sequence", next).put("event_type", "run.updated")
            .put("tenant_id", admission.context().tenant_id()).put("project_id", (String)values.get("projectId")).put("task_id", (String)values.get("taskId"))
            .put("run_id", (String)values.get("runId")).put("resource_kind", "RUN").put("resource_id", (String)values.get("runId"))
            .put("resource_revision", revision).put("occurred_at", now.toInstant(java.time.ZoneOffset.UTC).toString());
        event.set("stream", json.object().put("stream_id", streamId).put("kind", "RUN").put("resource_id", (String)values.get("runId"))); event.set("payload", payload);
        shared.validate("interfaces", event, "PublicEvent");
        values.put("eventId", eventId); values.put("streamId", streamId); values.put("sequence", sequence); values.put("nextSequence", next);
        values.put("eventPayload", json.canonical(payload)); values.put("eventManifest", json.manifestSha256());
        changed(permissions.saveEvent((String)values.get("userId"), eventId, (String)values.get("runId"), revision, "run.updated", json.canonical(event)));
        changed(writes.advanceStream(values)); changed(writes.appendEvent(values));
    }
    private void validate(JsonNode response, String runId, String task, String operationId) {
        shared.validate("interfaces", response, "Envelope"); var data = response.required("data"); if (!output.validate(data).isEmpty()) mismatch();
        if (!runId.equals(text(data.required("run"), "run_id")) || !task.equals(text(data.required("run"), "task_id"))
                || !runId.equals(text(data.required("operation"), "resource_id")) || !operationId.equals(text(data.required("operation"), "operation_id"))
                || !("/api/operations/" + operationId).equals(text(data.required("operation"), "status_url"))
                || !response.required("resource_revision").equals(data.required("run").required("revision"))) mismatch();
    }
    private void unchanged(EnterpriseAccess.Admission admission, PermissionProtocol.Scope scope, PermissionProtocol.GrantRow grant, String projectStatus) throws IOException {
        if (!grant.equals(resources.require(admission, scope, "task.run", "RUN_CANCELLATION"))
                || !List.of(projectStatus).equals(permissions.projectStatus(scope.project_id()))) fail(ErrorCode.SCOPE_STALE);
    }
    private static String next(String value) { var revision = PermissionSchema.revision(value); if (revision.equals(MAX)) fail(ErrorCode.REVISION_LIMIT); return revision.add(BigInteger.ONE).toString(); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String text(JsonNode node, String name) { var value = node.required(name); if (!value.isTextual()) mismatch(); return value.textValue(); }
    private static <T> T one(List<T> values, ErrorCode code) { if (values.size() != 1) fail(code); return values.get(0); }
    private static void changed(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
