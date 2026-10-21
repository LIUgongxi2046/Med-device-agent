package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import java.net.URI;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** Required operator-owned outbound material. Existing trust.json remains unchanged;
 * client-tls.json is additive and never generated or installed by this configuration. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
class TaskSectionWriterTlsConfiguration {
 @Bean(name="sectionWriterSupervisorTrust")
 ServiceCertificateIdentity.TrustSource sectionWriterSupervisorTrust(
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.configuration-directory}")Path directory,
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.configuration-owner}")String owner,
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.maximum-configuration-bytes}")long maximum)throws Exception{
  require(directory,owner,maximum);var store=new ServiceCertificateTrustStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum);return store::read;
 }
 @Bean(name="sectionWriterSupervisorTlsStore")
 ServiceClientTlsStore sectionWriterSupervisorTlsStore(
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.configuration-directory}")Path directory,
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.configuration-owner}")String owner,
   @Value("${medical.professional-runtime.section-writer.supervisor-tls.maximum-configuration-bytes}")long maximum,
   @Qualifier("sectionWriterSupervisorTrust")ServiceCertificateIdentity.TrustSource trust)throws Exception{
  require(directory,owner,maximum);return new ServiceClientTlsStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum,trust);
 }
 @Bean(name="sectionWriterSupervisorTls")
 ServiceClientTlsStore.ContextSource sectionWriterSupervisorTls(@Qualifier("sectionWriterSupervisorTlsStore")ServiceClientTlsStore store,TaskSectionWriterDeployment deployment)throws Exception{
  var d=deployment.value();return store.contexts(new ServiceClientTlsStore.Remote(URI.create(d.required("supervisor_endpoint").textValue()),d.required("supervisor_service_uri").textValue(),d.required("supervisor_certificate_sha256").textValue(),d.required("supervisor_trust_revision").textValue()));
 }
 private static void require(Path directory,String owner,long maximum){if(directory==null||!directory.isAbsolute()||!directory.normalize().equals(directory)||owner==null||owner.isBlank()||maximum<1||maximum>2097152)throw new IllegalArgumentException("EXPLICIT_SUPERVISOR_TLS_CONFIGURATION_REQUIRED");}
}
