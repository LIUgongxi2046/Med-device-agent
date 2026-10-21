package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit admission wiring only. Does not enable RN01, a launcher, or any worker. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-run.published-admission.enabled",havingValue="true",matchIfMissing=false)
public class PublishedRunCreateAdmissionConfiguration {
 @Bean PublishedRunCreateAdmission publishedRunCreateAdmission(
   ObjectProvider<AgentPublicationAuthority> publications,ObjectProvider<RuntimeRetentionAuthority> retentions,
   AgentExecutionPolicyAuthority policies,AgentReleaseScopeAuthority scopes,FormalTaskPermissionAuthority permissions,
   RuntimeReplayAssembler replay,TemplateDependencyPolicy limits,IdentityDirectoryPolicy identity,
   ObjectProvider<TaskCandidateAssemblyLaunchInputs> assemblyInputs,TaskSignedContinuationInputs signedInputs){
  var admission=new PublishedRunCreateAdmission(publications,retentions,policies,scopes,permissions,replay,limits,identity,assemblyInputs);admission.signedInputs(signedInputs);return admission;
 }
}
