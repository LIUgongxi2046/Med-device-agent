package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SimulationTaskData implements AutoCloseable {
    public final String tenant = id(), otherTenant = id(), project = id(), otherProject = id(), otherOwner = id();
    public final String first = id(), second = id(), hidden = id(), otherProjectTask = id();
    public final String projectGrant = id(), taskGrant = id();
    public final String role = id(), secondRole = id(), roleGrant = id();
    public final LocalDateTime updated = LocalDateTime.of(2026, 10, 7, 1, 0);
    private final JdbcTemplate jdbc;
    private final PermissionSchema json;
    private final TaskInputSchema inputs;
    private final String actor;
    private final Path manifestFile;
    private final String basis = id(), profile = id(), template = id(), release = id();
    private final TransactionTemplate transaction;

    public SimulationTaskData(javax.sql.DataSource dataSource, Path root, String actor,
            PermissionSchema json, TaskInputSchema inputs) throws Exception {
        this.jdbc = new JdbcTemplate(dataSource); this.actor = actor; this.json = json; this.inputs = inputs;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        manifestFile = root.resolve(".implementation-work/task-simulation/" + tenant + "/manifest.json");
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, json.canonical(json.tree(Map.of("data_kind", "SIMULATION",
            "purpose", "TASK_READING_ACCEPTANCE", "tenant_id", tenant))), StandardCharsets.UTF_8);
        transaction.execute(status -> org.springframework.util.function.ThrowingSupplier.of(() -> { seed(); return null; }).get());
    }

    private void seed() throws Exception {
        insert("tenant", row("tenant_id", tenant, "auth_epoch", 1, "governance_epoch", 1));
        insert("tenant", row("tenant_id", otherTenant, "auth_epoch", 1, "governance_epoch", 1));
        for (String user : List.of(actor, otherOwner)) {
            String member = id();
            insert("tenant_member", metadata(row("tenant_id", tenant, "member_id", member, "user_id", user,
                "status", "ACTIVE", "valid_from", updated.minusDays(1), "revision", 1, "updated_at", updated)));
            if (user.equals(actor)) {
                insert("identity_binding", metadata(row("tenant_id", tenant, "identity_id", id(), "user_id", user,
                    "mode", "local", "local_user_id", user, "status", "ACTIVE", "revision", 1, "updated_at", updated)));
                insert("platform_member_route", metadata(row("tenant_id", tenant, "member_id", member,
                    "user_id", user, "revision", 1, "updated_at", updated)));
            }
        }
        insert("tenant_member", metadata(row("tenant_id", otherTenant, "member_id", id(), "user_id", actor,
            "status", "ACTIVE", "valid_from", updated.minusDays(1), "revision", 1, "updated_at", updated)));
        for (String value : List.of(project, otherProject))
            insert("project", row("tenant_id", tenant, "project_id", value, "status", "ACTIVE", "revision", 1));
        insert("project", row("tenant_id", otherTenant, "project_id", project, "status", "ACTIVE", "revision", 1));
        version(basis, null, "SIMULATION_BASIS", hash(json.tree(Map.of("data_kind", "SIMULATION"))));
        grant(projectGrant, project, "PROJECT", project, "project.task.read");
        grant(taskGrant, project, "TASK", first, "task.read");
        grant(id(), project, "TASK", second, "task.read");
        var projects = new ProjectSchema(json);
        var profileContent = json.tree(row("attributes", List.of(), "authority_refs", List.of()));
        version(profile, project, "PROJECT_PROFILE", hash(profileContent));
        var profileRow = schema(metadata(row("tenant_id", tenant, "project_id", project,
            "profile_revision", profile, "attributes", "[]", "authority_refs", "[]", "content_sha256", hash(profileContent))));
        profileRow.put("schema_manifest_sha256", projects.manifestSha256()); insert("project_profile_revision", profileRow);
        var projectRow = schema(metadata(row("tenant_id", tenant, "project_id", project, "name", "SIMULATION 项目任务验收",
            "responsible_user_id", actor, "current_profile_revision", profile, "creation_basis", basis,
            "creation_basis_sha256", hash(json.tree(Map.of("data_kind", "SIMULATION"))), "creation_reason", "SIMULATION 任务读取验收",
            "authority_grant", projectGrant, "authority_revision", 1, "revision", 1, "updated_at", updated)));
        projectRow.put("schema_manifest_sha256", projects.manifestSha256()); insert("project_record", projectRow);
        grant(id(), project, "PROJECT", project, "project.read", "PROJECT_READING");
        version(template, null, "TEMPLATE_VERSION", hash(json.object()));
        insert("template_version", schema(metadata(row("tenant_id", tenant, "template_version", template,
            "document_type", "OVERVIEW", "application_path", "INITIAL", "status", "DRAFT", "basis_manifest", "[]",
            "format_profile", "{}", "condition_schema", "{}", "content_sha256", hash(json.object())))));
        String config = id(), agent = id(), accessSet = id(), artifact = id();
        version(agent, project, "AGENT_VERSION", hash(json.object()));
        insert("configuration_head", metadata(row("tenant_id", tenant, "project_id", project, "config_id", config,
            "kind", "AGENT", "name", "SIMULATION task reading", "status", "DRAFT", "revision", 1, "updated_at", updated)));
        insert("agent_version", schema(metadata(row("tenant_id", tenant, "project_id", project, "agent_version_id", agent,
            "agent_id", config, "configuration_kind", "AGENT", "name", "SIMULATION task reading", "status", "DRAFT",
            "dependency_manifest", "[]", "limits", "{}", "content_sha256", hash(json.object()), "revision", 1, "updated_at", updated))));
        String emptyHash = hash(json.tree(List.of()));
        version(accessSet, project, "DEPENDENCY_ACCESS_SET", emptyHash);
        insert("dependency_access_set", metadata(row("tenant_id", tenant, "project_id", project, "access_set_id", accessSet,
            "manifest_sha256", emptyHash, "content_sha256", emptyHash)));
        byte[] bytes = Files.readAllBytes(manifestFile); String manifestHash = DigestUtils.sha256Hex(bytes);
        version(artifact, project, "ARTIFACT", manifestHash);
        var reference = row("object_key", rootRelative(manifestFile), "object_version", "SIMULATION-1", "sha256", manifestHash,
            "size_bytes", bytes.length, "media_type", "application/json");
        insert("artifact", schema(metadata(row("tenant_id", tenant, "project_id", project, "artifact_id", artifact,
            "artifact_kind", "SIMULATION_MANIFEST", "object_ref", json.canonical(json.tree(reference)),
            "object_key", reference.get("object_key"), "object_version", reference.get("object_version"), "sha256", manifestHash,
            "size_bytes", bytes.length, "media_type", "application/json", "dependency_access_set_id", accessSet, "content_sha256", manifestHash))));
        version(release, project, "AGENT_RELEASE", manifestHash);
        insert("release_record", schema(metadata(row("tenant_id", tenant, "project_id", project, "release_id", release,
            "agent_version_id", agent, "manifest_artifact_id", artifact, "manifest", new String(bytes, StandardCharsets.UTF_8),
            "manifest_sha256", manifestHash, "content_sha256", manifestHash, "approved_by", actor, "released_at", updated))));
        task(first, "NEEDS_MATERIALS", actor, updated, true);
        task(second, "PLAN_PENDING", otherOwner, updated.minusSeconds(1), false);
        head(tenant, project, hidden, "PLAN_PENDING", actor, updated.plusSeconds(1));
        head(tenant, otherProject, otherProjectTask, "PLAN_PENDING", actor, updated.plusSeconds(2));
        // 相同标识在另一个企业存在，用实际记录核验企业隔离。
        head(otherTenant, project, first, "PLAN_PENDING", actor, updated.plusSeconds(3));
    }

    private void task(String task, String status, String owner, LocalDateTime time, boolean missing) throws Exception {
        String revision = id(), snapshot = id(), operation = id();
        head(tenant, project, task, status, owner, time);
        var scope = json.tree(row("mode", "SOURCE_VERSIONS", "collection_versions", List.of(), "source_versions", List.of(),
            "network_enabled", false, "network_policy_revision", null, "approved_query_revision", null));
        var request = json.tree(row("schema_version", "1.0.0", "project_id", project, "profile_revision", profile,
            "document_type", "OVERVIEW", "template_version", template, "agent_release_id", release,
            "scope_selection", scope, "owner_id", owner, "goal", "SIMULATION 任务读取验收 " + task));
        var validated = inputs.create(json.canonical(request).getBytes(StandardCharsets.UTF_8));
        var manifest = json.tree(row("schema_version", "1.0.0", "tenant_id", tenant, "project_id", project,
            "snapshot_id", snapshot, "profile_revision", profile, "scope_selection", scope,
            "auth_epoch", "1", "governance_epoch", "1", "members", List.of()));
        version(snapshot, project, "SCOPE_SNAPSHOT", hash(manifest));
        insert("scope_snapshot", row("tenant_id", tenant, "project_id", project, "snapshot_id", snapshot,
            "manifest", json.canonical(manifest), "manifest_sha256", hash(manifest), "auth_epoch", 1,
            "governance_epoch", 1, "created_at", updated));
        version(revision, project, "TASK_REVISION", validated.request_sha256());
        insert("task_revision", metadata(row("tenant_id", tenant, "project_id", project, "task_id", task, "task_revision", revision,
            "profile_revision", profile, "snapshot_id", snapshot, "template_version", template, "agent_release_id", release,
            "reason", "SIMULATION 任务读取验收", "content_sha256", validated.request_sha256())));
        insert("operation_record", schema(metadata(row("tenant_id", tenant, "operation_id", operation, "subject_id", actor,
            "operation_name", "SIMULATION_TASK_INPUT", "idempotency_key", operation, "request_sha256", validated.request_sha256(),
            "state", "COMPLETED", "http_status", 201, "target_id", task, "revision", 1, "updated_at", updated))));
        var requirements = json.tree(missing ? List.of(row("requirement_id", "SIMULATION_REQUIRED_SOURCE", "node_id", null,
            "field_id", null, "reason_code", "SOURCE_NOT_READY", "allowed_actions", List.of("task.read", "task.plan.confirm"))) : List.of());
        insert("task_input_revision", metadata(row("tenant_id", tenant, "project_id", project, "task_id", task, "task_revision", revision,
            "operation_id", operation, "document_type", "OVERVIEW", "owner_id", owner, "goal", request.required("goal").textValue(),
            "request_json", validated.canonical_request(), "request_sha256", validated.request_sha256(), "scope_json", json.canonical(scope),
            "scope_sha256", validated.scope_sha256(), "authority_grant", projectGrant, "authority_revision", 1, "auth_epoch", 1,
            "governance_epoch", 1, "schema_version", "1.0.0", "schema_manifest_sha256", inputs.manifestSha256(),
            "missing_requirements", json.canonical(requirements), "requirements_sha256", hash(requirements))));
        jdbc.update("UPDATE task_head SET current_revision=? WHERE tenant_id=? AND project_id=? AND task_id=?", revision, tenant, project, task);
    }

    public void revokeFirst() { jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND grant_id=?", tenant, taskGrant); }

    public void enableTaskCreation() {
        transaction.executeWithoutResult(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            grant(id(), project, "PROJECT", project, "task.create", "TASK_CREATION");
            grant(id(), project, "PROJECT", project, "project.read", "TASK_CREATION");
            grant(id(), project, "PROJECT", project, "task.read", "TASK_READING");
            return null;
        }).get());
    }
    public void revokeTaskCreation() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='task.create'", tenant);
    }
    public void revokeCreationOwnerRead() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND subject_id=? AND project_id=? AND action='task.read' AND resource_kind='PROJECT'",
                tenant, actor, project);
    }
    public void updateSecond() { jdbc.update("UPDATE task_head SET revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND task_id=?", tenant, project, second); }
    public void revokeProject() { jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND grant_id=?", tenant, projectGrant); }

    public JsonNode projectBasis() throws Exception {
        return json.tree(row("version_id", basis, "content_sha256", hash(json.tree(Map.of("data_kind", "SIMULATION")))));
    }

    public void enableProjectAdministration() {
        transaction.execute(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            for (var entry : Map.of("project.create", "PROJECT_CREATION", "project.read", "PROJECT_READING",
                    "profile.confirm", "PROJECT_PROFILE_CONFIRMATION", "project.manage", "PROJECT_ADMINISTRATION",
                    "task.read", "TASK_READING", "run.read", "RUN_READING", "work_item.read", "WORK_ITEM_READING").entrySet())
                grant(id(), null, "ENTERPRISE", tenant, entry.getKey(), entry.getValue());
            return null;
        }).get());
    }

    public void revokeProjectReading() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='project.read'", tenant);
    }

    public void enableSourceUploading() throws Exception {
        grant(id(), project, "PROJECT", project, "source.write", "SOURCE_UPLOAD");
    }

    public void enablePlanDecisions() throws Exception {
        for (String task : List.of(first, second)) grant(id(), project, "TASK", task, "plan.confirm", "PLAN_MAINTENANCE");
    }

    public void revokePlanDecisions() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND action='plan.confirm'", tenant);
    }

    public void enableOtherTenantSelection() {
        transaction.executeWithoutResult(status -> {
            String member = jdbc.queryForObject("SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=?", String.class, otherTenant, actor);
            insert("identity_binding", metadata(row("tenant_id", otherTenant, "identity_id", id(), "user_id", actor,
                "mode", "local", "local_user_id", actor, "status", "ACTIVE", "revision", 1, "updated_at", updated)));
            insert("platform_member_route", metadata(row("tenant_id", otherTenant, "member_id", member,
                "user_id", actor, "revision", 1, "updated_at", updated)));
        });
    }

    public void enableRoleAdministration() throws Exception {
        enableRoleAdministration(List.of("project.read", "task.read"));
    }

    public void enableRoleAdministration(List<String> actions) throws Exception {
        var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"), "purposes", null,
            "delegation", row("actions", actions, "role_ids", List.of(role, secondRole),
                "resource_kinds", List.of("ENTERPRISE", "PROJECT", "TASK"), "project_ids", null, "resource_ids", null)));
        json.condition(json.canonical(condition));
        insert("permission_grant", schema(metadata(row("tenant_id", tenant, "grant_id", roleGrant, "subject_id", actor,
            "resource_kind", "ENTERPRISE", "resource_id", tenant, "action", "role.manage", "condition", json.canonical(condition),
            "status", "ACTIVE", "valid_from", updated.minusDays(1), "basis_version", basis, "revision", 1, "updated_at", updated))));
    }

    public void revokeRoleAdministration() {
        jdbc.update("UPDATE permission_grant SET status='REVOKED',revision=revision+1 WHERE tenant_id=? AND grant_id=?", tenant, roleGrant);
    }

    public void enableMemberAdministration() throws Exception {
        var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"), "purposes", null,
            "delegation", row("actions", List.of("project.read", "project.task.read", "task.read"), "role_ids", List.of(role, secondRole),
                "resource_kinds", List.of("ENTERPRISE", "PROJECT", "TASK", "MEMBER"), "project_ids", null, "resource_ids", null)));
        json.condition(json.canonical(condition));
        insert("permission_grant", schema(metadata(row("tenant_id", tenant, "grant_id", id(), "subject_id", actor,
            "resource_kind", "ENTERPRISE", "resource_id", tenant, "action", "member.manage", "condition", json.canonical(condition),
            "status", "ACTIVE", "valid_from", updated.minusDays(1), "basis_version", basis, "revision", 1, "updated_at", updated))));
    }

    public void enableProjectGrantAdministration(String user) throws Exception {
        var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"),
            "purposes", List.of("PERMISSION_ADMINISTRATION"), "delegation", row("actions", List.of("project.read"),
            "role_ids", List.of(), "resource_kinds", List.of("PROJECT"), "project_ids", List.of(project), "resource_ids", null)));
        governanceGrant(user, "project.grant", project, "PROJECT", project, condition);
    }

    public void enableScopedMemberAdministration(String user, String member) throws Exception {
        var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"),
            "purposes", List.of("PERMISSION_ADMINISTRATION"), "delegation", row("actions", List.of(),
            "role_ids", List.of(), "resource_kinds", List.of("MEMBER"), "project_ids", null, "resource_ids", List.of(member))));
        governanceGrant(user, "member.manage", null, "MEMBER", member, condition);
    }

    private void governanceGrant(String user, String action, String ownerProject, String kind, String resource, JsonNode condition) throws Exception {
        json.condition(json.canonical(condition));
        insert("permission_grant", schema(metadata(row("tenant_id", tenant, "grant_id", id(), "subject_id", user,
            "project_id", ownerProject, "resource_kind", kind, "resource_id", resource, "action", action,
            "condition", json.canonical(condition), "status", "ACTIVE", "valid_from", updated.minusDays(1),
            "basis_version", basis, "revision", 1, "updated_at", updated))));
    }

    public void addMembers(int count) {
        if (count < 0 || count > 100) throw new IllegalArgumentException("SIMULATION_MEMBER_COUNT_INVALID");
        transaction.execute(status -> {
            for (int index = 0; index < count; index++) insert("tenant_member", metadata(row("tenant_id", tenant,
                "member_id", id(), "user_id", id(), "status", "INVITED", "valid_from", updated,
                "revision", 1, "updated_at", updated)));
            return null;
        });
    }

    public void addTasks(int count) {
        if (count < 0 || count > 100) throw new IllegalArgumentException("SIMULATION_TASK_COUNT_INVALID");
        transaction.execute(status -> org.springframework.util.function.ThrowingSupplier.of(() -> {
            for (int index = 0; index < count; index++) {
                String task = id(); task(task, "PLAN_PENDING", otherOwner, updated.minusSeconds(index + 2), false);
                grant(id(), project, "TASK", task, "task.read");
            }
            return null;
        }).get());
    }

    private void head(String tenantId, String projectId, String task, String status, String owner, LocalDateTime time) {
        insert("task_head", metadata(row("tenant_id", tenantId, "project_id", projectId, "task_id", task, "status", status,
            "owner_id", owner, "revision", 1, "updated_at", time)));
    }
    private void grant(String id, String projectId, String kind, String resource, String action) throws Exception {
        grant(id, projectId, kind, resource, action, "TASK_READING");
    }
    private void grant(String id, String projectId, String kind, String resource, String action, String purpose) throws Exception {
        var condition = json.tree(row("schema_version", "1.0.0", "identity_modes", List.of("local"), "purposes", List.of(purpose),
            "delegation", row("actions", List.of(), "role_ids", List.of(), "resource_kinds", List.of(), "project_ids", List.of(), "resource_ids", List.of())));
        json.condition(json.canonical(condition));
        insert("permission_grant", schema(metadata(row("tenant_id", tenant, "grant_id", id, "subject_id", actor,
            "project_id", projectId, "resource_kind", kind, "resource_id", resource, "action", action,
            "condition", json.canonical(condition), "status", "ACTIVE", "valid_from", updated.minusDays(1),
            "basis_version", basis, "revision", 1, "updated_at", updated))));
    }
    private void version(String version, String projectId, String kind, String digest) {
        insert("version_registry", row("tenant_id", tenant, "version_id", version, "owner_project_id", projectId,
            "entity_kind", kind, "content_sha256", digest));
    }
    private Map<String,Object> metadata(Map<String,Object> values) {
        values.put("created_by", actor); values.put("created_at", updated); values.put("classification", "INTERNAL"); return values;
    }
    private Map<String,Object> schema(Map<String,Object> values) {
        values.put("schema_version", "1.0.0"); values.put("schema_manifest_sha256", json.manifestSha256()); return values;
    }
    private void insert(String table, Map<String,Object> values) {
        String columns = String.join(",", values.keySet().stream().map(key -> "`" + key + "`").toList());
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", values.values().toArray());
    }
    private String hash(JsonNode value) throws Exception { return DigestUtils.sha256Hex(json.canonical(value)); }
    private static String rootRelative(Path file) { return ".implementation-work/task-simulation/" + file.getParent().getFileName() + "/manifest.json"; }
    private static String id() { return UUID.randomUUID().toString(); }
    private static Map<String,Object> row(Object... fields) {
        if (fields.length % 2 != 0) throw new IllegalArgumentException("SIMULATION_COLUMNS_INVALID");
        var result = new LinkedHashMap<String,Object>();
        for (int index = 0; index < fields.length; index += 2) result.put((String) fields[index], fields[index + 1]);
        return result;
    }

    @Override public void close() throws Exception {
        transaction.executeWithoutResult(status -> {
            // 仅清理本次生成的企业；保留当前其他会话与全部既有业务数据。
            jdbc.update("DELETE FROM platform_session_context WHERE tenant_id IN (?,?)", tenant, otherTenant);
            jdbc.update("UPDATE task_head SET current_revision=NULL WHERE tenant_id IN (?,?)", tenant, otherTenant);
            for (String table : List.of("source_upload_request", "object_upload_part", "object_upload_session", "upload_part", "upload_item"))
                jdbc.update("DELETE FROM " + table + " WHERE tenant_id IN (?,?)", tenant, otherTenant);
            for (String table : List.of("task_input_revision", "task_revision", "task_head", "scope_snapshot", "release_record",
                    "agent_version", "configuration_head", "artifact", "dependency_access_set", "template_version", "project_state_decision", "project_record", "project_profile_revision",
                    "enterprise_role_head", "member_role_grant", "platform_invitation_route", "member_invitation", "enterprise_role_publication", "enterprise_role_version",
                    "audit_event", "consumer_receipt", "outbox_delivery", "outbox_event", "operation_record", "permission_grant", "version_registry", "project",
                    "platform_member_route", "identity_binding", "tenant_member", "tenant"))
                jdbc.update("DELETE FROM " + table + " WHERE tenant_id IN (?,?)", tenant, otherTenant);
        });
        Files.delete(manifestFile); Files.delete(manifestFile.getParent());
    }
}
