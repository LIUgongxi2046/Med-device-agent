package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskCreateDraftService {
    private static final String PURPOSE = "TASK_CREATION";
    private static final String ACTION = "task.input_draft.saved";
    private final EnterpriseAccess access;
    private final PermissionSchema json;
    private final TaskInputSchema inputs;
    private final TaskCreateDraftSchema schema;
    private final ResourceReadAuthority resources;
    private final ProjectService projects;
    private final PermissionMapper permissions;
    private final TaskPlanWriteMapper writes;
    private final IdentityDirectoryPolicy identity;
    public record Reply(int httpStatus, JsonNode envelope) { }

    public TaskCreateDraftService(EnterpriseAccess access, PermissionSchema json, TaskInputSchema inputs, TaskCreateDraftSchema schema,
            ResourceReadAuthority resources, ProjectService projects, PermissionMapper permissions, TaskPlanWriteMapper writes,
            IdentityDirectoryPolicy identity) {
        this.access = access; this.json = json; this.inputs = inputs; this.schema = schema; this.resources = resources;
        this.projects = projects; this.permissions = permissions; this.writes = writes; this.identity = identity;
    }

    Reply save(ApplicationSessionIdentity.Verified subject, String context, String key, String project, byte[] body) throws IOException {
        json.headers(context, key); WireValues.uuid(project); var input = inputs.create(body);
        if (!project.equals(input.request().project_id())) fail(ErrorCode.SCHEMA_INVALID);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var grant = authorized(admission, project); var values = values(admission, project, key);
            values.put("requestHash", operationHash(input)); var existing = writes.operation(values);
            if (existing.size() > 1) mismatch();
            if (!existing.isEmpty()) {
                if (!values.get("requestHash").equals(existing.get(0).requestSha256())) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                return replay(admission, values, existing.get(0), input);
            }
            if (!"ACTIVE".equals(projects.fixedProfile(admission, project, input.request().profile_revision(), PURPOSE).project().status()))
                fail(ErrorCode.ACCESS_DENIED);
            var now = Objects.requireNonNull(permissions.databaseTime()); String operation = UUID.randomUUID().toString();
            var draft = json.object(); draft.put("schema_version", "1.0.0"); draft.put("operation_id", operation);
            draft.put("tenant_id", admission.context().tenant_id()); draft.put("project_id", project); draft.put("actor_id", subject.user().user_id());
            draft.put("created_at", DateTimeFormatter.ISO_INSTANT.format(now.toInstant(ZoneOffset.UTC)));
            draft.put("body_sha256", input.request_sha256()); draft.set("content", inputs.read(input.canonical_request()));
            var response = schema.envelope(draft, UUID.randomUUID().toString());
            values.put("operationId", operation); values.put("now", now); values.put("response", json.canonical(response)); values.put("httpStatus", 201);
            one(writes.beginOperation(values));
            // 审计与Outbox只保存身份和摘要，完整输入保存在本人操作记录中。
            var metadata = metadata(response, (String) values.get("requestHash")); String requestId = response.required("request_id").textValue();
            one(permissions.saveAudit(subject.user().user_id(), project, UUID.randomUUID().toString(), ACTION,
                    json.canonical(metadata), requestId, schema.manifestSha256(), identity.classification()));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), operation, "1", ACTION, json.canonical(metadata)));
            if (!grant.equals(authorized(admission, project))) fail(ErrorCode.SCOPE_STALE);
            one(writes.completeOperation(values)); return replay(admission, values, single(writes.operation(values)), input);
        }).get());
    }

    Reply original(ApplicationSessionIdentity.Verified subject, String context, String key, String project) {
        json.headers(context, key); WireValues.uuid(project);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); authorized(admission, project); var values = values(admission, project, key); var rows = writes.operation(values);
            if (rows.size() > 1) mismatch();
            return rows.isEmpty() ? new Reply(200, schema.envelope(null, UUID.randomUUID().toString())) : replay(admission, values, rows.get(0), null);
        }).get());
    }

    private Reply replay(EnterpriseAccess.Admission admission, Map<String,Object> values, SelectionOperationRow operation,
            TaskProtocol.ValidatedCreate expected) throws IOException {
        if (!values.get("projectId").equals(operation.targetId())) fail(ErrorCode.ACCESS_DENIED);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !schema.manifestSha256().equals(operation.schemaManifestSha256())) mismatch();
        WireValues.uuid(operation.operationId()); var response = schema.response(operation.responseRef()); var draft = response.required("data");
        var input = inputs.create(json.canonical(draft.required("content")).getBytes(StandardCharsets.UTF_8));
        if (!operation.operationId().equals(draft.required("operation_id").textValue())
                || !values.get("userId").equals(draft.required("actor_id").textValue())
                || !admission.context().tenant_id().equals(draft.required("tenant_id").textValue())
                || !values.get("projectId").equals(draft.required("project_id").textValue())
                || !operation.requestSha256().equals(operationHash(input)) || expected != null && !expected.equals(input)) mismatch();
        values.put("operationId", operation.operationId()); values.put("requestId", response.required("request_id").textValue());
        var createdAt = single(writes.draftOperationTime(values));
        if (!DateTimeFormatter.ISO_INSTANT.format(createdAt.toInstant(ZoneOffset.UTC)).equals(draft.required("created_at").textValue())) mismatch();
        var metadata = metadata(response, operation.requestSha256());
        if (!metadata.equals(inputs.read(single(writes.draftAudit(values)))) || !metadata.equals(inputs.read(single(writes.draftEvent(values))))) mismatch();
        authorized(admission, (String) values.get("projectId")); return new Reply(201, response);
    }
    private JsonNode metadata(JsonNode response, String requestHash) throws IOException {
        var draft = response.required("data"); var result = json.object(); result.put("schema_version", "1.0.0");
        for (String field : List.of("operation_id", "tenant_id", "project_id", "actor_id", "body_sha256")) result.set(field, draft.required(field));
        result.put("request_id", response.required("request_id").textValue()); result.put("request_sha256", requestHash);
        result.put("response_sha256", DigestUtils.sha256Hex(json.canonical(response))); return result;
    }
    private String operationHash(TaskProtocol.ValidatedCreate input) throws IOException {
        var result = json.object(); result.put("operation_id", "TK-01-DRAFT"); result.set("input", inputs.read(input.canonical_request()));
        return DigestUtils.sha256Hex(json.canonical(result));
    }
    private PermissionProtocol.GrantRow authorized(EnterpriseAccess.Admission admission, String project) throws IOException {
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project);
        resources.require(admission, scope, "project.read", PURPOSE); return resources.require(admission, scope, "task.create", PURPOSE);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission, String project, String key) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("projectId", project);
        values.put("taskId", project); values.put("key", key); values.put("operationName", "TK-01-DRAFT");
        values.put("operationManifest", schema.manifestSha256()); values.put("classification", identity.classification()); return values;
    }
    private static void current(EnterpriseAccess.Admission admission, String context) { if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
