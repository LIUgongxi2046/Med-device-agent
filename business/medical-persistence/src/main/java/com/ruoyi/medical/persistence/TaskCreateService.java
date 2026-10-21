package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.ResourceBindings;
import com.ruoyi.medical.contracts.ResourceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskCreateService {
    private static final String PURPOSE = "TASK_CREATION";
    private final EnterpriseAccess access;
    private final TaskInputSchema inputs;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    private final TemplateApplicabilityService templates;
    private final ScopeSelectionAuthority selections;
    private final ScopeSnapshotStore snapshots;
    private final ScopeSnapshotSchema snapshotSchema;
    private final AgentReleaseStore releases;
    private final AgentReleaseScopeAuthority releaseScopes;
    private final ObjectProvider<TaskCreateReleaseAdmission> admissionProvider;
    private final TemplateDependencyPolicy policy;
    private final IdentityDirectoryPolicy identity;
    private final PermissionMapper permissions;
    private final TaskPlanWriteMapper writes;
    private final TaskMapper mapper;
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final DependencyLineage lineage;
    private final SchemaCatalogue shared;

    public TaskCreateService(EnterpriseAccess access, TaskInputSchema inputs, PermissionSchema json,
            ResourceReadAuthority resources, TemplateApplicabilityService templates, ScopeSelectionAuthority selections,
            ScopeSnapshotStore snapshots, ScopeSnapshotSchema snapshotSchema, AgentReleaseStore releases,
            AgentReleaseScopeAuthority releaseScopes, ObjectProvider<TaskCreateReleaseAdmission> admissionProvider,
            TemplateDependencyPolicy policy, IdentityDirectoryPolicy identity, PermissionMapper permissions,
            TaskPlanWriteMapper writes, TaskMapper mapper, TaskService tasks, TaskPlanService plans, DependencyLineage lineage)
            throws IOException, java.security.NoSuchAlgorithmException {
        this.access = access; this.inputs = inputs; this.json = json; this.resources = resources; this.templates = templates;
        this.selections = selections; this.snapshots = snapshots; this.snapshotSchema = snapshotSchema; this.releases = releases;
        this.releaseScopes = releaseScopes; this.admissionProvider = admissionProvider; this.policy = policy; this.identity = identity;
        this.permissions = permissions; this.writes = writes; this.mapper = mapper; this.tasks = tasks; this.plans = plans; this.lineage = lineage;
        shared = SchemaCatalogue.fromClasspath();
    }

    record Reply(int httpStatus, String taskId, JsonNode envelope) { }

    Reply create(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] body) {
        json.headers(context, key);
        var input = ThrowingSupplier.of(() -> inputs.create(body.clone())).get();
        // Recover completed requests before requiring current execution/publication dependencies.
        var recovered = access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            resources.require(admission, projectScope(admission, input.request().project_id()), "task.create", PURPOSE);
            var values = values(admission, input.request().project_id(), key);
            var original = writes.operation(values); if (original.size() > 1) mismatch();
            if (original.isEmpty()) return null;
            if (!original.get(0).requestSha256().equals(operationHash(input))) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
            return replay(admission, values, original.get(0), input);
        }).get());
        if (recovered != null) return recovered;
        var provider = admissionProvider.getIfAvailable();
        if (provider == null || input.request().based_on_document_revision() != null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var preparedAdmission = ThrowingSupplier.of(() -> provider.prepare(subject, context, input)).get();
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var validated = input; var request = validated.request();
            var values = values(admission, request.project_id(), key);
            values.put("requestHash", operationHash(validated));
            var grant = resources.require(admission, projectScope(admission, request.project_id()), "task.create", PURPOSE);
            var original = writes.operation(values); if (original.size() > 1) mismatch();
            if (!original.isEmpty()) {
                if (!original.get(0).requestSha256().equals(values.get("requestHash"))) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                return replay(admission, values, original.get(0), validated);
            }
            var template = templates.requireTarget(admission, request.project_id(), request.profile_revision(),
                    request.document_type(), request.template_version(), PURPOSE);
            if (!"ACTIVE".equals(template.profile().project().status())) fail(ErrorCode.ACCESS_DENIED);
            var sourcePolicy = preparedAdmission.sourcePolicy(admission, validated);
            var selected = selections.requireForTaskCreation(admission, validated, sourcePolicy, policy.environment(), policy.region(), policy.limits());
            var release = releases.read(admission, request.agent_release_id(), PURPOSE);
            if (!"PUBLISHED".equals(release.agent().status())) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var scope = releaseScopes.require(release, projectScope(admission, request.project_id()), "task.create",
                    identity.classification(), policy.limits());
            var allowed = Objects.requireNonNull(preparedAdmission.require(admission, validated, template, selected, scope));
            var proof = Objects.requireNonNull(allowed.authorizations());
            if (!proof.isArray() || proof.isEmpty()) mismatch();
            var dependencies = new TreeSet<>(Objects.requireNonNull(allowed.dependencyVersions()));
            if (dependencies.size() != allowed.dependencyVersions().size()) mismatch();
            var required = new TreeSet<String>(); required.add(request.agent_release_id());
            release.content().dependencies().forEach(version -> required.add(version.versionId()));
            release.content().artifacts().forEach(artifact -> required.add(artifact.artifactId())); required.add(release.release().manifestArtifactId());
            scope.scopes().forEach(value -> required.add(value.scopeRevision())); scope.roles().forEach(value -> required.add(value.origin().roleVersion()));
            if (!dependencies.containsAll(required)) mismatch();
            var missing = new ArrayList<>(template.view().missing_requirements()); missing.addAll(Objects.requireNonNull(allowed.missingRequirements()));
            if (selected.sources().isEmpty()) missing.add(new ResourceProtocol.Requirement("task:sources", null, null, "SOURCE_MISSING", List.of()));
            var requirementIds = new HashSet<String>();
            for (var item : missing) {
                TypedSchemaBindings.bind(shared, ResourceBindings.REQUIREMENT, json.tree(item));
                if (!requirementIds.add(item.requirementId())) mismatch();
            }
            String taskId = UUID.randomUUID().toString(), taskRevision = UUID.randomUUID().toString();
            var owner = plans.creationOwner(admission, request.project_id(), taskId, request.owner_id());
            resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), request.project_id(), "TASK", taskId), "task.read", "TASK_READING");
            allowed.requireUnchanged(); releaseScopes.requireUnchanged(scope); templates.requireUnchanged(admission, template);
            var stored = snapshots.saveForTaskCreation(admission, validated, sourcePolicy, policy.environment(), policy.region(), policy.limits());
            if (!selected.equals(stored.selection())) fail(ErrorCode.SCOPE_STALE);
            values.put("taskId", taskId); values.put("taskRevision", taskRevision); values.put("basisTaskRevision", null);
            values.put("operationId", UUID.randomUUID().toString()); values.put("now", Objects.requireNonNull(permissions.databaseTime()));
            values.put("taskStatus", missing.isEmpty() ? "PLAN_PENDING" : "NEEDS_MATERIALS"); values.put("ownerId", request.owner_id());
            values.put("profileRevision", request.profile_revision()); values.put("snapshotId", stored.snapshot().snapshotId());
            values.put("templateVersion", request.template_version()); values.put("agentReleaseId", request.agent_release_id());
            values.put("documentType", request.document_type()); values.put("goal", request.goal());
            values.put("basedOnDocumentRevision", request.based_on_document_revision()); values.put("reason", "创建任务");
            values.put("request", validated.canonical_request()); values.put("inputHash", validated.request_sha256());
            values.put("scopeJson", json.canonicalObject(request.scope_selection())); values.put("scopeHash", validated.scope_sha256());
            values.put("requirements", json.canonicalObject(missing)); values.put("requirementsHash", hash((String) values.get("requirements")));
            values.put("authorityGrant", grant.grantId()); values.put("authorityRevision", grant.revision().toString());
            values.put("taskRevisionHash", revisionHash(admission.context().tenant_id(), taskId, taskRevision, stored.snapshot().snapshotId(), validated,
                    (String) values.get("requirementsHash"), (String) values.get("taskStatus")));
            values.put("versionId", taskRevision); values.put("versionKind", "TASK_REVISION"); values.put("versionHash", values.get("taskRevisionHash"));
            one(writes.beginOperation(values)); one(writes.insertRegisteredVersion(values)); one(writes.insertTaskHead(values));
            one(writes.insertTaskRevision(values)); one(writes.insertTaskInput(values)); one(writes.bindCreatedTask(values));
            dependencies.addAll(stored.dependencyVersions()); dependencies.addAll(List.of(stored.snapshot().snapshotId(), request.template_version()));
            writeLineage(values, taskRevision, dependencies); lineage.inspect(taskRevision, policy.limits());
            var saved = tasks.fixedTask(admission, taskId);
            if (!"1".equals(saved.envelope().required("resource_revision").textValue()) || !taskRevision.equals(saved.head().taskRevision())
                    || !values.get("taskRevisionHash").equals(saved.head().contentHash()) || !values.get("taskStatus").equals(saved.head().status())
                    || !validated.request_sha256().equals(saved.input().requestHash()) || !validated.scope_sha256().equals(saved.input().scopeHash())
                    || !values.get("requirementsHash").equals(saved.input().requirementsHash())) mismatch();
            allowed.requireUnchanged(); releaseScopes.requireUnchanged(scope); templates.requireUnchanged(admission, template);
            if (!grant.equals(resources.require(admission, projectScope(admission, request.project_id()), "task.create", PURPOSE))
                    || !owner.equals(plans.creationOwner(admission, request.project_id(), taskId, request.owner_id()))) fail(ErrorCode.SCOPE_STALE);
            String response = json.canonical(saved.envelope()); values.put("response", response); values.put("responseHash", hash(response));
            values.put("httpStatus", 201); String requestId = saved.envelope().required("request_id").textValue();
            var audit = json.object(); audit.put("task_id", taskId); audit.put("task_revision", taskRevision);
            audit.put("operation_id", (String) values.get("operationId")); audit.put("request_sha256", (String) values.get("requestHash"));
            audit.put("response_sha256", (String) values.get("responseHash")); audit.set("release_authorizations", proof);
            audit.set("template_authorizations", inputs.read(template.dependencyAuthorizations()));
            one(permissions.saveAudit(subject.user().user_id(), request.project_id(), UUID.randomUUID().toString(), "task.created",
                    json.canonical(audit), requestId, inputs.manifestSha256(), identity.classification()));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), taskId, "1", "task.created", response));
            one(writes.completeOperation(values));
            var result = replay(admission, values, single(writes.operation(values)), validated);
            if (!saved.envelope().equals(result.envelope())) mismatch(); return result;
        }).get());
    }

    TaskCreateReleaseAdmission prepareRevision(ApplicationSessionIdentity.Verified subject, String context,
            TaskProtocol.ValidatedCreate input) throws IOException {
        var provider = admissionProvider.getIfAvailable();
        if (provider == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return provider.prepare(subject, context, input);
    }

    /** Only TK-06 can mint this authority, after locking the old head and its genuine terminal run. */
    Map<String,Object> materializeRevision(EnterpriseAccess.Admission admission,
            TaskInputRevisionService.RevisionAuthority authority, TaskCreateReleaseAdmission preparedAdmission) throws Exception {
        var head = authority.requireCurrent(admission);
        var validated = authority.input(); var request = validated.request();
        var values = values(admission, head.projectId(), authority.key());
        var grant = resources.require(admission, projectScope(admission, head.projectId()), "task.create", PURPOSE);
            var template = templates.requireTarget(admission, request.project_id(), request.profile_revision(),
                    request.document_type(), request.template_version(), PURPOSE);
            if (!"ACTIVE".equals(template.profile().project().status())) fail(ErrorCode.ACCESS_DENIED);
            var sourcePolicy = preparedAdmission.sourcePolicy(admission, validated);
            var selected = selections.requireForTaskCreation(admission, validated, sourcePolicy, policy.environment(), policy.region(), policy.limits());
            var release = releases.read(admission, request.agent_release_id(), PURPOSE);
            if (!"PUBLISHED".equals(release.agent().status())) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var scope = releaseScopes.require(release, projectScope(admission, request.project_id()), "task.create",
                    identity.classification(), policy.limits());
            var allowed = Objects.requireNonNull(preparedAdmission.require(admission, validated, template, selected, scope));
            var proof = Objects.requireNonNull(allowed.authorizations());
            if (!proof.isArray() || proof.isEmpty()) mismatch();
            var dependencies = new TreeSet<>(Objects.requireNonNull(allowed.dependencyVersions()));
            if (dependencies.size() != allowed.dependencyVersions().size()) mismatch();
            var required = new TreeSet<String>(); required.add(request.agent_release_id());
            release.content().dependencies().forEach(version -> required.add(version.versionId()));
            release.content().artifacts().forEach(artifact -> required.add(artifact.artifactId())); required.add(release.release().manifestArtifactId());
            scope.scopes().forEach(value -> required.add(value.scopeRevision())); scope.roles().forEach(value -> required.add(value.origin().roleVersion()));
            if (!dependencies.containsAll(required)) mismatch();
            var missing = new ArrayList<>(template.view().missing_requirements()); missing.addAll(Objects.requireNonNull(allowed.missingRequirements()));
            if (selected.sources().isEmpty()) missing.add(new ResourceProtocol.Requirement("task:sources", null, null, "SOURCE_MISSING", List.of()));
            var requirementIds = new HashSet<String>();
            for (var item : missing) {
                TypedSchemaBindings.bind(shared, ResourceBindings.REQUIREMENT, json.tree(item));
                if (!requirementIds.add(item.requirementId())) mismatch();
            }
        String taskId = head.taskId(), taskRevision = UUID.randomUUID().toString();
        var owner = plans.creationOwner(admission, head.projectId(), taskId, request.owner_id());
        allowed.requireUnchanged(); releaseScopes.requireUnchanged(scope); templates.requireUnchanged(admission, template);
        var stored = snapshots.saveForTaskCreation(admission, validated, sourcePolicy, policy.environment(), policy.region(), policy.limits());
        if (!selected.equals(stored.selection())) fail(ErrorCode.SCOPE_STALE);
        values.put("taskId", taskId); values.put("taskRevision", taskRevision); values.put("basisTaskRevision", head.taskRevision());
        values.put("operationId", authority.operationId()); values.put("now", Objects.requireNonNull(permissions.databaseTime()));
        values.put("taskStatus", missing.isEmpty() ? "PLAN_PENDING" : "NEEDS_MATERIALS"); values.put("ownerId", request.owner_id());
        values.put("profileRevision", request.profile_revision()); values.put("snapshotId", stored.snapshot().snapshotId());
        values.put("templateVersion", request.template_version()); values.put("agentReleaseId", request.agent_release_id());
        values.put("documentType", request.document_type()); values.put("goal", request.goal());
        values.put("basedOnDocumentRevision", request.based_on_document_revision()); values.put("reason", authority.reason());
        values.put("request", validated.canonical_request()); values.put("inputHash", validated.request_sha256());
        values.put("scopeJson", json.canonicalObject(request.scope_selection())); values.put("scopeHash", validated.scope_sha256());
        values.put("requirements", json.canonicalObject(missing)); values.put("requirementsHash", hash((String)values.get("requirements")));
        values.put("authorityGrant", grant.grantId()); values.put("authorityRevision", grant.revision().toString());
        var manifest = json.object().put("schema_version", "1.0.0").put("kind", "TASK_INPUT_REVISION")
                .put("tenant_id", admission.context().tenant_id()).put("project_id", head.projectId()).put("task_id", taskId)
                .put("task_revision", taskRevision).put("parent_revision", head.taskRevision()).put("operation_id", authority.operationId())
                .put("input_sha256", validated.request_sha256()).put("snapshot_id", stored.snapshot().snapshotId())
                .put("requirements_sha256", (String)values.get("requirementsHash")).put("status", (String)values.get("taskStatus"));
        values.put("taskRevisionHash", hash(json.canonical(manifest))); values.put("versionId", taskRevision);
        values.put("versionKind", "TASK_REVISION"); values.put("versionHash", values.get("taskRevisionHash"));
        authority.requireCurrent(admission);
        one(writes.insertRegisteredVersion(values)); one(writes.insertTaskRevision(values)); one(writes.insertTaskInput(values));
        dependencies.addAll(stored.dependencyVersions()); dependencies.addAll(List.of(stored.snapshot().snapshotId(), request.template_version(), head.taskRevision()));
        if (request.based_on_document_revision() != null) dependencies.add(request.based_on_document_revision());
        writeLineage(values, taskRevision, dependencies); lineage.inspect(taskRevision, policy.limits());
        allowed.requireUnchanged(); releaseScopes.requireUnchanged(scope); templates.requireUnchanged(admission, template);
        if (!grant.equals(resources.require(admission, projectScope(admission, head.projectId()), "task.create", PURPOSE))
                || !owner.equals(plans.creationOwner(admission, head.projectId(), taskId, request.owner_id()))) fail(ErrorCode.SCOPE_STALE);
        return java.util.Collections.unmodifiableMap(new HashMap<>(values));
    }

    Reply original(ApplicationSessionIdentity.Verified subject, String context, String key, String projectId) {
        json.headers(context, key); WireValues.uuid(projectId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); resources.require(admission, projectScope(admission, projectId), "task.create", PURPOSE);
            var values = values(admission, projectId, key); var rows = writes.operation(values);
            if (rows.size() > 1) mismatch();
            if (rows.isEmpty()) {
                var empty = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(), null, json.tree(null)));
                shared.validate("interfaces", empty, "Envelope"); return new Reply(200, null, empty);
            }
            return replay(admission, values, rows.get(0), null);
        }).get());
    }

    private Reply replay(EnterpriseAccess.Admission admission, Map<String,Object> values, SelectionOperationRow operation,
            TaskProtocol.ValidatedCreate expected) throws IOException {
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus())
                || !"1.0.0".equals(operation.schemaVersion()) || !inputs.manifestSha256().equals(operation.schemaManifestSha256())) mismatch();
        WireValues.uuid(operation.operationId()); WireValues.uuid(operation.targetId());
        var response = inputs.read(operation.responseRef()); shared.validate("interfaces", response, "Envelope");
        var receipt = TypedSchemaBindings.bind(shared, ResourceBindings.TASK_DETAIL, response.required("data")).read();
        if (!"1".equals(response.required("resource_revision").textValue()) || !operation.targetId().equals(receipt.taskId())
                || !values.get("projectId").equals(receipt.projectId()) || receipt.planRevision() != null || receipt.snapshotId() == null
                || !Set.of(ResourceProtocol.TaskStatus.NEEDS_MATERIALS, ResourceProtocol.TaskStatus.PLAN_PENDING).contains(receipt.status())
                || !receipt.documentIds().isEmpty() || !receipt.waitingItemIds().isEmpty() || !receipt.activeRunIds().isEmpty()) mismatch();
        var current = tasks.fixedTask(admission, receipt.taskId());
        if (!receipt.projectId().equals(current.head().projectId())) mismatch();
        values.put("taskId", receipt.taskId()); values.put("taskRevision", receipt.taskRevision()); values.put("operationId", operation.operationId());
        var original = single(writes.decisionTask(values));
        var input = single(mapper.input((String) values.get("userId"), receipt.projectId(), receipt.taskId(), receipt.taskRevision()));
        var validated = inputs.create(input.requestJson().getBytes(StandardCharsets.UTF_8)); var request = validated.request();
        if (expected != null && !expected.equals(validated) || !operation.requestSha256().equals(operationHash(validated))
                || !operation.operationId().equals(single(writes.creationInputOperation(values))) || !validated.request_sha256().equals(input.requestHash())
                || !validated.scope_sha256().equals(input.scopeHash()) || !inputs.manifestSha256().equals(input.manifest())
                || !"1.0.0".equals(input.schemaVersion()) || !json.tree(request.scope_selection()).equals(inputs.read(input.scopeJson()))
                || !request.project_id().equals(receipt.projectId()) || !request.owner_id().equals(receipt.ownerId())
                || !request.owner_id().equals(input.ownerId()) || !request.document_type().equals(input.documentType()) || !request.goal().equals(input.goal())
                || !Objects.equals(request.based_on_document_revision(), input.basedOnDocumentRevision())
                || original.parentRevision() != null || original.planRevision() != null || !original.taskRevision().equals(receipt.taskRevision())
                || !original.tenantId().equals(admission.context().tenant_id()) || !original.projectId().equals(receipt.projectId())
                || !original.taskId().equals(receipt.taskId()) || !original.createdBy().equals(admission.subject().user().user_id())
                || !original.profileRevision().equals(request.profile_revision()) || !original.templateVersion().equals(request.template_version())
                || !original.agentReleaseId().equals(request.agent_release_id()) || !original.snapshotId().equals(receipt.snapshotId())
                || !"TASK_REVISION".equals(original.registeredKind()) || !original.projectId().equals(original.registeredProjectId())
                || !original.contentSha256().equals(original.registeredSha256()) || !"创建任务".equals(original.reason())) mismatch();
        var requirements = inputs.read(input.missingRequirements());
        if (!requirements.isArray() || !input.requirementsHash().equals(hash(json.canonical(requirements)))
                || receipt.status() == ResourceProtocol.TaskStatus.NEEDS_MATERIALS && requirements.isEmpty()
                || receipt.status() == ResourceProtocol.TaskStatus.PLAN_PENDING && !requirements.isEmpty()
                || !original.contentSha256().equals(revisionHash(original.tenantId(), receipt.taskId(), receipt.taskRevision(), receipt.snapshotId(),
                    validated, input.requirementsHash(), receipt.status().name()))) mismatch();
        var ids = new HashSet<String>(); var fixedRequirements = new HashMap<String,ResourceProtocol.Requirement>();
        for (var item : requirements) {
            var requirement = TypedSchemaBindings.bind(shared, ResourceBindings.REQUIREMENT, item).read();
            if (!ids.add(requirement.requirementId())) mismatch();
            fixedRequirements.put(requirement.requirementId(), requirement);
        }
        var receiptIds = new HashSet<String>();
        for (var item : receipt.missingRequirements()) {
            var saved = fixedRequirements.get(item.requirementId());
            if (saved == null || !receiptIds.add(item.requirementId()) || !Objects.equals(saved.nodeId(), item.nodeId())
                    || !Objects.equals(saved.fieldId(), item.fieldId()) || !saved.reasonCode().equals(item.reasonCode())
                    || !saved.allowedActions().containsAll(item.allowedActions())) mismatch();
        }
        if (!receiptIds.equals(ids)) mismatch();
        var snapshot = single(mapper.snapshot((String) values.get("userId"), receipt.projectId(), receipt.snapshotId()));
        if (!snapshot.manifestHash().equals(snapshot.registryHash())) mismatch();
        snapshotSchema.require(snapshot.manifest(), snapshot.manifestHash(), original.tenantId(), receipt.projectId(), receipt.snapshotId(),
                request.profile_revision(), snapshot.authEpoch(), snapshot.governanceEpoch(), request.scope_selection(),
                mapper.members((String) values.get("userId"), receipt.projectId(), receipt.snapshotId()));
        plans.sourceProof(admission, receipt.projectId(), receipt.taskId(), receipt.taskRevision(), receipt.snapshotId(), request.profile_revision(), false, PURPOSE);
        var audit = inputs.read(single(writes.creationAudit(values)));
        if (!operation.operationId().equals(audit.required("operation_id").textValue()) || !receipt.taskId().equals(audit.required("task_id").textValue())
                || !receipt.taskRevision().equals(audit.required("task_revision").textValue())
                || !operation.requestSha256().equals(audit.required("request_sha256").textValue())
                || !hash(json.canonical(response)).equals(audit.required("response_sha256").textValue())
                || !response.equals(inputs.read(single(writes.creationEvent(values))))) mismatch();
        resources.require(admission, projectScope(admission, receipt.projectId()), "task.create", PURPOSE);
        return new Reply(201, receipt.taskId(), response);
    }

    private String revisionHash(String tenant, String task, String version, String snapshot, TaskProtocol.ValidatedCreate input,
            String requirementsHash, String status) throws IOException {
        var request = input.request(); var value = json.object(); value.put("schema_version", "1.0.0"); value.put("tenant_id", tenant);
        value.put("project_id", request.project_id()); value.put("task_id", task); value.put("task_revision", version);
        value.put("profile_revision", request.profile_revision()); value.put("snapshot_id", snapshot); value.put("template_version", request.template_version());
        value.put("agent_release_id", request.agent_release_id()); value.putNull("plan_revision"); value.putNull("parent_revision");
        value.put("input_sha256", input.request_sha256()); value.put("scope_sha256", input.scope_sha256());
        value.put("requirements_sha256", requirementsHash); value.put("initial_status", status); value.put("reason", "创建任务");
        return hash(json.canonical(value));
    }
    private String operationHash(TaskProtocol.ValidatedCreate input) throws IOException {
        var value = json.object(); value.put("operation_id", "TK-01"); value.set("input", inputs.read(input.canonical_request())); return hash(json.canonical(value));
    }
    private Map<String,Object> values(EnterpriseAccess.Admission admission, String project, String key) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("projectId", project);
        values.put("operationName", "TK-01"); values.put("operationManifest", inputs.manifestSha256()); values.put("manifest", inputs.manifestSha256());
        values.put("key", key); values.put("classification", identity.classification()); values.put("authEpoch", admission.watermarks().authEpoch().toString());
        values.put("governanceEpoch", admission.watermarks().governanceEpoch().toString()); return values;
    }
    private void writeLineage(Map<String,Object> values, String version, TreeSet<String> dependencies) {
        if (dependencies.size() > policy.maximumVersions() || dependencies.size() > policy.maximumRelations()) fail(ErrorCode.CONTEXT_LIMIT);
        values.put("consumerVersion", version); values.put("relationKind", "TASK_REVISION"); values.put("lineageDecisionId", null);
        for (String id : dependencies) { WireValues.uuid(id); if (version.equals(id)) mismatch(); values.put("upstreamVersion", id); one(writes.insertLineage(values)); }
    }
    private static PermissionProtocol.Scope projectScope(EnterpriseAccess.Admission admission, String project) {
        return new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project);
    }
    private static void current(EnterpriseAccess.Admission admission, String context) { if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED); }
    private static String hash(String value) { return DigestUtils.sha256Hex(value); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static <T> T single(List<T> values) { if (values.size() != 1) mismatch(); return values.get(0); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
