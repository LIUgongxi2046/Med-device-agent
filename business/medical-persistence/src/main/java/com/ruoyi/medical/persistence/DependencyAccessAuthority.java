package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.DependencyAccessMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DependencyAccessAuthority {
    private final DependencyAccessMapper mapper;
    private final ArtifactRegistryMapper registry;
    private final DependencyManifestSchema manifests;
    private final DependencyLineage lineage;
    private final DependencySourceAuthority sources;

    public DependencyAccessAuthority(DependencyAccessMapper mapper, ArtifactRegistryMapper registry,
            DependencyManifestSchema manifests, DependencyLineage lineage, DependencySourceAuthority sources) {
        this.mapper = Objects.requireNonNull(mapper); this.registry = Objects.requireNonNull(registry);
        this.manifests = Objects.requireNonNull(manifests); this.lineage = Objects.requireNonNull(lineage);
        this.sources = Objects.requireNonNull(sources);
    }

    DependencyAccessProtocol.VerifiedAccess require(EnterpriseAccess.Admission admission, String accessSetId,
            DependencyAccessProtocol.ReadRequirement required, List<SourceReadProtocol.ReadRequirement> sourceRequirements,
            VersionResourceProtocol.ReadContext context, Map<String, ArtifactRegistryProtocol.ReadRequirement> upstreamArtifacts) throws IOException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.DependencyAccessMapper.header");
        Objects.requireNonNull(admission); WireValues.uuid(accessSetId); Objects.requireNonNull(required);
        Objects.requireNonNull(sourceRequirements); Objects.requireNonNull(context); Objects.requireNonNull(upstreamArtifacts);
        String tenant = admission.context().tenant_id();
        if (!TenantScope.requireCurrent().tenantId().equals(tenant)) denied();
        var header = single(mapper.header(accessSetId));
        if (!tenant.equals(header.tenantId()) || !accessSetId.equals(header.accessSetId())
                || !Objects.equals(required.projectId(), header.projectId())
                || !header.manifestSha256().equals(header.contentSha256())) mismatch();
        var version = single(registry.version(accessSetId));
        if (!accessSetId.equals(version.id()) || !Objects.equals(header.projectId(), version.projectId())
                || !header.manifestSha256().equals(version.contentSha256())) mismatch();
        var row = single(registry.manifest(accessSetId));
        if (!tenant.equals(row.tenantId()) || !accessSetId.equals(row.accessSetId())
                || !Objects.equals(header.projectId(), row.projectId())
                || !header.manifestSha256().equals(row.manifestSha256()) || !header.contentSha256().equals(row.contentSha256())) mismatch();
        var manifest = manifests.verify(row, registry.members(accessSetId));
        // 使用已登记的依赖清单版本核验全部关系，无需预先创建制品。
        var completeLineage = lineage.require(accessSetId, manifest.members(), required.lineageLimits());
        var verified = sources.require(admission, completeLineage, manifest, header.projectId(), required.purpose(),
            sourceRequirements, context, upstreamArtifacts);
        return new DependencyAccessProtocol.VerifiedAccess(header, manifest, completeLineage, verified.sources(), verified.versions());
    }

    private static <T> T single(List<T> rows) { if (rows.size() != 1) denied(); return rows.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
