package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-owned read environment and published artifact schemas; request bodies cannot expand this list. */
@ConfigurationProperties("medical.evaluations")
public record EvaluationDependencyPolicy(String environment,String region,int maximumVersions,int maximumRelations,List<ArtifactProfile> artifactProfiles) {
    public EvaluationDependencyPolicy {
        artifactProfiles=artifactProfiles==null?List.of():List.copyOf(artifactProfiles);
        if(environment!=null||region!=null||maximumVersions!=0||maximumRelations!=0||!artifactProfiles.isEmpty()) {
            ProjectSchema.text(environment,128);ProjectSchema.text(region,128);new DependencyLineage.Limits(maximumVersions,maximumRelations);
            var keys=new java.util.HashSet<String>();for(var p:artifactProfiles)if(!keys.add(p.schemaRef()))throw new IllegalArgumentException("EVALUATION_ARTIFACT_PROFILE_DUPLICATE");
        }
    }
    public record ArtifactProfile(String resourceKind,String action,String schemaRef,String schemaDocumentSha256,String artifactSchemaManifest) {
        public ArtifactProfile {
            if(!java.util.Set.of("ARTIFACT","SOURCE","PROJECT","TASK","DOCUMENT","EVALUATION","RULE","CONFIGURATION").contains(resourceKind)
                ||!action.equals(resourceKind.toLowerCase(java.util.Locale.ROOT)+".read"))throw new IllegalArgumentException("EVALUATION_ARTIFACT_RESOURCE_INVALID");
            ProjectSchema.text(schemaRef,2048);WireValues.hash(schemaDocumentSha256);WireValues.hash(artifactSchemaManifest);
        }
    }
    void requireConfigured(){if(environment==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
    DependencyLineage.Limits limits(){requireConfigured();return new DependencyLineage.Limits(maximumVersions,maximumRelations);}
}
