package com.ruoyi.medical.persistence;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated bean registration only: no application boot, datasource, sockets or services. */
class SimulationInvocationConfigurationTest {
    @Test void absentEnableFlagNeedsNoSimulationDependenciesOrKeys() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(SimulationInvocationConfiguration.class);context.refresh();
            assertEquals(0,context.getBeanNamesForType(SimulationInvocationService.class).length);
            assertEquals(0,context.getBeanNamesForType(SimulationInvocationSchema.class).length);
        }
    }
    @Test void explicitDisabledFlagDoesNotConstructExecutionBeans() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("simulation-test",Map.of("medical.simulation-execution.enabled","false")));
            context.register(SimulationInvocationConfiguration.class);context.refresh();
            assertEquals(0,context.getBeanNamesForType(SimulationInvocationService.class).length);
            assertEquals(0,context.getBeanNamesForType(SimulationInvocationSchema.class).length);
        }
    }
    @Test void explicitlyEnabledWithoutRequiredDependenciesDoesNotFallBackToProduction() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("simulation-test",Map.of("medical.simulation-execution.enabled","true")));
            context.register(SimulationInvocationConfiguration.class);
            assertThrows(org.springframework.beans.BeansException.class,context::refresh);
        }
    }
}
