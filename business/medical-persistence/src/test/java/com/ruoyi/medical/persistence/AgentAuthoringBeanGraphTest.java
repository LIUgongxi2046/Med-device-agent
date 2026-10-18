package com.ruoyi.medical.persistence;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.identity.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real new-component graph over explicit existing-platform collaborators, not a deployed infrastructure test. */
class AgentAuthoringBeanGraphTest {
 private AnnotationConfigApplicationContext context(boolean enabled){
  var c=new AnnotationConfigApplicationContext();c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-gate",Map.of("medical.agent-authoring.enabled",Boolean.toString(enabled))));
  for(Class<?> type:new Class<?>[]{EnterpriseAccess.class,ApplicationSessionIdentity.class,ConfigurationAuthoringMapper.class,AgentPublicationMapper.class,FormalCapabilityCancellationMapper.class,PermissionMapper.class,ResourceReadAuthority.class,EvaluationReferences.class,DependencyLineage.class,VersionResourceAuthority.class,SourceReadAuthority.class,SourceReadMapper.class,ArtifactRegistryMapper.class})register(c,type);
  c.registerBean(PermissionSchema.class);c.registerBean(IdentityDirectoryPolicy.class,()->new IdentityDirectoryPolicy("INTERNAL"));
  c.register(FormalCapabilityCancellationSchema.class,FormalCapabilityCancellation.class,FormalCapabilityCancellationController.class,AgentPublicationSchema.class,AgentPublicationJournal.class,AgentPublicationDependencies.class,FormalCapabilityIdentityAuthority.class,FormalCapabilityAuthoringService.class,AgentAuthoringCursor.class,AgentAuthoringOperations.class,AgentAuthoringFeatureAvailability.class,AgentAuthoringFeatureAvailabilityController.class,AgentAuthoringController.class);c.refresh();return c;
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void register(AnnotationConfigApplicationContext c,Class type){c.registerBean(type,()->mock(type));}
 @Test void capabilityGraphStartsWithoutAnyPolicyOrPublicationImplementation(){try(var c=context(true)){assertNotNull(c.getBean(FormalCapabilityAuthoringService.class));assertNotNull(c.getBean(AgentAuthoringController.class));assertFalse(java.util.Arrays.stream(c.getBeanDefinitionNames()).anyMatch(n->n.toLowerCase().contains("evaluationacceptancepolicy")));}}
 @Test void disabledWritesLeaveAuthenticatedAvailabilityRegistered(){try(var c=context(false)){assertTrue(c.getBeansOfType(AgentAuthoringController.class).isEmpty());assertTrue(c.getBeansOfType(FormalCapabilityCancellationController.class).isEmpty());assertNotNull(c.getBean(AgentAuthoringFeatureAvailabilityController.class));}}
}
