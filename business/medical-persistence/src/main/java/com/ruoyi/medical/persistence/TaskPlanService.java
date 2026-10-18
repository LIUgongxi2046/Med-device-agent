package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskPlanService {
    private static final String PURPOSE = "PLAN_MAINTENANCE";
    private final EnterpriseAccess access;
    private final TaskService taskService;
    private final TaskMapper tasks;
    private final TaskPlanWriteMapper mapper;
    private final PermissionMapper permissions;
    private final TemplateMapper templates;
    private final ResourceReadAuthority resources;
    private final PermissionAuthority authority;
    private final TemplateApplicabilityService applicability;
    private final TemplateVersionStore templateStore;
    private final TemplateDependencyPolicy policy;
    private final TaskInputSchema inputs;
    private final PermissionSchema json;
    private final ScopeSnapshotSchema snapshots;
    private final SourceReadAuthority sources;
    private final TaskPlanPolicy plans;
    private final TaskPlanRelationStore relations;
    private final DependencyLineage lineage;
    private final IdentityDirectoryPolicy identity;
    private final PlatformMemberIdentityStatus memberIdentities;
    private final SchemaCatalogue shared;

    public TaskPlanService(EnterpriseAccess access, TaskService taskService, TaskMapper tasks, TaskPlanWriteMapper mapper,
            PermissionMapper permissions, TemplateMapper templates, ResourceReadAuthority resources, PermissionAuthority authority,
            TemplateApplicabilityService applicability, TemplateVersionStore templateStore, TemplateDependencyPolicy policy,
            TaskInputSchema inputs, PermissionSchema json, ScopeSnapshotSchema snapshots, SourceReadAuthority sources,
            TaskPlanPolicy plans, TaskPlanRelationStore relations, DependencyLineage lineage, IdentityDirectoryPolicy identity,
            PlatformMemberIdentityStatus memberIdentities)
            throws IOException, java.security.NoSuchAlgorithmException {
        this.access = access; this.taskService = taskService; this.tasks = tasks; this.mapper = mapper; this.permissions = permissions;
        this.templates = templates; this.resources = resources; this.authority = authority; this.applicability = applicability;
        this.templateStore = templateStore; this.policy = policy; this.inputs = inputs; this.json = json; this.snapshots = snapshots;
        this.sources = sources; this.plans = plans; this.relations = relations; this.lineage = lineage; this.identity = identity;
        this.memberIdentities = memberIdentities; shared = SchemaCatalogue.fromClasspath();
    }

    record Reply(JsonNode envelope, int httpStatus, String planRevision) { }

    Reply save(ApplicationSessionIdentity.Verified subject, String context, String key, String taskId, byte[] body) {
        json.headers(context, key); WireValues.uuid(taskId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var input = inputs.plan(body);
            var fixed = taskService.fixedTask(admission, taskId); var head = fixed.head(); var values = values(admission, head);
            var scope = scope(head); var grant = resources.require(admission, scope, "plan.write", PURPOSE);
            var request = json.object(); request.put("operation_id", "TK-03"); request.put("task_id", taskId);
            request.set("input", json.tree(input)); String requestHash = DigestUtils.sha256Hex(json.canonical(request));
            values.put("operationName", "TK-03"); values.put("key", key); values.put("requestHash", requestHash);
            var operations = mapper.operation(values);
            if (operations.size() > 1) mismatch();
            if (!operations.isEmpty()) {
                var original = operations.get(0);
                if (!requestHash.equals(original.requestSha256())) fail(ErrorCode.IDEMPOTENCY_CONFLICT);
                if (!"COMPLETED".equals(original.state()) || original.httpStatus() == null || original.responseRef() == null
                        || !taskId.equals(original.targetId()) || !"1.0.0".equals(original.schemaVersion())
                        || !json.manifestSha256().equals(original.schemaManifestSha256())) mismatch();
                var response = inputs.read(original.responseRef()); shared.validate("interfaces", response, "Envelope");
                var data = response.required("data"); shared.validate("task-plan-view", data, "View");
                String id = data.required("plan_revision").textValue(); var actual = readFixed(admission, fixed, id, false);
                if (!input.content().equals(actual.content()) || !sameFixedView(data, actual)) mismatch();
                owners(admission, head, input.content(), values);
                resources.require(admission, scope, "plan.write", PURPOSE);
                return new Reply(response, original.httpStatus(), id);
            }
            if (!Set.of("NEEDS_MATERIALS", "PLAN_PENDING", "READY", "FAILED", "CANCELLED").contains(head.status())) fail(ErrorCode.REVISION_CONFLICT);
            if (!head.revision().equals(WireValues.revision(input.expected_revision()))) fail(ErrorCode.REVISION_CONFLICT);
            if (!head.profileRevision().equals(input.content().profileRevision())) fail(ErrorCode.RESULT_VERSION_MISMATCH);
            var base = planHead(values);
            if (!Objects.equals(input.base_version(), base == null ? null : base.planRevision())) fail(ErrorCode.PLAN_STALE);
            if (base != null) {
                var original = readFixed(admission, fixed, base.planRevision(), true);
                if (!head.taskRevision().equals(original.taskRevision())) fail(ErrorCode.PLAN_STALE);
                values.put("basePlanHash", original.contentSha256());
            }
            var target = applicability.requireTarget(admission, head.projectId(), head.profileRevision(), fixed.input().documentType(), head.templateVersion(), PURPOSE);
            var validation = validate(admission, input.content(), target.view().template(), target.view().conditions(), head.projectId(), true);
            var sourceProof = sourceProof(admission, head.projectId(), head.taskId(), head.taskRevision(), head.snapshotId(), head.profileRevision(), true);
            var ownerProof = owners(admission, head, input.content(), values);
            String id = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var now = Objects.requireNonNull(permissions.databaseTime());
            var next = head.revision().add(java.math.BigInteger.ONE); WireValues.revision(next.toString());
            var planNext = base == null ? java.math.BigInteger.ONE : base.revision().add(java.math.BigInteger.ONE); WireValues.revision(planNext.toString());
            values.put("planRevision", id); values.put("taskRevision", head.taskRevision()); values.put("basisTaskRevision", head.taskRevision());
            values.put("templateVersion", head.templateVersion()); values.put("snapshotId", head.snapshotId()); values.put("planStatus", "DRAFT");
            values.put("contentHash", validation.contentSha256()); values.put("content", validation.canonicalContent()); values.put("schemaRef", input.schema_ref());
            values.put("basePlanRevision", input.base_version()); values.put("operationId", operationId); values.put("expectedRevision", input.expected_revision());
            values.put("resultingRevision", next.toString()); values.put("expectedPlanHeadRevision", base == null ? null : base.revision().toString());
            values.put("resultingPlanHeadRevision", planNext.toString()); values.put("fromStatus", head.status()); values.put("taskStatus", head.status());
            values.put("reason", input.reason()); values.put("manifest", inputs.manifestSha256()); values.put("operationManifest", json.manifestSha256());
            values.put("classification", identity.classification()); values.put("now", now); values.put("authorityGrant", grant.grantId());
            values.put("authorityRevision", grant.revision().toString()); values.put("authEpoch", admission.watermarks().authEpoch().toString());
            values.put("governanceEpoch", admission.watermarks().governanceEpoch().toString());
            one(mapper.beginOperation(values)); values.put("versionId", id); values.put("versionKind", "PLAN_REVISION");
            values.put("versionHash", validation.contentSha256()); one(mapper.insertRegisteredVersion(values));
            one(mapper.insertPlan(values)); one(mapper.insertContent(values)); relations.save(admission, head.projectId(), taskId, id, validation, false);
            var dependencies = new TreeSet<>(List.of(head.taskRevision(), head.templateVersion(), head.snapshotId(), head.profileRevision()));
            if (input.base_version() != null) dependencies.add(input.base_version());
            input.content().sections().forEach(section -> dependencies.addAll(section.completionRuleIds()));
            input.content().questions().forEach(question -> dependencies.addAll(question.completionRuleIds()));
            values.put("consumerVersion", id); values.put("relationKind", "PLAN_REVISION"); values.put("lineageDecisionId", null);
            for (String upstream : dependencies) { values.put("upstreamVersion", upstream); one(mapper.insertLineage(values)); }
            lineage.inspect(id, policy.limits());
            one(base == null ? mapper.insertPlanHead(values) : mapper.advancePlanHead(values)); one(mapper.advanceTaskHead(values));
            var after = taskService.fixedTask(admission, taskId);
            if (!head.taskRevision().equals(after.head().taskRevision()) || !next.equals(after.head().revision())
                    || !fixed.input().equals(after.input()) || !head.status().equals(after.head().status())) mismatch();
            var view = readFixed(admission, after, id, true);
            if (!id.equals(view.currentPlanRevision()) || !planNext.toString().equals(view.currentPlanHeadRevision())
                    || !input.content().equals(view.content())) mismatch();
            applicability.requireUnchanged(admission, target);
            if (!sourceProof.equals(sourceProof(admission, head.projectId(), taskId, head.taskRevision(), head.snapshotId(), head.profileRevision(), true))
                    || !ownerProof.equals(owners(admission, head, input.content(), values))
                    || !grant.equals(resources.require(admission, scope, "plan.write", PURPOSE))) fail(ErrorCode.SCOPE_STALE);
            var response = envelope(view, requestId, next.toString()); String savedResponse = json.canonical(response);
            values.put("response", savedResponse); values.put("httpStatus", 201); values.put("auditId", UUID.randomUUID().toString());
            values.put("eventId", UUID.randomUUID().toString()); values.put("action", "task.plan.saved"); values.put("requestId", requestId);
            var audit = json.object(); audit.set("scope", json.tree(scope)); audit.put("plan_revision", id);
            audit.put("content_sha256", validation.contentSha256()); audit.put("operation_id", operationId); audit.put("reason", input.reason());
            one(permissions.saveAudit(subject.user().user_id(), head.projectId(), (String) values.get("auditId"), "task.plan.saved",
                    json.canonical(audit), requestId, json.manifestSha256(), identity.classification()));
            one(permissions.saveEvent(subject.user().user_id(), (String) values.get("eventId"), taskId, next.toString(), "task.plan.saved", savedResponse));
            one(mapper.completeOperation(values));
            if (!json.tree(List.of(audit)).equals(json.tree(mapper.audit(values).stream().map(value -> ThrowingSupplier.of(() -> inputs.read(value)).get()).toList()))
                    || !List.of(response).equals(mapper.event(values).stream().map(value -> ThrowingSupplier.of(() -> inputs.read(value)).get()).toList())) mismatch();
            var completed = mapper.operation(values);
            if (completed.size() != 1 || !operationId.equals(completed.get(0).operationId()) || !requestHash.equals(completed.get(0).requestSha256())
                    || !"COMPLETED".equals(completed.get(0).state()) || !Integer.valueOf(201).equals(completed.get(0).httpStatus())
                    || !response.equals(inputs.read(completed.get(0).responseRef()))) mismatch();
            return new Reply(response, 201, id);
        }).get());
    }

    Reply read(ApplicationSessionIdentity.Verified subject, String context, String taskId, String planRevision) {
        WireValues.uuid(taskId); WireValues.uuid(planRevision); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var fixed = taskService.fixedTask(admission, taskId);
            var view = readFixed(admission, fixed, planRevision, false);
            return new Reply(envelope(view, UUID.randomUUID().toString(), fixed.head().revision().toString()), 200, planRevision);
        }).get());
    }

    Reply currentPlan(ApplicationSessionIdentity.Verified subject, String context, String taskId) {
        WireValues.uuid(taskId); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var fixed = taskService.fixedTask(admission, taskId);
            var head = planHead(values(admission, fixed.head()));
            var view = head == null ? null : readFixed(admission, fixed, head.planRevision(), false);
            return new Reply(envelope(view, UUID.randomUUID().toString(), fixed.head().revision().toString()), 200,
                    head == null ? null : head.planRevision());
        }).get());
    }

    JsonNode editBasis(ApplicationSessionIdentity.Verified subject, String context, String taskId) {
        WireValues.uuid(taskId); PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var fixed = taskService.fixedTask(admission, taskId); var head = fixed.head();
            resources.require(admission, scope(head), "plan.write", PURPOSE);
            if (!Set.of("NEEDS_MATERIALS", "PLAN_PENDING", "READY", "FAILED", "CANCELLED").contains(head.status())) fail(ErrorCode.REVISION_CONFLICT);
            var target = applicability.requireTarget(admission, head.projectId(), head.profileRevision(), fixed.input().documentType(), head.templateVersion(), PURPOSE);
            sourceProof(admission, head.projectId(), taskId, head.taskRevision(), head.snapshotId(), head.profileRevision(), true);
            var rules = new TreeSet<String>();
            for (var reference : target.view().template().dependency_refs()) {
                var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("upstreamId", reference.version_id());
                var status = templates.ruleState(values); if (status.size() > 1) mismatch();
                if (status.isEmpty()) continue;
                if (!"APPROVED".equals(status.get(0))) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
                resources.require(admission, new PermissionProtocol.Scope(head.tenantId(), null, "RULE", reference.version_id()), "rule.read", PURPOSE);
                rules.add(reference.version_id());
            }
            var basis = new TaskPlanWriteProtocol.Basis("1.0.0", head.tenantId(), head.projectId(), taskId, head.taskRevision(),
                    head.snapshotId(), head.templateVersion(), head.profileRevision(), target.view().template().content(), target.view().conditions(), List.copyOf(rules));
            var content = shared.bind("task-plan-view", json.tree(basis), "Basis");
            applicability.requireUnchanged(admission, target); resources.require(admission, scope(head), "plan.write", PURPOSE);
            var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", UUID.randomUUID().toString(), head.revision().toString(), inputs.read(content.canonicalJson())));
            shared.validate("interfaces", response, "Envelope"); return response;
        }).get());
    }

    Reply savedOperation(ApplicationSessionIdentity.Verified subject, String context, String key, String taskId) {
        json.headers(context, key); WireValues.uuid(taskId);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var fixed = taskService.fixedTask(admission, taskId);
            resources.require(admission, scope(fixed.head()), "plan.write", PURPOSE);
            var values = values(admission, fixed.head()); values.put("operationName", "TK-03"); values.put("key", key);
            var rows = mapper.operation(values); if (rows.size() > 1) mismatch();
            if (rows.isEmpty()) return new Reply(envelope(null, UUID.randomUUID().toString(), fixed.head().revision().toString()), 200, null);
            var original = rows.get(0);
            if (!taskId.equals(original.targetId()) || !"COMPLETED".equals(original.state())
                    || !Integer.valueOf(201).equals(original.httpStatus()) || original.responseRef() == null
                    || !"1.0.0".equals(original.schemaVersion()) || !json.manifestSha256().equals(original.schemaManifestSha256())) mismatch();
            var response = inputs.read(original.responseRef()); shared.validate("interfaces", response, "Envelope");
            var data = response.required("data"); shared.validate("task-plan-view", data, "View");
            var id = data.required("plan_revision").textValue(); var actual = readFixed(admission, fixed, id, false);
            if (!sameFixedView(data, actual)) mismatch();
            owners(admission, fixed.head(), actual.content(), values);
            resources.require(admission, scope(fixed.head()), "plan.write", PURPOSE);
            return new Reply(response, 200, id);
        }).get());
    }

    TaskPlanWriteProtocol.View readFixed(EnterpriseAccess.Admission admission, TaskService.VerifiedTask fixed,
            String id, boolean formal) throws IOException {
        var head = fixed.head(); String user = admission.subject().user().user_id(); var rows = tasks.plan(user, head.projectId(), head.taskId(), id);
        if (rows.size() != 1) fail(ErrorCode.PLAN_STALE); var row = rows.get(0);
        var binding = TypedSchemaBindings.bind(shared, ArtifactBindings.PLAN_CANDIDATE, inputs.read(row.contentJson()));
        var candidate = binding.read();
        if ("CONFIRMED".equals(row.status()) && row.decisionId() == null) mismatch();
        if (row.decisionId() != null) WireValues.uuid(row.decisionId());
        if (!id.equals(row.planRevision()) || !row.templateVersion().equals(candidate.templateVersion())
                || !binding.payload().schemaRef().equals(row.schemaRef()) || !"1.0.0".equals(row.schemaVersion())
                || !inputs.manifestSha256().equals(row.manifest()) || !row.contentHash().equals(row.registryHash())
                || !row.contentHash().equals(DigestUtils.sha256Hex(binding.payload().canonicalBytes()))) mismatch();
        var template = templateStore.historicalTarget(admission, row.templateVersion(), PURPOSE);
        var validation = validate(admission, candidate, template, List.of(), head.projectId(), formal); relations.read(admission, head.projectId(), head.taskId(), id, validation);
        sourceProof(admission, head.projectId(), head.taskId(), row.taskRevision(), row.snapshotId(), candidate.profileRevision(), formal);
        var current = planHead(values(admission, head));
        return new TaskPlanWriteProtocol.View("1.0.0", head.tenantId(), head.projectId(), head.taskId(), row.taskRevision(), id,
                row.snapshotId(), row.templateVersion(), candidate.profileRevision(), row.status(), candidate, row.contentHash(),
                current == null ? null : current.planRevision(), current == null ? null : current.revision().toString());
    }

    private TaskPlanPolicy.Validation validate(EnterpriseAccess.Admission admission, ArtifactProtocol.PlanCandidate candidate, TemplateProtocol.Stored template,
            List<TemplateApplicabilityProtocol.ConditionResult> conditions, String projectId, boolean newUse) throws IOException {
        var content = template.content(); var allowed = new HashSet<String>();
        content.nodes().forEach(node -> allowed.add(node.node_id())); content.field_definitions().forEach(field -> allowed.add(field.field_id()));
        content.attachment_requirements().forEach(item -> allowed.add(item.attachment_requirement_id())); allowed.add(template.template_version()); allowed.add(projectId);
        conditions.forEach(condition -> allowed.add(condition.target_id()));
        for (var condition : conditions) if (condition.status().equals("UNKNOWN") && !candidate.pendingConditionIds().contains(condition.target_id()))
            fail(ErrorCode.CONDITION_UNKNOWN);
        var completion = new HashSet<String>(); var references = template.dependency_refs();
        var requested = new HashSet<String>(); candidate.sections().forEach(item -> requested.addAll(item.completionRuleIds()));
        candidate.questions().forEach(item -> requested.addAll(item.completionRuleIds()));
        for (String rule : requested) {
            var ref = references.stream().filter(item -> item.version_id().equals(rule)).findFirst().orElseThrow(() -> new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
            var actual = permissions.basis(rule); if (actual.size() != 1 || !ref.content_sha256().equals(actual.get(0).sha256())) mismatch();
            var ruleScope = new PermissionProtocol.Scope(TenantScope.requireCurrent().tenantId(), null, "RULE", rule);
            if (actual.get(0).ownerProjectId() != null) mismatch();
            resources.require(admission, ruleScope, "rule.read", PURPOSE);
            var values = new HashMap<String,Object>(); values.put("userId", TenantScope.requireCurrent().subjectId()); values.put("upstreamId", rule);
            var status = templates.ruleState(values);
            if (status.size() != 1 || !(newUse ? Set.of("APPROVED") : Set.of("APPROVED", "DISABLED")).contains(status.get(0))) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            completion.add(rule);
        }
        return plans.inspect(json.tree(candidate), template.template_version(), candidate.profileRevision(),
                content.nodes().stream().map(TemplateProtocol.Node::node_id).toList(), allowed, completion);
    }

    List<SourceReadProtocol.VerifiedSource> sourceProof(EnterpriseAccess.Admission admission, String project, String task,
            String revision, String snapshotId, String profileId, boolean formal) throws IOException {
        return sourceProof(admission, project, task, revision, snapshotId, profileId, formal, PURPOSE);
    }

    List<SourceReadProtocol.VerifiedSource> sourceProof(EnterpriseAccess.Admission admission, String project, String task,
            String revision, String snapshotId, String profileId, boolean formal, String purpose) throws IOException {
        ProjectSchema.text(purpose, 128);
        String user = admission.subject().user().user_id(); var original = tasks.input(user, project, task, revision);
        if (original.size() != 1) mismatch(); var input = original.get(0); var validated = inputs.create(input.requestJson().getBytes(StandardCharsets.UTF_8));
        if (!"1.0.0".equals(input.schemaVersion()) || !inputs.manifestSha256().equals(input.manifest())
                || !input.requestHash().equals(validated.request_sha256()) || !input.scopeHash().equals(validated.scope_sha256())
                || !project.equals(validated.request().project_id()) || !profileId.equals(validated.request().profile_revision())
                || !json.tree(validated.request().scope_selection()).equals(inputs.read(input.scopeJson()))) mismatch();
        var rows = tasks.snapshot(user, project, snapshotId); if (rows.size() != 1) mismatch(); var stored = rows.get(0);
        if (!stored.manifestHash().equals(stored.registryHash())) mismatch();
        var manifest = snapshots.require(stored.manifest(), stored.manifestHash(), admission.context().tenant_id(), project, snapshotId,
                profileId, stored.authEpoch(), stored.governanceEpoch(), validated.request().scope_selection(), tasks.members(user, project, snapshotId));
        var proof = new java.util.ArrayList<SourceReadProtocol.VerifiedSource>();
        for (var member : manifest.members()) {
            var actual = sources.require(admission, new SourceReadProtocol.ReadRequirement(member.source_version(), member.rights_revision(), project,
                    snapshotId, member.parse_version(), null, formal ? SourceReadProtocol.Use.FORMAL : SourceReadProtocol.Use.HISTORY,
                    purpose, policy.environment(), policy.region(), null));
            ScopeSnapshotSchema.requireSource(member, actual); var selected = actual.selection();
            if (selected == null || !member.index_version().equals(selected.indexVersion()) || !member.approval_revision().equals(selected.approvalRevision())
                    || !member.binding_revision().equals(selected.bindingRevision())) mismatch();
            proof.add(actual);
        }
        return List.copyOf(proof);
    }

    private Map<String,PermissionProtocol.GrantRow> owners(EnterpriseAccess.Admission admission, TaskProtocol.Head head,
            ArtifactProtocol.PlanCandidate candidate, Map<String,Object> values) throws IOException {
        var result = new HashMap<String,PermissionProtocol.GrantRow>(); var now = Objects.requireNonNull(permissions.databaseTime());
        for (var section : candidate.sections()) if (section.ownerId() != null && !result.containsKey(section.ownerId())) {
            result.put(section.ownerId(), owner(admission, head, section.ownerId(), values, now));
        }
        return Map.copyOf(result);
    }

    private PermissionProtocol.GrantRow owner(EnterpriseAccess.Admission admission, TaskProtocol.Head head,
            String id, Map<String,Object> values, java.time.LocalDateTime now) throws IOException {
        return owner(admission, scope(head), id, values, now);
    }

    PermissionProtocol.GrantRow creationOwner(EnterpriseAccess.Admission admission, String projectId, String taskId,
            String ownerId) throws IOException {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", projectId); values.put("taskId", taskId);
        return owner(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), projectId, "TASK", taskId),
                ownerId, values, Objects.requireNonNull(permissions.databaseTime()));
    }

    private PermissionProtocol.GrantRow owner(EnterpriseAccess.Admission admission, PermissionProtocol.Scope scope,
            String id, Map<String,Object> values, java.time.LocalDateTime now) throws IOException {
        var binding = ownerIdentity(admission, id, now); values.put("ownerId", id);
        if (!memberIdentities.active(bindingView(binding))) fail(ErrorCode.ACCESS_DENIED);
        return authority.requireResource(scope, id, binding.mode(), "task.read", "TASK_READING", mapper.ownerGrants(values), now);
    }

    boolean creationOwnerEligible(EnterpriseAccess.Admission admission, String projectId, String id) throws IOException {
        var now = Objects.requireNonNull(permissions.databaseTime()); var binding = ownerIdentity(admission, id, now);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("ownerId", id);
        return memberIdentities.active(bindingView(binding))
                && authority.taskSelection(admission.context().tenant_id(), projectId, id, binding.mode(), mapper.ownerGrants(values), now).all();
    }

    private PermissionProtocol.BindingRow ownerIdentity(EnterpriseAccess.Admission admission, String id, java.time.LocalDateTime now) {
        var members = permissions.targetMember(id); var bindings = permissions.targetBinding(id);
        if (members.size() != 1 || bindings.size() != 1) fail(ErrorCode.ACCESS_DENIED);
        var member = members.get(0); var binding = bindings.get(0);
        if (!admission.context().tenant_id().equals(member.tenantId()) || !id.equals(member.userId()) || !id.equals(binding.userId())
                || !"ACTIVE".equals(member.status()) || !"ACTIVE".equals(binding.status()) || member.validFrom().isAfter(now)
                || !Set.of("local", "oidc").contains(binding.mode()) || "local".equals(binding.mode()) && !id.equals(binding.localUserId())
                || "oidc".equals(binding.mode()) && binding.localUserId() != null
                || member.validTo() != null && !member.validTo().isAfter(now)) fail(ErrorCode.ACCESS_DENIED);
        return binding;
    }

    private static AuthenticatedIdentityBinding.View bindingView(PermissionProtocol.BindingRow binding) {
        return new AuthenticatedIdentityBinding.View(binding.mode(), binding.userId(), binding.issuer(), binding.subject());
    }

    private Map<String,PermissionProtocol.GrantRow> confirmationOwners(EnterpriseAccess.Admission admission,
            TaskProtocol.Head head, ArtifactProtocol.PlanCandidate candidate) throws IOException {
        var values = values(admission, head); var result = new HashMap<>(owners(admission, head, candidate, values));
        if (!result.containsKey(head.ownerId())) result.put(head.ownerId(), owner(admission, head, head.ownerId(), values,
                Objects.requireNonNull(permissions.databaseTime())));
        return Map.copyOf(result);
    }

    record Confirmation(TemplateApplicabilityService.Target template, TaskPlanPolicy.Validation validation,
            List<SourceReadProtocol.VerifiedSource> sources, Map<String,PermissionProtocol.GrantRow> owners) { }

    Confirmation confirmation(EnterpriseAccess.Admission admission, TaskService.VerifiedTask fixed,
            TaskPlanWriteProtocol.View draft) throws IOException {
        var head = fixed.head();
        if (!"PLAN_PENDING".equals(head.status()) || !"DRAFT".equals(draft.status())
                || !head.taskRevision().equals(draft.taskRevision()) || !head.templateVersion().equals(draft.templateVersion())
                || !head.snapshotId().equals(draft.snapshotId()) || !head.profileRevision().equals(draft.profileRevision())) fail(ErrorCode.PLAN_STALE);
        var missing = inputs.read(fixed.input().missingRequirements());
        if (!missing.isArray() || !missing.isEmpty()) fail(ErrorCode.SOURCE_NOT_READY);
        var target = applicability.requireTarget(admission, head.projectId(), head.profileRevision(), fixed.input().documentType(), head.templateVersion(), PURPOSE);
        var validation = validate(admission, draft.content(), target.view().template(), target.view().conditions(), head.projectId(), true);
        plans.requireConfirmable(validation);
        var sourceProof = sourceProof(admission, head.projectId(), head.taskId(), head.taskRevision(), head.snapshotId(), head.profileRevision(), true);
        var ownerProof = confirmationOwners(admission, head, draft.content());
        return new Confirmation(target, validation, sourceProof, ownerProof);
    }

    // RN-01 repeats current template, rule, source and owner checks for the fixed confirmed plan.
    Confirmation execution(EnterpriseAccess.Admission admission, TaskService.VerifiedTask fixed,
            TaskPlanWriteProtocol.View plan) throws IOException {
        var head = fixed.head();
        if (!"READY".equals(head.status()) || !"CONFIRMED".equals(plan.status())
                || !head.taskRevision().equals(plan.taskRevision()) || !head.planRevision().equals(plan.planRevision())
                || !head.templateVersion().equals(plan.templateVersion()) || !head.snapshotId().equals(plan.snapshotId())
                || !head.profileRevision().equals(plan.profileRevision())) fail(ErrorCode.PLAN_STALE);
        var missing = inputs.read(fixed.input().missingRequirements());
        if (!missing.isArray() || !missing.isEmpty()) fail(ErrorCode.SOURCE_NOT_READY);
        var target = applicability.requireTarget(admission, head.projectId(), head.profileRevision(), fixed.input().documentType(), head.templateVersion(), PURPOSE);
        var validation = validate(admission, plan.content(), target.view().template(), target.view().conditions(), head.projectId(), true);
        plans.requireConfirmable(validation);
        return new Confirmation(target, validation,
                sourceProof(admission, head.projectId(), head.taskId(), head.taskRevision(), head.snapshotId(), head.profileRevision(), true),
                confirmationOwners(admission, head, plan.content()));
    }

    void requireUnchanged(EnterpriseAccess.Admission admission, TaskService.VerifiedTask fixed,
            TaskPlanWriteProtocol.View draft, Confirmation original) throws IOException {
        applicability.requireUnchanged(admission, original.template());
        if (!original.sources().equals(sourceProof(admission, fixed.head().projectId(), fixed.head().taskId(),
                fixed.head().taskRevision(), fixed.head().snapshotId(), fixed.head().profileRevision(), true))
                || !original.owners().equals(confirmationOwners(admission, fixed.head(), draft.content()))) fail(ErrorCode.SCOPE_STALE);
    }

    JsonNode confirmationDependencies(Confirmation confirmation) throws IOException {
        return inputs.read(confirmation.template().dependencyAuthorizations());
    }

    TaskPlanWriteProtocol.Head planHead(Map<String,Object> values) {
        var heads = mapper.currentPlanHead(values); if (heads.size() > 1) mismatch(); if (heads.isEmpty()) return null;
        var head = heads.get(0);
        if (!TenantScope.requireCurrent().tenantId().equals(head.tenantId()) || !values.get("projectId").equals(head.projectId())
                || !values.get("taskId").equals(head.taskId()) || !identity.classification().equals(head.classification())
                || head.createdAt() == null || head.updatedAt() == null || head.revision().signum() <= 0) mismatch();
        WireValues.uuid(head.planRevision()); WireValues.uuid(head.createdBy()); WireValues.revision(head.revision().toString()); return head;
    }

    private JsonNode envelope(TaskPlanWriteProtocol.View view, String requestId, String revision) throws IOException {
        JsonNode content = view == null ? json.tree(null) : inputs.read(shared.bind("task-plan-view", json.tree(view), "View").canonicalJson());
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, content));
        shared.validate("interfaces", response, "Envelope"); return response;
    }
    private boolean sameFixedView(JsonNode saved, TaskPlanWriteProtocol.View actual) throws IOException {
        var expected = json.tree(actual).deepCopy(); var original = saved.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).remove(List.of("current_plan_revision", "current_plan_head_revision"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) original).remove(List.of("current_plan_revision", "current_plan_head_revision")); return expected.equals(original);
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission, TaskProtocol.Head head) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", head.projectId()); values.put("taskId", head.taskId()); return values;
    }
    private static PermissionProtocol.Scope scope(TaskProtocol.Head head) { return new PermissionProtocol.Scope(head.tenantId(), head.projectId(), "TASK", head.taskId()); }
    private static void current(EnterpriseAccess.Admission admission, String context) { if (!admission.context().context_revision().equals(context)) fail(ErrorCode.CONTEXT_CHANGED); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { fail(ErrorCode.CONTENT_MISMATCH); }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
