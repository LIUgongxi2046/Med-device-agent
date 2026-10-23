package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.business-operations")
public record BusinessOperationsClassificationPolicy(List<Binding> classificationBindings) {
    private static final Set<String> LEVELS=Set.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");
    public BusinessOperationsClassificationPolicy {
        classificationBindings=classificationBindings==null?List.of():List.copyOf(classificationBindings);
        var identities=new HashSet<String>();
        for(var value:classificationBindings)if(!identities.add(value.tenantId()+":"+value.kind()+":"+value.versionId()))throw new IllegalArgumentException("BUSINESS_CLASSIFICATION_BINDING_DUPLICATED");
    }
    public record Binding(String tenantId,String kind,String versionId,String contentSha256,String metadataClassification,String classification,String reason) {
        public Binding {
            WireValues.uuid(tenantId);WireValues.uuid(versionId);
            if(!Set.of("RULE","SOURCE","RIGHTS","PROJECT").contains(kind)||contentSha256==null||!contentSha256.matches("[a-f0-9]{64}")
                    ||metadataClassification==null||metadataClassification.isBlank()||metadataClassification.length()>24||classification==null||!LEVELS.contains(classification))throw new IllegalArgumentException("BUSINESS_CLASSIFICATION_BINDING_INVALID");
            ProjectSchema.text(reason,2000);
        }
    }
    static boolean standard(String value){return value!=null&&LEVELS.contains(value);}
    String resolve(String tenant,String kind,String version,String hash,String metadata){
        if(standard(metadata))return metadata;
        var matches=classificationBindings.stream().filter(value->value.tenantId().equals(tenant)&&value.kind().equals(kind)&&value.versionId().equals(version)).toList();
        if(matches.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var binding=matches.get(0);
        if(!binding.contentSha256().equals(hash)||!binding.metadataClassification().equals(metadata))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        return binding.classification();
    }
}
