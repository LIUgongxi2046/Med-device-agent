package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.RunReadMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class RunReadService {
    private final EnterpriseAccess access;
    private final RunReadMapper mapper;
    private final ResourceReadAuthority resources;
    private final TaskService tasks;
    private final PermissionSchema json;
    private final BudgetSchema budgets;
    private final SchemaCatalogue shared;
    private final com.networknt.schema.JsonSchema schema;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader = com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public RunReadService(EnterpriseAccess access, RunReadMapper mapper, ResourceReadAuthority resources,
            TaskService tasks, PermissionSchema json, BudgetSchema budgets) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.mapper = mapper; this.resources = resources; this.tasks = tasks;
        this.json = json; this.budgets = budgets; shared = SchemaCatalogue.fromClasspath();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/run-view.schema.json"))) {
            schema = shared.compileApiSchema(reader.readTree(input));
        }
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String runId) {
        WireValues.uuid(runId); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> snapshot(admission, context, runId)).get());
    }

    JsonNode snapshot(EnterpriseAccess.Admission admission, String context, String runId) throws IOException {
        if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("runId", runId);
        // 运行及全部子资源写入均持有原运行锁，快照和事件游标共享同一读取事务。
        var head = reader.readTree(one(mapper.head(values), ErrorCode.ACCESS_DENIED));
        String tenant = admission.context().tenant_id(), project = text(head, "project_id"), task = text(head, "task_id");
        WireValues.uuid(project); WireValues.uuid(task); WireValues.uuid(text(head, "task_revision"));
        WireValues.uuid(text(head, "budget_revision")); WireValues.hash(text(head, "schema_manifest"));
        if (!tenant.equals(text(head, "tenant_id")) || !runId.equals(text(head, "run_id"))
                || !"TASK".equals(text(head, "execution_kind")) || !"TASK".equals(text(head, "registry_kind"))
                || !project.equals(text(head, "registry_project_id")) || !"1.0.0".equals(text(head, "schema_version"))) mismatch();
        var scope = new PermissionProtocol.Scope(tenant, project, "TASK", task);
        var grant = resources.require(admission, scope, "run.read", "RUN_READING");
        var currentTask = tasks.fixedTask(admission, task);
        if (!project.equals(currentTask.head().projectId())) mismatch();
        values.put("projectId", project); values.put("budgetRevision", text(head, "budget_revision"));
        var result = json.object();
        for (String name : List.of("run_id", "task_id", "task_revision", "status", "reason_code", "started_at", "ended_at", "revision"))
            result.set(name, head.required(name));
        var steps = rows(mapper.steps(values), "step_id"); result.set("steps", steps);
        var stepIds = new HashSet<String>(); for (var step : steps) stepIds.add(text(step, "step_id"));
        var waiting = json.object().arrayNode();
        if (resources.find(admission, scope, "work_item.read", "WORK_ITEM_READING").isPresent()) {
            waiting = rows(mapper.waiting(values), "waiting_item_id");
            for (var item : waiting) {
                if (!stepIds.contains(text(item, "step_id"))) mismatch();
                var actions = json.object().arrayNode();
                for (var action : item.required("allowed_resolution_actions")) {
                    if (!action.isTextual()) mismatch();
                    if (resources.find(admission, scope, action.textValue(), "WORK_ITEM_READING").isPresent()) actions.add(action.textValue());
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) item).set("allowed_resolution_actions", actions);
            }
        }
        result.set("waiting_items", waiting);
        var invocations = rows(mapper.invocations(values), "invocation_id");
        for (var invocation : invocations) {
            if (!stepIds.contains(text(invocation, "step_id"))) mismatch();
            // 调用结果和费用结算分别读取，未知金额保持原空值。
            if (!invocation.required("settled_amount").isNull()) BudgetSchema.money(text(invocation, "settled_amount"));
            if (!invocation.required("total_tokens").isNull()) WireValues.revision(text(invocation, "total_tokens"));
        }
        result.set("inflight_invocations", invocations); result.set("budget", budget(admission, values, project));
        var cursors = mapper.cursor(values);
        if (cursors.isEmpty()) result.putNull("event_cursor");
        else {
            var cursor = reader.readTree(one(cursors, ErrorCode.CONTENT_MISMATCH)); WireValues.uuid(text(cursor, "stream_id"));
            var sequence = WireValues.revision(text(cursor, "sequence"));
            if (sequence.signum() == 0) { if (!cursor.required("event_id").isNull()) mismatch(); result.putNull("event_cursor"); }
            else { WireValues.uuid(text(cursor, "event_id")); result.set("event_cursor", cursor.required("event_id")); }
        }
        if (!schema.validate(result).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        WireValues.revision(text(result, "revision"));
        for (var step : steps) { WireValues.revision(text(step, "revision")); WireValues.revision(text(step, "lease_generation")); }
        if (!grant.equals(resources.require(admission, scope, "run.read", "RUN_READING"))) fail(ErrorCode.SCOPE_STALE);
        var envelope = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(), text(result, "revision"), result));
        shared.validate("interfaces", envelope, "Envelope"); return envelope;
    }

    private JsonNode budget(EnterpriseAccess.Admission admission, HashMap<String,Object> values, String project) throws IOException {
        var row = reader.readTree(one(mapper.budget(values), ErrorCode.CONTENT_MISMATCH));
        String content = json.canonical(row.required("approval")); var approval = budgets.approval(content);
        String digest = DigestUtils.sha256Hex(content);
        if (!admission.context().tenant_id().equals(text(row, "tenant_id"))
                || !(row.required("project_id").isNull() || project.equals(text(row, "project_id")))
                || !admission.context().tenant_id().equals(approval.tenant_id())
                || !Objects.equals(approval.project_id(), row.required("project_id").isNull() ? null : text(row, "project_id"))
                || !approval.budget_id().equals(text(row, "budget_id")) || !values.get("budgetRevision").equals(text(row, "budget_revision"))
                || !approval.currency().equals(text(row, "currency")) || !approval.currency().equals(text(row, "account_currency"))
                || !approval.approver_id().equals(text(row, "approver_id")) || !approval.reason().equals(text(row, "reason"))
                || !"1.0.0".equals(text(row, "schema_version")) || !budgets.manifestSha256().equals(text(row, "schema_manifest"))
                || !digest.equals(text(row, "content_sha256")) || !digest.equals(text(row, "registry_sha256"))) mismatch();
        var result = json.object();
        for (String name : List.of("budget_id", "budget_revision", "currency", "spent", "reserved")) result.set(name, row.required(name));
        BudgetSchema.money(text(row, "spent")); BudgetSchema.money(text(row, "reserved"));
        result.put("limit", approval.limits().amount()); return result;
    }

    private ArrayNode rows(List<String> values, String identity) throws IOException {
        if (values.size() > 10000) fail(ErrorCode.CONTEXT_LIMIT);
        var result = json.object().arrayNode(); var ids = new HashSet<String>();
        for (String value : values) { var row = reader.readTree(value); String id = text(row, identity); WireValues.uuid(id);
            if (!ids.add(id)) mismatch(); result.add(row); }
        return result;
    }
    private static String text(JsonNode value, String name) {
        var field = value.required(name); if (!field.isTextual()) mismatch(); return field.textValue();
    }
    private static <T> T one(List<T> rows, ErrorCode code) { if (rows.size() != 1) fail(code); return rows.get(0); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
}
