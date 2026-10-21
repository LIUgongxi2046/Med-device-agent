package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceClientTlsStore;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Independently default-off poller on the same operator-pinned activity origin.
 * Reuses a fresh-context SOURCE, never a cached TLS session or browser credential. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.activities.client-enabled","medical.task-run.waiting-v2.relay-client-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunRelayClientConfigurationV2 {
    @Bean TaskRunRelayHttpClientV2 taskRunRelayHttpClientV2(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,
            @Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.waiting-v2.relay.http-timeout}")Duration timeout,
            @Value("${medical.task-run.waiting-v2.relay.maximum-response-bytes}")int maximum){return new TaskRunRelayHttpClientV2(origin,contexts,timeout,maximum);}
    @Bean TaskRunRelayLifecycleV2 taskRunRelayLifecycleV2(TaskRunRelayHttpClientV2 client,@Value("${medical.task-run.waiting-v2.relay.tenant-id}")String tenant,
            @Value("${medical.task-run.waiting-v2.relay.poll-interval}")Duration interval,@Value("${medical.task-run.waiting-v2.relay.shutdown-timeout}")Duration shutdown,
            @Value("${medical.task-run.waiting-v2.relay.maximum-consecutive-failures}")int failures){return new TaskRunRelayLifecycleV2(client,tenant,interval,shutdown,failures);}
}
