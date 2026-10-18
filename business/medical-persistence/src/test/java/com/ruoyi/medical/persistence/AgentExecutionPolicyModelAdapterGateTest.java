package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;import org.springframework.core.env.MapPropertySource;import java.util.Map;
class AgentExecutionPolicyModelAdapterGateTest {
 @Test void defaultDisabledDoesNotRequireModelBean(){check(false,false,false);}
 @Test void oneEnabledDependencyStillKeepsAdapterAbsent(){check(true,false,false);check(false,true,false);}
 @Test void bothRealFeatureGatesRegisterAdapterWithActualBoundary(){check(true,true,true);}
 private void check(boolean model,boolean invocation,boolean expected){try(var c=new AnnotationConfigApplicationContext()){
  c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("medical.model-runtime.enabled",model,"medical.formal-invocation.enabled",invocation)));
  if(expected){c.registerBean(ModelEligibilityAuthority.class,()->mock(ModelEligibilityAuthority.class));c.registerBean(PermissionSchema.class,()->{try{return new PermissionSchema();}catch(Exception e){throw new IllegalStateException(e);}});}
  c.register(AgentExecutionPolicyModelAdapter.class);c.refresh();assertEquals(expected,c.getBeanNamesForType(AgentExecutionPolicyModelPort.class,false,false).length==1);
 }}
}
