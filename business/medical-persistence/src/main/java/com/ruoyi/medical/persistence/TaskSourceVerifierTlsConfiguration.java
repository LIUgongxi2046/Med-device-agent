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
@ConditionalOnProperty(name="medical.professional-runtime.source-verifier-enabled",havingValue="true",matchIfMissing=false)
class TaskSourceVerifierTlsConfiguration {
 @Bean(name="sourceVerifierSupervisorTrust")
 ServiceCertificateIdentity.TrustSource sourceVerifierSupervisorTrust(
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.configuration-directory}")Path directory,
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.configuration-owner}")String owner,
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.maximum-configuration-bytes}")long maximum)throws Exception{
  require(directory,owner,maximum);var store=new ServiceCertificateTrustStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum);return store::read;
 }
 @Bean(name="sourceVerifierSupervisorTlsStore")
 ServiceClientTlsStore sourceVerifierSupervisorTlsStore(
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.configuration-directory}")Path directory,
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.configuration-owner}")String owner,
   @Value("${medical.professional-runtime.source-verifier.supervisor-tls.maximum-configuration-bytes}")long maximum,
   @Qualifier("sourceVerifierSupervisorTrust")ServiceCertificateIdentity.TrustSource trust)throws Exception{
  require(directory,owner,maximum);return new ServiceClientTlsStore(directory,directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(owner),maximum,trust);
 }
 @Bean(name="sourceVerifierSupervisorTls")
 ServiceClientTlsStore.ContextSource sourceVerifierSupervisorTls(@Qualifier("sourceVerifierSupervisorTlsStore")ServiceClientTlsStore store,TaskSourceVerifierDeployment deployment)throws Exception{
  var d=deployment.value();return store.contexts(new ServiceClientTlsStore.Remote(URI.create(d.required("supervisor_endpoint").textValue()),d.required("supervisor_service_uri").textValue(),d.required("supervisor_certificate_sha256").textValue(),d.required("supervisor_trust_revision").textValue()));
 }
 private static void require(Path directory,String owner,long maximum){if(directory==null||!directory.isAbsolute()||!directory.normalize().equals(directory)||owner==null||owner.isBlank()||maximum<1||maximum>2097152)throw new IllegalArgumentException("EXPLICIT_SUPERVISOR_TLS_CONFIGURATION_REQUIRED");}
}
