package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.ProjectMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class ProjectService {
    private final EnterpriseAccess access;
    private final ProjectMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;
    private final PermissionDecisionService permissionDecisions;
    private final PermissionSchema json;
    private final ProjectSchema schema;
    private final TaskService tasks;
    private final IdentityDirectoryPolicy policy;
    private final JdbcTemplate jdbc;

    public ProjectService(EnterpriseAccess access, ProjectMapper mapper, PermissionMapper permissions, PermissionAuthority authority,
            PermissionDecisionService permissionDecisions, PermissionSchema json, ProjectSchema schema,
            IdentityDirectoryPolicy policy, TaskService tasks, @Qualifier("masterDataSource") DataSource master) {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.authority = authority;
        this.permissionDecisions = permissionDecisions; this.json = json; this.schema = schema; this.policy = policy;
        this.tasks = tasks;
        jdbc = new JdbcTemplate(master);
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, ProjectProtocol.Query query) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            var scope = scope(admission, grants, "project.read", "PROJECT_READING");
            if (!scope.available()) denied(ErrorCode.ACCESS_DENIED);
            filters(values, scope, query);
            var count = Objects.requireNonNull(mapper.projectCount(values));
            var ids = PageHelper.startPage(1, query.limit() + 1, false)
                    .<String>doSelectPageInfo(() -> mapper.projects(values)).getList();
            var items = json.object().arrayNode();
            for (String id : ids.subList(0, Math.min(query.limit(), ids.size()))) items.add(item(admission, values, id, grants, false));
            var result = json.object(); result.put("schema_version", "1.0.0"); result.set("projects", items);
            result.put("total", count.toString()); result.set("next_after", json.tree(ids.size() > query.limit() ? ids.get(query.limit()-1) : null));
            return schema.envelope("Listing", result, UUID.randomUUID().toString(), null);
        }));
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String projectId) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            require(admission, grants, projectId, "project.read", "PROJECT_READING");
            var value = item(admission, values, projectId, grants, true);
            return schema.envelope("Project", value, UUID.randomUUID().toString(), value.required("revision").textValue());
        }));
    }

    ProjectProtocol.VerifiedProfile fixedProfile(EnterpriseAccess.Admission admission, String projectId, String profileId, String purpose) throws IOException {
        com.ruoyi.medical.contracts.WireValues.uuid(projectId); com.ruoyi.medical.contracts.WireValues.uuid(profileId);
        var values = values(admission); var grants = mapper.grants(values);
        require(admission, grants, projectId, "project.read", purpose);
        var project = row(values, projectId);
        if (!project.profileRevision().equals(profileId)) denied(ErrorCode.RESULT_VERSION_MISMATCH);
        var profile = profile(values, project);
        return new ProjectProtocol.VerifiedProfile(project, profile.required("content_sha256").textValue(),
                schema.attributes(profile.required("attributes")), schema.bases(profile.required("authority_refs")));
    }

    public JsonNode create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var values = values(admission); var grants = mapper.grants(values);
            var witness = require(admission, grants, null, "project.create", "PROJECT_CREATION");
            var request = schema.create(bytes); basis(request.creation_basis(), null);
            responsible(request.responsible_user_id());
            request(values, admission, "PJ-02", "/api/projects", context, key, request);
            var previous = mapper.currentOperation(values);
            if (!previous.isEmpty()) return replay(admission, values, one(previous, ErrorCode.IDEMPOTENCY_CONFLICT), "CREATE", null, grants);
            current(admission, context);
            String project = UUID.randomUUID().toString(), profile = UUID.randomUUID().toString();
            values.put("projectId", project); values.put("profileId", profile); values.put("name", request.name());
            values.put("responsible", request.responsible_user_id()); values.put("basisId", request.creation_basis().version_id());
            values.put("basisHash", request.creation_basis().content_sha256()); values.put("reason", request.reason());
            witness(values, witness); profileValues(values, request.attributes(), project);
            requireOne(mapper.insertProject(values)); requireOne(mapper.insertVersion(values)); requireOne(mapper.insertProfile(values)); requireOne(mapper.insertRecord(values));
            var stored = row(values, project); profile(values, stored);
            if (!stored.name().equals(request.name()) || !stored.responsibleUserId().equals(request.responsible_user_id())
                    || !stored.creationBasis().equals(request.creation_basis().version_id()) || !stored.creationBasisSha256().equals(request.creation_basis().content_sha256())
                    || !stored.creationReason().equals(request.reason()) || !stored.profileRevision().equals(profile)
                    || !stored.authorityGrant().equals(witness.grantId()) || !stored.authorityRevision().equals(witness.revision())
                    || !stored.createdBy().equals(subject.user().user_id()) || !stored.status().equals("PENDING") || !stored.revision().equals(BigInteger.ONE)) {
                throw new IllegalStateException("PROJECT_CREATE_READ_MISMATCH");
            }
            return complete(admission, values, "CREATE", stored, json.tree(null), json.tree(request), witness, grants, null, null);
        }));
    }

    public JsonNode revise(ApplicationSessionIdentity.Verified subject, String context, String key, String projectId, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var values = values(admission); var grants = mapper.grants(values);
            require(admission, grants, projectId, "project.read", "PROJECT_READING");
            var witness = require(admission, grants, projectId, "profile.confirm", "PROJECT_PROFILE_CONFIRMATION");
            var request = schema.revise(bytes);
            request(values, admission, "PJ-04", "/api/projects/" + projectId + "/profile-revisions", context, key, request);
            var previous = mapper.currentOperation(values);
            if (!previous.isEmpty()) return replay(admission, values, one(previous, ErrorCode.IDEMPOTENCY_CONFLICT), "REVISE", projectId, grants);
            current(admission, context); var before = row(values, projectId); var beforeValue = baseline(before, profile(values, before));
            if (!List.of("PENDING", "ACTIVE").contains(before.status())) denied(ErrorCode.ACCESS_DENIED);
            if (!before.revision().toString().equals(request.expected_revision()) || !before.profileRevision().equals(request.based_on_profile_revision())) denied(ErrorCode.REVISION_CONFLICT);
            PermissionSchema.next(request.expected_revision());
            values.put("expected", request.expected_revision()); values.put("oldProfile", before.profileRevision());
            values.put("oldStatus", before.status()); values.put("newStatus", before.status()); values.put("reason", request.reason());
            values.put("profileId", UUID.randomUUID().toString()); witness(values, witness); profileValues(values, request.attributes(), projectId);
            requireOne(mapper.insertVersion(values)); requireOne(mapper.insertProfile(values));
            requireOne(mapper.advanceProfile(values)); requireOne(mapper.advanceProject(values));
            var stored = row(values, projectId); profile(values, stored);
            if (!stored.profileRevision().equals(values.get("profileId")) || !stored.revision().toString().equals(PermissionSchema.next(request.expected_revision()))) {
                throw new IllegalStateException("PROJECT_PROFILE_READ_MISMATCH");
            }
            return complete(admission, values, "REVISE", stored, beforeValue, json.tree(request), witness, grants, before.profileRevision(), null);
        }));
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key, String projectId, byte[] bytes) {
        return access.execute(subject, admission -> checked(() -> {
            var values = values(admission); var grants = mapper.grants(values);
            require(admission, grants, projectId, "project.read", "PROJECT_READING");
            var witness = require(admission, grants, projectId, "project.manage", "PROJECT_ADMINISTRATION");
            var request = schema.decide(bytes);
            request(values, admission, "PJ-05", "/api/projects/" + projectId + "/state-decisions", context, key, request);
            var previous = mapper.currentOperation(values);
            if (!previous.isEmpty()) return replay(admission, values, one(previous, ErrorCode.IDEMPOTENCY_CONFLICT), request.decision(), projectId, grants);
            current(admission, context); var before = row(values, projectId); var beforeValue = baseline(before, profile(values, before));
            if (!before.revision().toString().equals(request.expected_revision())) denied(ErrorCode.REVISION_CONFLICT);
            String status = nextStatus(before.status(), request.decision()), decision = UUID.randomUUID().toString();
            values.put("expected", request.expected_revision()); values.put("resulting", PermissionSchema.next(request.expected_revision()));
            values.put("oldStatus", before.status()); values.put("newStatus", status); values.put("profileId", before.profileRevision());
            values.put("reason", request.reason()); values.put("decisionId", decision); values.put("decision", request.decision()); witness(values, witness);
            requireOne(mapper.advanceProject(values)); requireOne(mapper.advanceRecord(values));
            var stored = row(values, projectId);
            if (!stored.status().equals(status) || !stored.revision().toString().equals(values.get("resulting"))) throw new IllegalStateException("PROJECT_STATE_READ_MISMATCH");
            return complete(admission, values, request.decision(), stored, beforeValue, json.tree(request), witness, grants, null, decision);
        }));
    }

    public JsonNode waiting(ApplicationSessionIdentity.Verified subject, String context, ProjectProtocol.Query query) {
        return access.execute(subject, admission -> checked(() -> {
            current(admission, context); var values = values(admission); var grants = mapper.grants(values);
            var scope = scope(admission, grants, "work_item.read", "WORK_ITEM_READING");
            if (!scope.available() || query.project() != null && !scope.permits(query.project())) denied(ErrorCode.ACCESS_DENIED);
            filters(values, scope, query); values.put("projectId", query.project());
            values.put("responsible", query.responsible() == null ? subject.user().user_id() : query.responsible());
            var count = Objects.requireNonNull(mapper.waitingCount(values));
            var rows = PageHelper.startPage(1, query.limit() + 1, false)
                    .<ProjectProtocol.Waiting>doSelectPageInfo(() -> mapper.waiting(values)).getList();
            var items = json.object().arrayNode();
            for (var row : rows.subList(0, Math.min(query.limit(), rows.size()))) {
                if (!row.schemaVersion().equals("1.0.0") || !scope.permits(row.projectId())) throw new IllegalStateException("PROJECT_WAITING_SCOPE_MISMATCH");
                var value = json.object(); value.put("waiting_item_id", row.id()); value.set("project_id", json.tree(row.projectId()));
                value.put("run_id", row.runId()); value.put("target_revision", row.targetRevision()); value.put("reason_code", row.reasonCode());
                value.put("question", row.question()); value.set("requirement_refs", schema.read(row.requirements()));
                value.set("allowed_resolution_actions", schema.read(row.resolutionActions())); value.put("assignee_id", row.assigneeId());
                value.put("status", row.status()); value.put("revision", row.revision().toString()); value.put("updated_at", time(row.updatedAt()));
                schema.verify("Waiting", value); items.add(value);
            }
            var result = json.object(); result.put("schema_version", "1.0.0"); result.set("items", items); result.put("total", count.toString());
            result.set("next_after", json.tree(rows.size() > query.limit() ? rows.get(query.limit()-1).id() : null));
            return schema.envelope("WaitingListing", result, UUID.randomUUID().toString(), null);
        }));
    }

    private JsonNode item(EnterpriseAccess.Admission admission, Map<String,Object> values, String id, List<PermissionProtocol.GrantRow> grants, boolean detail) throws IOException {
        require(admission, grants, id, "project.read", "PROJECT_READING"); var row = row(values, id); var profile = profile(values, row);
        var value = json.object(); value.put("schema_version", "1.0.0"); value.put("tenant_id", row.tenantId()); value.put("project_id", id);
        value.put("name", row.name()); value.put("responsible_user_id", row.responsibleUserId()); value.put("status", row.status());
        value.put("revision", row.revision().toString()); value.set("profile", profile);
        value.set("creation_basis", json.tree(new PermissionProtocol.Basis(row.creationBasis(), row.creationBasisSha256())));
        value.put("creation_reason", row.creationReason()); value.put("created_by", row.createdBy()); value.put("created_at", time(row.createdAt()));
        value.put("updated_at", time(row.updatedAt()));
        var actions = json.object().arrayNode(); var allowed = new java.util.HashSet<String>();
        for (String action : List.of("project.read", "profile.confirm", "project.manage", "project.task.read", "task.read", "run.read", "work_item.read")) {
            String purpose = switch (action) {
                case "project.read" -> "PROJECT_READING"; case "profile.confirm" -> "PROJECT_PROFILE_CONFIRMATION";
                case "project.manage" -> "PROJECT_ADMINISTRATION"; case "task.read", "project.task.read" -> "TASK_READING";
                case "run.read" -> "RUN_READING"; default -> "WORK_ITEM_READING";
            };
            if (scope(admission, grants, action, purpose).permits(id)) {
                boolean available = !action.equals("profile.confirm") || List.of("PENDING", "ACTIVE").contains(row.status());
                if (available) { actions.add(action); allowed.add(action); }
            }
        }
        var counts = json.object(); values.put("oldProfile", null);
        counts.set("tasks", json.tree(detail && allowed.contains("project.task.read") ? tasks.visibleCount(admission, id).toString() : null));
        counts.set("active_runs", json.tree(detail && allowed.contains("run.read") ? mapper.runCount(values).toString() : null));
        values.put("enterprise", true); values.put("responsible", null); values.put("status", null); values.put("after", null);
        counts.set("waiting_items", json.tree(detail && allowed.contains("work_item.read") ? mapper.waitingCount(values).toString() : null));
        value.set("children", counts); value.set("allowed_actions", actions); schema.verify("Project", value); return value;
    }

    private ProjectProtocol.Row row(Map<String,Object> values, String id) {
        values.put("projectId", id); var row = one(mapper.project(values), ErrorCode.OBJECT_UNAVAILABLE);
        if (!row.projectId().equals(id) || !row.revision().equals(row.metadataRevision()) || !row.schemaVersion().equals("1.0.0")
                || !row.manifest().equals(schema.manifestSha256()) || row.revision().signum() <= 0
                || !row.tenantId().equals(TenantScope.requireCurrent().tenantId())) throw new IllegalStateException("PROJECT_RECORD_INVALID");
        basis(new PermissionProtocol.Basis(row.creationBasis(), row.creationBasisSha256()), null);
        ProjectSchema.text(row.name(), 256); ProjectSchema.text(row.creationReason(), 2000); return row;
    }

    private JsonNode profile(Map<String,Object> values, ProjectProtocol.Row project) throws IOException {
        values.put("projectId", project.projectId()); values.put("profileId", project.profileRevision());
        var row = one(mapper.profile(values), ErrorCode.CONTENT_MISMATCH);
        var attributes = schema.attributes(schema.read(row.attributes())); var bases = schema.bases(schema.read(row.authorityRefs()));
        var expected = refs(attributes);
        if (!json.tree(expected).equals(json.tree(bases)) || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(schema.manifestSha256())) denied(ErrorCode.CONTENT_MISMATCH);
        for (var basis : bases) basis(basis, project.projectId());
        var content = json.object(); content.set("attributes", json.tree(attributes)); content.set("authority_refs", json.tree(bases));
        String sha = DigestUtils.sha256Hex(json.canonical(content)); String registry = one(mapper.profileRegistry(values), ErrorCode.CONTENT_MISMATCH);
        if (!row.sha256().equals(sha) || !registry.equals(sha)) denied(ErrorCode.CONTENT_MISMATCH);
        var value = content.deepCopy(); value.put("profile_revision", row.profileRevision()); value.put("content_sha256", sha);
        value.put("created_by", row.createdBy()); value.put("created_at", time(row.createdAt())); schema.verify("Profile", value); return value;
    }

    private void profileValues(Map<String,Object> values, List<ProjectProtocol.Attribute> attributes, String projectId) throws IOException {
        var bases = refs(attributes); for (var basis : bases) basis(basis, projectId);
        var content = json.object(); content.set("attributes", json.tree(attributes)); content.set("authority_refs", json.tree(bases));
        values.put("attributes", json.canonicalObject(attributes)); values.put("authorityRefs", json.canonicalObject(bases));
        values.put("contentHash", DigestUtils.sha256Hex(json.canonical(content)));
    }
    private static List<PermissionProtocol.Basis> refs(List<ProjectProtocol.Attribute> attributes) {
        var result = new java.util.TreeMap<String,PermissionProtocol.Basis>();
        for (var attribute : attributes) for (var basis : attribute.authority_refs()) {
            var previous = result.putIfAbsent(basis.version_id(), basis);
            if (previous != null && !previous.equals(basis)) denied(ErrorCode.CONTENT_MISMATCH);
        }
        if (result.size() > 1000) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return List.copyOf(result.values());
    }
    private void basis(PermissionProtocol.Basis basis, String projectId) {
        var version = one(permissions.basis(basis.version_id()), ErrorCode.CONTENT_MISMATCH);
        if (!basis.content_sha256().equals(version.sha256()) || version.ownerProjectId() != null && !version.ownerProjectId().equals(projectId)) denied(ErrorCode.CONTENT_MISMATCH);
    }
    private void responsible(String userId) throws IOException {
        var member = one(permissions.targetMember(userId), ErrorCode.ACCESS_DENIED); var binding = one(permissions.targetBinding(userId), ErrorCode.ACCESS_DENIED);
        LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        if (!member.userId().equals(userId) || !binding.userId().equals(userId) || !member.status().equals("ACTIVE")
                || member.validFrom().isAfter(now) || member.validTo() != null && !member.validTo().isAfter(now)
                || !permissionDecisions.verifyPlatformBinding(binding)) denied(ErrorCode.ACCESS_DENIED);
    }

    private JsonNode complete(EnterpriseAccess.Admission admission, Map<String,Object> values, String decision, ProjectProtocol.Row stored,
            JsonNode before, JsonNode request, PermissionProtocol.GrantRow witness, List<PermissionProtocol.GrantRow> grants, String oldProfile, String decisionId) throws IOException {
        values.put("oldProfile", oldProfile); var internal = impact(values, stored, true, true, oldProfile);
        var visible = impact(values, stored, scope(admission, grants, "task.read", "TASK_READING").permits(stored.projectId()),
                scope(admission, grants, "run.read", "RUN_READING").permits(stored.projectId()), oldProfile);
        values.put("governanceEpoch", admission.context().governance_epoch()); values.put("authEpoch", admission.context().auth_epoch());
        String governance = PermissionSchema.next(admission.context().governance_epoch()), context = PermissionSchema.next(admission.context().context_revision());
        requireOne(mapper.advanceGovernance(values));
        requireOne(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, context, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(), admission.context().tenant_id(),
                admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id()); current.put("context_revision", context);
        current.put("auth_epoch", admission.context().auth_epoch()); current.put("governance_epoch", governance);
        String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.set("decision_id", json.tree(decisionId)); receipt.put("decision", decision); receipt.put("tenant_id", admission.context().tenant_id());
        receipt.put("project_id", stored.projectId()); receipt.put("status", stored.status()); receipt.put("revision", stored.revision().toString());
        receipt.put("profile_revision", stored.profileRevision()); receipt.set("impact", visible); receipt.set("current_context", current);
        var response = schema.envelope("Receipt", receipt, requestId, stored.revision().toString());
        values.put("operationId", operationId); values.put("httpStatus", decision.equals("CREATE") ? 201 : 200);
        values.put("response", json.canonical(response)); values.put("profileId", stored.profileRevision());
        values.put("internalImpact", json.canonical(internal)); requireOne(mapper.saveOperation(values));
        if (decisionId != null) {
            requireOne(mapper.saveDecision(values)); var actual = schema.read(one(mapper.decision(values), ErrorCode.CONTENT_MISMATCH));
            var expected = json.object(); expected.put("decision_id", decisionId); expected.put("operation_id", operationId); expected.put("decision", decision);
            expected.put("from_status", (String) values.get("oldStatus")); expected.put("to_status", stored.status());
            expected.put("expected_revision", (String) values.get("expected")); expected.put("resulting_revision", stored.revision().toString());
            expected.put("profile_revision", stored.profileRevision()); expected.put("reason", (String) values.get("reason")); expected.set("impact", internal);
            expected.put("authority_grant", witness.grantId()); expected.put("authority_revision", witness.revision().toString());
            if (!actual.equals(expected)) throw new IllegalStateException("PROJECT_DECISION_READ_MISMATCH");
        }
        var audit = json.object(); audit.put("schema_version", "1.0.0"); audit.put("operation_id", operationId);
        audit.set("request", request); audit.set("before", before); audit.set("after", receipt); audit.set("actual_impact", internal);
        audit.put("authority_grant_id", witness.grantId()); audit.put("authority_revision", witness.revision().toString());
        String event = decision.equals("CREATE") ? "project.created" : decision.equals("REVISE") ? "project.profile_revised" : "project.state_changed";
        String auditId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
        requireOne(permissions.saveAudit(admission.subject().user().user_id(), stored.projectId(), auditId, event,
                json.canonical(audit), requestId, schema.manifestSha256(), policy.classification()));
        var payload = receipt.deepCopy(); payload.set("impact", internal);
        requireOne(permissions.saveEvent(admission.subject().user().user_id(), eventId, stored.projectId(), stored.revision().toString(), event, json.canonical(payload)));
        values.put("auditId", auditId); values.put("eventId", eventId); values.put("eventType", event); values.put("requestId", requestId); values.put("resulting", stored.revision().toString());
        if (!schema.read(one(mapper.audit(values), ErrorCode.CONTENT_MISMATCH)).equals(audit)
                || !schema.read(one(mapper.event(values), ErrorCode.CONTENT_MISMATCH)).equals(payload)) throw new IllegalStateException("PROJECT_AUDIT_EVENT_READ_MISMATCH");
        var operation = one(mapper.currentOperation(values), ErrorCode.CONTENT_MISMATCH);
        if (!operation.operationId().equals(operationId) || !operation.requestSha256().equals(values.get("requestHash"))
                || !schema.receipt(operation.responseRef()).equals(response)) throw new IllegalStateException("PROJECT_OPERATION_READ_MISMATCH");
        return response;
    }

    private JsonNode impact(Map<String,Object> values, ProjectProtocol.Row project, boolean taskRead, boolean runRead, String oldProfile) {
        var result = json.object(); result.set("old_profile_revision", json.tree(oldProfile)); result.put("new_profile_revision", project.profileRevision());
        var taskCount = Objects.requireNonNull(mapper.taskCount(values)); var runCount = Objects.requireNonNull(mapper.runCount(values));
        var tasks = PageHelper.startPage(1, 1000, false).<String>doSelectPageInfo(() -> mapper.tasks(values)).getList();
        var runs = PageHelper.startPage(1, 1000, false).<ProjectProtocol.Run>doSelectPageInfo(() -> mapper.runs(values)).getList();
        var runValues = json.object().arrayNode();
        for (var run : runs) {
            var value = json.object(); value.put("run_id", run.runId()); value.put("status", run.status()); value.put("revision", run.revision().toString()); runValues.add(value);
        }
        result.set("dependent_tasks", json.tree(taskRead ? taskCount.toString() : null)); result.set("task_ids", json.tree(taskRead ? tasks : null));
        result.set("active_runs", json.tree(runRead ? runCount.toString() : null)); result.set("runs", runRead ? runValues : json.tree(null));
        result.put("complete", taskRead && runRead && taskCount.equals(BigInteger.valueOf(tasks.size())) && runCount.equals(BigInteger.valueOf(runs.size())));
        result.put("new_dispatch", project.status().equals("PENDING") ? "PENDING" : project.status().equals("ACTIVE") ? "REVALIDATE" : "DENIED");
        result.put("inflight", "RECONCILE_ACTUAL_INVOCATIONS"); schema.verify("Impact", result); return result;
    }

    private JsonNode replay(EnterpriseAccess.Admission admission, Map<String,Object> values, SelectionOperationRow saved, String decision,
            String projectId, List<PermissionProtocol.GrantRow> grants) throws IOException {
        if (!saved.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!saved.state().equals("COMPLETED") || !saved.httpStatus().equals(decision.equals("CREATE") ? 201 : 200)
                || !saved.schemaVersion().equals("1.0.0") || !saved.schemaManifestSha256().equals(schema.manifestSha256())) throw new IllegalStateException("PROJECT_OPERATION_INVALID");
        var response = schema.receipt(saved.responseRef()); var receipt = response.required("data");
        if (!saved.operationId().equals(receipt.required("operation_id").textValue()) || !saved.targetId().equals(receipt.required("project_id").textValue())
                || !decision.equals(receipt.required("decision").textValue()) || !admission.context().tenant_id().equals(receipt.required("tenant_id").textValue())
                || projectId != null && !projectId.equals(saved.targetId())) throw new IllegalStateException("PROJECT_OPERATION_INVALID");
        var row = row(values, saved.targetId()); profile(values, row);
        if (!row.status().equals(receipt.required("status").textValue()) || !row.revision().toString().equals(receipt.required("revision").textValue())
                || !row.profileRevision().equals(receipt.required("profile_revision").textValue())) denied(ErrorCode.SCOPE_STALE);
        // 原响应含有子资源身份时，重放同样要求当前对应许可。
        if (!receipt.required("impact").required("task_ids").isNull()) require(admission, grants, row.projectId(), "task.read", "TASK_READING");
        if (!receipt.required("impact").required("runs").isNull()) require(admission, grants, row.projectId(), "run.read", "RUN_READING");
        return response;
    }

    private JsonNode baseline(ProjectProtocol.Row row, JsonNode profile) {
        var result = json.object(); result.put("tenant_id", row.tenantId()); result.put("project_id", row.projectId()); result.put("name", row.name());
        result.put("responsible_user_id", row.responsibleUserId()); result.put("status", row.status()); result.put("revision", row.revision().toString());
        result.set("profile", profile); result.put("created_by", row.createdBy()); result.put("created_at", time(row.createdAt())); result.put("updated_at", time(row.updatedAt()));
        result.set("creation_basis", json.tree(new PermissionProtocol.Basis(row.creationBasis(), row.creationBasisSha256()))); result.put("creation_reason", row.creationReason());
        result.put("authority_grant", row.authorityGrant()); result.put("authority_revision", row.authorityRevision().toString()); return result;
    }

    static String nextStatus(String current, String decision) {
        String result = switch (decision) {
            case "ACTIVATE" -> current.equals("PENDING") ? "ACTIVE" : null;
            case "PAUSE" -> current.equals("ACTIVE") ? "PAUSED" : null;
            case "ARCHIVE" -> List.of("PENDING", "ACTIVE", "PAUSED").contains(current) ? "ARCHIVED" : null;
            case "RESTORE" -> List.of("PAUSED", "ARCHIVED").contains(current) ? "ACTIVE" : null;
            default -> null;
        };
        if (result == null) denied(ErrorCode.REVISION_CONFLICT); return result;
    }
    private ProjectProtocol.Access scope(EnterpriseAccess.Admission admission, List<PermissionProtocol.GrantRow> grants, String action, String purpose) throws IOException {
        return authority.projectAccess(admission.context().tenant_id(), admission.subject().binding().view().mode(), action, purpose, grants);
    }
    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission, List<PermissionProtocol.GrantRow> grants, String project, String action, String purpose) throws IOException {
        return authority.requireProject(admission.context().tenant_id(), admission.subject().binding().view().mode(), project, action, purpose, grants);
    }
    private void request(Map<String,Object> values, EnterpriseAccess.Admission admission, String operation, String path, String context, String key, Object request) throws IOException {
        values.put("operation", operation); values.put("key", key); values.put("requestHash", schema.hash(path, request, context, admission.subject().sessionId()));
    }
    private static void witness(Map<String,Object> values, PermissionProtocol.GrantRow witness) {
        values.put("authorityGrant", witness.grantId()); values.put("authorityRevision", witness.revision().toString());
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var result = new HashMap<String,Object>(); result.put("userId", admission.subject().user().user_id());
        result.put("classification", policy.classification()); result.put("manifest", schema.manifestSha256());
        result.put("now", Objects.requireNonNull(permissions.databaseTime())); return result;
    }
    private static void filters(Map<String,Object> values, ProjectProtocol.Access scope, ProjectProtocol.Query query) {
        values.put("enterprise", scope.enterprise()); values.put("allowedProjects", scope.projects());
        values.put("after", query.after()); values.put("name", query.name()); values.put("status", query.status()); values.put("responsible", query.responsible());
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static void current(EnterpriseAccess.Admission admission, String header) { if (!admission.context().context_revision().equals(header)) denied(ErrorCode.CONTEXT_CHANGED); }
    private static <T> T checked(ThrowingSupplier<T> work) {
        return work.get((message, failure) -> failure instanceof com.fasterxml.jackson.core.JsonProcessingException
                ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException(message, failure));
    }
    private static <T> T one(List<T> values, ErrorCode code) { if (values.size() != 1) denied(code); return Objects.requireNonNull(values.get(0)); }
    private static void requireOne(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
}
