package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.templates")
public record TemplateDependencyPolicy(String environment, String region, int maximumVersions, int maximumRelations,
        List<ArtifactProfile> artifactProfiles) {
    public TemplateDependencyPolicy {
        ProjectSchema.text(environment, 128); ProjectSchema.text(region, 128);
        new DependencyLineage.Limits(maximumVersions, maximumRelations);
        artifactProfiles = List.copyOf(artifactProfiles);
        var keys = new java.util.HashSet<String>();
        for (var profile : artifactProfiles) if (!keys.add(profile.role() + ":" + profile.schemaRef()))
            throw new IllegalArgumentException("TEMPLATE_ARTIFACT_PROFILE_DUPLICATE");
    }

    public record ArtifactProfile(String role, String resourceKind, String action, String schemaRef, String schemaDocumentSha256, String artifactSchemaManifest) {
        public ArtifactProfile {
            if (!Set.of("FONT", "FORMAT", "DEPENDENCY").contains(role)) throw new IllegalArgumentException("TEMPLATE_ARTIFACT_ROLE_INVALID");
            if (!Set.of("ARTIFACT", "TEMPLATE", "RULE", "SOURCE").contains(resourceKind)
                    || !action.equals(resourceKind.toLowerCase(java.util.Locale.ROOT) + ".read"))
                throw new IllegalArgumentException("TEMPLATE_ARTIFACT_RESOURCE_INVALID");
            ProjectSchema.text(schemaRef, 2048); WireValues.hash(schemaDocumentSha256); WireValues.hash(artifactSchemaManifest);
        }
    }

    DependencyLineage.Limits limits() { return new DependencyLineage.Limits(maximumVersions, maximumRelations); }
}
