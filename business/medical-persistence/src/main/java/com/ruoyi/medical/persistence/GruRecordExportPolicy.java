package com.ruoyi.medical.persistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** No runtime defaults; bean is enabled only together with the record-only foundation. */
@ConfigurationProperties("medical.gru-record-export")
public record GruRecordExportPolicy(boolean enabled,Integer maximumBytes,Integer maximumSeconds,Integer maximumNodes,Integer maximumRelationships) {
 public GruRecordExportPolicy {
  if(enabled&&(maximumBytes==null||maximumBytes<=0||maximumBytes>GruRecordExportSchema.MAX_CONTENT_BYTES||maximumSeconds==null||maximumSeconds<=0||maximumNodes==null||maximumNodes<=0||maximumRelationships==null||maximumRelationships<=0))
   throw new IllegalArgumentException("GRU_RECORD_EXPORT_CAPACITY_REQUIRED");
 }
 void requireConfigured(){if(!enabled)throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
