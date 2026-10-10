package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.medical.contracts.ArtifactProtocol;

public final class TaskPlanWriteProtocol {
    private TaskPlanWriteProtocol() { }

    public record Head(String tenantId, String projectId, String taskId, String planRevision, BigInteger revision,
            String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt, String classification) { }

    public record View(String schemaVersion, String tenantId, String projectId, String taskId, String taskRevision,
            String planRevision, String snapshotId, String templateVersion, String profileRevision, String status,
            ArtifactProtocol.PlanCandidate content, String contentSha256, String currentPlanRevision, String currentPlanHeadRevision) { }

    public record Basis(String schemaVersion, String tenantId, String projectId, String taskId, String taskRevision,
            String snapshotId, String templateVersion, String profileRevision, TemplateProtocol.Content templateContent,
            List<TemplateApplicabilityProtocol.ConditionResult> conditions, List<String> completionRuleIds) {
        public Basis { conditions = List.copyOf(conditions); completionRuleIds = List.copyOf(completionRuleIds); }
    }
}
