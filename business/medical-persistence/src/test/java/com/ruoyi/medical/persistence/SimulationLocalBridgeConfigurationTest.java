package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class SimulationLocalBridgeConfigurationTest {
    @Test void bothExplicitFlagsAreRequiredWithoutTouchingProcessOrStore() {
        for (var properties : java.util.List.of(Map.<String,Object>of(),
                Map.<String,Object>of("medical.simulation-execution.enabled", "true"),
                Map.<String,Object>of("medical.simulation-execution.local-bridge.enabled", "true"),
                Map.<String,Object>of("medical.simulation-execution.enabled", "true", "medical.simulation-execution.local-bridge.enabled", "false"))) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("local-bridge-test", properties));
                context.register(SimulationLocalBridgeConfiguration.class, SimulationEvaluationDispatchController.class);
                context.refresh();
                assertEquals(0, context.getBeanNamesForType(SimulationLocalProcessBridge.class).length);
                assertEquals(0, context.getBeanNamesForType(SimulationEvaluationDispatchService.class).length);
                assertEquals(0, context.getBeanNamesForType(SimulationEvaluationDispatchController.class).length);
            }
        }
    }
    @Test void enabledWithoutExplicitDeploymentConfigurationFailsClosed() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("local-bridge-test",
                Map.of("medical.simulation-execution.enabled", "true", "medical.simulation-execution.local-bridge.enabled", "true")));
            context.register(SimulationLocalBridgeConfiguration.class);
            assertThrows(org.springframework.beans.BeansException.class, context::refresh);
        }
    }
}
