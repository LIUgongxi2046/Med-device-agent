package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

/** Normal preparation routes with absent technical configuration reported fail-closed.
 * No deployment values, domain grants, business approvals or runtime enablement are seeded. */
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(TaskFieldTargetPolicy.class)
public class TaskFieldTargetConfiguration {
    @Bean TaskFieldTargetSchema taskFieldTargetSchema()throws Exception{return new TaskFieldTargetSchema();}
    @Bean TaskFieldTargetBasis taskFieldTargetBasis(TaskService t,TaskPlanService p,ResourceReadAuthority r,PermissionSchema j,IdentityDirectoryPolicy i,TaskInputSchema s){return new TaskFieldTargetBasis(t,p,r,j,i,s);}
    @Bean TaskFieldMappingService taskFieldMappingService(EnterpriseAccess a,TaskFieldTargetBasis b,TaskPlanRelationStore r,TaskFieldTargetSchema s,TaskFieldTargetPolicy p,PermissionSchema j,PermissionMapper m,ResourceReadAuthority reads,TaskService tasks,DataSource d,IdentityDirectoryPolicy i){return new TaskFieldMappingService(a,b,r,s,p,j,m,reads,tasks,d,i);}
    @Bean TaskFieldTargetLedger taskFieldTargetLedger(DataSource d,TaskFieldTargetSchema s,PermissionSchema j,TaskFieldSourceReader r,TaskService t,TaskFieldTargetPolicy p){return new TaskFieldTargetLedger(d,s,j,r,t,p);}
    @Bean TaskFieldTargetRegistration taskFieldTargetRegistration(DataSource d,PermissionSchema j,TaskFieldTargetSchema s,TaskFieldTargetLedger l,ArtifactRegistryMapper registry,DependencyManifestSchema manifests,DependencyLineage lineage,TaskFieldTargetPolicy p,VersionResourceMapper owners,TaskFieldSourceReceiptStore receipts,TaskService tasks,TaskPlanService plans,ResourceReadAuthority reads,IdentityDirectoryPolicy identity){return new TaskFieldTargetRegistration(d,j,s,l,registry,manifests,lineage,p::lineageLimits,owners,receipts,tasks,plans,reads,identity);}
    @Bean(name="taskFieldTargetTransport")
    @ConditionalOnProperty(prefix="medical.task-field-target",name="object-directory")
    @ConditionalOnBean(SourceUploadTransport.class)
    RuntimeObjectTransport taskFieldTargetTransport(SourceUploadTransport source,TaskFieldTargetPolicy p,@Value("${medical.task-field-target.object-directory}")String path)throws Exception{p.requireConfigured();p.lineageLimits();return new RuntimeObjectTransport(source,java.nio.file.Path.of(path),p.maximumTargetBytes());}
    @Bean TaskFieldTargetService taskFieldTargetService(EnterpriseAccess a,TaskFieldTargetBasis b,TaskFieldMappingService m,TaskFieldSourceReader r,TaskFieldTargetLedger l,@Qualifier("taskFieldTargetTransport")ObjectProvider<RuntimeObjectTransport> t,TaskFieldTargetRegistration registered,TaskFieldTargetSchema s,TaskFieldTargetPolicy p){return new TaskFieldTargetService(a,b,m,r,l,t.getIfAvailable(),registered,s,p);}
    @Bean TaskFieldTargetPreparationService taskFieldTargetPreparationService(EnterpriseAccess a,TaskFieldTargetBasis b,TaskFieldMappingService m,TaskPlanRelationStore relations,TaskFieldTargetSchema s,PermissionSchema j,TaskFieldTargetPolicy p,ResourceReadAuthority r,SourceExactService exact,SourceExactMapper rows,TaskFieldTargetLedger l,TaskFieldTargetRegistration registered,DataSource d,@Qualifier("taskFieldTargetTransport")ObjectProvider<RuntimeObjectTransport> transport){return new TaskFieldTargetPreparationService(a,b,m,relations,s,j,p,r,exact,rows,l,registered,d,transport.getIfAvailable()!=null);}
    @Bean
    @ConditionalOnProperty(prefix="medical.formal-agent-publication",name="enabled",havingValue="true")
    TaskFieldTargetInputCapabilityService taskFieldTargetInputCapabilityService(EnterpriseAccess a,DataSource d,AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskService tasks,TaskPlanService plans,PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,ResourceReadAuthority resources,TaskFieldTargetRegistration registrations,TaskFieldTargetSchema s,PermissionSchema j){return new TaskFieldTargetInputCapabilityService(a,d,publications,policies,roles,tasks,plans,permissions,grants,identities,resources,registrations,s,j);}
    @Bean
    @ConditionalOnProperty(prefix="medical.formal-agent-publication",name="enabled",havingValue="true")
    TaskFieldTargetInputAuthority taskFieldTargetInputAuthority(TaskFieldTargetInputCapabilityService c,PermissionSchema j){return new TaskFieldTargetInputAuthority(c,j);}
}
