package com.ruoyi.medical.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SimulationRunData {
    public final String run = id(), step = id(), waiting = id(), budget = id(), budgetRevision = id(), stream = id(), event = id();
    private final JdbcTemplate jdbc;
    private final SimulationTaskData task;
    private final String actor;
    private final PermissionSchema json;
    private final TransactionTemplate transaction;

    public SimulationRunData(javax.sql.DataSource source, SimulationTaskData task, String actor, PermissionSchema json, BudgetSchema budgets) {
        this.jdbc = new JdbcTemplate(source); this.task = task; this.actor = actor; this.json = json;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> { initialize(budgets); return null; }).get());
    }

    private void initialize(BudgetSchema budgets) throws Exception {
        String basis = id(), role = id(), grant = id(), action = id();
        version(basis, "SIMULATION_BASIS", DigestUtils.sha256Hex("SIMULATION run reading"));
        version(role, "ROLE_VERSION", DigestUtils.sha256Hex("SIMULATION run role"));
        for (String permission : List.of("run.read", "work_item.read")) {
            var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"),
                "purposes", List.of(permission.equals("run.read") ? "RUN_READING" : "WORK_ITEM_READING"),
                "delegation", row("actions", List.of(), "role_ids", List.of(), "resource_kinds", List.of(), "project_ids", List.of(), "resource_ids", List.of())));
            json.condition(json.canonical(condition));
            insert("permission_grant", schema(metadata(row("grant_id", id(), "subject_id", actor, "project_id", task.project,
                "resource_kind", "TASK", "resource_id", task.first, "action", permission, "condition", json.canonical(condition),
                "status", "ACTIVE", "valid_from", task.updated.minusDays(1), "basis_version", basis, "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        }
        var approval = json.tree(row("schema_version", "1.0.0", "tenant_id", task.tenant, "project_id", task.project, "budget_id", budget,
            "currency", "CNY", "limits", row("amount", "20.00000000", "tokens", "1000", "calls", "10", "research_rounds", "3", "revision_rounds", "3", "deadline_at", "2026-10-10T01:00:00Z"),
            "approval_basis", row("version_id", basis, "content_sha256", DigestUtils.sha256Hex("SIMULATION run reading")),
            "approver_id", actor, "reason", "SIMULATION 运行读取批准", "authority_grant_id", grant, "authority_revision", "1", "based_on_budget_revision", null));
        String definition = json.canonical(approval), digest = DigestUtils.sha256Hex(definition);
        version(budgetRevision, "BUDGET", digest);
        insert("budget_account", metadata(row("project_id", task.project, "budget_id", budget, "currency", "CNY", "spent", "2.50000000",
            "reserved", "1.23000000", "spent_tokens", 10, "reserved_tokens", 20, "spent_calls", 1, "reserved_calls", 1, "revision", 1, "updated_at", task.updated)));
        insert("budget_revision", schema(metadata(row("project_id", task.project, "budget_id", budget, "budget_revision", budgetRevision, "currency", "CNY",
            "limits", definition, "approver_id", actor, "reason", "SIMULATION 运行读取批准", "content_sha256", digest)), budgets.manifestSha256()));
        jdbc.update("UPDATE budget_account SET current_budget_revision=? WHERE tenant_id=? AND budget_id=?", budgetRevision, task.tenant, budget);
        String taskRevision = jdbc.queryForObject("SELECT current_revision FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=?", String.class, task.tenant, task.project, task.first);
        String roleConfig = id();
        insert("configuration_head", metadata(row("project_id", task.project, "config_id", roleConfig, "kind", "CAPABILITY",
            "name", "SIMULATION 运行角色", "status", "DRAFT", "revision", 1, "updated_at", task.updated)));
        insert("capability_version", schema(metadata(row("project_id", task.project, "config_id", roleConfig, "capability_version", role,
            "configuration_kind", "CAPABILITY", "kind", "ROLE", "input_schema_ref", "{}", "output_schema_ref", "{}", "tool_scope", "[]",
            "artifact_digest", DigestUtils.sha256Hex("SIMULATION run role"), "content_sha256", DigestUtils.sha256Hex("SIMULATION run role"),
            "status", "DRAFT", "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        insert("execution_registry", schema(metadata(row("project_id", task.project, "run_id", run, "execution_kind", "TASK", "scope_manifest", json.canonical(json.tree(row("data_kind", "SIMULATION", "task_id", task.first))))), json.manifestSha256()));
        insert("task_run", schema(metadata(row("project_id", task.project, "run_id", run, "execution_kind", "TASK", "task_id", task.first,
            "task_revision", taskRevision, "attempt_no", 1, "workflow_id", task.tenant + "/" + run, "status", "WAITING_INPUT", "replay_manifest", "{\"data_kind\":\"SIMULATION\"}",
            "budget_revision", budgetRevision, "started_at", task.updated, "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        insert("run_step", schema(metadata(row("project_id", task.project, "run_id", run, "step_id", step, "role_version", role,
            "input_sha256", DigestUtils.sha256Hex("SIMULATION step"), "status", "WAITING_INPUT", "lease_generation", 1, "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        insert("step_action_binding", schema(metadata(row("project_id", task.project, "run_id", run, "step_id", step, "action_id", action,
            "input_sha256", DigestUtils.sha256Hex("SIMULATION step"), "origin_run_id", run, "origin_step_id", step, "origin_action_id", action,
            "kind", "ASK", "arguments_sha256", DigestUtils.sha256Hex("SIMULATION question"), "tool_version", "SIMULATION-1", "state", "WAITING_INPUT",
            "lease_generation", 1, "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        insert("waiting_item", schema(metadata(row("project_id", task.project, "waiting_item_id", waiting, "run_id", run, "step_id", step, "action_id", action,
            "target_revision", taskRevision, "reason_code", "SOURCE_NOT_READY", "question", "SIMULATION 请补充项目资料", "requirement_refs", "[]", "allowed_resolution_actions", "[]",
            "assignee_id", actor, "status", "WAITING_INPUT", "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
        String retention = id(); version(retention, "RETENTION_POLICY", DigestUtils.sha256Hex("SIMULATION event retention"));
        insert("stream_registry", metadata(row("project_id", task.project, "stream_id", stream, "kind", "RUN", "owner_resource_id", run,
            "last_sequence", 1, "retention_policy_revision", retention, "revision", 1, "updated_at", task.updated)));
        String payload = json.canonical(json.tree(row("business_event_type", "SIMULATION_RUN_WAITING_INPUT", "status", "WAITING_INPUT", "reason_code", null, "read_operation_id", "RN-02")));
        insert("outbox_event", row("event_id", event, "aggregate_id", run, "aggregate_revision", 1, "event_type", "run.updated", "payload", payload, "occurred_at", task.updated));
        insert("stream_event", schema(metadata(row("project_id", task.project, "event_id", event, "stream_id", stream, "sequence", 1,
            "task_id", task.first, "run_id", run, "resource_kind", "RUN", "resource_id", run, "resource_revision", 1, "event_type", "run.updated",
            "payload", payload, "occurred_at", task.updated)), json.manifestSha256()));
    }

    private void version(String id, String kind, String digest) { insert("version_registry", row("version_id", id, "owner_project_id", task.project, "entity_kind", kind, "content_sha256", digest)); }
    public void enableCancellation() {
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            String basis = id(); version(basis, "SIMULATION_BASIS", DigestUtils.sha256Hex("SIMULATION cancellation authority"));
            var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"), "purposes", List.of("RUN_CANCELLATION"),
                "delegation", row("actions", List.of(), "role_ids", List.of(), "resource_kinds", List.of(), "project_ids", List.of(), "resource_ids", List.of())));
            json.condition(json.canonical(condition));
            insert("permission_grant", schema(metadata(row("grant_id", id(), "subject_id", actor, "project_id", task.project,
                "resource_kind", "TASK", "resource_id", task.first, "action", "task.run", "condition", json.canonical(condition),
                "status", "ACTIVE", "valid_from", task.updated.minusDays(1), "basis_version", basis, "revision", 1, "updated_at", task.updated)), json.manifestSha256()));
            insert("task_active_run", metadata(row("project_id", task.project, "task_id", task.first, "run_id", run, "revision", 1, "updated_at", task.updated)));
            return null;
        }).get());
    }
    public String updateStepAndAppendEvent() {
        String next = id();
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            jdbc.queryForObject("SELECT revision FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? FOR UPDATE", String.class, task.tenant, task.project, run);
            jdbc.update("UPDATE run_step SET revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=?", task.updated.plusSeconds(1), task.tenant, task.project, run, step);
            String payload = json.canonical(json.tree(row("business_event_type", "SIMULATION_STEP_UPDATED", "status", "WAITING_INPUT", "reason_code", null, "read_operation_id", "RN-02")));
            insert("outbox_event", row("event_id", next, "aggregate_id", step, "aggregate_revision", 2, "event_type", "step.updated", "payload", payload, "occurred_at", task.updated.plusSeconds(1)));
            insert("stream_event", schema(metadata(row("project_id", task.project, "event_id", next, "stream_id", stream, "sequence", 2,
                "task_id", task.first, "run_id", run, "resource_kind", "STEP", "resource_id", step, "resource_revision", 2, "event_type", "step.updated",
                "payload", payload, "occurred_at", task.updated.plusSeconds(1))), json.manifestSha256()));
            jdbc.update("UPDATE stream_registry SET last_sequence=2,revision=revision+1,updated_at=? WHERE tenant_id=? AND stream_id=?", task.updated.plusSeconds(1), task.tenant, stream);
            return null;
        }).get());
        return next;
    }
    private Map<String,Object> metadata(Map<String,Object> value) { value.put("created_by", actor); value.put("created_at", task.updated); value.put("classification", "INTERNAL"); return value; }
    private Map<String,Object> schema(Map<String,Object> value, String digest) { value.put("schema_version", "1.0.0"); value.put("schema_manifest_sha256", digest); return value; }
    private void insert(String table, Map<String,Object> values) {
        values.put("tenant_id", task.tenant);
        String columns = String.join(",", values.keySet().stream().map(key -> "`" + key + "`").toList());
        jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Map<String,Object> row(Object... fields) {
        var result = new LinkedHashMap<String,Object>(); for (int index = 0; index < fields.length; index += 2) result.put((String)fields[index], fields[index + 1]); return result;
    }
}
