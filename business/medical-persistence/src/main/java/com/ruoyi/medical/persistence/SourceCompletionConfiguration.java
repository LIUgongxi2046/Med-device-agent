package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import com.ruoyi.medical.persistence.mapper.TenantMemberMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(prefix="medical.source-completion",name="enabled",havingValue="true")
@EnableConfigurationProperties({SourceCompletionPolicy.class,SourceProcessingPolicy.class})
class SourceCompletionConfiguration {
    @Bean SourceCompletionAccess sourceCompletionAccess(DataSource data,TenantTransaction transactions,PermissionMapper permissions,
            TenantMemberMapper members,SourceUploadMapper uploads,UploadTransferMapper transfers,SourceUploadService service,
            SourceUploadSchema schema,PermissionSchema json,SourceCompletionPolicy completion,SourceProcessingPolicy processing,
            SourceGovernanceMapper governance,PermissionAuthority authority) {
        return new SourceCompletionAccess(data,transactions,permissions,members,uploads,transfers,service,schema,json,completion,processing,governance,authority);
    }
    @Bean SourceCompletionWorker sourceCompletionWorker(SourceCompletionAccess access,SourceCompletionLeaseStore leases,
            SourceCompletionCheckpointStore checkpoints,SourceCompletionRegistration registration,SourceCompletionExecution execution,SourceUploadTransport transport,
            SourceProcessingPolicy policy,SourceUploadTransportPolicy storage,PermissionSchema json) throws Exception {
        return new SourceCompletionWorker(access,leases,checkpoints,registration,execution,transport,policy,storage,json);
    }
    @Bean(destroyMethod="shutdown") ThreadPoolTaskScheduler sourceCompletionScheduler(SourceCompletionWorker worker,SourceProcessingPolicy policy) {
        var scheduler=new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("source-completion-");
        scheduler.setErrorHandler(error -> { throw new IllegalStateException("上传完成消费者执行失败",error); });
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
