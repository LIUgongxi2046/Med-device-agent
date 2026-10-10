package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UploadAuthorizationProtocol {
    private UploadAuthorizationProtocol() { }

    public record Manifest(String schemaVersion, String tenantId, String subjectId, String projectId, String purpose,
            String authEpoch, String governanceEpoch, String schemaRef, String schemaDocumentSha256, String artifactSchemaManifest,
            String dependencyAccessSetId, String dependencyManifestSha256, DependencyAccessProtocol.ReadRequirement dependencyRequirement,
            List<SourceReadProtocol.ReadRequirement> sources, VersionResourceProtocol.ReadContext context,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> upstreamArtifacts, DocumentUploadBasisProtocol.Binding documentBasis) {
        public Manifest {
            if (!"1.0.0".equals(schemaVersion) || !Set.of("DOCUMENT_BODY", "AGENT_ARTIFACT").contains(purpose)) invalid();
            WireValues.uuid(tenantId); WireValues.uuid(subjectId); if (projectId != null) WireValues.uuid(projectId);
            WireValues.revision(authEpoch); WireValues.revision(governanceEpoch); WireValues.boundedString(schemaRef, 512);
            if (schemaRef.isEmpty()) invalid();
            WireValues.hash(schemaDocumentSha256); WireValues.hash(artifactSchemaManifest);
            WireValues.uuid(dependencyAccessSetId); WireValues.hash(dependencyManifestSha256);
            Objects.requireNonNull(dependencyRequirement); Objects.requireNonNull(context);
            if (purpose.equals("DOCUMENT_BODY") && (documentBasis == null || projectId == null
                    || context.use() != SourceReadProtocol.Use.FORMAL || context.conversationId() != null)) invalid();
            if (purpose.equals("AGENT_ARTIFACT") && documentBasis != null) invalid();
            if (!Objects.equals(projectId, dependencyRequirement.projectId())) invalid();
            sources = List.copyOf(sources); upstreamArtifacts = Map.copyOf(upstreamArtifacts);
            var versions = new HashSet<String>();
            for (var source : sources) {
                if (!versions.add(source.sourceVersion()) || !Objects.equals(projectId, source.projectId())
                        || source.use() != context.use() || !Objects.equals(source.conversationId(), context.conversationId())
                        || !source.purpose().equals(dependencyRequirement.purpose())) invalid();
            }
            for (var entry : upstreamArtifacts.entrySet()) {
                WireValues.uuid(entry.getKey()); var artifact = entry.getValue();
                if (!tenantId.equals(artifact.scope().tenant_id()) || !artifact.purpose().equals(dependencyRequirement.purpose())) invalid();
            }
        }
    }

    private static void invalid() { throw new IllegalArgumentException("上传授权清单身份或完整选择不一致"); }
}
