package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import org.springframework.context.annotation.AnnotationConfigApplicationContext;import org.springframework.core.env.MapPropertySource;import com.ruoyi.medical.identity.*;import com.ruoyi.medical.persistence.mapper.PermissionMapper;import javax.sql.DataSource;import java.util.*;
/** Actual Spring factories, token authority, private bootstrap authority and worker bridge.
 * External DB and configuration stores are explicit test seams; no runtime proof is mocked. */
class FormalRuntimeBeanGraphTest {
 @Test void enabledFactoryInjectsActualOptionalBootstrapAndOneWorkerBridge()throws Exception{graph(true);}
 @Test void absentBootstrapRetainsOrdinaryTokenAuthority()throws Exception{graph(false);}
 private void graph(boolean withBootstrap)throws Exception{
  try(var c=new AnnotationConfigApplicationContext()){
   c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("medical.run-authority.enabled","true","medical.model-runtime.enabled","true","medical.formal-invocation.enabled","true")));
   var data=mock(DataSource.class);var permissions=mock(PermissionMapper.class);var grants=mock(PermissionAuthority.class);var schema=new RunTokenAuthoritySchema();
   c.registerBean("masterDataSource",DataSource.class,()->data);c.registerBean(TenantTransaction.class,()->mock(TenantTransaction.class));c.registerBean(PermissionMapper.class,()->permissions);c.registerBean(PermissionAuthority.class,()->grants);c.registerBean(RunTokenAuthoritySchema.class,()->schema);
   var bootstrap=new FormalModelVerificationBootstrap(mock(FormalVerificationJobs.class),mock(FormalCapabilityIdentityAuthority.class),permissions,grants,new FormalInvocationSchema(),schema,data);
   if(withBootstrap)c.registerBean(FormalModelVerificationBootstrap.class,()->bootstrap);
   var identity=new ServiceCertificateIdentity(()->{throw new java.io.IOException("no deployed trust in isolated bean graph");});
   c.register(RunTokenAuthorityConfiguration.class,FormalWorkerAccessConfiguration.class);
   c.addBeanFactoryPostProcessor(factory->{var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;for(String name:List.of("runTokenPolicyStore","runTokenSigningKeyStore","formalRunServiceIdentity"))registry.removeBeanDefinition(name);factory.registerSingleton("runTokenPolicyStore",mock(RunTokenPolicyStore.class));factory.registerSingleton("runTokenSigningKeyStore",mock(RunTokenSigningKeyStore.class));factory.registerSingleton("formalRunServiceIdentity",identity);});
   c.refresh();var tokens=c.getBean(RunTokenAuthority.class);var worker=c.getBean(FormalWorkerAccess.class);assertFalse(mockingDetails(tokens).isMock());assertFalse(mockingDetails(worker).isMock());assertFalse(mockingDetails(bootstrap).isMock());assertSame(identity,field(tokens,"services"));assertSame(withBootstrap?bootstrap:null,field(tokens,"modelVerification"));assertSame(tokens,field(worker,"tokens"));assertEquals(1,c.getBeansOfType(FormalWorkerAccess.class).size());
  }
 }
 @Test void defaultOffCreatesNeitherTokenNorWorkerBean(){try(var c=new AnnotationConfigApplicationContext()){c.register(RunTokenAuthorityConfiguration.class,FormalWorkerAccessConfiguration.class);c.refresh();assertTrue(c.getBeansOfType(RunTokenAuthority.class).isEmpty());assertTrue(c.getBeansOfType(FormalWorkerAccess.class).isEmpty());assertTrue(c.getBeansOfType(ServiceCertificateIdentity.class).isEmpty());}}
 private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
}
