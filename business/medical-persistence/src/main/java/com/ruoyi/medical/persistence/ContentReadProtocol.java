package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ContentReadProtocol {
    private ContentReadProtocol() { }

    public record HeaderRow(String tenantId, String projectId, String receiptId, String resourceId, String artifactId,
            String subjectId, String runId, String stepId, BigInteger leaseGeneration, String selectionSha256,
            String deliveredSha256, String contentSha256, BigInteger authEpoch, BigInteger governanceEpoch,
            LocalDateTime readAt, String createdBy, LocalDateTime createdAt, String classification) { }

    public record RangeRow(String tenantId, String projectId, String receiptId, String artifactId, String resourceKind,
            String resourceId, String schemaRef, String schemaDocumentSha256, String accessSetId,
            String dependencyManifestSha256, String objectRefSha256, BigInteger startByte, BigInteger endByte,
            BigInteger sizeBytes, String chunkSha256, String purpose, String authorizationSha256,
            BigInteger contextRevision, String createdBy, LocalDateTime createdAt, String classification) { }

    public record AuditRow(String tenantId, String projectId, String auditId, String subjectId, String action,
            String resource, String result, String reasonCode, String requestId, String traceId,
            String schemaVersion, String schemaManifest, String createdBy, LocalDateTime createdAt, String classification) { }

    // 领域消费者固定资源、Schema及来源选择，浏览器只能提供原页游标。
    record ReadIntent(String artifactId, String contextRevision, ArtifactRegistryProtocol.ReadRequirement artifact,
            List<SourceReadProtocol.ReadRequirement> sources, VersionResourceProtocol.ReadContext context,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> upstreamArtifacts) {
        ReadIntent {
            WireValues.uuid(artifactId); WireValues.revision(contextRevision);
            Objects.requireNonNull(artifact); Objects.requireNonNull(context);
            sources = List.copyOf(sources); upstreamArtifacts = Map.copyOf(upstreamArtifacts);
        }
    }
}
