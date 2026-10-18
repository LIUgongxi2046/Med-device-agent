package com.ruoyi.medical.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No default activation or capacity: simulation result persistence needs explicit deployment approval. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.simulation-execution", name={"enabled", "result-persistence.enabled"}, havingValue="true")
class SimulationResultTransportConfiguration {
    @Bean SimulationResultTransport simulationResultTransport(SourceUploadTransport storage,
            @Value("${medical.simulation-execution.result-persistence.maximum-object-bytes}") int maximumBytes) {
        return new VerifiedS3SimulationResultTransport(storage, maximumBytes);
    }
}
