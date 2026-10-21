package com.ruoyi.medical.persistence;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;
class TaskRegisteredToolWaitingConfigurationTest {
 private AnnotationConfigApplicationContext context(boolean task,boolean waiting){var c=new AnnotationConfigApplicationContext();c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("explicit-test-flags",Map.of("medical.professional-runtime.task-enabled",Boolean.toString(task),"medical.professional-runtime.waiting-v2-enabled",Boolean.toString(waiting))));c.register(TaskRegisteredToolWaitingConfiguration.class,TaskRegisteredToolWaitingAnswerConfiguration.class,TaskProfessionalWaitingV2Controller.class,TaskRegisteredToolWaitingAnswerController.class);return c;}
 @Test void defaultOffAndOneFlagCannotCreateWaitingRoutes(){for(boolean task:new boolean[]{false,true})try(var c=context(task,false)){c.refresh();assertFalse(c.containsBean("taskRegisteredToolWaitingGate"));assertTrue(c.getBeansOfType(TaskProfessionalWaitingV2Controller.class).isEmpty());assertTrue(c.getBeansOfType(TaskRegisteredToolWaitingAnswerController.class).isEmpty());}}
 @Test void enablingWithoutRealDependencyGraphFailsClosed(){try(var c=context(true,true)){assertThrows(org.springframework.beans.BeansException.class,c::refresh);}}
}
