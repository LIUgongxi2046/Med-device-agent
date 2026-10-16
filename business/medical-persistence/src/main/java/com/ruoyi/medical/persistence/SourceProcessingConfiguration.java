package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import javax.sql.DataSource;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(prefix="medical.source-processing",name="enabled",havingValue="true")
@EnableConfigurationProperties(SourceProcessingPolicy.class)
class SourceProcessingConfiguration {
    @Bean SourceProcessingAccess sourceProcessingAccess(DataSource data,TenantTransaction transactions,
            TenantMemberMapper members,PermissionMapper permissions,PermissionAuthority authority,SourceReadMapper sources,
            SourceRightsSchema rights,SourceGovernanceMapper governance,SourceProcessingMapper mapper,
            SourceProcessingPolicy policy,SourceGovernanceSchema json) {
        return new SourceProcessingAccess(data,transactions,members,permissions,authority,sources,rights,governance,mapper,policy,json);
    }

    @Bean SourceProcessingWorker sourceProcessingWorker(SourceProcessingAccess access,SourceProcessingMapper mapper,
            PermissionMapper permissions,PermissionSchema json,SourceProcessingPolicy policy,SourceUploadTransport transport,
            SourceUploadTransportPolicy storage) throws Exception {
        return new SourceProcessingWorker(access,mapper,permissions,json,policy,transport,storage);
    }

    @Bean(destroyMethod="shutdown") ThreadPoolTaskScheduler sourceProcessingScheduler(SourceProcessingWorker worker,
            SourceProcessingPolicy policy) {
        var scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("source-processing-");
        scheduler.setErrorHandler(error -> { throw new IllegalStateException("来源处理 Worker 执行失败",error); });
        scheduler.setWaitForTasksToCompleteOnShutdown(false); scheduler.setAwaitTerminationSeconds(20); scheduler.initialize();
        var polling=new Runnable() {
            @Override public void run() {
                try {
                    for(var job:worker.pending()) scheduler.schedule(() -> worker.run(job),Instant.now());
                }
                finally {
                    if(!scheduler.getScheduledThreadPoolExecutor().isShutdown()) scheduler.schedule(this,Instant.now().plus(policy.polling()));
                }
            }
        };
        scheduler.schedule(polling,Instant.now().plus(policy.polling())); return scheduler;
    }
}
