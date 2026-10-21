package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(StrictComparisonRuntimePolicy.class)
public class StrictComparisonConfiguration {
    @Bean StrictComparisonCodec strictComparisonCodec(StrictComparisonRuntimePolicy policy){return new StrictComparisonCodec(Math.max(1,policy.maximumRequestBytes()));}
    @Bean StrictComparisonStore strictComparisonStore(@Qualifier("masterDataSource")DataSource data,StrictComparisonCodec codec,IdentityDirectoryPolicy identity)throws Exception{return new StrictComparisonStore(data,codec,identity);}
    @Bean StrictComparisonRunScope strictComparisonRunScope(TaskService tasks,TaskPlanService plans,TemplateApplicabilityService templates,StrictComparisonCodec codec){return new StrictComparisonRunScope(tasks,plans,templates,codec);}
    @Bean StrictComparisonAuthority strictComparisonAuthority(TaskFieldTargetBasis tasks,ResourceReadAuthority resources,StrictComparisonStore store,StrictComparisonCodec codec,
            com.ruoyi.medical.persistence.mapper.PermissionMapper permissions,PermissionAuthority grants,com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities){return new StrictComparisonAuthority(tasks,resources,store,codec,permissions,grants,identities);}
    @Bean StrictComparisonPolicyService strictComparisonPolicyService(EnterpriseAccess access,StrictComparisonStore store,StrictComparisonCodec codec,StrictComparisonAuthority authority,StrictComparisonRuntimePolicy policy){return new StrictComparisonPolicyService(access,store,codec,authority,policy);}
    @Bean StrictDecimalEvidenceService strictDecimalEvidenceService(EnterpriseAccess access,StrictComparisonStore store,StrictComparisonCodec codec,StrictComparisonAuthority authority,SourceReadAuthority sources,TaskFieldSourceAuthority objects,ObjectProvider<SourceUploadTransport> transport,TaskFieldTargetPolicy bounds,StrictComparisonRuntimePolicy policy)throws Exception{return new StrictDecimalEvidenceService(access,store,codec,authority,sources,objects,transport.getIfAvailable(),bounds,policy);}
}
