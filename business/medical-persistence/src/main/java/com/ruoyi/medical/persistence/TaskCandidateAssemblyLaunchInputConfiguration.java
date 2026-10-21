package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.task-candidate-assembly-producer.enabled",havingValue="true",matchIfMissing=false)
public class TaskCandidateAssemblyLaunchInputConfiguration {
    @Bean TaskCandidateAssemblyLaunchInputs taskCandidateAssemblyLaunchInputs(EnterpriseAccess access,@Qualifier("masterDataSource")DataSource data,
            AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,IdentityDirectoryPolicy identity,
            @Qualifier("taskCandidateAssemblyProducerService")TaskNodeProducerService producers,TemplateVersionStore templates,
            DocumentReadService documents,DependencyLineage lineage,TemplateDependencyPolicy limits){
        return new TaskCandidateAssemblyLaunchInputs(access,data,new TaskSourceVerifierSchema(),publications,policies,identity,producers,templates,documents,lineage,limits);
    }
}
