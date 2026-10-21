package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.nio.file.Path;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Concrete TASK authority/receipt dependencies only. This does not register RN01,
 * PublishedRunCreateAdmission, a Temporal launcher, or a complete RunActivities worker.
 * Missing producer governance, model policy, retention, TLS or explicit bounds fails
 * enabled dependency construction rather than selecting a permissive fallback. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true",matchIfMissing=false)
public class TaskNodeRuntimeConfiguration {
    @Bean TaskRunBootstrapSchema taskRunBootstrapSchema()throws Exception{return new TaskRunBootstrapSchema();}
    @Bean TaskNodeInputSchema taskNodeInputSchema()throws Exception{return new TaskNodeInputSchema();}
    @Bean(name="taskRunLineageLimits") DependencyLineage.Limits taskRunLineageLimits(
            @Value("${medical.task-runtime.maximum-lineage-versions}") int versions,
            @Value("${medical.task-runtime.maximum-lineage-relations}") int relations){return new DependencyLineage.Limits(versions,relations);}
    @Bean(name="taskNodeObjectTransport") RuntimeObjectTransport taskNodeObjectTransport(SourceUploadTransport objects,
            @Value("${medical.task-runtime.object-directory}") Path directory,
            @Value("${medical.task-runtime.maximum-object-bytes}") int bytes)throws Exception{return new RuntimeObjectTransport(objects,directory,bytes);}
    @Bean FormalWorkerControlAuthority formalWorkerControlAuthority(@Qualifier("masterDataSource") DataSource data,
            RunTokenAuthoritySchema schema,@Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services,
            PermissionMapper permissions,PermissionAuthority grants){return new FormalWorkerControlAuthority(data,schema,services,permissions,grants);}
    @Bean FormalRunControlAccess formalRunControlAccess(@Qualifier("masterDataSource") DataSource data,TenantTransaction transactions,
            FormalWorkerControlAuthority controls,@Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services,
            PermissionMapper permissions,VersionResourceMapper owners,ArtifactRegistryMapper artifacts,PermissionSchema json,RunTokenAuthoritySchema schema,
            @Value("${medical.task-runtime.maximum-control-duration}") Duration duration,
            @Value("${medical.task-runtime.maximum-dependencies}") int dependencies){
        return new FormalRunControlAccess(data,transactions,controls,services,permissions,owners,artifacts,json,schema,duration,dependencies);
    }
    @Bean FormalRunRecoveryAccess formalRunRecoveryAccess(@Qualifier("masterDataSource") DataSource data,TenantTransaction transactions,
            @Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services,PermissionMapper permissions,
            RunTokenAuthoritySchema schema,PermissionSchema json,ResourceReadAuthority resources){
        return new FormalRunRecoveryAccess(data,transactions,services,permissions,schema,json,resources);
    }
    @Bean TaskRunBootstrapAuthority taskRunBootstrapAuthority(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            ResourceReadAuthority resources,RunTokenAuthoritySchema tokens,RuntimeReplaySchema replays,TaskDelegationService delegations,
            AgentReleaseStore releases,AgentReleaseScopeAuthority scopes,@Qualifier("taskRunLineageLimits") DependencyLineage.Limits limits){
        return new TaskRunBootstrapAuthority(data,json,resources,tokens,replays,delegations,releases,scopes,limits);
    }
    @Bean TaskNodeProductionAuthority taskNodeProductionAuthority(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            ResourceReadAuthority resources,RunTokenAuthoritySchema tokens,RuntimeReplaySchema replays,TaskDelegationService delegations,
            AgentReleaseStore releases,AgentReleaseScopeAuthority scopes,@Qualifier("taskRunLineageLimits") DependencyLineage.Limits limits){
        return new TaskNodeProductionAuthority(data,json,resources,tokens,replays,delegations,releases,scopes,limits);
    }
    @Bean TaskRunLaunchRecoveryAuthority taskRunLaunchRecoveryAuthority(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            ResourceReadAuthority resources,RunTokenAuthoritySchema tokens,RuntimeReplaySchema replays,
            @Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity services){return new TaskRunLaunchRecoveryAuthority(data,json,resources,tokens,replays,services);}
    @Bean TaskRunWorkflowLedger taskRunWorkflowLedger(@Qualifier("masterDataSource") DataSource data,TaskRunBootstrapSchema schema){return new TaskRunWorkflowLedger(data,schema);}
    @Bean TaskRunAcceptedStart taskRunAcceptedStart(@Qualifier("masterDataSource") DataSource data,
            TaskRunLaunchRecoveryAuthority recoveries,TaskRunWorkflowLedger launches,TaskRunBootstrapSchema schema){return new TaskRunAcceptedStart(data,recoveries,launches,schema);}
    @Bean TaskRunReplayLedger taskRunReplayLedger(@Qualifier("masterDataSource") DataSource data,TaskRunBootstrapSchema schema,PermissionSchema json,
            @Value("${medical.task-runtime.maximum-replay-bytes}") int bytes){return new TaskRunReplayLedger(data,schema,json,bytes);}
    @Bean TaskRunReplayGovernanceLedger taskRunReplayGovernanceLedger(@Qualifier("masterDataSource") DataSource data,PermissionSchema json){return new TaskRunReplayGovernanceLedger(data,json);}
    @Bean TaskRunReplayContentAuthority taskRunReplayContentAuthority(SourceReadAuthority sources,DependencyLineage lineage,VersionResourceMapper owners,
            @Qualifier("taskRunLineageLimits") DependencyLineage.Limits limits){return new TaskRunReplayContentAuthority(sources,lineage,owners,limits);}
    @Bean TaskRunReplayRegistration taskRunReplayRegistration(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            TaskRunReplayLedger ledger,TaskRunReplayGovernanceLedger governance,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests,
            DependencyLineage lineage,@Qualifier("taskRunLineageLimits") DependencyLineage.Limits limits,TaskRunReplayContentAuthority content,TaskRunBootstrapSchema schema){
        return new TaskRunReplayRegistration(data,json,ledger,governance,artifacts,manifests,lineage,limits,content,schema);
    }
    @Bean TaskNodeInputLedger taskNodeInputLedger(@Qualifier("masterDataSource") DataSource data,TaskNodeInputSchema schema,PermissionSchema json){return new TaskNodeInputLedger(data,schema,json);}
    @Bean TaskNodeInputRegistration taskNodeInputRegistration(@Qualifier("masterDataSource") DataSource data,TaskNodeInputLedger ledger,
            TaskNodeInputSchema schema,PermissionSchema json,DependencyManifestSchema dependencies){return new TaskNodeInputRegistration(data,ledger,schema,json,dependencies);}
    @Bean TaskResearchCompletionAuthority taskResearchCompletionAuthority(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            TaskResearchSourceReader sources,TaskProfessionalSupervisor supervisor,TaskProfessionalRuntimeService.Deployment deployment,
            TaskResearchRuleInputs rules,RuleExpression engine,TaskInvocationResults invocations,TaskNodeInputLedger inputs,
            TaskNodeInputRegistration registrations,TaskResearchRetrievalAuthority retrievals,TaskResearchWaitingService waiting,
            TaskProfessionalObjectRegistration objects){
        return new TaskResearchCompletionAuthority(data,json,sources,supervisor,deployment,rules,engine,invocations,inputs,registrations,retrievals,waiting,objects);
    }
    @Bean TaskRunGraphService taskRunGraphService(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            @Value("${medical.task-runtime.maximum-questions}") int questions,TaskResearchCompletionAuthority completions){return new TaskRunGraphService(data,json,questions,completions);}
    @Bean TaskNodeInputAuthority taskNodeInputAuthority(@Qualifier("masterDataSource") DataSource data,TaskNodeInputSchema schema,
            PermissionSchema json,TaskNodeInputLedger ledger,TaskNodeInputRegistration registrations,TaskRunGraphService graphs,
            TaskNodeProducerAuthority producers,TaskRunReplayContentAuthority content,SourceReadAuthority sources,
            VersionResourceMapper owners,RunTokenAuthoritySchema tokens){return new TaskNodeInputAuthority(data,schema,json,ledger,registrations,graphs,producers,content,sources,owners,tokens);}
    @Bean TaskNodeMaterializer taskNodeMaterializer(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,TaskNodeInputSchema schema,
            TaskNodeInputLedger ledger,TaskNodeInputRegistration registrations,RunTokenAuthoritySchema tokens){return new TaskNodeMaterializer(data,json,schema,ledger,registrations,tokens);}
    @Bean FormalTaskNodeAuthority formalTaskNodeAuthority(@Qualifier("masterDataSource") DataSource data,FormalRunControlAccess controls,
            AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,
            TaskDelegationService delegations,ModelEligibilityAuthority models,TaskNodeInputAuthority inputs,RunCreateBudget budgets,
            PermissionMapper permissions,PermissionAuthority grants,VersionResourceMapper owners,RunTokenAuthoritySchema schema,PermissionSchema json,
            org.springframework.beans.factory.ObjectProvider<TaskStrictFieldInputAuthority> strictInputs,
            TaskNodeProductionAuthority strictProduction,RuntimeRetentionAuthority strictRetention,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writerInputs,org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierInputAuthority> verifierInputs){
        return new FormalTaskNodeAuthority(data,controls,publications,policies,roles,delegations,models,inputs,budgets,permissions,grants,owners,schema,json,strictInputs,strictProduction,strictRetention,writerInputs,verifierInputs);
    }
    @Bean TaskRuntimeJournal taskRuntimeJournal(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,TaskRunGraphService graphs)throws Exception{return new TaskRuntimeJournal(data,json,graphs);}
    @Bean TaskResearchCommitService taskResearchCommitService(@Qualifier("masterDataSource") DataSource data,PermissionSchema json,
            TaskResearchCompletionAuthority completions,TaskRuntimeJournal journal){return new TaskResearchCommitService(data,json,completions,journal);}
    @Bean TaskRunActivityPreparation taskRunActivityPreparation(@Qualifier("masterDataSource") DataSource data,FormalRunControlAccess control,
            AgentPublicationAuthority publications,AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,
            com.ruoyi.medical.identity.IdentityDirectoryPolicy identity,TaskRunBootstrapAuthority bootstrap,TaskNodeProductionAuthority production,
            TaskRunAcceptedStart accepted,TaskRunReplayLedger replayLedger,TaskRunReplayRegistration replayRegistration,TaskRunReplayContentAuthority content,
            TaskRunGraphService graphs,TaskNodeProducerAuthority producers,TaskNodeInputAuthority inputs,TaskNodeInputLedger inputLedger,
            TaskNodeInputRegistration inputRegistration,TaskNodeMaterializer materializer,TaskRuntimeJournal journal,
            @Qualifier("taskNodeObjectTransport") RuntimeObjectTransport objects){
        return new TaskRunActivityPreparation(data,control,publications,policies,retention,identity,bootstrap,production,accepted,replayLedger,
            replayRegistration,content,graphs,producers,inputs,inputLedger,inputRegistration,materializer,journal,objects);
    }
}
