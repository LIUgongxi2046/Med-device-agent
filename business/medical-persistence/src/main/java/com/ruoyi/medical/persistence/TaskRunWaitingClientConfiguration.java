package com.ruoyi.medical.persistence;
import com.ruoyi.medical.identity.ServiceClientTlsStore;
import com.ruoyi.medical.workflow.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
/** Same explicitly pinned activities origin, never the supervisor's TLS context. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.client-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunWaitingClientConfiguration {
    @Bean(name="taskRunWaitingClient")TaskRunWaitingActivities client(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,@Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.timeout-millis}")long timeout,@Value("${medical.task-run.activities.maximum-response-bytes}")int maximum){return new TaskRunWaitingHttpClient(origin,contexts::create,Duration.ofMillis(timeout),maximum,new TaskRunWaitingSchema());}
    @Bean(name="taskRunRecoveryClientV2")TaskRunRecoveryActivitiesV2 recoveryClient(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,@Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.timeout-millis}")long timeout,@Value("${medical.task-run.activities.maximum-response-bytes}")int maximum){return new TaskRunRecoveryHttpClientV2(origin,contexts::create,Duration.ofMillis(timeout),maximum,new TaskRunRecoverySchemaV2());}
    @Bean(name="taskRunStageClientV2")TaskRunStageActivitiesV2 stageClient(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,@Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.timeout-millis}")long timeout,@Value("${medical.task-run.activities.maximum-response-bytes}")int maximum){return new TaskRunStageHttpClientV2(origin,contexts::create,Duration.ofMillis(timeout),maximum,new TaskRunStageSchemaV2());}
}
