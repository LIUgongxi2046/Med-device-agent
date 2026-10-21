package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import io.temporal.client.*;
import io.temporal.serviceclient.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Default-off real Temporal connection factory. Reads existing operator-owned
 * protected trust/client secret references. No credentials or insecure local
 * fallback are provisioned; namespace must match the immutable V2 deployment. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.temporal-enabled"},havingValue="true",matchIfMissing=false)
class TaskRunTemporalConfigurationV2 {
    @Bean(name="taskRunTemporalTrustV2")ServiceCertificateIdentity.TrustSource trust(
            @Value("${medical.task-run.waiting-v2.temporal.tls.configuration-directory}")Path directory,
            @Value("${medical.task-run.waiting-v2.temporal.tls.configuration-owner}")String owner,
            @Value("${medical.task-run.waiting-v2.temporal.tls.maximum-configuration-bytes}")long maximum)throws Exception{
        require(directory,owner,maximum);var trust=new ServiceCertificateTrustStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum);return trust::read;
    }
    @Bean(name="taskRunTemporalTlsV2")ServiceClientTlsStore.ContextSource tls(
            @Value("${medical.task-run.waiting-v2.temporal.tls.configuration-directory}")Path directory,
            @Value("${medical.task-run.waiting-v2.temporal.tls.configuration-owner}")String owner,
            @Value("${medical.task-run.waiting-v2.temporal.tls.maximum-configuration-bytes}")long maximum,
            @Qualifier("taskRunTemporalTrustV2")ServiceCertificateIdentity.TrustSource trust,
            @Value("${medical.task-run.waiting-v2.temporal.origin}")URI origin,@Value("${medical.task-run.waiting-v2.temporal.service-uri}")String service,
            @Value("${medical.task-run.waiting-v2.temporal.certificate-sha256}")String certificate,@Value("${medical.task-run.waiting-v2.temporal.trust-revision}")String revision)throws Exception{
        require(directory,owner,maximum);var store=new ServiceClientTlsStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum,trust);
        return store.contexts(new ServiceClientTlsStore.Remote(origin,service,certificate,revision));
    }
    @Bean(destroyMethod="close")TaskRunTemporalChannelV2 taskRunTemporalChannelV2(
            @Value("${medical.task-run.waiting-v2.temporal.origin}")URI origin,@Qualifier("taskRunTemporalTlsV2")ServiceClientTlsStore.ContextSource contexts,
            @Value("${medical.task-run.waiting-v2.temporal.connect-timeout}")Duration connect,@Value("${medical.task-run.waiting-v2.temporal.maximum-call-timeout}")Duration maximumCall,
            @Value("${medical.task-run.waiting-v2.temporal.maximum-message-bytes}")int maximumBytes,@Value("${medical.task-run.waiting-v2.temporal.maximum-concurrent-calls}")int calls){
        return new TaskRunTemporalChannelV2(origin,contexts,connect,maximumCall,maximumBytes,calls);
    }
    @Bean(name="taskRunWorkflowServiceStubsV2",destroyMethod="shutdownNow")WorkflowServiceStubs stubs(TaskRunTemporalChannelV2 channel,
            @Value("${medical.task-run.waiting-v2.temporal.rpc-timeout}")Duration rpc,@Value("${medical.task-run.waiting-v2.temporal.long-poll-timeout}")Duration poll,
            @Value("${medical.task-run.waiting-v2.temporal.maximum-call-timeout}")Duration maximumCall){
        if(rpc==null||rpc.compareTo(Duration.ofMillis(100))<0||rpc.compareTo(Duration.ofSeconds(60))>0||poll==null||poll.compareTo(Duration.ofSeconds(60))<0||poll.compareTo(Duration.ofMinutes(2))>0||maximumCall==null||maximumCall.compareTo(rpc)<0||maximumCall.compareTo(poll)<0)throw new IllegalArgumentException("BOUNDED_TEMPORAL_RPC_REQUIRED");
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setChannel(channel).setDisableHealthCheck(true)
            .setRpcTimeout(rpc).setRpcQueryTimeout(rpc).setRpcLongPollTimeout(poll).setSystemInfoTimeout(rpc)
            .setRpcRetryOptions(RpcRetryOptions.newBuilder().setMaximumAttempts(1).setExpiration(rpc).validateBuildWithDefaults()).build());
    }
    @Bean(name="taskRunWorkflowClientV2")WorkflowClient client(@Qualifier("taskRunWorkflowServiceStubsV2")WorkflowServiceStubs service,
            TaskRunWorkflowV2Configuration.Pinned pinned,@Value("${medical.task-run.waiting-v2.temporal.namespace}")String namespace){
        if(!pinned.protocol().namespace().equals(namespace)||!pinned.launch().namespace().equals(namespace))throw new IllegalArgumentException("PINNED_TEMPORAL_NAMESPACE_REQUIRED");
        return WorkflowClient.newInstance(service,WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }
    private static void require(Path directory,String owner,long maximum){if(directory==null||!directory.isAbsolute()||!directory.normalize().equals(directory)||owner==null||owner.isBlank()||maximum<1||maximum>2097152)throw new IllegalArgumentException("EXPLICIT_TEMPORAL_TLS_CONFIGURATION_REQUIRED");}
}
