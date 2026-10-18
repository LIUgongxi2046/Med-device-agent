package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="medical.simulation-execution", name={"enabled", "local-bridge.enabled"}, havingValue="true")
public final class SimulationEvaluationDispatchController {
    private final SimulationEvaluationDispatchService service;
    public SimulationEvaluationDispatchController(SimulationEvaluationDispatchService service) { this.service = service; }
    @PostMapping(value="/internal/v1/simulation-evaluations/execute", consumes="application/json")
    public JsonNode execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var versions = request.getHeaders("X-Schema-Version");
        if (versions == null || !versions.hasMoreElements() || !"1.0.0".equals(versions.nextElement())
                || versions.hasMoreElements() || request.getQueryString() != null)
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        var result = service.execute(request, SimulationInvocationController.body(request));
        var value = new ObjectMapper().createObjectNode().put("schema_version", "1.0.0")
            .put("invocation_id", result.invocation().invocationId()).put("status", result.invocation().status())
            .put("revision", result.invocation().revision()).put("dispatch_generation", result.invocation().dispatchGeneration())
            .put("execution_mode", "SIMULATION").put("production_eligible", false).put("settlement_status", "PENDING");
        if (result.fact() == null) value.putNull("fact_status");
        else value.put("fact_status", result.fact().status());
        return value;
    }
    @ExceptionHandler(SimulationLocalProcessBridge.BridgeFailure.class)
    void unknown(SimulationLocalProcessBridge.BridgeFailure failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 409, ErrorCode.INVOCATION_UNKNOWN);
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, switch(failure.code()) {
            case ACCESS_DENIED -> 403; case OBJECT_UNAVAILABLE -> 404; case FILE_TOO_LARGE -> 413;
            case DEPENDENCY_UNAVAILABLE -> 503; default -> 409;
        }, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(SecurityException.class)
    void token(SecurityException failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 403, ErrorCode.ACCESS_DENIED);
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, java.nio.charset.CharacterCodingException.class})
    void malformed(Exception failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
