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

@Configuration
@ConditionalOnProperty(prefix="medical.task-delegation",name="enabled",havingValue="true")
public class TaskDelegationConfiguration {
    @Bean TaskDelegationSchema taskDelegationSchema(PermissionSchema json)throws Exception{return new TaskDelegationSchema(json);}
    @Bean TaskDelegationService taskDelegationService(EnterpriseAccess access,@Qualifier("masterDataSource") DataSource data,
            AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,
            TaskMapper tasks,PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,
            @Value("${medical.task-delegation.maximum-candidates}") int maximumCandidates){
        return new TaskDelegationService(access,data,publications,policies,roles,tasks,permissions,budgets,grants,identities,resources,schema,json,maximumCandidates);
    }
}
