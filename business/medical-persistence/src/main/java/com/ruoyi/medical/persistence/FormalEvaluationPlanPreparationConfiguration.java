package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;

/** Opt-in real083/088 wiring. No approval, grant, compiler, provider call or object-store credential is seeded. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.formal-evaluation-plan",name="enabled",havingValue="true",matchIfMissing=false)
@Import(FormalEvaluationPlanPreparationConfiguration.WiringSelector.class)
public class FormalEvaluationPlanPreparationConfiguration {
    public static final class WiringSelector implements DeferredImportSelector {
        @Override public String[] selectImports(AnnotationMetadata metadata){return new String[]{ReadWiring.class.getName(),Wiring.class.getName()};}
    }
    @ConditionalOnBean({FormalEvaluationInputService.class,FormalEvaluationContextStore.class,FormalEvaluationContextSchema.class,
            ModelEligibilityAuthority.class,FormalAgentCandidateAuthority.class,AgentExecutionPolicyAuthority.class,
            FormalCapabilityIdentityAuthority.class,TemplateApplicabilityService.class,AgentPublicationDependencies.class,
            FormalEvaluationContextReadAuthority.class,EvaluationService.class,DependencyManifestSchema.class,
            ModelConfigurationService.class,EvaluationReferences.class,SourceReadAuthority.class,RunCreateBudget.class,
            ScopeSnapshotStore.class,ResourceReadAuthority.class,RunTokenAuthoritySchema.class,TemplateVersionStore.class,
            ProjectService.class,TaskPlanPolicy.class,DependencyLineage.class,ProjectMapper.class,SourceReadMapper.class,ArtifactRegistryMapper.class})
    public static class Wiring {
        @Bean @ConditionalOnMissingBean
        FormalEvaluationExecutionAdmission formalEvaluationExecutionAdmission(EvaluationService e,FormalAgentCandidateAuthority c,
                AgentExecutionPolicyAuthority p,EvaluationReferences r,SourceReadAuthority s,SourceReadMapper rows,RunCreateBudget b,PermissionSchema j,DataSource d){return new FormalEvaluationExecutionAdmission(e,c,p,r,s,rows,b,j,d);}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationExecutionInputs formalEvaluationExecutionInputs(EnterpriseAccess a,FormalEvaluationExecutionAdmission r,FormalEvaluationInputService i,PermissionSchema j){return new FormalEvaluationExecutionInputs(a,r,i,j);}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationExecutionScopes formalEvaluationExecutionScopes(ScopeSnapshotStore s,ProjectMapper p){return new FormalEvaluationExecutionScopes(s,p,new DependencyLineage.Limits(512,2048));}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationModelPreparation formalEvaluationModelPreparation(ModelEligibilityAuthority e,ModelConfigurationService m,
                FormalCapabilityIdentityAuthority c,SourceReadAuthority s,ResourceReadAuthority r,RunTokenAuthoritySchema schema,PermissionSchema j){return new FormalEvaluationModelPreparation(e,m,c,s,r,schema,j);}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationExecutionRegistrations formalEvaluationExecutionRegistrations(EnterpriseAccess a,FormalEvaluationExecutionInputs i,
                FormalEvaluationExecutionScopes s,FormalEvaluationModelPreparation m,PermissionSchema j,DataSource d){return new FormalEvaluationExecutionRegistrations(a,i,s,m,j,d);}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationBoundExecutions formalEvaluationBoundExecutions(EnterpriseAccess a,FormalEvaluationExecutionInputs i,
                FormalEvaluationExecutionRegistrations r,FormalEvaluationExecutionScopes s,FormalEvaluationContextStore c,FormalEvaluationContextSchema schema,PermissionSchema j,DataSource d){return new FormalEvaluationBoundExecutions(a,i,r,s,c,schema,j,d);}
        @Bean @ConditionalOnMissingBean
        FormalEvaluationExactInputObjectAuthorization formalEvaluationExactInputObjectAuthorization(ResourceReadAuthority r,
                FormalCapabilityIdentityAuthority c,RunTokenAuthoritySchema s,PermissionSchema j){return new FormalEvaluationExactInputObjectAuthorization(r,c,s,j);}
        @Bean FormalEvaluationPlanExpansion formalEvaluationPlanExpansion(TaskPlanPolicy p,PermissionSchema j){return new FormalEvaluationPlanExpansion(p,j);}
        @Bean FormalEvaluationPlanPreparationAuthority formalEvaluationPlanPreparationAuthority(EnterpriseAccess a,FormalEvaluationExecutionInputs i,
                FormalEvaluationBoundExecutions b,FormalAgentCandidateAuthority c,TemplateVersionStore t,TemplateApplicabilityService ap,
                ProjectService p,ResourceReadAuthority r,FormalEvaluationExactInputObjectAuthorization w,FormalEvaluationPlanExpansion x,
                FormalEvaluationPlanPreparationDependencies d,FormalEvaluationPlanPreparationSchema s,PermissionSchema j){return new FormalEvaluationPlanPreparationAuthority(a,i,b,c,t,ap,p,r,w,x,d,s,j);}
        @Bean(name="formalEvaluationPlanObjectTransport")
        @ConditionalOnProperty(prefix="medical.formal-evaluation-plan",name="object-directory")
        @ConditionalOnBean(SourceUploadTransport.class)
        RuntimeObjectTransport formalEvaluationPlanObjectTransport(SourceUploadTransport deployed,@Value("${medical.formal-evaluation-plan.object-directory}")String directory)throws Exception{return new RuntimeObjectTransport(deployed,java.nio.file.Path.of(directory),FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES);}
        @Bean FormalEvaluationPlanPreparationService formalEvaluationPlanPreparationService(EnterpriseAccess a,FormalEvaluationExecutionInputs i,
                FormalEvaluationBoundExecutions b,FormalEvaluationExecutionRegistrations roots,FormalEvaluationPlanPreparationAuthority auth,
                FormalEvaluationPlanPreparationLedger l,FormalEvaluationPlanPreparationRegistration r,
                @Qualifier("formalEvaluationPlanObjectTransport")ObjectProvider<RuntimeObjectTransport> t,
                FormalEvaluationPlanPreparationSchema s,ResourceReadAuthority resources,PermissionSchema j){return new FormalEvaluationPlanPreparationService(a,i,b,roots,auth,l,r,t,s,resources,j);}
    }
    @ConditionalOnBean({EvaluationService.class,FormalAgentCandidateAuthority.class,AgentExecutionPolicyAuthority.class,FormalEvaluationContextStore.class,FormalEvaluationContextReadAuthority.class,
            FormalEvaluationContextSchema.class,AgentPublicationDependencies.class,ResourceReadAuthority.class,DependencyManifestSchema.class,DependencyLineage.class,SourceReadMapper.class,ArtifactRegistryMapper.class})
    public static class ReadWiring {
        @Bean FormalEvaluationPlanPreparationDependencies formalEvaluationPlanPreparationDependencies(AgentPublicationDependencies a,
                DependencyLineage l,SourceReadMapper s,ArtifactRegistryMapper r,PermissionSchema j){return new FormalEvaluationPlanPreparationDependencies(a,l,s,r,j);}

        @Bean FormalEvaluationPlanPreparationLedger formalEvaluationPlanPreparationLedger(DataSource d,FormalEvaluationPlanPreparationSchema s,PermissionSchema j){return new FormalEvaluationPlanPreparationLedger(d,s,j);}
        @Bean FormalEvaluationPlanPreparationRegistration formalEvaluationPlanPreparationRegistration(DataSource d,FormalEvaluationPlanPreparationLedger l,
                FormalEvaluationPlanPreparationSchema s,DependencyManifestSchema deps,PermissionSchema j){return new FormalEvaluationPlanPreparationRegistration(d,l,s,deps,j);}

        @Bean FormalEvaluationPlanPreparationReadService formalEvaluationPlanPreparationReadService(EnterpriseAccess a,EvaluationService e,FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,
                FormalEvaluationContextStore c,FormalEvaluationContextReadAuthority r,FormalEvaluationContextSchema cs,ResourceReadAuthority resources,
                FormalEvaluationPlanPreparationDependencies p,FormalEvaluationPlanPreparationLedger l,FormalEvaluationPlanPreparationRegistration registrations,
                FormalEvaluationPlanPreparationSchema s,PermissionSchema j,DataSource d){return new FormalEvaluationPlanPreparationReadService(a,e,candidates,policies,c,r,cs,resources,p,l,registrations,s,j,d);}
        // Same complete read dependency group; only the genuine deployed GET adapter is additional.
        @Bean @ConditionalOnBean(SourceUploadTransport.class) FormalEvaluationPlanPreparationContentSchema formalEvaluationPlanPreparationContentSchema(FormalEvaluationPlanPreparationSchema schema)throws Exception{return new FormalEvaluationPlanPreparationContentSchema(schema);}
        @Bean @ConditionalOnBean(SourceUploadTransport.class) FormalEvaluationPlanPreparationContentAuthority formalEvaluationPlanPreparationContentAuthority(FormalEvaluationPlanPreparationReadService reads,FormalEvaluationPlanPreparationSchema schema,FormalEvaluationPlanPreparationContentSchema content,PermissionSchema json){return new FormalEvaluationPlanPreparationContentAuthority(reads,schema,content,json);}
        @Bean @ConditionalOnBean(SourceUploadTransport.class) FormalEvaluationPlanPreparationContentObjects formalEvaluationPlanPreparationContentObjects(SourceUploadTransport transport){return new FormalEvaluationPlanPreparationContentObjects(transport);}
        @Bean @ConditionalOnBean(SourceUploadTransport.class) FormalEvaluationPlanPreparationContentService formalEvaluationPlanPreparationContentService(FormalEvaluationPlanPreparationContentAuthority authority,FormalEvaluationPlanPreparationContentObjects objects){return new FormalEvaluationPlanPreparationContentService(authority,objects);}
    }

}
