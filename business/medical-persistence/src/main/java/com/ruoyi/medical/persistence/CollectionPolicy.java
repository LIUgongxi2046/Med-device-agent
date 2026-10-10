package com.ruoyi.medical.persistence;

import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.collections")
public record CollectionPolicy(String environment, String region, int maximumMembers, int maximumVersions,
        int maximumRelations, List<TemplateDependencyPolicy.ArtifactProfile> artifactProfiles) {
    public CollectionPolicy {
        ProjectSchema.text(environment, 128); ProjectSchema.text(region, 128);
        if (maximumMembers <= 0 || maximumMembers == Integer.MAX_VALUE) throw new IllegalArgumentException("COLLECTION_MEMBER_LIMIT_REQUIRED");
        new DependencyLineage.Limits(maximumVersions, maximumRelations);
        artifactProfiles = List.copyOf(artifactProfiles);
        var identities = new HashSet<String>();
        for (var profile : artifactProfiles)
            if (!"DEPENDENCY".equals(profile.role()) || !identities.add(profile.schemaRef()))
                throw new IllegalArgumentException("COLLECTION_ARTIFACT_PROFILE_INVALID");
    }
    DependencyLineage.Limits limits() { return new DependencyLineage.Limits(maximumVersions, maximumRelations); }
}
