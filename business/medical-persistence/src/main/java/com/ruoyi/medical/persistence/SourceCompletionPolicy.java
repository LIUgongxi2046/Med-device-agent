package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("medical.source-completion")
public record SourceCompletionPolicy(List<Binding> bindings) {
    public SourceCompletionPolicy {
        bindings=List.copyOf(bindings);
        if(bindings.isEmpty() || bindings.size()>1000
                || bindings.stream().map(item -> item.tenantId()+":"+item.projectId()).distinct().count()!=bindings.size())
            throw new IllegalArgumentException("上传完成消费者的企业、项目与配置范围无效");
    }
    public record Binding(String tenantId,String projectId,String configRevision) {
        public Binding { WireValues.uuid(tenantId); WireValues.uuid(projectId); WireValues.uuid(configRevision); }
    }
    Binding binding(String tenant,String project) {
        return bindings.stream().filter(item -> item.tenantId().equals(tenant) && item.projectId().equals(project)).findFirst()
            .orElseThrow(() -> new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
    }
}
