package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.document-upload")
public record DocumentUploadPolicy(String environment, String region, int maximumSources, int maximumVersions,
        int maximumRelations, BigInteger maximumBytes, long expiresAfterSeconds, String artifactSchemaManifest,
        List<ArtifactProfile> artifactProfiles, int maximumNodes, int maximumTableCells, int maximumBodyRelationships) {
    public DocumentUploadPolicy {
        boolean absent = environment == null && region == null && maximumSources == 0 && maximumVersions == 0
            && maximumRelations == 0 && maximumBytes == null && expiresAfterSeconds == 0
            && artifactSchemaManifest == null && artifactProfiles == null && maximumNodes == 0
            && maximumTableCells == 0 && maximumBodyRelationships == 0;
        if (absent) {
            artifactProfiles = List.of();
        } else {
            new DocumentUploadBasisProtocol.Settings(environment, region, maximumSources);
            new DependencyLineage.Limits(maximumVersions, maximumRelations);
            new DocumentBodySemantics.Limits(maximumNodes, maximumTableCells, maximumBodyRelationships);
            Objects.requireNonNull(maximumBytes); WireValues.hash(artifactSchemaManifest);
            if (maximumBytes.signum() <= 0 || maximumBytes.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                    || expiresAfterSeconds <= 0) throw new IllegalArgumentException("正文上传容量与期限必须明确配置");
            artifactProfiles = List.copyOf(Objects.requireNonNull(artifactProfiles));
            var identities = new HashSet<String>();
            for (var profile : artifactProfiles)
                if (!identities.add(profile.resourceKind() + ":" + profile.schemaRef()))
                    throw new IllegalArgumentException("正文上游制品读取配置重复");
        }
    }

    void requireConfigured() {
        if (maximumBytes == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    DependencyLineage.Limits limits() { requireConfigured(); return new DependencyLineage.Limits(maximumVersions, maximumRelations); }
    DocumentBodySemantics.Limits bodyLimits() {
        requireConfigured(); return new DocumentBodySemantics.Limits(maximumNodes, maximumTableCells, maximumBodyRelationships);
    }

    public record ArtifactProfile(String resourceKind, String schemaRef, String schemaDocumentSha256, String artifactSchemaManifest) {
        public ArtifactProfile {
            if (resourceKind == null || !resourceKind.matches("[A-Z][A-Z_]{0,63}"))
                throw new IllegalArgumentException("正文上游制品资源类型无效");
            ProjectSchema.text(schemaRef, 2048); WireValues.hash(schemaDocumentSha256); WireValues.hash(artifactSchemaManifest);
        }
        String action() { return resourceKind.toLowerCase(Locale.ROOT) + ".read"; }
    }
}
