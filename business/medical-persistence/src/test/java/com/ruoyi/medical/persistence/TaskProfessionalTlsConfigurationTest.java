package com.ruoyi.medical.persistence;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

class TaskProfessionalTlsConfigurationTest {
 @Test void defaultDoesNotCreateTlsOrLoadCredentialFiles(){try(var context=new AnnotationConfigApplicationContext()){context.register(TaskProfessionalTlsConfiguration.class);context.refresh();assertFalse(context.containsBean("taskProfessionalSupervisorTls"));}}
 @Test void enabledRequiresExplicitOwnedMaterialAndImmutableDeployment(){try(var context=new AnnotationConfigApplicationContext()){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("gate",Map.of("medical.professional-runtime.task-enabled","true")));context.register(TaskProfessionalTlsConfiguration.class);assertThrows(org.springframework.beans.BeansException.class,context::refresh);}}
 @Test void oneRequestTlsContextCannotBeReplacedByAnInjectedRawSslContext()throws Exception{var method=java.util.Arrays.stream(TaskProfessionalConfiguration.class.getDeclaredMethods()).filter(m->m.getName().equals("taskProfessionalSupervisor")).findFirst().orElseThrow();assertTrue(java.util.Arrays.asList(method.getParameterTypes()).contains(com.ruoyi.medical.identity.ServiceClientTlsStore.ContextSource.class));assertFalse(java.util.Arrays.asList(method.getParameterTypes()).contains(javax.net.ssl.SSLContext.class));}
 static final class GlobalIdentityConsumer {
  final com.ruoyi.medical.identity.ServiceCertificateIdentity identity;
  GlobalIdentityConsumer(com.ruoyi.medical.identity.ServiceCertificateIdentity identity){this.identity=identity;}
 }
 @Test void actualEnabledConfigurationKeepsUnqualifiedGlobalIdentityUnique()throws Exception{
  try(var context=new AnnotationConfigApplicationContext()){
   context.setAllowCircularReferences(false);context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("task-enabled",Map.of("medical.professional-runtime.task-enabled","true")));
   var global=org.mockito.Mockito.mock(com.ruoyi.medical.identity.ServiceCertificateIdentity.class);
   context.registerBean("formalRunServiceIdentity",com.ruoyi.medical.identity.ServiceCertificateIdentity.class,()->global);
   context.registerBean("masterDataSource",javax.sql.DataSource.class,()->org.mockito.Mockito.mock(javax.sql.DataSource.class));
   context.registerBean(PermissionSchema.class,()->org.mockito.Mockito.mock(PermissionSchema.class));context.registerBean(GlobalIdentityConsumer.class);
   // Explicit peripheral component doubles for this identity/TLS configuration slice.
   // Spring still processes @Autowired setters on the replacement beans below.
   context.registerBean(TaskResearchEvidenceContract.class,()->org.mockito.Mockito.mock(TaskResearchEvidenceContract.class));
   context.registerBean(ApprovedWebAcquisitionService.class,()->org.mockito.Mockito.mock(ApprovedWebAcquisitionService.class));
   context.register(TaskProfessionalConfiguration.class,TaskProfessionalTlsConfiguration.class);
   context.addBeanFactoryPostProcessor(factory->{
    // Keep the actual conditional configurations and supervisor factory. Replace only
    // peripheral I/O/policy beans, never a service-identity definition or consumer.
    var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
    for(Class<?> configuration:java.util.List.of(TaskProfessionalConfiguration.class,TaskProfessionalTlsConfiguration.class))for(var method:configuration.getDeclaredMethods()){
     var bean=method.getAnnotation(org.springframework.context.annotation.Bean.class);if(bean==null||method.getName().equals("taskProfessionalSupervisor")||method.getReturnType()==com.ruoyi.medical.identity.ServiceCertificateIdentity.class)continue;
     String name=bean.name().length==0?method.getName():bean.name()[0];assertTrue(registry.containsBeanDefinition(name),"Actual enabled @Bean definition required: "+name);
     var replacement=new org.springframework.beans.factory.support.RootBeanDefinition(method.getReturnType());replacement.setInstanceSupplier(()->org.mockito.Mockito.mock(method.getReturnType()));registry.removeBeanDefinition(name);registry.registerBeanDefinition(name,replacement);
    }
   });
   context.refresh();assertSame(global,context.getBean(com.ruoyi.medical.identity.ServiceCertificateIdentity.class));assertSame(global,context.getBean(GlobalIdentityConsumer.class).identity);assertEquals(1,context.getBeanNamesForType(com.ruoyi.medical.identity.ServiceCertificateIdentity.class).length);
   var supervisor=context.getBean(TaskProfessionalSupervisor.class);assertFalse(org.mockito.Mockito.mockingDetails(supervisor).isMock());var identity=TaskProfessionalSupervisor.class.getDeclaredField("identities");identity.setAccessible(true);assertNotSame(global,identity.get(supervisor),"Supervisor verifier is scoped to its separate current trust");
  }
 }

}
