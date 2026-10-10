package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Objects;

public final class SourceReadProtocol {
    private SourceReadProtocol() { }
    public enum Use { REVIEW, HISTORY, FORMAL, MODEL_SEND, TEMPLATE }
    public record SourceRow(String tenantId, String sourceId, String sourceVersion, String ownerProjectId, String ownership,
            String classification, String rightsRevision, String originalSha256, String registeredSha256, String registeredProjectId) { }
    public record RightsRow(String tenantId, String rightsRevision, String sourceVersion, String purpose, String allowedActions,
            String providers, String regions, LocalDateTime validFrom, LocalDateTime validTo, String status, String evidenceManifest,
            String contentSha256, String schemaVersion, String schemaManifest, String classification,
            String registeredSha256, String registeredProjectId) { }
    public record SelectionRow(String tenantId, String projectId, String snapshotId, String sourceId, String sourceVersion,
            String parseVersion, String indexVersion, String approvalRevision, String bindingRevision, String parseStatus,
            String indexStatus, BigInteger expectedCount, BigInteger verifiedCount, String currentApprovalRevision,
            String uploadStatus, String approvalStatus, String lifecycle, BigInteger gateRevision, String decision,
            String reviewedManifestSha256, String applicability, String projectStatus) { }
    public record GateRow(String parseVersion, String uploadStatus, String approvalStatus, String lifecycle, BigInteger revision) { }
    public record ConversationRow(String tenantId, String projectId, String conversationId, String sourceId,
            String ownerId, String status, String classification, BigInteger revision) { }
    public record ParentRow(String consumerVersionId, String upstreamVersionId, String relationKind) { }
    public record CollectionMember(String tenantId, String collectionVersion, String sourceVersion, String parseVersion) { }
    public record SnapshotRequirement(String sourceVersion, String rightsRevision, String projectId, String parseVersion,
            String indexVersion, String approvalRevision, String bindingRevision, String purpose, String environment, String region) {
        public SnapshotRequirement {
            WireValues.uuid(projectId); WireValues.uuid(parseVersion); WireValues.uuid(indexVersion);
            WireValues.uuid(approvalRevision); WireValues.uuid(bindingRevision);
            new ReadRequirement(sourceVersion, rightsRevision, projectId, null, null, null, Use.REVIEW, purpose, environment, region, null);
        }
    }
    public record ReadRequirement(String sourceVersion, String rightsRevision, String projectId, String snapshotId, String parseVersion,
            String conversationId, Use use, String purpose, String environment, String region, String provider) {
        public ReadRequirement {
            WireValues.uuid(sourceVersion); WireValues.uuid(rightsRevision); Objects.requireNonNull(use);
            if (projectId != null) WireValues.uuid(projectId);
            if (snapshotId != null) WireValues.uuid(snapshotId);
            if (parseVersion != null) WireValues.uuid(parseVersion);
            if (conversationId != null) WireValues.uuid(conversationId);
            if (use != Use.REVIEW && use != Use.TEMPLATE && (projectId == null || snapshotId == null || parseVersion == null)) throw new IllegalArgumentException("使用来源需要固定项目、范围和解析版本");
            if (use == Use.TEMPLATE && (projectId != null || snapshotId != null || parseVersion != null || conversationId != null))
                throw new IllegalArgumentException("企业模板依据不能使用项目或会话来源");
            if ((snapshotId == null) != (parseVersion == null)) throw new IllegalArgumentException("范围和解析版本必须同时提供");
            if (snapshotId != null && projectId == null || conversationId != null && projectId == null) throw new IllegalArgumentException("来源范围缺少项目");
            if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("来源用途无效");
            requiredText(environment, 128); requiredText(region, 128);
            if (use == Use.MODEL_SEND) requiredText(provider, 128);
            else if (provider != null) throw new IllegalArgumentException("内部来源读取不指定外部提供方");
        }
    }
    record VerifiedSource(SourceRow source, RightsRow rights, SelectionRow selection, ConversationRow conversation,
            PermissionProtocol.GrantRow sourceGrant, PermissionProtocol.GrantRow projectGrant,
            PermissionProtocol.GrantRow externalGrant) { }

    private static void requiredText(String value, int maximum) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("来源使用参数不能为空");
        WireValues.boundedString(value, maximum);
    }
}
