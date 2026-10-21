package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextSchemaTest.*;

class FormalEvaluationContextFeatureAvailabilityTest {
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final PermissionSchema json=new PermissionSchema();
    final FormalEvaluationContextHttpSchema schema=new FormalEvaluationContextHttpSchema();
    FormalEvaluationContextFeatureAvailabilityTest()throws Exception {
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));
        when(access.execute(eq(subject),any())).thenAnswer(i->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission));
    }
    @Test void disabledPropertyNeverCreatesDomainBeans() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(FormalEvaluationContextConfiguration.class,FormalEvaluationContextController.class);context.refresh();
            assertTrue(context.getBeansOfType(FormalEvaluationContextStore.class).isEmpty());
            assertTrue(context.getBeansOfType(FormalEvaluationContextController.class).isEmpty());
        }
        var beans=mock(org.springframework.beans.factory.ListableBeanFactory.class);
        var availability=new FormalEvaluationContextFeatureAvailability(access,beans,new MockEnvironment(),json,schema);
        var result=availability.read(subject,"1");assertEquals("FEATURE_DISABLED",result.required("unavailable_reason").asText());verifyNoInteractions(beans);
    }
    @Test void enabledPropertyWithoutRealDependencyBeansIsUnavailable() {
        var availability=new FormalEvaluationContextFeatureAvailability(access,new DefaultListableBeanFactory(),new MockEnvironment().withProperty("medical.formal-evaluation-context.enabled","true"),json,schema);
        var result=availability.read(subject,"1");assertFalse(result.required("features").required("context_bindings").asBoolean());assertEquals("DEPENDENCY_UNAVAILABLE",result.required("unavailable_reason").asText());
    }
    private AnnotationConfigApplicationContext contextWithout(Class<?> missing) {
        var context=new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",java.util.Map.of("medical.formal-evaluation-context.enabled","true")));
        context.registerBean(EnterpriseAccess.class,()->access);context.registerBean(PermissionSchema.class,()->json);
        context.registerBean(FormalEvaluationContextHttpSchema.class,()->schema);context.registerBean(AgentAuthoringCursor.class,()->new AgentAuthoringCursor());
        context.registerBean(ApplicationSessionIdentity.class,()->mock(ApplicationSessionIdentity.class));
        context.registerBean(DataSource.class,()->mock(DataSource.class));
        for(Class<?> type:java.util.List.of(FormalAgentCandidateAuthority.class,AgentExecutionPolicyAuthority.class,
                FormalCapabilityIdentityAuthority.class,FormalRuntimeCapabilityIdentityAuthority.class,AgentPublicationDependencies.class))
            if(type!=missing)registerMock(context,type);
        context.registerBean(ResourceReadAuthority.class,()->mock(ResourceReadAuthority.class));
        context.registerBean(PermissionMapper.class,()->mock(PermissionMapper.class));
        context.registerBean(PermissionAuthority.class,()->mock(PermissionAuthority.class));
        context.registerBean(PlatformMemberIdentityStatus.class,()->mock(PlatformMemberIdentityStatus.class));
        context.registerBean("unrelatedApplication",String.class,()->"still usable");
        context.register(FormalEvaluationContextConfiguration.class,FormalEvaluationContextController.class);
        context.refresh();return context;
    }
    private static <T>void registerMock(AnnotationConfigApplicationContext context,Class<T> type) { context.registerBean(type,()->mock(type)); }
    @Test void actualConfiguredBeansEnableOnlyContextRoutes() {
        try(var context=contextWithout(null)) {
            assertNotNull(context.getBean(FormalEvaluationContextService.class));assertNotNull(context.getBean(FormalEvaluationContextStore.class));
            var availability=new FormalEvaluationContextFeatureAvailability(access,context,context.getEnvironment(),json,schema);
            var result=availability.read(subject,"1");assertTrue(result.required("features").required("context_bindings").asBoolean());assertFalse(result.required("features").required("bound_preparation").asBoolean());assertTrue(result.required("unavailable_reason").isNull());
            assertTrue(context.getBeansOfType(FormalEvaluationBoundExecutions.class).isEmpty());
        }
    }
    @Test void enabledWithMissingDomainPrerequisiteStartsNonLazyAndReportsUnavailable() {
        for(Class<?> missing:java.util.List.of(FormalAgentCandidateAuthority.class,AgentExecutionPolicyAuthority.class,
                FormalCapabilityIdentityAuthority.class,FormalRuntimeCapabilityIdentityAuthority.class,AgentPublicationDependencies.class))
            try(var context=contextWithout(missing)) {
                assertEquals("still usable",context.getBean("unrelatedApplication"));
                assertNotNull(context.getBean(FormalEvaluationContextController.class));
                assertTrue(context.getBeansOfType(FormalEvaluationContextService.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationContextStore.class).isEmpty());
                var availability=new FormalEvaluationContextFeatureAvailability(access,context,context.getEnvironment(),json,schema);
                var result=availability.read(subject,"1");assertFalse(result.required("features").required("context_bindings").asBoolean());
                assertEquals("DEPENDENCY_UNAVAILABLE",result.required("unavailable_reason").asText());
            }
    }
    @Test void controllerClassDeclarationAloneIsNotReadiness() {
        var beans=new DefaultListableBeanFactory();beans.registerBeanDefinition("broken",new RootBeanDefinition(FormalEvaluationContextController.class));
        var availability=new FormalEvaluationContextFeatureAvailability(access,beans,new MockEnvironment().withProperty("medical.formal-evaluation-context.enabled","true"),json,schema);
        assertFalse(availability.read(subject,"1").required("features").required("context_bindings").asBoolean());
    }
    @Test void staleContextCannotInspectAvailability() {
        var availability=new FormalEvaluationContextFeatureAvailability(access,new DefaultListableBeanFactory(),new MockEnvironment(),json,schema);
        assertThrows(TenantAccessException.class,()->availability.read(subject,"2"));
    }
}
