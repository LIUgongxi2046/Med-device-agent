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
@ConditionalOnProperty(prefix="medical.task-section-writer-producer",name="enabled",havingValue="true")
public class TaskSectionWriterProducerConfiguration {
    @Bean TaskNodeProducerSchema taskSectionWriterProducerSchema()throws Exception{return new TaskNodeProducerSchema(TaskNodeProducerProfile.WRITER);}
    @Bean TaskNodeProducerOriginalAuthority taskSectionWriterProducerOriginalAuthority(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,
            @Value("${medical.task-delegation.maximum-candidates}") int maximumCandidates){
        return new TaskNodeProducerOriginalAuthority(access,data,publications,policies,roles,tasks,permissions,budgets,
                grants,identities,resources,schema,json,maximumCandidates,TaskNodeProducerProfile.WRITER);
    }
    @Bean TaskNodeProducerService taskSectionWriterProducerService(EnterpriseAccess access,
            @Qualifier("masterDataSource") DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskService tasks,TaskPlanService plans,
            PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,@org.springframework.beans.factory.annotation.Qualifier("taskSectionWriterProducerOriginalAuthority") TaskNodeProducerOriginalAuthority originals,@org.springframework.beans.factory.annotation.Qualifier("taskSectionWriterProducerSchema") TaskNodeProducerSchema schema,PermissionSchema json){
        return new TaskNodeProducerService(access,data,publications,policies,roles,tasks,plans,permissions,grants,identities,
                resources,originals,schema,json);
    }
    @Bean TaskSectionWriterProducerAuthority taskSectionWriterProducerAuthority(
            @Qualifier("taskSectionWriterProducerService") TaskNodeProducerService rules,PermissionSchema json,
            @Qualifier("masterDataSource") DataSource data,ResourceReadAuthority resources){return new TaskSectionWriterProducerAuthority(rules,json,data,resources);}
}
