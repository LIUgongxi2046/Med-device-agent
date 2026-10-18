package com.ruoyi.medical.persistence;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.identity.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Actual complete new core bean graph over explicit platform collaborators; no B/E or publication stubs. */
class AgentExecutionPolicyBeanGraphTest {
 private AnnotationConfigApplicationContext context(boolean enabled){
  var c=new AnnotationConfigApplicationContext();c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-gate",Map.of("medical.agent-execution-policy.enabled",Boolean.toString(enabled))));
  for(Class<?> type:new Class<?>[]{EnterpriseAccess.class,ApplicationSessionIdentity.class,AgentPublicationMapper.class,PermissionMapper.class,ResourceReadAuthority.class,ArtifactRegistryMapper.class,SourceGovernanceMapper.class,AgentPublicationJournal.class,AgentPublicationDependencies.class,FormalCapabilityIdentityAuthority.class,AgentAuthoringCursor.class,AgentExecutionPolicyMapper.class})register(c,type);
  c.registerBean(PermissionSchema.class);c.registerBean(PermissionAuthority.class);c.registerBean(IdentityDirectoryPolicy.class,()->new IdentityDirectoryPolicy("INTERNAL"));c.registerBean(EvaluationDependencyPolicy.class,()->new EvaluationDependencyPolicy("test","test-region",512,2048,List.of()));
  c.register(AgentExecutionPolicySchema.class,AgentExecutionPolicyStore.class,AgentExecutionPolicyDependencies.class,AgentExecutionPolicyAuthority.class,AgentExecutionPolicyRegisteredAuthority.class,AgentExecutionPolicyService.class,AgentExecutionPolicyOperations.class,AgentExecutionPolicyController.class,AgentExecutionPolicyFeatureAvailability.class,AgentExecutionPolicyFeatureAvailabilityController.class);c.refresh();return c;
 }
 @SuppressWarnings({"rawtypes","unchecked"}) private static void register(AnnotationConfigApplicationContext c,Class type){c.registerBean(type,()->mock(type));}
 @Test void actualCoreStartsWithoutModelOrPublicationProvider(){try(var c=context(true)){assertNotNull(c.getBean(AgentExecutionPolicyService.class));assertNotNull(c.getBean(AgentExecutionPolicyController.class));assertNotNull(c.getBean(AgentExecutionPolicyRegisteredAuthority.class));assertTrue(c.getBeansOfType(AgentExecutionPolicyModelPort.class).isEmpty());}}
 @Test void disabledWritesRetainAuthenticatedAvailability(){try(var c=context(false)){assertTrue(c.getBeansOfType(AgentExecutionPolicyController.class).isEmpty());assertNotNull(c.getBean(AgentExecutionPolicyFeatureAvailabilityController.class));}}
}
