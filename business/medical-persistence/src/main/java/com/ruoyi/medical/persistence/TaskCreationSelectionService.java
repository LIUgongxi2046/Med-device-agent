package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskPlanWriteMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class TaskCreationSelectionService {
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final TaskPlanWriteMapper mapper;
    private final TaskPlanService plans;
    private final PermissionSchema json;
    private final TaskCreationSelectionSchema schema;

    public TaskCreationSelectionService(EnterpriseAccess access, ResourceReadAuthority resources, PermissionMapper permissions,
            TaskPlanWriteMapper mapper, TaskPlanService plans, PermissionSchema json, TaskCreationSelectionSchema schema) {
        this.access = access; this.resources = resources; this.permissions = permissions; this.mapper = mapper;
        this.plans = plans; this.json = json; this.schema = schema;
    }

    JsonNode owners(ApplicationSessionIdentity.Verified subject, String context, String project, TaskCreationSelectionSchema.Query query) {
        PermissionSchema.revision(context); WireValues.uuid(project);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project);
            resources.require(admission, scope, "project.read", "TASK_CREATION");
            var grant = resources.require(admission, scope, "task.create", "TASK_CREATION");
            if (!List.of("ACTIVE").equals(permissions.projectStatus(project))) fail(ErrorCode.ACCESS_DENIED);
            var filter = json.object(); filter.put("schema_version", "1.0.0"); filter.put("tenant_id", admission.context().tenant_id());
            filter.put("project_id", project); filter.put("actor_id", subject.user().user_id()); filter.put("context_revision", context);
            filter.put("auth_epoch", admission.watermarks().authEpoch().toString()); filter.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
            filter.put("limit", query.limit()); String watermark = DigestUtils.sha256Hex(json.canonical(filter));
            if (query.datasetWatermark() != null && !watermark.equals(query.datasetWatermark())) fail(ErrorCode.CURSOR_EXPIRED);
            var values = new HashMap<String,Object>(); values.put("userId", subject.user().user_id()); values.put("projectId", project);
            values.put("after", query.after()); values.put("limit", query.limit() + 1);
            var candidates = mapper.creationOwnerCandidates(values);
            if (candidates.size() > query.limit() + 1) fail(ErrorCode.CONTENT_MISMATCH);
            var items = json.object().arrayNode(); var ids = new HashSet<String>(); String previous = query.after(), last = null;
            for (var candidate : candidates.subList(0, Math.min(query.limit(), candidates.size()))) {
                WireValues.uuid(candidate.userId()); WireValues.uuid(candidate.memberId());
                if (!admission.context().tenant_id().equals(candidate.tenantId()) || !ids.add(candidate.userId())
                        || previous != null && previous.compareTo(candidate.userId()) >= 0) fail(ErrorCode.CONTENT_MISMATCH);
                previous = candidate.userId(); last = previous;
                if (plans.creationOwnerEligible(admission, project, candidate.userId())) {
                    var item = json.object(); item.put("member_id", candidate.memberId()); item.put("user_id", candidate.userId());
                    if (candidate.revision().signum() <= 0) fail(ErrorCode.CONTENT_MISMATCH);
                    item.put("member_revision", candidate.revision().toString()); items.add(item);
                }
            }
            // 游标推进经过完整资格检查的候选集合；空页仍明确保留下一页入口。
            var result = filter.deepCopy(); result.remove("actor_id"); result.set("items", items);
            result.set("next_after", json.tree(candidates.size() > query.limit() ? last : null)); result.put("dataset_watermark", watermark);
            if (!grant.equals(resources.require(admission, scope, "task.create", "TASK_CREATION"))) fail(ErrorCode.SCOPE_STALE);
            return schema.envelope(result, UUID.randomUUID().toString());
        }).get());
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
