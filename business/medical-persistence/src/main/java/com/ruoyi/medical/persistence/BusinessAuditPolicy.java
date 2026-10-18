package com.ruoyi.medical.persistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("medical.business-audit")
public record BusinessAuditPolicy(boolean enabled,Integer maximumPageSize,Integer maximumScanRows,Integer maximumPayloadBytes,Integer maximumResponseBytes,Integer maximumWindowSeconds,Integer cursorSeconds,Integer maximumCursorEntries){
 public BusinessAuditPolicy{if(enabled&&(maximumPageSize==null||maximumPageSize<1||maximumScanRows==null||maximumScanRows<maximumPageSize||maximumScanRows==Integer.MAX_VALUE||maximumPayloadBytes==null||maximumPayloadBytes<1||maximumResponseBytes==null||maximumResponseBytes<1||maximumWindowSeconds==null||maximumWindowSeconds<1||cursorSeconds==null||cursorSeconds<1||maximumCursorEntries==null||maximumCursorEntries<1))throw new IllegalArgumentException("BUSINESS_AUDIT_CAPACITY_REQUIRED");}
 void requireConfigured(){if(!enabled)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
