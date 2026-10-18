package com.ruoyi.medical.persistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("medical.document-history")
public record DocumentRevisionDirectoryPolicy(boolean enabled,Integer maximumPageSize,Integer maximumScanRows,Integer maximumReferences,Integer maximumCursorEntries,Integer cursorSeconds){
 public DocumentRevisionDirectoryPolicy{if(enabled&&(maximumPageSize==null||maximumPageSize<1||maximumScanRows==null||maximumScanRows<maximumPageSize||maximumScanRows==Integer.MAX_VALUE||maximumReferences==null||maximumReferences<1||maximumReferences==Integer.MAX_VALUE||maximumCursorEntries==null||maximumCursorEntries<1||cursorSeconds==null||cursorSeconds<1))throw new IllegalArgumentException("DOCUMENT_HISTORY_CAPACITY_REQUIRED");}
 void requireConfigured(){if(!enabled)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
