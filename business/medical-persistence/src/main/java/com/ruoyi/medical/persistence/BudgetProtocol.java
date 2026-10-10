package com.ruoyi.medical.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

public final class BudgetProtocol {
    private BudgetProtocol() { }
    public record Limits(String amount, String tokens, String calls, String research_rounds, String revision_rounds, String deadline_at) { }
    public record Create(String schema_version, String project_id, String currency, Limits limits,
            PermissionProtocol.Basis approval_basis, String reason, boolean approval_attested) { }
    public record Decide(String schema_version, String expected_revision, String based_on_budget_revision, String currency,
            Limits limits, PermissionProtocol.Basis approval_basis, String reason, boolean approval_attested) { }
    public record Approval(String schema_version, String tenant_id, String project_id, String budget_id, String currency,
            Limits limits, PermissionProtocol.Basis approval_basis, String approver_id, String reason,
            String authority_grant_id, String authority_revision, String based_on_budget_revision) { }
    public record Account(String tenantId, String projectId, String budgetId, String approvalId, String currency,
            BigDecimal spent, BigDecimal reserved, BigInteger spentTokens, BigInteger reservedTokens, BigInteger spentCalls,
            BigInteger reservedCalls, String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) { }
    public record Revision(String projectId, String approvalId, String budgetId, String currency, String definition,
            String approverId, String reason, String sha256, String schemaVersion, String manifest,
            String createdBy, LocalDateTime createdAt, String classification) { }
    public record Scope(String projectId, String kind, String resourceId) {
        boolean permits(String project, String budget) {
            return switch (kind) {
                case "ENTERPRISE" -> true;
                case "PROJECT" -> projectId != null && projectId.equals(project);
                case "BUDGET" -> (projectId == null || projectId.equals(project)) && (resourceId == null || resourceId.equals(budget));
                default -> false;
            };
        }
    }
    public record Query(int limit, String after, String project) { }
}
