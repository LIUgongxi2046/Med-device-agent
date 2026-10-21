package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.net.URI;

/** Explicit operator-supplied deployment configuration, never an execution grant.
 * No defaults or bean registration: the deployment must supply every bound. */
public record TaskRunWorkflowConfiguration(String revision, String namespace, String serviceUri,
        String controlTaskQueue, String professionalTaskQueue, long runTimeoutMillis,
        int maximumCommands, int maximumSteps, int maximumParallelSteps,
        long readTimeoutMillis, long stepTimeoutMillis, long scheduleTimeoutMillis) {
    public TaskRunWorkflowConfiguration {
        WireValues.uuid(revision);
        if (namespace == null || !namespace.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")
                || serviceUri == null || !URI.create(serviceUri).isAbsolute() || runTimeoutMillis < 1
                || maximumCommands < 1 || maximumSteps < 1 || maximumParallelSteps < 1
                || maximumParallelSteps > maximumSteps || readTimeoutMillis < 1
                || stepTimeoutMillis < 1 || scheduleTimeoutMillis < 1) invalid();
        for (String queue : new String[]{controlTaskQueue, professionalTaskQueue})
            if (queue == null || !queue.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) invalid();
    }
    /** Closed deployment JSON: omitted, unknown, duplicate, coerced and trailing values fail. */
    public static TaskRunWorkflowConfiguration load(String configurationJson)throws java.io.IOException {
        var mapper=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
        return mapper.readValue(configurationJson,TaskRunWorkflowConfiguration.class);
    }
    WorkflowProtocol.Reference reference(TaskRunReplayRegistration.Registered registered, long now) {
        var r=registered.receipt();
        return new WorkflowProtocol.Reference("1.0.0",r.required("tenant_id").asText(),r.required("project_id").asText(),
            r.required("run_id").asText(),WorkflowProtocol.ExecutionKind.TASK,registered.artifactId(),registered.reference().sha256(),
            r.required("execution_policy").required("version_id").asText(),Math.addExact(now,runTimeoutMillis),maximumCommands,
            maximumSteps,maximumParallelSteps,readTimeoutMillis,stepTimeoutMillis,scheduleTimeoutMillis,controlTaskQueue,professionalTaskQueue);
    }
    private static void invalid(){throw new IllegalArgumentException("EXPLICIT_WORKFLOW_DEPLOYMENT_CONFIGURATION_REQUIRED");}
}
