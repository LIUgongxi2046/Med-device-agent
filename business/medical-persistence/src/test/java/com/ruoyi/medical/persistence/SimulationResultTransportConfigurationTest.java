package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Configuration boundary test only; no storage, credentials, Redis, or network is opened. */
class SimulationResultTransportConfigurationTest {
    @Test void disabledUnlessBothSimulationAndResultFlagsAreExplicit() {
        for (var properties : java.util.List.of(Map.<String,Object>of(),
                Map.<String,Object>of("medical.simulation-execution.enabled", "true"),
                Map.<String,Object>of("medical.simulation-execution.result-persistence.enabled", "true"))) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
                context.register(SimulationResultTransportConfiguration.class); context.refresh();
                assertEquals(0, context.getBeanNamesForType(SimulationResultTransport.class).length);
            }
        }
    }
    @Test void enabledRequiresExplicitPositiveCapacityAndConfiguredStorage() {
        for (String capacity : java.util.List.of("", "0", "-1", "16777216")) {
            try (var context = new AnnotationConfigApplicationContext()) {
                var properties = new java.util.HashMap<String,Object>();
                properties.put("medical.simulation-execution.enabled", "true");
                properties.put("medical.simulation-execution.result-persistence.enabled", "true");
                if (!capacity.isEmpty()) properties.put("medical.simulation-execution.result-persistence.maximum-object-bytes", capacity);
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
                context.getBeanFactory().registerSingleton("sourceUploadTransport", mock(SourceUploadTransport.class));
                context.register(SimulationResultTransportConfiguration.class);
                if ("16777216".equals(capacity)) {
                    context.refresh(); assertInstanceOf(VerifiedS3SimulationResultTransport.class, context.getBean(SimulationResultTransport.class));
                } else assertThrows(org.springframework.beans.BeansException.class, context::refresh);
            }
        }
    }
}
