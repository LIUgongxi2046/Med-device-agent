package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.ResourceBindings;
import com.ruoyi.medical.contracts.ResourceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.github.pagehelper.PageHelper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskService {
    private final EnterpriseAccess access;
    private final TaskMapper mapper;
    private final ResourceReadAuthority resources;
    private final TaskInputSchema inputs;
    private final PermissionSchema json;
    private final ScopeSnapshotSchema snapshots;
    private final SchemaCatalogue shared;
    private final TaskListSchema listing;
    private final PermissionMapper permissions;
    private final PermissionAuthority authority;

    public TaskService(EnterpriseAccess access, TaskMapper mapper, ResourceReadAuthority resources,
            TaskInputSchema inputs, PermissionSchema json, ScopeSnapshotSchema snapshots, TaskListSchema listing,
            PermissionMapper permissions, PermissionAuthority authority) throws IOException, java.security.NoSuchAlgorithmException {
        this.access = access; this.mapper = mapper; this.resources = resources; this.inputs = inputs; this.json = json;
        this.snapshots = snapshots;
        this.listing = listing; this.permissions = permissions; this.authority = authority;
        shared = SchemaCatalogue.fromClasspath();
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String taskId) {
        return access.execute(subject, admission -> {
            ThrowingSupplier<JsonNode> operation = () -> detail(admission, context, taskId);
            return operation.get((message, failure) -> new IllegalStateException(message, failure));
        });
    }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context, String projectId, TaskProtocol.Query query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED);
            var project = new PermissionProtocol.Scope(admission.context().tenant_id(), projectId, "PROJECT", projectId);
            var projectGrant = resources.require(admission, project, "project.task.read", "TASK_READING");
            String user = subject.user().user_id();
            var selection = selection(admission, projectId);
            var values = listValues(user, projectId, query, selection);
            var number = Objects.requireNonNull(mapper.datasetRevision(values)).add(admission.watermarks().authEpoch())
                .add(admission.watermarks().governanceEpoch());
            if (number.signum() < 0 || number.bitLength() > 64) fail(ErrorCode.REVISION_LIMIT);
            String watermark = number.toString(), filter = listHash(admission, projectId, query, projectGrant, selection);
            if (query.watermark() != null && (!watermark.equals(query.watermark()) || !filter.equals(query.filterSha256()))) fail(ErrorCode.REVISION_CONFLICT);
            // 原PageHelper共同执行授权范围内计数、分页与线程状态清理。
            var page = PageHelper.startPage(query.page(), query.pageSize()).<String>doSelectPageInfo(() -> mapper.listed(values));
            if (page.getTotal() < 0) fail(ErrorCode.REVISION_LIMIT);
            var items = json.object().arrayNode();
            for (String id : page.getList()) {
                var state = detailState(admission, context, id);
                var value = state.envelope().required("data");
                if (!projectId.equals(value.required("project_id").textValue())) mismatch();
                var item = json.object(); item.set("detail", value);
                item.put("goal", state.input().goal()); item.put("document_type", state.input().documentType());
                item.put("updated_at", state.head().updatedAt().toInstant(java.time.ZoneOffset.UTC).toString());
                items.add(item);
            }
            // 到期许可与当前身份继续核验；变化时不返回部分分页结果。
            var currentProjectGrant = resources.require(admission, project, "project.task.read", "TASK_READING");
            var currentSelection = selection(admission, projectId);
            if (!filter.equals(listHash(admission, projectId, query, currentProjectGrant, currentSelection))) fail(ErrorCode.REVISION_CONFLICT);
            var info = json.object(); info.put("page", query.page()); info.put("page_size", query.pageSize());
            info.put("total", Long.toString(page.getTotal())); info.put("watermark", watermark); info.put("filter_sha256", filter);
            var result = json.object(); result.set("items", items); result.set("page_info", info);
            return listing.envelope(result, UUID.randomUUID().toString(), watermark);
        }).get());
    }

    java.math.BigInteger visibleCount(EnterpriseAccess.Admission admission, String projectId) throws IOException {
        var project = new PermissionProtocol.Scope(admission.context().tenant_id(), projectId, "PROJECT", projectId);
        var grant = resources.require(admission, project, "project.task.read", "TASK_READING");
        var selected = selection(admission, projectId);
        var values = listValues(admission.subject().user().user_id(), projectId,
            new TaskProtocol.Query(1, 1, null, null, null, null, null, null), selected);
        // 项目展示与列表采用同一授权范围；内部治理依赖继续使用其原完整集合。
        var count = Objects.requireNonNull(mapper.visibleCount(values));
        if (count.signum() < 0 || count.bitLength() > 64) fail(ErrorCode.REVISION_LIMIT);
        if (!grant.equals(resources.require(admission, project, "project.task.read", "TASK_READING"))
                || !selected.equals(selection(admission, projectId))) fail(ErrorCode.REVISION_CONFLICT);
        return count;
    }

    private PermissionAuthority.ResourceSelection selection(EnterpriseAccess.Admission admission, String projectId) throws IOException {
        String user = admission.subject().user().user_id();
        return authority.taskSelection(admission.context().tenant_id(), projectId, user, admission.subject().binding().view().mode(),
            permissions.resourceGrants(user, "task.read"), Objects.requireNonNull(permissions.databaseTime()));
    }

    private java.util.Map<String,Object> listValues(String user, String project, TaskProtocol.Query query, PermissionAuthority.ResourceSelection scope) {
        var values = new java.util.HashMap<String,Object>(); values.put("userId", user); values.put("projectId", project);
        values.put("allResources", scope.all()); values.put("resourceIds", scope.ids()); values.put("status", query.status());
        values.put("ownerId", query.ownerId()); values.put("updatedFrom", query.updatedFrom()); values.put("updatedTo", query.updatedTo());
        return values;
    }

    private String listHash(EnterpriseAccess.Admission admission, String project, TaskProtocol.Query query,
            PermissionProtocol.GrantRow projectGrant, PermissionAuthority.ResourceSelection scope) throws IOException {
        var value = json.object(); value.put("subject_id", admission.subject().user().user_id());
        value.put("identity_mode", admission.subject().binding().view().mode()); value.put("tenant_id", admission.context().tenant_id());
        value.put("context_revision", admission.context().context_revision()); value.put("auth_epoch", admission.context().auth_epoch());
        value.put("governance_epoch", admission.context().governance_epoch()); value.put("project_id", project);
        value.put("page_size", query.pageSize()); value.set("status", json.tree(query.status())); value.set("owner_id", json.tree(query.ownerId()));
        value.set("updated_from", json.tree(query.updatedFrom() == null ? null : query.updatedFrom().toInstant(java.time.ZoneOffset.UTC).toString()));
        value.set("updated_to", json.tree(query.updatedTo() == null ? null : query.updatedTo().toInstant(java.time.ZoneOffset.UTC).toString()));
        value.set("project_grant", grantIdentity(projectGrant)); var grants = json.object().arrayNode();
        for (var grant : scope.witnesses()) grants.add(grantIdentity(grant)); value.set("task_grants", grants);
        return DigestUtils.sha256Hex(json.canonical(value));
    }

    private JsonNode grantIdentity(PermissionProtocol.GrantRow grant) throws IOException {
        var value = json.object(); value.put("grant_id", grant.grantId()); value.put("revision", grant.revision().toString());
        value.put("condition_sha256", DigestUtils.sha256Hex(json.canonical(inputs.read(grant.condition())))); return value;
    }

    private JsonNode detail(EnterpriseAccess.Admission admission, String context, String taskId) throws IOException {
        return detailState(admission, context, taskId).envelope();
    }

    record VerifiedTask(JsonNode envelope, TaskProtocol.Head head, TaskProtocol.Input input) { }

    VerifiedTask fixedTask(EnterpriseAccess.Admission admission, String taskId) throws IOException {
        return detailState(admission, admission.context().context_revision(), taskId);
    }

    private VerifiedTask detailState(EnterpriseAccess.Admission admission, String context, String taskId) throws IOException {
        if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED);
        String user = admission.subject().user().user_id();
        var head = one(mapper.head(user, taskId), ErrorCode.ACCESS_DENIED);
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), head.projectId(), "TASK", taskId);
        resources.require(admission, scope, "task.read", "TASK_READING");
        if (!scope.tenant_id().equals(head.tenantId()) || !taskId.equals(head.taskId())
                || head.revision() == null || head.revision().signum() <= 0 || head.updatedAt() == null) mismatch();
        WireValues.uuid(head.taskRevision()); WireValues.uuid(head.profileRevision()); WireValues.uuid(head.templateVersion());
        WireValues.uuid(head.agentReleaseId()); WireValues.uuid(head.snapshotId());
        requireHash(head.contentHash(), head.registryHash());
        var input = one(mapper.input(user, head.projectId(), taskId, head.taskRevision()), ErrorCode.CONTENT_MISMATCH);
        verifyInput(head, input);
        var snapshot = one(mapper.snapshot(user, head.projectId(), head.snapshotId()), ErrorCode.CONTENT_MISMATCH);
        if (!head.snapshotId().equals(snapshot.snapshotId()) || !scope.tenant_id().equals(snapshot.tenantId())
                || !head.projectId().equals(snapshot.projectId()) || snapshot.createdAt() == null) mismatch();
        requireHash(snapshot.manifestHash(), snapshot.registryHash());
        var request = inputs.create(input.requestJson().getBytes(StandardCharsets.UTF_8)).request();
        snapshots.require(snapshot.manifest(), snapshot.manifestHash(), snapshot.tenantId(), snapshot.projectId(),
            snapshot.snapshotId(), head.profileRevision(), snapshot.authEpoch(), snapshot.governanceEpoch(),
            request.scope_selection(), mapper.members(user, head.projectId(), head.snapshotId()));
        if (head.planRevision() != null) verifyPlan(head, one(mapper.plan(user, head.projectId(), taskId,
                head.planRevision()), ErrorCode.CONTENT_MISMATCH));

        var missing = requirements(admission, scope, input);
        if ("NEEDS_MATERIALS".equals(head.status()) && missing.isEmpty()) mismatch();
        if (List.of("READY", "RUNNING", "REVIEW_PENDING", "SIGN_PENDING", "SIGNED").contains(head.status())
                && (head.planRevision() == null || !missing.isEmpty())) mismatch();
        var documents = new ArrayList<String>();
        for (String id : mapper.documents(user, head.projectId(), taskId)) {
            WireValues.uuid(id);
            if (resources.find(admission, new PermissionProtocol.Scope(scope.tenant_id(), head.projectId(), "DOCUMENT", id),
                    "document.read", "DOCUMENT_READING").isPresent()) documents.add(id);
        }
        var runs = new ArrayList<String>();
        if (resources.find(admission, scope, "run.read", "RUN_READING").isPresent()) {
            for (var run : mapper.runs(user, head.projectId(), taskId)) { WireValues.uuid(run.runId()); runs.add(run.runId()); }
        }
        var waiting = new ArrayList<String>();
        if (resources.find(admission, scope, "work_item.read", "WORK_ITEM_READING").isPresent()) {
            for (var item : mapper.waiting(user, head.projectId(), taskId)) {
                if (!head.projectId().equals(item.projectId()) || !"1.0.0".equals(item.schemaVersion())
                        || !"WAITING_INPUT".equals(item.status())) mismatch();
                WireValues.uuid(item.id()); waiting.add(item.id());
            }
        }
        var result = new ResourceProtocol.TaskDetail(taskId, head.taskRevision(), head.projectId(), head.ownerId(),
                ResourceProtocol.TaskStatus.valueOf(head.status()), head.planRevision(), head.snapshotId(), documents, missing, waiting, runs);
        var bound = TypedSchemaBindings.bind(shared, ResourceBindings.TASK_DETAIL, json.tree(result));
        var envelope = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(),
                head.revision().toString(), inputs.read(bound.payload().canonicalJson())));
        shared.validate("interfaces", envelope, "Envelope"); return new VerifiedTask(envelope, head, input);
    }

    private void verifyInput(TaskProtocol.Head head, TaskProtocol.Input input) throws IOException {
        if (!"1.0.0".equals(input.schemaVersion()) || !inputs.manifestSha256().equals(input.manifest())) mismatch();
        var validated = inputs.create(input.requestJson().getBytes(StandardCharsets.UTF_8));
        var request = validated.request();
        requireHash(input.requestHash(), validated.request_sha256());
        requireHash(input.scopeHash(), validated.scope_sha256());
        if (!inputs.read(input.scopeJson()).equals(json.tree(request.scope_selection()))
                || !head.projectId().equals(request.project_id()) || !head.profileRevision().equals(request.profile_revision())
                || !head.templateVersion().equals(request.template_version()) || !head.agentReleaseId().equals(request.agent_release_id())
                || !head.ownerId().equals(request.owner_id()) || !input.ownerId().equals(request.owner_id())
                || !input.goal().equals(request.goal()) || !input.documentType().equals(request.document_type())
                || !Objects.equals(input.basedOnDocumentRevision(), request.based_on_document_revision())) mismatch();
    }

    private void verifyPlan(TaskProtocol.Head head, TaskProtocol.Plan plan) throws IOException {
        if (!head.planRevision().equals(plan.planRevision()) || !head.taskRevision().equals(plan.taskRevision())
                || !head.templateVersion().equals(plan.templateVersion()) || !head.snapshotId().equals(plan.snapshotId())
                || !"CONFIRMED".equals(plan.status()) || plan.decisionId() == null
                || !"1.0.0".equals(plan.schemaVersion()) || !inputs.manifestSha256().equals(plan.manifest())) mismatch();
        WireValues.uuid(plan.decisionId());
        var value = inputs.read(plan.contentJson());
        var content = TypedSchemaBindings.bind(shared, ArtifactBindings.PLAN_CANDIDATE, value);
        if (!content.payload().schemaRef().equals(plan.schemaRef())
                || !head.profileRevision().equals(content.read().profileRevision())
                || !head.templateVersion().equals(content.read().templateVersion())) mismatch();
        requireHash(plan.contentHash(), DigestUtils.sha256Hex(content.payload().canonicalBytes()));
        requireHash(plan.contentHash(), plan.registryHash());
    }

    private List<ResourceProtocol.Requirement> requirements(EnterpriseAccess.Admission admission,
            PermissionProtocol.Scope scope, TaskProtocol.Input input) throws IOException {
        var array = inputs.read(input.missingRequirements());
        if (!array.isArray()) mismatch();
        requireHash(input.requirementsHash(), hash(array));
        var result = new ArrayList<ResourceProtocol.Requirement>(); var identities = new HashSet<String>();
        for (var value : array) {
            var item = TypedSchemaBindings.bind(shared, ResourceBindings.REQUIREMENT, value).read();
            if (!identities.add(item.requirementId())) mismatch();
            var actions = new ArrayList<String>();
            for (String action : item.allowedActions()) {
                if (resources.find(admission, scope, action, "TASK_READING").isPresent()) actions.add(action);
            }
            result.add(new ResourceProtocol.Requirement(item.requirementId(), item.nodeId(), item.fieldId(), item.reasonCode(), actions));
        }
        return List.copyOf(result);
    }

    private String hash(JsonNode value) throws IOException { return DigestUtils.sha256Hex(json.canonical(value)); }
    private static void requireHash(String expected, String actual) {
        WireValues.hash(expected); WireValues.hash(actual); if (!expected.equals(actual)) mismatch();
    }
    private static <T> T one(List<T> rows, ErrorCode code) { if (rows.size() != 1) fail(code); return Objects.requireNonNull(rows.get(0)); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
}
