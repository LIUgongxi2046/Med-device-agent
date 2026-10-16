package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceDeletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import javax.sql.DataSource;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(prefix="medical.source-deletion",name="enabled",havingValue="true")
@EnableConfigurationProperties(SourceDeletionPolicy.class)
@EnableScheduling
public class SourceDeletionConfiguration {
    @Bean
    ThreadPoolTaskScheduler sourceDeletionScheduler() {
        var scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("medical-source-deletion-"); scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(error -> { throw new IllegalStateException("删除 Worker 执行失败，原作业保持待核验状态",error); });
        scheduler.setWaitForTasksToCompleteOnShutdown(true); scheduler.setAwaitTerminationSeconds(35); return scheduler;
    }

    @Bean
    SourceDeletionWorker sourceDeletionWorker(@Qualifier("masterDataSource") DataSource data,SourceDeletionPolicy policy,
            TenantTransaction transactions,TenantMemberMapper members,PermissionMapper permissions,PermissionAuthority authority,
            SourceGovernanceMapper governance,SourceDeletionMapper mapper,PermissionSchema json,SourceGovernanceSchema schema,
            RedissonClient redis) throws Exception {
        var access=new SourceDeletionAccess(data,transactions,members,permissions,authority,governance,mapper,policy);
        var stores=new SourceDeletionStores(policy,json,schema,redis);
        return new SourceDeletionWorker(data,policy,access,stores,mapper,governance,permissions,json,schema);
    }
}
