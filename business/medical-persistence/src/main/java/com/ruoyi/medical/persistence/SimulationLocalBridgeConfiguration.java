package com.ruoyi.medical.persistence;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="medical.simulation-execution", name={"enabled", "local-bridge.enabled"}, havingValue="true")
class SimulationLocalBridgeConfiguration {
    @Bean SimulationLocalProcessBridge simulationLocalProcessBridge(
            @Value("${medical.simulation-execution.local-bridge.python-executable}") Path python,
            @Value("${medical.simulation-execution.local-bridge.project-root}") Path root,
            @Value("${medical.simulation-execution.local-bridge.store-directory}") Path store,
            @Value("${medical.simulation-execution.local-bridge.maximum-record-bytes}") int maximum,
            @Value("${medical.simulation-execution.local-bridge.deadline-seconds}") long seconds) throws Exception {
        return new SimulationLocalProcessBridge(new SimulationLocalProcessBridge.Configuration(
            python, root, store, maximum, Duration.ofSeconds(seconds)));
    }
    @Bean SimulationEvaluationDispatchService simulationEvaluationDispatchService(SimulationInvocationService ledger,
            SimulationLocalProcessBridge bridge, SimulationInvocationSchema schema,org.springframework.beans.factory.ObjectProvider<SimulationResultPersistenceService> results) {
        return new SimulationEvaluationDispatchService(ledger, bridge, schema,results.getIfAvailable());
    }
    @Bean SimulationLocalRecoveryService simulationLocalRecoveryService(SimulationInvocationService ledger,
            SimulationLocalProcessBridge bridge, SimulationInvocationSchema schema,org.springframework.beans.factory.ObjectProvider<SimulationResultPersistenceService> results) {
        return new SimulationLocalRecoveryService(ledger, bridge, schema, results.getIfAvailable());
    }
}
