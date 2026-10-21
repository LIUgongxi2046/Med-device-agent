package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.CollectionMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskScopeSelectionService {
    private static final String PURPOSE = "TASK_CREATION";
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final ScopeSelectionAuthority selections;
    private final CollectionAuthority collections;
    private final CollectionMapper mapper;
    private final TemplateDependencyPolicy policy;
    private final PermissionSchema json;
    private final TaskScopeSelectionSchema schema;
    private final ObjectProvider<AgentPublicationAuthority> publications;
    private final AgentExecutionPolicyAuthority executionPolicies;

    public TaskScopeSelectionService(EnterpriseAccess access, ResourceReadAuthority resources, PermissionMapper permissions,
            ScopeSelectionAuthority selections, CollectionAuthority collections, CollectionMapper mapper,
            TemplateDependencyPolicy policy, PermissionSchema json, TaskScopeSelectionSchema schema,
            ObjectProvider<AgentPublicationAuthority> publications, AgentExecutionPolicyAuthority executionPolicies) {
        this.access = access; this.resources = resources; this.permissions = permissions; this.selections = selections;
        this.collections = collections; this.mapper = mapper; this.policy = policy; this.json = json; this.schema = schema;
        this.publications = publications; this.executionPolicies = executionPolicies;
    }

    JsonNode choices(ApplicationSessionIdentity.Verified subject, String context, String project, String kind, TaskScopeSelectionSchema.Query query) {
        PermissionSchema.revision(context); WireValues.uuid(project);
        if (!List.of("SOURCE", "COLLECTION").contains(kind)) throw new IllegalArgumentException("资料选择类型无效");
        // Publication object evidence is prepared before opening the enterprise transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) fail(ErrorCode.ACCESS_DENIED);
        var authority = publications.getIfAvailable();
        if (authority == null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var prepared = ThrowingSupplier.of(() -> authority.prepare(subject, context, query.agentReleaseId(), PURPOSE, false)).get();
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project);
            var read = resources.require(admission, scope, "project.read", PURPOSE);
            var create = resources.require(admission, scope, "task.create", PURPOSE);
            if (!List.of("ACTIVE").equals(permissions.projectStatus(project))) fail(ErrorCode.ACCESS_DENIED);
            var published = authority.require(admission, query.agentReleaseId(), PURPOSE, prepared);
            var execution = executionPolicies.requireByVersion(admission, published.executionPolicyId(),
                    published.executionPolicySha256(), project, PURPOSE);
            AgentExecutionPolicyAuthority.requireManifest(execution.definition(), published.manifest().manifest());
            var releaseScope = published.manifest().manifest().releaseScope();
            if (!admission.context().tenant_id().equals(releaseScope.tenantId())
                    || "PROJECTS".equals(releaseScope.projectMode()) && !releaseScope.projectIds().contains(project)) fail(ErrorCode.ACCESS_DENIED);
            execution.requireConsumer(admission, project, PURPOSE);
            if (!policy.environment().equals(execution.definition().environment())
                    || !policy.region().equals(execution.definition().region())
                    || "SIMULATION".equals(execution.definition().environment())) fail(ErrorCode.ACCESS_DENIED);
            PublishedTaskCreateAdmission.requireSelection(execution.definition(), List.of());
            var all = "SOURCE".equals(kind) ? sourceChoices(admission, project, execution) : collectionChoices(admission, project, execution);
            all.sort(Comparator.comparing(item -> item.required("version_id").textValue()));
            String previous = null;
            for (var item : all) {
                String id = item.required("version_id").textValue(); WireValues.uuid(id);
                if (id.equals(previous)) fail(ErrorCode.SOURCE_NOT_READY); previous = id;
            }
            var filter = json.object(); filter.put("schema_version", "1.0.0"); filter.put("tenant_id", admission.context().tenant_id());
            filter.put("project_id", project); filter.put("actor_id", subject.user().user_id()); filter.put("context_revision", context);
            filter.put("auth_epoch", admission.watermarks().authEpoch().toString()); filter.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
            filter.put("scope_kind", kind); filter.put("agent_release_id", query.agentReleaseId()); filter.put("limit", query.limit());
            String filterHash = DigestUtils.sha256Hex(json.canonical(filter));
            var dataset = json.object(); dataset.put("filter_sha256", filterHash); dataset.set("items", json.tree(all));
            String watermark = DigestUtils.sha256Hex(json.canonical(dataset));
            if (query.datasetWatermark() != null && !watermark.equals(query.datasetWatermark())) fail(ErrorCode.CURSOR_EXPIRED);
            int start = 0;
            if (query.after() != null) {
                int found = -1;
                for (int index = 0; index < all.size(); index++) if (query.after().equals(all.get(index).required("version_id").textValue())) found = index;
                if (found < 0) fail(ErrorCode.CURSOR_INVALID); start = found + 1;
            }
            int end = Math.min(all.size(), start + query.limit());
            var result = filter.deepCopy(); result.remove("actor_id"); result.set("items", json.tree(all.subList(start, end)));
            result.set("next_after", json.tree(end < all.size() ? all.get(end - 1).required("version_id").textValue() : null));
            result.put("filter_sha256", filterHash); result.put("dataset_watermark", watermark);
            if (!read.equals(resources.require(admission, scope, "project.read", PURPOSE))
                    || !create.equals(resources.require(admission, scope, "task.create", PURPOSE))) fail(ErrorCode.SCOPE_STALE);
            published.requireUnchanged(); execution.requireConsumer(admission, project, PURPOSE);
            return schema.envelope(result, UUID.randomUUID().toString());
        }).get());
    }

    private ArrayList<ObjectNode> sourceChoices(EnterpriseAccess.Admission admission, String project, AgentExecutionPolicyAuthority.VerifiedPolicy execution) throws IOException {
        var selected = selections.requireForTaskCreation(admission, project, new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.PROJECT_APPROVED,
                List.of(), List.of(), false, null, null), execution, policy.environment(), policy.region(), policy.limits());
        return sourceItems(selected);
    }

    private ArrayList<ObjectNode> sourceItems(ScopeSelectionAuthority.Selection selected) {
        var items = new ArrayList<ObjectNode>();
        for (var verified : selected.sources()) {
            var source = verified.source(); var actual = verified.selection();
            var item = json.object(); item.put("kind", "SOURCE"); item.put("version_id", source.sourceVersion()); item.put("source_id", source.sourceId());
            item.put("rights_revision", source.rightsRevision()); item.put("parse_version", actual.parseVersion()); item.put("index_version", actual.indexVersion());
            item.put("approval_revision", actual.approvalRevision()); item.put("binding_revision", actual.bindingRevision());
            item.put("gate_revision", actual.gateRevision().toString()); item.put("original_sha256", source.originalSha256()); items.add(item);
        }
        items.sort(Comparator.comparing(item -> item.required("version_id").textValue()));
        return items;
    }

    private ArrayList<ObjectNode> collectionChoices(EnterpriseAccess.Admission admission, String project, AgentExecutionPolicyAuthority.VerifiedPolicy execution) throws IOException {
        int maximum = policy.limits().maximumVersions();
        if (maximum == Integer.MAX_VALUE) fail(ErrorCode.CONTEXT_LIMIT);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", project); values.put("limit", maximum + 1);
        var candidates = mapper.selectionVersions(values); if (candidates.size() > maximum) fail(ErrorCode.CONTEXT_LIMIT);
        var items = new ArrayList<ObjectNode>();
        for (var candidate : candidates) {
            String ownerProject = candidate.registeredProjectId(), tenant = admission.context().tenant_id();
            if (!tenant.equals(candidate.tenantId()) || ownerProject != null && !project.equals(ownerProject)) fail(ErrorCode.CONTENT_MISMATCH);
            var scope = new PermissionProtocol.Scope(tenant, ownerProject, ownerProject == null ? "ENTERPRISE" : "PROJECT", ownerProject == null ? tenant : ownerProject);
            if (resources.find(admission, scope, "collection.read", PURPOSE).isEmpty()) continue;
            var proof = collections.require(admission, candidate.versionId(), execution.businessPurpose(), policy.environment(), policy.region(), policy.limits());
            // A collection is selectable only as a whole, never by silently dropping fixed members.
            if (!execution.definition().sourceSelection().sources().containsAll(proof.manifest().members().stream()
                    .map(member -> new PermissionProtocol.Basis(member.source_version(), member.original_sha256())).toList())) continue;
            // 资料集读取保留固定成员；查询同时核验全部成员在当前任务中具有正式来源资格。
            var selected = selections.requireForTaskCreation(admission, project, new CommonProtocol.SourceScope(CommonProtocol.ScopeMode.COLLECTION_VERSIONS,
                    List.of(candidate.versionId()), List.of(), false, null, null), execution, policy.environment(), policy.region(), policy.limits());
            var item = json.object(); item.put("kind", "COLLECTION"); item.put("version_id", proof.version().versionId());
            item.put("collection_id", proof.head().collectionId()); item.put("title", proof.manifest().title());
            item.put("content_sha256", proof.version().contentSha256()); item.put("member_count", proof.manifest().members().size());
            item.put("source_scope_sha256", DigestUtils.sha256Hex(json.canonical(json.tree(sourceItems(selected))))); items.add(item);
        }
        return items;
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
