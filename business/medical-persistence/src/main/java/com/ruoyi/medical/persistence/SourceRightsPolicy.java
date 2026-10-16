package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-rights")
public record SourceRightsPolicy(boolean enabled, String revision, String environment, String region,
        Boolean separateReviewer, int maximumEvidence, int maximumEvidenceBytes, int maximumVersions,
        int maximumRelations, List<EvidenceProfile> artifactProfiles) {
    public SourceRightsPolicy {
        boolean absent=!enabled && revision==null && environment==null && region==null && separateReviewer==null
            && maximumEvidence==0 && maximumEvidenceBytes==0 && maximumVersions==0 && maximumRelations==0 && artifactProfiles==null;
        if(!absent) {
            WireValues.uuid(revision); ProjectSchema.text(environment,128); ProjectSchema.text(region,128);
            if(separateReviewer==null || maximumEvidence<1 || maximumEvidence>64
                    || maximumEvidenceBytes<1 || maximumEvidenceBytes>268435456)
                throw new IllegalArgumentException("来源权利审核范围必须完整配置");
            new DependencyLineage.Limits(maximumVersions,maximumRelations);
            artifactProfiles=List.copyOf(artifactProfiles);
            var profiles=new HashSet<String>(); boolean authorization=false;
            for(var profile:artifactProfiles) {
                if(!profiles.add(profile.role()+":"+profile.artifactKind()+":"+profile.schemaRef()))
                    throw new IllegalArgumentException("来源权利证据配置重复");
                authorization|="AUTHORIZATION".equals(profile.role());
            }
            if(!authorization) throw new IllegalArgumentException("来源权利授权文件类型缺少");
        }
    }
    public record EvidenceProfile(String role, String artifactKind, String resourceKind, String action,
            String schemaRef, String schemaDocumentSha256, String artifactSchemaManifest) {
        public EvidenceProfile {
            if(!Set.of("AUTHORIZATION","DEPENDENCY").contains(role)
                    || !Set.of("ARTIFACT","SOURCE","DOCUMENT","TEMPLATE","RULE").contains(resourceKind)
                    || !action.equals(resourceKind.toLowerCase(java.util.Locale.ROOT)+".read"))
                throw new IllegalArgumentException("来源权利证据资源类型无效");
            ProjectSchema.text(artifactKind,64); ProjectSchema.text(schemaRef,2048);
            WireValues.hash(schemaDocumentSha256); WireValues.hash(artifactSchemaManifest);
        }
    }
    void requireConfigured() { if(!enabled) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    DependencyLineage.Limits limits() { requireConfigured(); return new DependencyLineage.Limits(maximumVersions,maximumRelations); }
}
