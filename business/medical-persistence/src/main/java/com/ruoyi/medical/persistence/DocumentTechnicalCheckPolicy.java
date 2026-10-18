package com.ruoyi.medical.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment must explicitly opt in and supply finite capacities. No grants or defaults. */
@ConfigurationProperties("medical.document-technical-check")
public record DocumentTechnicalCheckPolicy(boolean enabled, Integer maximumBytes, Integer maximumNodes,
        Integer maximumTableCells, Integer maximumRelationships, Integer maximumSeconds) {
    public DocumentTechnicalCheckPolicy {
        if(enabled && (maximumBytes==null||maximumBytes<=0||maximumNodes==null||maximumNodes<=0
                ||maximumTableCells==null||maximumTableCells<=0||maximumRelationships==null||maximumRelationships<=0
                ||maximumSeconds==null||maximumSeconds<=0))throw new IllegalArgumentException("DOCUMENT_CHECK_CAPACITY_REQUIRED");
    }
    void requireConfigured(){if(!enabled)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);}
    DocumentBodySemantics.Limits limits(){requireConfigured();return new DocumentBodySemantics.Limits(maximumNodes,maximumTableCells,maximumRelationships);}
}
