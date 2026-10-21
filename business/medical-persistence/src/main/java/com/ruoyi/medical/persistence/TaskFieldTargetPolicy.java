package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local technical bounds only. Configuration never creates source or producer authority. */
@ConfigurationProperties("medical.task-field-target")
public record TaskFieldTargetPolicy(int maximumMappings, int maximumInventoryFields,
        int maximumTargetBytes, int maximumOriginalBytes, int maximumParsedBytes,
        String environment, String region, int maximumVersions, int maximumRelations) {
    /** Compatibility constructor for non-transport unit fixtures; lineage remains unconfigured. */
    public TaskFieldTargetPolicy(int mappings,int fields,int target,int original,int parsed,String environment,String region){
        this(mappings,fields,target,original,parsed,environment,region,0,0);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public TaskFieldTargetPolicy {
        if(maximumVersions<0||maximumRelations<0)throw new IllegalArgumentException("Invalid lineage bounds");
        if(maximumVersions>0&&maximumRelations>0)new DependencyLineage.Limits(maximumVersions,maximumRelations);
        if (maximumMappings < 0 || maximumMappings > 10000 || maximumInventoryFields < 0
                || maximumInventoryFields > 10000 || maximumTargetBytes < 0 || maximumTargetBytes > 8388608
                || maximumOriginalBytes < 0 || maximumOriginalBytes > 104857600
                || maximumParsedBytes < 0 || maximumParsedBytes > 268435456)
            throw new IllegalArgumentException("Invalid field target bounds");
        if (environment != null && (environment.isBlank() || environment.length() > 128)
                || region != null && (region.isBlank() || region.length() > 128))
            throw new IllegalArgumentException("Invalid field target location");
    }
    public DependencyLineage.Limits lineageLimits(){
        requireConfigured();if(maximumVersions==0||maximumRelations==0)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return new DependencyLineage.Limits(maximumVersions,maximumRelations);
    }
    public void requireConfigured() {
        if (maximumMappings == 0 || maximumInventoryFields == 0 || maximumTargetBytes == 0
                || maximumOriginalBytes == 0 || maximumParsedBytes == 0 || environment == null || region == null)
            throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
