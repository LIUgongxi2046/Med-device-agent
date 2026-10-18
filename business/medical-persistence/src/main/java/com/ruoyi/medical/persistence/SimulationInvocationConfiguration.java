package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Separate protected policy, signing key and audience; no production fallback. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.simulation-execution",name="enabled",havingValue="true")
class SimulationInvocationConfiguration {
    record Keys(RunTokenPolicyStore tokens,RunTokenSigningKeyStore signing,ServiceCertificateTrustStore trust,ServiceCertificateIdentity services){}
    @Bean Keys simulationExecutionKeys(@Value("${medical.simulation-execution.configuration-directory}")Path directory,
            @Value("${medical.simulation-execution.configuration-owner}")String ownerName,
            @Value("${medical.simulation-execution.maximum-configuration-bytes}")long maximumBytes)throws Exception {
        if(!directory.isAbsolute()||ownerName.isBlank()||maximumBytes<=0)throw new IllegalArgumentException("SIMULATION_EXECUTION_CONFIGURATION_REQUIRED");
        var owner=directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(ownerName);
        var tokens=new RunTokenPolicyStore(directory,owner,maximumBytes);var trust=new ServiceCertificateTrustStore(directory,owner,maximumBytes);
        if(!SimulationRunTokenVerifier.AUDIENCE.equals(tokens.read().policy().audience()))throw new SecurityException("SIMULATION_TOKEN_AUDIENCE_REQUIRED");
        return new Keys(tokens,new RunTokenSigningKeyStore(directory,owner,maximumBytes,tokens),trust,new ServiceCertificateIdentity(trust::read));
    }
    @Bean SimulationInvocationSchema simulationInvocationSchema()throws Exception{return new SimulationInvocationSchema();}
    @Bean SimulationEvaluationSchema simulationEvaluationSchema()throws Exception{return new SimulationEvaluationSchema();}
    @Bean SimulationEvaluationStore simulationEvaluationStore(@Qualifier("masterDataSource")DataSource data){return new SimulationEvaluationStore(data);}
    @Bean SimulationWorkerAccess simulationWorkerAccess(@Qualifier("masterDataSource")DataSource data,TenantTransaction transactions,
            PermissionMapper permissions,PermissionAuthority grants,RunTokenAuthoritySchema manifests,Keys keys){return new SimulationWorkerAccess(data,transactions,permissions,manifests,keys.services(),grants);}
    @Bean SimulationInvocationService simulationInvocationService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,
            TenantTransaction transactions,SimulationInvocationSchema schema,SimulationExecutionPermissionPolicy policy,
            PermissionMapper permissions,PermissionAuthority grants,SimulationInvocationSources sources,SimulationEvaluationCandidateAuthority candidates,
            SimulationWorkerAccess workers,Keys keys)throws Exception {
        return new SimulationInvocationService(data,access,transactions,schema,policy,permissions,grants,sources,
            keys.services(),new SimulationRunTokenVerifier(data,keys.tokens(),schema),candidates,workers);
    }
    @Bean SimulationEvaluationPolicyService simulationEvaluationPolicyService(EnterpriseAccess access,SimulationEvaluationStore store,
            SimulationInvocationSchema schema,SimulationQuotaPermissionPolicy quota,SimulationRetentionPermissionPolicy retention){return new SimulationEvaluationPolicyService(access,store,schema,quota,retention);}
    @Bean SimulationEvaluationService simulationEvaluationService(@Qualifier("masterDataSource")DataSource data,EnterpriseAccess access,SimulationWorkerAccess workers,
            SimulationEvaluationStore store,SimulationInvocationSchema schema,SimulationEvaluationSchema api,SimulationContextProducer contexts,
            SimulationEvaluationCandidateAuthority candidates,EvaluationMapper evaluations,EvaluationSchema evaluationSchema,RunTokenAuthoritySchema manifests,
            SimulationExecutionPermissionPolicy execute,SimulationQuotaPermissionPolicy quota,SimulationRetentionPermissionPolicy retention,
            SimulationEvaluationPolicyService policies,Keys keys,org.springframework.beans.factory.ObjectProvider<SimulationInputArtifactService> inputArtifacts,EvaluationService evaluationDefinitions)throws Exception {
        return new SimulationEvaluationService(access,workers,store,new SimulationInvocationStore(data,schema),schema,api,contexts,candidates,evaluations,evaluationSchema,
            manifests,execute,quota,retention,policies,new SimulationEvaluationTokenIssuer(keys.tokens(),keys.signing()),keys.services(),keys.trust(),inputArtifacts,evaluationDefinitions);
    }
    @Bean
    @ConditionalOnProperty(prefix="medical.simulation-execution.result-persistence",name="enabled",havingValue="true")
    SimulationResultPersistenceService simulationResultPersistenceService(@Qualifier("masterDataSource")DataSource data,SimulationInvocationService ledger,
            SimulationInvocationSchema schema,SimulationResultTransport transport,com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper artifacts,
            DependencyManifestSchema manifests,DependencyLineage lineage)throws Exception {
        var results=new SimulationResultSchema(schema);
        return new SimulationResultPersistenceService(ledger,new SimulationResultStore(data,schema,results,artifacts,manifests,lineage),results,transport);
    }
}
