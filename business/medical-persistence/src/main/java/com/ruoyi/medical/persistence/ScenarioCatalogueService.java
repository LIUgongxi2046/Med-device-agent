package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.ScenarioCatalogueMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class ScenarioCatalogueService {
    private static final String PROJECT_PURPOSE = "PROJECT_READING";
    private static final String CONFIGURATION_PURPOSE = "CONFIGURATION_READING";
    private static final List<String> DEFINITION_FIELDS = List.of("ROLES", "INPUTS", "APPLICABLE_KNOWLEDGE", "TASK_RULES",
            "OUTPUTS", "HUMAN_REVIEW", "FAILURE_STATES", "ACCEPTANCE_SAMPLES");
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final ScenarioCatalogueMapper mapper;
    private final TemplateDependencyPolicy policy;
    private final PermissionSchema json;
    private final ScenarioCatalogueSchema schema;

    public ScenarioCatalogueService(EnterpriseAccess access, ResourceReadAuthority resources, PermissionMapper permissions,
            ScenarioCatalogueMapper mapper, TemplateDependencyPolicy policy, PermissionSchema json, ScenarioCatalogueSchema schema) {
        this.access = access; this.resources = resources; this.permissions = permissions; this.mapper = mapper;
        this.policy = policy; this.json = json; this.schema = schema;
    }

    JsonNode read(ApplicationSessionIdentity.Verified subject, String context, ScenarioCatalogueSchema.Query query) {
        PermissionSchema.revision(context);
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) fail(ErrorCode.CONTEXT_CHANGED);
            if (query.projectId() != null) requireProject(admission, query.projectId());
            // 文稿适用性需要完整场景规格，元数据不能推定该资格。
            if (query.documentType() != null) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var all = authorizedVersions(admission, query.projectId());
            var planning = plannedRequirements();
            var filter = json.object(); filter.put("schema_version", "1.0.0"); filter.put("tenant_id", admission.context().tenant_id());
            filter.put("actor_id", subject.user().user_id()); filter.put("context_revision", context);
            filter.put("auth_epoch", admission.watermarks().authEpoch().toString()); filter.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
            filter.put("project_id", query.projectId()); filter.put("document_type", query.documentType()); filter.put("limit", query.limit());
            String filterHash = DigestUtils.sha256Hex(json.canonical(filter));
            var dataset = json.object(); dataset.put("filter_sha256", filterHash); dataset.set("items", json.tree(all));
            dataset.set("planned_requirements", planning);
            String watermark = DigestUtils.sha256Hex(json.canonical(dataset));
            if (query.datasetWatermark() != null && !watermark.equals(query.datasetWatermark())) fail(ErrorCode.CURSOR_EXPIRED);
            int start = 0;
            if (query.after() != null) {
                int found = -1;
                for (int index = 0; index < all.size(); index++) if (query.after().equals(all.get(index).required("version_id").textValue())) found = index;
                if (found < 0) fail(ErrorCode.CURSOR_INVALID); start = found + 1;
            }
            int end = Math.min(all.size(), start + query.limit());
            var result = filter.deepCopy(); result.remove("actor_id"); result.put("total", Integer.toString(all.size()));
            result.set("items", json.tree(all.subList(start, end))); result.set("planned_requirements", planning);
            result.set("next_after", json.tree(end < all.size() ? all.get(end - 1).required("version_id").textValue() : null));
            result.put("filter_sha256", filterHash); result.put("dataset_watermark", watermark);
            if (query.projectId() != null) requireProject(admission, query.projectId());
            for (var item : all) {
                String project = item.required("project_id").isNull() ? null : item.required("project_id").textValue();
                resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project,
                        "CONFIGURATION", item.required("configuration_id").textValue()), "configuration.read", CONFIGURATION_PURPOSE);
                if (project != null) requireProject(admission, project);
            }
            return schema.envelope(result, UUID.randomUUID().toString());
        }).get());
    }

    private ArrayList<ObjectNode> authorizedVersions(EnterpriseAccess.Admission admission, String project) throws IOException {
        int maximum = policy.limits().maximumVersions();
        if (maximum == Integer.MAX_VALUE) fail(ErrorCode.CONTEXT_LIMIT);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("projectId", project); values.put("limit", maximum + 1);
        var rows = mapper.versions(values); if (rows.size() > maximum) fail(ErrorCode.CONTEXT_LIMIT);
        var items = new ArrayList<ObjectNode>(); String previous = null; String tenant = admission.context().tenant_id();
        for (var row : rows) {
            var scope = new PermissionProtocol.Scope(tenant, row.projectId(), "CONFIGURATION", row.configurationId());
            if (resources.find(admission, scope, "configuration.read", CONFIGURATION_PURPOSE).isEmpty()) continue;
            if (row.projectId() != null) {
                var projectScope = new PermissionProtocol.Scope(tenant, row.projectId(), "PROJECT", row.projectId());
                if (resources.find(admission, projectScope, "project.read", PROJECT_PURPOSE).isEmpty()
                        || !List.of("ACTIVE").equals(permissions.projectStatus(row.projectId()))) continue;
            }
            WireValues.uuid(row.configurationId()); WireValues.uuid(row.versionId()); WireValues.hash(row.contentSha256());
            if (!tenant.equals(row.tenantId()) || !Objects.equals(row.projectId(), row.registeredProjectId())
                    || !"CAPABILITY_VERSION".equals(row.registeredKind()) || !row.contentSha256().equals(row.registeredSha256())
                    || project != null && row.projectId() != null && !project.equals(row.projectId())
                    || row.configurationRevision() == null || row.configurationRevision().signum() <= 0
                    || previous != null && previous.compareTo(row.versionId()) >= 0) fail(ErrorCode.CONTENT_MISMATCH);
            previous = row.versionId();
            var item = json.object(); item.put("configuration_id", row.configurationId()); item.put("version_id", row.versionId());
            item.put("project_id", row.projectId()); item.put("name", row.name()); item.put("status", row.status());
            item.put("content_sha256", row.contentSha256()); item.put("configuration_revision", row.configurationRevision().toString());
            item.put("definition_status", "UNVERIFIED"); item.putNull("task_entry");
            var missing = item.putArray("missing_requirements"); missing.add("SCENARIO_SPECIFICATION_UNVERIFIED"); missing.add("RELEASE_ADMISSION_UNAVAILABLE");
            if ("DISABLED".equals(row.status())) missing.add("SCENARIO_DISABLED");
            items.add(item);
        }
        return items;
    }

    private void requireProject(EnterpriseAccess.Admission admission, String project) throws IOException {
        WireValues.uuid(project);
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, "PROJECT", project), "project.read", PROJECT_PURPOSE);
        if (!List.of("ACTIVE").equals(permissions.projectStatus(project))) fail(ErrorCode.ACCESS_DENIED);
    }

    private JsonNode plannedRequirements() {
        // PRD规划要求与数据库配置版本分别返回，规划说明不生成业务版本。
        var result = json.object().arrayNode();
        List<List<String>> definitions = List.of(
                List.of("REGULATION_COMPARISON", "法规版本比较与变化解读", "比较法规版本并说明变化及其适用影响"),
                List.of("REPORT_GENERATION", "报告生成", "按照独立报告规格组织输入、证据和报告内容"),
                List.of("TRANSLATION", "翻译", "按照语言、术语和人工审查要求翻译获授权内容"),
                List.of("MARKET_ANALYSIS", "市场分析", "按照独立分析范围和资料要求形成市场分析"),
                List.of("MULTIMODAL_QA", "多模态问答", "按照输入类型、知识范围和证据要求回答问题"));
        for (var definition : definitions) {
            var item = result.addObject(); item.put("scenario_id", definition.get(0)); item.put("name", definition.get(1)); item.put("purpose", definition.get(2));
            item.put("status", "PLANNED"); item.set("required_definition_fields", json.tree(DEFINITION_FIELDS)); item.putNull("task_entry");
        }
        return result;
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
