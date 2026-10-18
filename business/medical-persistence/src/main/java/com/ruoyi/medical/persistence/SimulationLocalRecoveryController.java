package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Returns receipt status only. A caller cannot provide journal content, paths or replacement input. */
@RestController
@ConditionalOnProperty(prefix="medical.simulation-execution", name={"enabled", "local-bridge.enabled"}, havingValue="true")
public final class SimulationLocalRecoveryController {
    private final SimulationLocalRecoveryService service;
    private final org.springframework.beans.factory.ObjectProvider<SimulationResultPersistenceService> results;
    public SimulationLocalRecoveryController(SimulationLocalRecoveryService service) { this.service = service; this.results = null; }
    @org.springframework.beans.factory.annotation.Autowired
    public SimulationLocalRecoveryController(SimulationLocalRecoveryService service,
            org.springframework.beans.factory.ObjectProvider<SimulationResultPersistenceService> results) {
        this.service = service; this.results = results;
    }
    @PostMapping(value="/internal/v1/simulation-evaluations/reconcile", consumes="application/json")
    public JsonNode reconcile(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var identity = identity(request, response);
        var fact = service.reconcile(request, identity.path("tenant_id").textValue(), identity.path("invocation_id").textValue());
        return new JsonMapper().createObjectNode().put("schema_version", "1.0.0").put("invocation_id", fact.invocationId())
            .put("receipt_id", fact.receiptId()).put("fact_status", fact.status()).put("replayed", fact.replay())
            .put("execution_mode", "SIMULATION").put("production_eligible", false);
    }
    @PostMapping(value="/internal/v1/simulation-evaluations/settle", consumes="application/json")
    public JsonNode settle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        var identity = identity(request, response);
        var persistence = results == null ? null : results.getIfAvailable();
        if (persistence == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var view = persistence.persist(request, identity.path("tenant_id").textValue(), identity.path("invocation_id").textValue());
        if (!Set.of("SUCCEEDED", "FAILED").contains(view.status())) throw new TenantAccessException(ErrorCode.INVOCATION_UNKNOWN);
        return new JsonMapper().createObjectNode().put("schema_version", "1.0.0").put("invocation_id", view.invocationId())
            .put("status", view.status()).put("revision", view.revision()).put("execution_mode", "SIMULATION")
            .put("local_quota_settlement_status", "SETTLED").put("provider_settlement_status", "PENDING").put("production_eligible", false);
    }
    private static JsonNode identity(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        var versions = request.getHeaders("X-Schema-Version");
        if (versions == null || !versions.hasMoreElements() || !"1.0.0".equals(versions.nextElement())
                || versions.hasMoreElements() || request.getQueryString() != null) invalid();
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        if (request.getContentLengthLong() > 4096) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] input = request.getInputStream().readNBytes(4097);
        if (input.length > 4096) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return parse(input);
    }
    static JsonNode parse(byte[] input) throws java.io.IOException {
        if (input == null || input.length == 0 || input.length > 4096) invalid();
        var value = JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(input);
        if (value == null || !value.isObject()) invalid();
        Set<String> fields = new HashSet<>(); value.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(Set.of("schema_version", "tenant_id", "invocation_id"))
                || !"1.0.0".equals(value.path("schema_version").textValue())) invalid();
        WireValues.uuid(value.path("tenant_id").textValue()); WireValues.uuid(value.path("invocation_id").textValue());
        return value;
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SimulationLocalProcessBridge.BridgeFailure.class)
    void unknown(SimulationLocalProcessBridge.BridgeFailure failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 409, ErrorCode.INVOCATION_UNKNOWN);
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, switch (failure.code()) {
            case ACCESS_DENIED -> 403; case FILE_TOO_LARGE -> 413; case OBJECT_UNAVAILABLE -> 404;
            case DEPENDENCY_UNAVAILABLE -> 503; default -> 409;
        }, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void schema(SchemaRejection failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(SecurityException.class)
    void token(SecurityException failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 403, ErrorCode.ACCESS_DENIED);
    }
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)
    void malformed(Exception failure, HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
