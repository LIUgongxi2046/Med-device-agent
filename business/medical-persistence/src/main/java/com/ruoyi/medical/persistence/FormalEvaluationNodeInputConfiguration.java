package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.persistence.mapper.*;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;

/** Separately opt-in091. No credentials, grants, approved package, candidate or model result is seeded. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.formal-evaluation-node-input",name="enabled",havingValue="true",matchIfMissing=false)
@Import(FormalEvaluationNodeInputConfiguration.Selector.class)
public class FormalEvaluationNodeInputConfiguration {
    public static final class Selector implements DeferredImportSelector {public String[] selectImports(AnnotationMetadata m){return new String[]{ReadWiring.class.getName(),WriteWiring.class.getName()};}}
    @ConditionalOnBean({FormalEvaluationPlanPreparationReadService.class,FormalEvaluationPlanPreparationSchema.class,AgentPublicationDependencies.class,
        DependencyLineage.class,SourceReadMapper.class,ArtifactRegistryMapper.class,DependencyManifestSchema.class,ResourceReadAuthority.class})
    public static class ReadWiring {
        @Bean FormalEvaluationNodeInputObjectDependencies formalEvaluationNodeInputObjectDependencies(AgentPublicationDependencies a,DependencyLineage l,SourceReadMapper s,ArtifactRegistryMapper r,PermissionSchema j){return new FormalEvaluationNodeInputObjectDependencies(a,l,s,r,j);}
        @Bean FormalEvaluationNodeInputObjectLedger formalEvaluationNodeInputObjectLedger(DataSource d,FormalEvaluationNodeInputObjectSchema s,PermissionSchema j){return new FormalEvaluationNodeInputObjectLedger(d,s,j);}
        @Bean FormalEvaluationNodeInputObjectRegistration formalEvaluationNodeInputObjectRegistration(DataSource d,FormalEvaluationNodeInputObjectLedger l,FormalEvaluationNodeInputObjectSchema s,DependencyManifestSchema deps,PermissionSchema j){return new FormalEvaluationNodeInputObjectRegistration(d,l,s,deps,j);}
        @Bean FormalEvaluationNodeInputReadService formalEvaluationNodeInputReadService(EnterpriseAccess a,FormalEvaluationNodeInputObjectSchema s,FormalEvaluationNodeInputObjectLedger l,
            FormalEvaluationNodeInputObjectRegistration r,FormalEvaluationPlanPreparationReadService p,FormalEvaluationPlanPreparationSchema ps,FormalEvaluationNodeInputObjectDependencies deps,
            ResourceReadAuthority resources,ObjectProvider<SourceUploadTransport> t,PermissionSchema j){return new FormalEvaluationNodeInputReadService(a,s,l,r,p,ps,deps,resources,t,j);}
    }
    @ConditionalOnProperty(prefix="medical.formal-evaluation-node-input",name="object-directory")
    @ConditionalOnBean({FormalEvaluationPlanPreparationAuthority.class,FormalEvaluationPlanPreparationLedger.class,FormalEvaluationPlanPreparationRegistration.class,
        FormalEvaluationPlanPreparationSchema.class,FormalEvaluationExecutionInputs.class,FormalEvaluationBoundExecutions.class,FormalEvaluationModelPreparation.class,
        FormalEvaluationContextStore.class,FormalCapabilityIdentityAuthority.class,FormalRuntimeCapabilityIdentityAuthority.class,ScopeSnapshotStore.class,
        SourceReadAuthority.class,FormalInvocationSourceOriginals.class,FormalTokenizerRunner.class,ModelConfigurationService.class,RunTokenAuthoritySchema.class,
        SourceUploadTransport.class,ArtifactContentService.class,ArtifactRegistryMapper.class,SourceReadMapper.class,DependencyLineage.class,
        AgentPublicationDependencies.class,EvaluationReferences.class,EvaluationDependencyPolicy.class,FormalEvaluationExactInputObjectAuthorization.class,
        FormalEvaluationNodeInputObjectDependencies.class,FormalEvaluationNodeInputObjectLedger.class,FormalEvaluationNodeInputObjectRegistration.class})
    public static class WriteWiring {
        @Bean @ConditionalOnMissingBean FormalEvaluationContextPackages formalEvaluationContextPackages(EnterpriseAccess a,ArtifactContentService c,SourceUploadTransport t,ArtifactRegistryMapper r,
            SourceReadMapper s,DependencyLineage l,AgentPublicationDependencies d,EvaluationReferences p,EvaluationDependencyPolicy policy,PermissionSchema j){return new FormalEvaluationContextPackages(a,c,t,r,s,l,d,p,policy,j);}
        @Bean FormalEvaluationPlanningSource formalEvaluationPlanningSource(EnterpriseAccess a,FormalEvaluationPlanPreparationAuthority c,FormalEvaluationPlanPreparationLedger l,
            FormalEvaluationPlanPreparationRegistration r,FormalEvaluationPlanPreparationSchema s,ResourceReadAuthority auth,SourceUploadTransport t,DependencyLineage deps,SourceReadMapper rows,SourceReadAuthority sources,PermissionSchema j){return new FormalEvaluationPlanningSource(a,c,l,r,s,auth,t,deps,rows,sources,j);}
        @Bean FormalEvaluationPlanningInputPreparation formalEvaluationPlanningInputPreparation(EnterpriseAccess a,FormalEvaluationContextStore c,FormalEvaluationModelPreparation m,
            FormalCapabilityIdentityAuthority caps,FormalRuntimeCapabilityIdentityAuthority components,FormalEvaluationContextPackages p,ScopeSnapshotStore scopes,SourceReadAuthority sources,
            FormalInvocationSourceOriginals originals,FormalTokenizerRunner tokenizer,ModelConfigurationService models,RunTokenAuthoritySchema roles,PermissionSchema j,DependencyLineage lineage)throws Exception{return new FormalEvaluationPlanningInputPreparation(a,c,m,caps,components,p,scopes,sources,originals,tokenizer,models,roles,j,SchemaCatalogue.fromClasspath(),lineage);}
        @Bean FormalEvaluationNodeInputObjectAuthority formalEvaluationNodeInputObjectAuthority(EnterpriseAccess a,FormalEvaluationExecutionInputs i,FormalEvaluationBoundExecutions b,
            FormalEvaluationModelPreparation m,FormalEvaluationPlanningInputPreparation e,FormalEvaluationNodeInputObjectDependencies d,FormalEvaluationExactInputObjectAuthorization auth,
            FormalEvaluationNodeInputObjectSchema s,PermissionSchema j,FormalEvaluationPlanningSource p){return new FormalEvaluationNodeInputObjectAuthority(a,i,b,m,e,d,auth,s,j,p);}
        @Bean(name="formalEvaluationNodeInputTransport") RuntimeObjectTransport transport(SourceUploadTransport deployed,@Value("${medical.formal-evaluation-node-input.object-directory}")String path)throws Exception{return new RuntimeObjectTransport(deployed,java.nio.file.Path.of(path),BuiltinEvaluationPlanningContextCompiler.MAXIMUM_BYTES);}
        @Bean FormalEvaluationNodeInputObjectService formalEvaluationNodeInputObjectService(EnterpriseAccess a,FormalEvaluationNodeInputObjectSchema s,FormalEvaluationNodeInputObjectAuthority auth,
            FormalEvaluationNodeInputObjectLedger l,@Qualifier("formalEvaluationNodeInputTransport")RuntimeObjectTransport t,FormalEvaluationNodeInputObjectRegistration r,PermissionSchema j){return new FormalEvaluationNodeInputObjectService(a,s,auth,l,t,r,j);}
    }
}
