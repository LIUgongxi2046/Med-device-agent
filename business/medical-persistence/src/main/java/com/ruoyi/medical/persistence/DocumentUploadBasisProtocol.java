package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DocumentUploadBasisProtocol {
    private DocumentUploadBasisProtocol() { }
    public record TaskRow(String tenantId, String projectId, String taskId, String status, String currentRevision,
            String ownerId, String createdBy, LocalDateTime createdAt, String classification, BigInteger revision, LocalDateTime updatedAt) { }
    public record TaskRevisionRow(String tenantId, String projectId, String taskRevision, String taskId, String profileRevision,
            String snapshotId, String templateVersion, String agentReleaseId, String planRevision, String parentRevision,
            String reason, String contentSha256, String createdBy, LocalDateTime createdAt, String classification) { }
    public record PlanRow(String tenantId, String projectId, String planRevision, String taskId, String taskRevision,
            String templateVersion, String snapshotId, String status, String decisionId, String contentSha256,
            String createdBy, LocalDateTime createdAt, String classification) { }
    public record SnapshotRow(String tenantId, String projectId, String snapshotId, String manifest, String manifestSha256,
            BigInteger authEpoch, BigInteger governanceEpoch, LocalDateTime createdAt) { }
    public record MemberRow(String tenantId, String projectId, String snapshotId, String sourceId, String sourceVersion,
            String parseVersion, String indexVersion, String approvalRevision, String bindingRevision) { }
    public record Settings(String environment, String region, int maximumSources) {
        public Settings {
            if (environment == null || environment.isBlank() || region == null || region.isBlank()
                    || maximumSources <= 0 || maximumSources >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("上传依据的环境、区域及完整来源容量必须明确配置");
            WireValues.boundedString(environment, 128); WireValues.boundedString(region, 128);
        }
    }
    public record Binding(String taskId, String taskRevision, String planRevision, String snapshotId,
            String basisSha256, Settings settings) {
        public Binding {
            if (taskId == null || taskRevision == null || planRevision == null || snapshotId == null || basisSha256 == null || settings == null)
                throw new IllegalArgumentException("上传绑定必须具有完整实际任务、计划、快照、摘要和服务器配置");
            WireValues.uuid(taskId); WireValues.uuid(taskRevision); WireValues.uuid(planRevision); WireValues.uuid(snapshotId);
            WireValues.hash(basisSha256); Objects.requireNonNull(settings);
        }
    }
    record Basis(UploadTransferProtocol.DocumentRow document, TaskRow task, TaskRevisionRow taskRevision, PlanRow plan,
            SnapshotRow snapshot, List<MemberRow> members, List<SourceReadProtocol.ReadRequirement> sources,
            Map<String, SourceReadProtocol.VerifiedSource> verifiedSources) {
        Basis { members = List.copyOf(members); sources = List.copyOf(sources); verifiedSources = Map.copyOf(verifiedSources); }
    }
}
