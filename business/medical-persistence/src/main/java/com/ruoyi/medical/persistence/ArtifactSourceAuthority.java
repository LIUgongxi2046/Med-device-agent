package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ArtifactSourceAuthority {
    private final ArtifactRegistry registry;
    private final DependencySourceAuthority dependencies;

    public ArtifactSourceAuthority(ArtifactRegistry registry, DependencySourceAuthority dependencies) {
        this.registry = Objects.requireNonNull(registry); this.dependencies = Objects.requireNonNull(dependencies);
    }

    record VerifiedSources(ArtifactRegistryProtocol.Registration registration, Map<String, SourceReadProtocol.VerifiedSource> sources,
            Map<String, VersionResourceAuthority.VerifiedVersion> versions) {
        VerifiedSources { sources = Map.copyOf(sources); versions = Map.copyOf(versions); }
    }

    VerifiedSources require(EnterpriseAccess.Admission admission, String artifactId, ArtifactRegistryProtocol.ReadRequirement artifactRequirement,
            List<SourceReadProtocol.ReadRequirement> requirements, VersionResourceProtocol.ReadContext context,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> upstreamArtifacts) throws IOException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.SourceReadMapper.source");
        Objects.requireNonNull(admission); Objects.requireNonNull(artifactRequirement); Objects.requireNonNull(requirements);
        Objects.requireNonNull(context); Objects.requireNonNull(upstreamArtifacts);
        // 每次来源准入共同重查主资源与制品，当前许可不能引用较早的登记结果。
        var registered = registry.requireRegistration(admission, artifactId, artifactRequirement);
        if (!admission.context().tenant_id().equals(registered.artifact().tenantId())) denied();
        var artifactRequirements = new LinkedHashMap<>(upstreamArtifacts);
        if (artifactRequirements.putIfAbsent(artifactId, artifactRequirement) != null) mismatch();
        var verified = dependencies.require(admission, registered.lineage(), registered.manifest(), registered.artifact().projectId(),
            artifactRequirement.purpose(), requirements, context, artifactRequirements);
        // 当前来源与派生版本准入不能代替实际字节发送和读取回执。
        return new VerifiedSources(registered, verified.sources(), verified.versions());
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
