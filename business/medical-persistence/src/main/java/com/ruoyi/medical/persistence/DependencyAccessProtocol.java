package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

public final class DependencyAccessProtocol {
    private DependencyAccessProtocol() { }

    public record HeaderRow(String tenantId, String projectId, String accessSetId, String manifestSha256,
            String contentSha256, String createdBy, LocalDateTime createdAt, String classification) { }

    public record ReadRequirement(String projectId, String purpose, DependencyLineage.Limits lineageLimits) {
        public ReadRequirement {
            if (projectId != null) WireValues.uuid(projectId);
            if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("依赖读取用途无效");
            Objects.requireNonNull(lineageLimits);
        }
    }

    // 核验结果供服务器领域服务使用，不能代替上传和内容读取许可。
    record VerifiedAccess(HeaderRow header, ArtifactRegistryProtocol.DependencyManifest manifest,
            DependencyLineage.VerifiedLineage lineage, Map<String, SourceReadProtocol.VerifiedSource> sources,
            Map<String, VersionResourceAuthority.VerifiedVersion> versions) {
        VerifiedAccess { sources = Map.copyOf(sources); versions = Map.copyOf(versions); }
    }
}
