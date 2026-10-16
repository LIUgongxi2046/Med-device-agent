package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceIndexMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(prefix="medical.source-index",name="enabled",havingValue="true")
@EnableConfigurationProperties({SourceIndexPolicy.class,SourceProcessingPolicy.class})
class SourceIndexConfiguration {
    @Bean SourceIndexAccess sourceIndexAccess(DataSource data,TenantTransaction transactions,TenantMemberMapper members,
            PermissionMapper permissions,PermissionAuthority authority,SourceReadMapper sources,SourceRightsSchema rights,
            SourceGovernanceMapper governance,SourceProcessingMapper parses,SourceIndexMapper mapper,
            SourceProcessingPolicy processing,SourceIndexPolicy policy,SourceGovernanceSchema json) throws Exception {
        var original=new SourceProcessingAccess(data,transactions,members,permissions,authority,sources,rights,governance,parses,processing,json);
        return new SourceIndexAccess(data,original,processing,policy,mapper,parses,governance);
    }
    @Bean SourceIndexWorker sourceIndexWorker(SourceIndexAccess access,SourceIndexMapper mapper,SourceProcessingMapper parses,
            PermissionMapper permissions,SourceUploadTransport storage,SourceIndexPolicy policy,PermissionSchema json) throws Exception {
        return new SourceIndexWorker(access,mapper,parses,permissions,storage,policy,json);
    }
    @Bean(destroyMethod="shutdown") ThreadPoolTaskScheduler sourceIndexScheduler(SourceIndexWorker worker,SourceIndexPolicy policy) {
        var scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("source-index-");
        scheduler.setErrorHandler(error -> { throw new IllegalStateException("全文索引消费者执行失败",error); });
        scheduler.setWaitForTasksToCompleteOnShutdown(false); scheduler.setAwaitTerminationSeconds(20); scheduler.initialize();
        var polling=new Runnable() {
            @Override public void run() {
                try { for(var job:worker.pending()) scheduler.schedule(() -> worker.run(job),Instant.now()); }
                finally {
                    if(!scheduler.getScheduledThreadPoolExecutor().isShutdown()) scheduler.schedule(this,Instant.now().plus(policy.polling()));
                }
            }
        };
        scheduler.schedule(polling,Instant.now().plus(policy.polling())); return scheduler;
    }
}
