package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class DependencySourceAuthority {
    private final SourceReadMapper mapper;
    private final SourceReadAuthority authority;
    private final VersionResourceAuthority versions;

    public DependencySourceAuthority(SourceReadMapper mapper, SourceReadAuthority authority, VersionResourceAuthority versions) {
        this.mapper = Objects.requireNonNull(mapper); this.authority = Objects.requireNonNull(authority);
        this.versions = Objects.requireNonNull(versions);
    }

    record VerifiedDependencies(EnterpriseAccess.Admission admission, String root, Map<String, SourceReadProtocol.VerifiedSource> sources,
            Map<String, VersionResourceAuthority.VerifiedVersion> versions) {
        VerifiedDependencies { Objects.requireNonNull(admission); Objects.requireNonNull(root); sources = Map.copyOf(sources); versions = Map.copyOf(versions); }
    }

    VerifiedDependencies require(EnterpriseAccess.Admission admission, DependencyLineage.VerifiedLineage lineage,
            ArtifactRegistryProtocol.DependencyManifest manifest, String projectId, String purpose,
            List<SourceReadProtocol.ReadRequirement> requirements, VersionResourceProtocol.ReadContext context,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> artifactRequirements) throws IOException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.SourceReadMapper.source");
        Objects.requireNonNull(admission); Objects.requireNonNull(lineage); Objects.requireNonNull(manifest);
        Objects.requireNonNull(requirements); Objects.requireNonNull(context); Objects.requireNonNull(artifactRequirements);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        if (!admission.context().tenant_id().equals(manifest.tenant_id()) || !Objects.equals(projectId, manifest.project_id())) mismatch();
        if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("依赖来源用途无效");
        var selected = new LinkedHashMap<String, SourceReadProtocol.ReadRequirement>();
        for (var required : requirements) {
            if (!purpose.equals(required.purpose()) || !Objects.equals(projectId, required.projectId())
                    || required.use() != context.use()
                    || required.conversationId() != null && !required.conversationId().equals(context.conversationId())) mismatch();
            if (selected.putIfAbsent(required.sourceVersion(), required) != null) mismatch();
        }
        var verified = new LinkedHashMap<String, SourceReadProtocol.VerifiedSource>();
        for (String version : lineage.versions().keySet()) {
            var sources = mapper.source(version);
            if (sources.size() > 1) mismatch();
            if (sources.size() == 1) {
                var requirement = selected.get(version);
                if (requirement == null) mismatch();
                verified.put(version, authority.require(admission, requirement));
            }
        }
        if (!verified.keySet().equals(selected.keySet())) mismatch();
        var verifiedVersions = versions.require(admission, lineage, purpose, context, artifactRequirements);
        for (var version : verifiedVersions.values()) {
            String sourceVersion = version.owner().sourceVersion();
            if (sourceVersion != null) {
                var source = verified.get(sourceVersion);
                if (source == null || !source.source().sourceId().equals(version.owner().resourceId())
                        || !lineage.reachableFrom(version.version().id()).contains(sourceVersion)) mismatch();
            }
        }
        // 每个成员引用的权利必须属于其实际可达的原来源，并保持当前版本。
        for (var member : manifest.members()) {
            var rows = mapper.rights(member.rightsRevision());
            if (rows.size() != 1) denied();
            var rights = rows.get(0); var source = verified.get(rights.sourceVersion());
            if (source == null || !source.rights().rightsRevision().equals(member.rightsRevision())
                    || !lineage.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion())) mismatch();
        }
        return new VerifiedDependencies(admission, lineage.root(), verified, verifiedVersions);
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
