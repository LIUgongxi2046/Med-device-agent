package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring conditional construction; dependencies are explicit doubles. No runtime deployment claim. */
class FormalEvaluationPlanPreparationContentWiringTest {
    private static final List<Class<?>> READ=List.of(EvaluationService.class,FormalAgentCandidateAuthority.class,AgentExecutionPolicyAuthority.class,
        FormalEvaluationContextStore.class,FormalEvaluationContextReadAuthority.class,FormalEvaluationContextSchema.class,
        AgentPublicationDependencies.class,ResourceReadAuthority.class,DependencyManifestSchema.class,DependencyLineage.class,
        SourceReadMapper.class,ArtifactRegistryMapper.class);
    AnnotationConfigApplicationContext context(boolean enabled,boolean transport,Class<?> omit)throws Exception {
        var c=new AnnotationConfigApplicationContext();c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("medical.formal-evaluation-plan.enabled",Boolean.toString(enabled))));
        c.getBeanFactory().registerSingleton("access",mock(EnterpriseAccess.class));c.getBeanFactory().registerSingleton("identities",mock(ApplicationSessionIdentity.class));c.getBeanFactory().registerSingleton("data",mock(DataSource.class));
        var json=new PermissionSchema();c.getBeanFactory().registerSingleton("json",json);c.getBeanFactory().registerSingleton("schema",new FormalEvaluationPlanPreparationSchema(json));
        for(Class<?> type:READ)if(type!=omit)c.getBeanFactory().registerSingleton(type.getSimpleName(),mock(type));
        if(transport)c.getBeanFactory().registerSingleton("deployed",mock(SourceUploadTransport.class));
        c.register(FormalEvaluationPlanPreparationConfiguration.class,FormalEvaluationPlanPreparationContentController.class);c.refresh();return c;
    }
    @Test void historicalContentStartsWithoutBudgetModelRunOrRegistrationDependencies()throws Exception {
        try(var c=context(true,true,null)) {
            assertNotNull(c.getBean(FormalEvaluationPlanPreparationReadService.class));assertNotNull(c.getBean(FormalEvaluationPlanPreparationContentService.class));
            assertFalse(mockingDetails(c.getBean(FormalEvaluationPlanPreparationContentObjects.class)).isMock());assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationService.class).isEmpty());
            assertTrue(c.getBeansOfType(RunCreateBudget.class).isEmpty());assertFalse(c.containsBean("formalEvaluationPlanObjectTransport"));verifyNoInteractions(c.getBean(SourceUploadTransport.class));
        }
    }
    @Test void metadataSurvivesAbsentDeployedGetWithoutClaimingContent()throws Exception {
        try(var c=context(true,false,null)){assertNotNull(c.getBean(FormalEvaluationPlanPreparationReadService.class));assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationContentService.class).isEmpty());assertNotNull(c.getBean(FormalEvaluationPlanPreparationContentController.class));}
    }
    @Test void disabledFeatureAndMissingReadDependenciesFailClosed()throws Exception {
        try(var c=context(false,true,null)){assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationContentService.class).isEmpty());assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationContentController.class).isEmpty());}
        for(Class<?> type:READ)try(var c=context(true,true,type)){assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationReadService.class).isEmpty(),type.getName());assertTrue(c.getBeansOfType(FormalEvaluationPlanPreparationContentService.class).isEmpty(),type.getName());verifyNoInteractions(c.getBean(SourceUploadTransport.class));}
    }
}
