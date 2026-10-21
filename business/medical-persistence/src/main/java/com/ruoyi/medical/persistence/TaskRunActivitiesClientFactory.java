package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.workflow.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Explicit independent outbound activities material. This reads existing protected
 * configuration only; it never creates credentials/pins or registers a worker. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-run.activities.client-enabled",havingValue="true",matchIfMissing=false)
class TaskRunActivitiesClientFactory {
    @Bean(name="taskRunActivitiesTrust")ServiceCertificateIdentity.TrustSource trust(
            @Value("${medical.task-run.activities.tls.configuration-directory}")Path directory,@Value("${medical.task-run.activities.tls.configuration-owner}")String owner,
            @Value("${medical.task-run.activities.tls.maximum-configuration-bytes}")long maximum)throws Exception{
        require(directory,owner,maximum);var store=new ServiceCertificateTrustStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum);return store::read;
    }
    @Bean(name="taskRunActivitiesTlsStore")ServiceClientTlsStore store(
            @Value("${medical.task-run.activities.tls.configuration-directory}")Path directory,@Value("${medical.task-run.activities.tls.configuration-owner}")String owner,
            @Value("${medical.task-run.activities.tls.maximum-configuration-bytes}")long maximum,@Qualifier("taskRunActivitiesTrust")ServiceCertificateIdentity.TrustSource trust)throws Exception{
        require(directory,owner,maximum);return new ServiceClientTlsStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum,trust);
    }
    @Bean(name="taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts(@Qualifier("taskRunActivitiesTlsStore")ServiceClientTlsStore store,
            @Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.service-uri}")String service,
            @Value("${medical.task-run.activities.certificate-sha256}")String certificate,@Value("${medical.task-run.activities.trust-revision}")String trust)throws Exception{
        return store.contexts(new ServiceClientTlsStore.Remote(origin,service,certificate,trust));
    }
    @Bean(name="taskRunActivitiesClient")RunActivities client(@Qualifier("taskRunActivitiesTls")ServiceClientTlsStore.ContextSource contexts,
            @Value("${medical.task-run.activities.origin}")URI origin,@Value("${medical.task-run.activities.timeout-millis}")long timeout,
            @Value("${medical.task-run.activities.maximum-response-bytes}")int maximum){
        return new TaskRunActivitiesHttpClient(origin,contexts::create,Duration.ofMillis(timeout),maximum,new TaskRunActivitySchema());
    }
    private static void require(Path directory,String owner,long maximum){if(directory==null||!directory.isAbsolute()||!directory.normalize().equals(directory)||owner==null||owner.isBlank()||maximum<1||maximum>2097152)throw new IllegalArgumentException("EXPLICIT_ACTIVITY_TLS_CONFIGURATION_REQUIRED");}
}
