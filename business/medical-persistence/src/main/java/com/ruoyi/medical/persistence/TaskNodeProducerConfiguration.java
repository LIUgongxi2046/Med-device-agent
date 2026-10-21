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
@ConditionalOnProperty(prefix="medical.task-node-producer",name="enabled",havingValue="true")
public class TaskNodeProducerConfiguration {
    @Bean TaskNodeProducerSchema taskNodeProducerSchema()throws Exception{return new TaskNodeProducerSchema();}
    @Bean TaskNodeProducerOriginalAuthority taskNodeProducerOriginalAuthority(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,
            @Value("${medical.task-delegation.maximum-candidates}") int maximumCandidates){
        return new TaskNodeProducerOriginalAuthority(access,data,publications,policies,roles,tasks,permissions,budgets,
                grants,identities,resources,schema,json,maximumCandidates);
    }
    @Bean TaskNodeProducerService taskNodeProducerService(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskService tasks,TaskPlanService plans,
            PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,@org.springframework.beans.factory.annotation.Qualifier("taskNodeProducerOriginalAuthority") TaskNodeProducerOriginalAuthority originals,@org.springframework.beans.factory.annotation.Qualifier("taskNodeProducerSchema") TaskNodeProducerSchema schema,PermissionSchema json){
        return new TaskNodeProducerService(access,data,publications,policies,roles,tasks,plans,permissions,grants,identities,
                resources,originals,schema,json);
    }
    @Bean TaskNodeProducerAuthority taskNodeProducerAuthority(@org.springframework.beans.factory.annotation.Qualifier("taskNodeProducerService") TaskNodeProducerService rules,PermissionSchema json,
            @Qualifier("masterDataSource") DataSource data){return new TaskNodeProducerAuthority(rules,json,data);}
}
