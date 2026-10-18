package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.RunCreateMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class RunCreateBudget {
    private final RunCreateMapper mapper;
    private final BudgetSchema budgets;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    public RunCreateBudget(RunCreateMapper mapper, BudgetSchema budgets, PermissionSchema json, ResourceReadAuthority resources) {
        this.mapper = mapper; this.budgets = budgets; this.json = json; this.resources = resources;
    }
    JsonNode require(EnterpriseAccess.Admission admission, Map<String,Object> values, LocalDateTime now, String currency) throws IOException {
        var rows = mapper.budget(values); if (rows.size() != 1) fail(ErrorCode.ACCESS_DENIED);
        var row = budgets.read(rows.get(0)); var content = json.canonical(row.required("approval")); var approval = budgets.approval(content);
        String project = row.required("project_id").textValue(), hash = DigestUtils.sha256Hex(content);
        if (!admission.context().tenant_id().equals(text(row, "tenant_id")) || !approval.tenant_id().equals(text(row, "tenant_id"))
                || !(project == null || project.equals(values.get("projectId"))) || !Objects.equals(project, approval.project_id())
                || !approval.budget_id().equals(text(row, "budget_id")) || !values.get("budgetRevision").equals(text(row, "budget_revision"))
                || !approval.currency().equals(text(row, "currency")) || !approval.currency().equals(text(row, "account_currency"))
                || !approval.approver_id().equals(text(row, "approver_id")) || !approval.reason().equals(text(row, "reason"))
                || !hash.equals(text(row, "content_sha256")) || !hash.equals(text(row, "registry_sha256"))
                || !"1.0.0".equals(text(row, "schema_version")) || !budgets.manifestSha256().equals(text(row, "schema_manifest"))) fail(ErrorCode.CONTENT_MISMATCH);
        if (!values.get("budgetRevision").equals(text(row, "current_budget_revision"))) fail(ErrorCode.REVISION_CONFLICT);
        if (!currency.equals(approval.currency())) fail(ErrorCode.MODEL_POLICY_INCOMPLETE);
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), project, "BUDGET", approval.budget_id()), "budget.read", "BUDGET_READING");
        requireAvailable(row, approval.limits(), now);
        return row;
    }
    static void requireAvailable(JsonNode row, BudgetProtocol.Limits limits, LocalDateTime now) {
        if (!now.isBefore(PermissionAuthority.time(limits.deadline_at()))) fail(ErrorCode.BUDGET_EXCEEDED);
        if (BudgetSchema.money(text(row, "spent")).add(BudgetSchema.money(text(row, "reserved"))).compareTo(BudgetSchema.money(limits.amount())) >= 0
                || PermissionSchema.revision(text(row, "spent_tokens")).add(PermissionSchema.revision(text(row, "reserved_tokens")))
                    .compareTo(PermissionSchema.revision(limits.tokens())) >= 0
                || PermissionSchema.revision(text(row, "spent_calls")).add(PermissionSchema.revision(text(row, "reserved_calls")))
                    .compareTo(PermissionSchema.revision(limits.calls())) >= 0) fail(ErrorCode.BUDGET_EXCEEDED);
        // Admission is not a charge or a reservation. Each paid action must reserve against the same account before dispatch.
    }
    private static String text(JsonNode row, String field) {
        var value = row.required(field); if (!value.isTextual()) fail(ErrorCode.CONTENT_MISMATCH); return value.textValue();
    }
    private static void fail(ErrorCode code) { throw new TenantAccessException(code); }
}
