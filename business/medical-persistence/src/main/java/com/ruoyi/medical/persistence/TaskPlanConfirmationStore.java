package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class TaskPlanConfirmationStore {
    private final TaskPlanService plans;
    private final TaskPlanWriteMapper mapper;
    private final TaskPlanRelationStore relations;
    private final TaskInputSchema inputs;
    private final PermissionSchema json;
    private final DependencyLineage lineage;
    private final TemplateDependencyPolicy limits;
    private final ObjectProvider<TaskPlanReleaseAdmission> releases;
    private final SchemaCatalogue shared;
    private final TaskMapper tasks;
    private final AgentReleaseStore releaseStore;
    private final AgentReleaseScopeAuthority releaseScopes;
    private final IdentityDirectoryPolicy identity;

    public TaskPlanConfirmationStore(TaskPlanService plans, TaskPlanWriteMapper mapper, TaskPlanRelationStore relations,
            TaskInputSchema inputs, PermissionSchema json, DependencyLineage lineage, TemplateDependencyPolicy limits,
            ObjectProvider<TaskPlanReleaseAdmission> releases, TaskMapper tasks, AgentReleaseStore releaseStore,
            AgentReleaseScopeAuthority releaseScopes, IdentityDirectoryPolicy identity) throws IOException, java.security.NoSuchAlgorithmException {
        this.plans = plans; this.mapper = mapper; this.relations = relations; this.inputs = inputs; this.json = json;
        this.lineage = lineage; this.limits = limits; this.releases = releases; this.tasks = tasks; shared = SchemaCatalogue.fromClasspath();
        this.releaseStore = releaseStore; this.releaseScopes = releaseScopes; this.identity = identity;
    }

    record Prepared(TaskService.VerifiedTask task, TaskPlanWriteProtocol.View draft, TaskPlanService.Confirmation plan,
            TaskPlanReleaseAdmission.Verified release, AgentReleaseScopeAuthority.VerifiedScope scope, JsonNode authorizations) { }
    record Result(String taskRevision, String planRevision, String taskRevisionSha256) { }

    Prepared prepare(EnterpriseAccess.Admission admission, TaskService.VerifiedTask task,
            TaskPlanWriteProtocol.View draft, JsonNode commonAuthorizations) throws IOException {
        var plan = plans.confirmation(admission, task, draft);
        var stored = releaseStore.read(admission, task.head().agentReleaseId(), "PLAN_MAINTENANCE");
        if (!"PUBLISHED".equals(stored.agent().status())) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var scope = releaseScopes.require(stored, new PermissionProtocol.Scope(task.head().tenantId(), task.head().projectId(),
                "TASK", task.head().taskId()), "plan.confirm", identity.classification(), limits.limits());
        var provider = releases.getIfAvailable();
        if (provider == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var release = Objects.requireNonNull(provider.require(admission, task, draft, scope));
        var proof = Objects.requireNonNull(release.authorizations());
        if (!proof.isArray() || proof.isEmpty()) mismatch();
        var dependencies = Objects.requireNonNull(release.dependencyVersions());
        if (new TreeSet<>(dependencies).size() != dependencies.size() || dependencies.size() > limits.maximumVersions())
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        dependencies.forEach(WireValues::uuid);
        var expected = new TreeSet<String>(); expected.add(task.head().agentReleaseId());
        stored.content().dependencies().forEach(version -> expected.add(version.versionId()));
        stored.content().artifacts().forEach(artifact -> expected.add(artifact.artifactId()));
        expected.add(stored.release().manifestArtifactId());
        scope.scopes().forEach(value -> expected.add(value.scopeRevision()));
        scope.roles().forEach(value -> expected.add(value.origin().roleVersion()));
        if (!dependencies.containsAll(expected)) mismatch();
        // 完整证明进入Baseline摘要，页面不能用退回依据执行确认。
        var authorizations = commonAuthorizations.deepCopy();
        if (!authorizations.isArray()) mismatch();
        var array = (com.fasterxml.jackson.databind.node.ArrayNode) authorizations;
        var published = json.object(); published.put("kind", "AGENT_RELEASE_SCOPE"); published.put("release_id", stored.release().releaseId());
        published.put("manifest_sha256", stored.release().manifestSha256());
        var scopeValues = json.object().arrayNode();
        for (var value : scope.scopes()) {
            var item = json.object(); item.put("scope_revision", value.scopeRevision()); item.put("revision", value.revision().toString());
            item.put("content_sha256", value.contentSha256()); item.put("schema_version", value.schemaVersion());
            item.put("schema_manifest_sha256", value.schemaManifest()); item.put("status", value.status());
            item.put("created_by", value.createdBy()); item.put("created_at", utc(value.createdAt())); item.put("updated_at", utc(value.updatedAt()));
            item.put("classification", value.classification()); item.set("audience_manifest", inputs.read(value.audienceManifest())); scopeValues.add(item);
        }
        var roles = json.object().arrayNode();
        for (var value : scope.roles()) {
            var item = json.object(); item.set("origin", json.tree(value.origin()));
            item.put("role_revision", value.role().revision().toString()); item.put("invitation_revision", value.invitation().revision().toString());
            item.put("role_definition_sha256", DigestUtils.sha256Hex(json.canonical(inputs.read(value.role().definition()))));
            item.put("invitation_scope_sha256", DigestUtils.sha256Hex(json.canonical(inputs.read(value.invitation().scope()))));
            item.put("invitation_roles_sha256", DigestUtils.sha256Hex(json.canonical(inputs.read(value.invitation().roles()))));
            item.put("invitation_authorizations_sha256", DigestUtils.sha256Hex(json.canonical(inputs.read(value.invitation().authorizations()))));
            item.set("grant", grant(value.grant())); roles.add(item);
        }
        var grants = json.object().arrayNode();
        for (var value : stored.grants()) grants.add(grant(value));
        published.set("scopes", scopeValues); published.set("roles", roles); published.set("grants", grants); array.add(published);
        array.addAll((com.fasterxml.jackson.databind.node.ArrayNode) proof);
        var template = json.object(); template.put("kind", "TEMPLATE_CONFIRMATION");
        template.put("template_version", task.head().templateVersion()); template.put("profile_revision", task.head().profileRevision());
        template.put("content_sha256", plan.template().view().template().content_sha256());
        template.put("grant_id", plan.template().grant().grantId());
        template.put("grant_revision", plan.template().grant().revision().toString());
        template.set("conditions", json.tree(plan.template().view().conditions()));
        template.set("dependency_authorizations", plans.confirmationDependencies(plan)); array.add(template);
        plan.owners().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var owner = json.object(); owner.put("kind", "SECTION_OWNER"); owner.put("owner_id", entry.getKey());
            owner.put("grant_id", entry.getValue().grantId()); owner.put("grant_revision", entry.getValue().revision().toString());
            owner.put("basis_version", entry.getValue().basisVersion()); array.add(owner);
        });
        release.requireUnchanged(); releaseScopes.requireUnchanged(scope); plans.requireUnchanged(admission, task, draft, plan);
        return new Prepared(task, draft, plan, release, scope, authorizations);
    }

    Result save(EnterpriseAccess.Admission admission, Prepared prepared, TaskPlanDecisionProtocol.Request request,
            Map<String,Object> values) throws IOException {
        Objects.requireNonNull(prepared); Objects.requireNonNull(request);
        if (!"CONFIRM".equals(request.decision()) || !request.targetRevision().equals(prepared.draft().planRevision())
                || !request.baselineManifest().dependencyAuthorizationsSha256().equals(DigestUtils.sha256Hex(json.canonical(prepared.authorizations())))) mismatch();
        var head = prepared.task().head(); var input = prepared.task().input();
        String task = UUID.randomUUID().toString(), plan = UUID.randomUUID().toString();
        values.put("taskRevision", task); values.put("planRevision", plan); values.put("resultingPlanRevision", plan);
        values.put("profileRevision", head.profileRevision()); values.put("snapshotId", head.snapshotId());
        values.put("templateVersion", head.templateVersion()); values.put("agentReleaseId", head.agentReleaseId());
        values.put("inputHash", input.requestHash()); values.put("scopeHash", input.scopeHash()); values.put("requirementsHash", input.requirementsHash());
        values.put("manifest", inputs.manifestSha256()); values.put("planStatus", "CONFIRMED");
        values.put("contentHash", prepared.plan().validation().contentSha256()); values.put("content", prepared.plan().validation().canonicalContent());
        values.put("schemaRef", shared.schemaRef("artifacts", ArtifactBindings.PLAN_CANDIDATE.name()));
        values.put("expectedPlanHeadRevision", request.baselineManifest().planHeadRevision());
        values.put("resultingPlanHeadRevision", PermissionSchema.next(request.baselineManifest().planHeadRevision()));
        var fixed = json.object(); fixed.put("schema_version", "1.0.0"); fixed.put("tenant_id", head.tenantId());
        fixed.put("project_id", head.projectId()); fixed.put("task_id", head.taskId()); fixed.put("task_revision", task);
        fixed.put("parent_revision", head.taskRevision()); fixed.put("profile_revision", head.profileRevision());
        fixed.put("snapshot_id", head.snapshotId()); fixed.put("template_version", head.templateVersion());
        fixed.put("agent_release_id", head.agentReleaseId()); fixed.put("plan_revision", plan);
        fixed.put("plan_sha256", prepared.plan().validation().contentSha256()); fixed.put("input_sha256", input.requestHash());
        fixed.put("scope_sha256", input.scopeHash()); fixed.put("requirements_sha256", input.requirementsHash()); fixed.put("reason", request.reason());
        String taskHash = DigestUtils.sha256Hex(json.canonical(fixed)); values.put("taskRevisionHash", taskHash);
        values.put("versionId", task); values.put("versionKind", "TASK_REVISION"); values.put("versionHash", taskHash);
        one(mapper.insertRegisteredVersion(values)); one(mapper.insertTaskRevision(values)); one(mapper.copyTaskInput(values));
        values.put("versionId", plan); values.put("versionKind", "PLAN_REVISION"); values.put("versionHash", values.get("contentHash"));
        one(mapper.insertRegisteredVersion(values)); one(mapper.insertPlan(values)); one(mapper.insertContent(values));
        relations.save(admission, head.projectId(), head.taskId(), plan, prepared.plan().validation(), true);
        // 新计划引用原固定输入，新任务引用新计划，保存顺序与lineage均保持无环。
        var upstream = new TreeSet<>(List.of(head.taskRevision(), prepared.draft().planRevision(), head.templateVersion(), head.snapshotId(), head.profileRevision()));
        prepared.draft().content().sections().forEach(section -> upstream.addAll(section.completionRuleIds()));
        prepared.draft().content().questions().forEach(question -> upstream.addAll(question.completionRuleIds()));
        writeLineage(values, plan, "PLAN_REVISION", upstream);
        var taskParents = new TreeSet<>(List.of(head.taskRevision(), plan, head.profileRevision(), head.snapshotId(), head.templateVersion(), head.agentReleaseId()));
        taskParents.addAll(prepared.release().dependencyVersions()); writeLineage(values, task, "TASK_REVISION", taskParents);
        lineage.inspect(plan, limits.limits()); lineage.inspect(task, limits.limits());
        one(mapper.bindConfirmedPlan(values)); one(mapper.advancePlanHead(values));
        return new Result(task, plan, taskHash);
    }

    void requireUnchanged(EnterpriseAccess.Admission admission, Prepared prepared) throws IOException {
        prepared.release().requireUnchanged(); releaseScopes.requireUnchanged(prepared.scope());
        plans.requireUnchanged(admission, prepared.task(), prepared.draft(), prepared.plan());
    }

    void verifyResult(EnterpriseAccess.Admission admission, TaskService.VerifiedTask current, TaskPlanWriteProtocol.View draft,
            TaskPlanDecisionProtocol.Row decision, TaskPlanDecisionProtocol.Baseline baseline) throws IOException {
        var values = new java.util.HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", current.head().projectId()); values.put("taskId", current.head().taskId());
        values.put("taskRevision", decision.basisTaskRevision()); var original = single(mapper.decisionTask(values));
        values.put("taskRevision", decision.resultingTaskRevision()); var result = single(mapper.decisionTask(values));
        var originalInput = single(tasks.input(admission.subject().user().user_id(), original.projectId(), original.taskId(), original.taskRevision()));
        var resultInput = single(tasks.input(admission.subject().user().user_id(), result.projectId(), result.taskId(), result.taskRevision()));
        var request = inputs.create(resultInput.requestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!resultInput.equals(originalInput) || !"1.0.0".equals(resultInput.schemaVersion()) || !inputs.manifestSha256().equals(resultInput.manifest())
                || !resultInput.requestHash().equals(request.request_sha256()) || !resultInput.scopeHash().equals(request.scope_sha256())
                || !resultInput.requestHash().equals(baseline.inputSha256()) || !resultInput.scopeHash().equals(baseline.scopeSha256())
                || !resultInput.requirementsHash().equals(baseline.requirementsSha256())
                || !json.tree(request.request().scope_selection()).equals(inputs.read(resultInput.scopeJson()))
                || !resultInput.documentType().equals(request.request().document_type()) || !resultInput.ownerId().equals(request.request().owner_id())
                || !resultInput.goal().equals(request.request().goal()) || !Objects.equals(resultInput.basedOnDocumentRevision(), request.request().based_on_document_revision())
                || !resultInput.requirementsHash().equals(DigestUtils.sha256Hex(json.canonical(inputs.read(resultInput.missingRequirements()))))
                || !inputs.read(resultInput.missingRequirements()).isArray() || !inputs.read(resultInput.missingRequirements()).isEmpty()
                || !baseline.taskRevisionSha256().equals(original.contentSha256()) || !original.contentSha256().equals(original.registeredSha256())
                || !"TASK_REVISION".equals(original.registeredKind()) || !original.projectId().equals(original.registeredProjectId())
                || !result.tenantId().equals(admission.context().tenant_id()) || !result.projectId().equals(current.head().projectId())
                || !result.taskId().equals(current.head().taskId()) || !result.taskRevision().equals(decision.resultingTaskRevision())
                || !Objects.equals(result.planRevision(), decision.resultingPlanRevision()) || !Objects.equals(result.parentRevision(), original.taskRevision())
                || !result.profileRevision().equals(baseline.profileRevision()) || !result.snapshotId().equals(baseline.snapshotId())
                || !result.templateVersion().equals(baseline.templateVersion()) || !result.agentReleaseId().equals(original.agentReleaseId())
                || !result.projectId().equals(request.request().project_id()) || !result.profileRevision().equals(request.request().profile_revision())
                || !result.templateVersion().equals(request.request().template_version()) || !result.agentReleaseId().equals(request.request().agent_release_id())
                || !result.reason().equals(decision.reason()) || !result.createdBy().equals(decision.createdBy())
                || !result.createdAt().equals(decision.createdAt()) || !result.classification().equals(decision.classification())
                || !result.projectId().equals(result.registeredProjectId()) || !"TASK_REVISION".equals(result.registeredKind())
                || !result.contentSha256().equals(result.registeredSha256())) mismatch();
        var plan = plans.readFixed(admission, current, decision.resultingPlanRevision(), false);
        if (!plan.taskRevision().equals(result.taskRevision()) || !java.util.Set.of("CONFIRMED", "SUPERSEDED").contains(plan.status())
                || !plan.content().equals(draft.content()) || !plan.contentSha256().equals(draft.contentSha256())) mismatch();
        var storedPlan = single(tasks.plan(admission.subject().user().user_id(), result.projectId(), result.taskId(), result.planRevision()));
        if (!decision.decisionId().equals(storedPlan.decisionId())) mismatch();
        var fixed = json.object(); fixed.put("schema_version", "1.0.0"); fixed.put("tenant_id", result.tenantId());
        fixed.put("project_id", result.projectId()); fixed.put("task_id", result.taskId()); fixed.put("task_revision", result.taskRevision());
        fixed.put("parent_revision", result.parentRevision()); fixed.put("profile_revision", result.profileRevision());
        fixed.put("snapshot_id", result.snapshotId()); fixed.put("template_version", result.templateVersion());
        fixed.put("agent_release_id", result.agentReleaseId()); fixed.put("plan_revision", result.planRevision()); fixed.put("plan_sha256", plan.contentSha256());
        fixed.put("input_sha256", resultInput.requestHash()); fixed.put("scope_sha256", resultInput.scopeHash());
        fixed.put("requirements_sha256", resultInput.requirementsHash()); fixed.put("reason", result.reason());
        if (!result.contentSha256().equals(DigestUtils.sha256Hex(json.canonical(fixed)))) mismatch();
    }

    private void writeLineage(Map<String,Object> values, String consumer, String kind, TreeSet<String> upstream) {
        if (upstream.size() > limits.maximumRelations()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        values.put("consumerVersion", consumer); values.put("relationKind", kind); values.put("lineageDecisionId", values.get("decisionId"));
        for (String id : upstream) { if (id.equals(consumer)) mismatch(); values.put("upstreamVersion", id); one(mapper.insertLineage(values)); }
    }
    private JsonNode grant(PermissionProtocol.GrantRow value) throws IOException {
        var result = json.object(); result.put("grant_id", value.grantId()); result.put("subject_id", value.subjectId());
        result.set("project_id", json.tree(value.projectId())); result.put("resource_kind", value.resourceKind());
        result.set("resource_id", json.tree(value.resourceId())); result.put("action", value.action()); result.set("condition", inputs.read(value.condition()));
        result.put("status", value.status()); result.put("valid_from", utc(value.validFrom()));
        result.set("valid_to", json.tree(value.validTo() == null ? null : utc(value.validTo())));
        result.put("basis_version", value.basisVersion()); result.put("schema_version", value.schemaVersion());
        result.put("schema_manifest_sha256", value.manifest()); result.put("revision", value.revision().toString()); return result;
    }
    private static String utc(java.time.LocalDateTime value) { return value.toInstant(java.time.ZoneOffset.UTC).toString(); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static <T> T single(List<T> values) { if (values.size() != 1) mismatch(); return values.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
