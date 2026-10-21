package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit governance enablement only. Does not enable RN01 or create an approval. */
@Configuration
@ConditionalOnProperty(prefix="medical.task-source-verifier-producer",name="enabled",havingValue="true")
public class TaskSourceVerifierProducerConfiguration {
    @Bean TaskNodeProducerSchema taskSourceVerifierProducerSchema()throws Exception{return new TaskNodeProducerSchema(TaskNodeProducerProfile.VERIFIER);}
    @Bean TaskNodeProducerOriginalAuthority taskSourceVerifierProducerOriginalAuthority(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,
            @Value("${medical.task-delegation.maximum-candidates}") int maximumCandidates){
        return new TaskNodeProducerOriginalAuthority(access,data,publications,policies,roles,tasks,permissions,budgets,
                grants,identities,resources,schema,json,maximumCandidates,TaskNodeProducerProfile.VERIFIER);
    }
    @Bean TaskNodeProducerService taskSourceVerifierProducerService(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskService tasks,TaskPlanService plans,
            PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,@org.springframework.beans.factory.annotation.Qualifier("taskSourceVerifierProducerOriginalAuthority") TaskNodeProducerOriginalAuthority originals,@org.springframework.beans.factory.annotation.Qualifier("taskSourceVerifierProducerSchema") TaskNodeProducerSchema schema,PermissionSchema json){
        return new TaskNodeProducerService(access,data,publications,policies,roles,tasks,plans,permissions,grants,identities,
                resources,originals,schema,json);
    }
    @Bean TaskSourceVerifierProducerAuthority taskSourceVerifierProducerAuthority(
            @Qualifier("taskSourceVerifierProducerService") TaskNodeProducerService rules,PermissionSchema json,
            @Qualifier("masterDataSource") DataSource data){return new TaskSourceVerifierProducerAuthority(rules,json,data);}
}
