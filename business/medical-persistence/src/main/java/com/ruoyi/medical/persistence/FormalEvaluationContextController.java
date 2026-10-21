package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "medical.formal-evaluation-context", name = "enabled", havingValue = "true", matchIfMissing = false)
public final class FormalEvaluationContextController {
    private final ApplicationSessionIdentity identities;
    private final ObjectProvider<FormalEvaluationContextService> services;
    private final PermissionSchema json;
    public FormalEvaluationContextController(ApplicationSessionIdentity identities, ObjectProvider<FormalEvaluationContextService> services, PermissionSchema json) {
        this.identities = identities; this.services = services; this.json = json;
    }

    @PostMapping(value = "/api/formal-evaluation-context-bindings", consumes = "application/json")
    public JsonNode author(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, true, true);
        var subject = identities.requireCurrent(request, authentication);
        var result = service().author(subject, context, FormalEvaluationContextHttp.key(request),
                FormalEvaluationContextHttp.body(request, FormalEvaluationContextHttpSchema.MAXIMUM_BYTES));
        response.setStatus(201);
        return FormalEvaluationContextHttp.envelope(result, response, json);
    }
    @PostMapping(value = "/api/formal-evaluation-context-decisions", consumes = "application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, true, true);
        var subject = identities.requireCurrent(request, authentication);
        var result = service().decide(subject, context, FormalEvaluationContextHttp.key(request),
                FormalEvaluationContextHttp.body(request, FormalEvaluationContextHttpSchema.MAXIMUM_BYTES));
        response.setStatus(201);
        return FormalEvaluationContextHttp.envelope(result, response, json);
    }
    @GetMapping("/api/formal-evaluation-context-bindings/{id}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable("id") String id) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, false, false);
        var subject = identities.requireCurrent(request, authentication);
        return FormalEvaluationContextHttp.envelope(service().read(subject, context, id), response, json);
    }
    @PostMapping(value = "/api/formal-evaluation-context-bindings/query", consumes = "application/json")
    public JsonNode query(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, true, false);
        var subject = identities.requireCurrent(request, authentication);
        return FormalEvaluationContextHttp.envelope(service().query(subject, context,
                FormalEvaluationContextHttp.body(request, FormalEvaluationContextHttpSchema.MAXIMUM_QUERY_BYTES)), response, json);
    }
    @PostMapping(value = "/api/formal-evaluation-context-bindings/recovery", consumes = "application/json")
    public JsonNode recover(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        String context = FormalEvaluationContextHttp.headers(request, response, true, false);
        var subject = identities.requireCurrent(request, authentication);
        return FormalEvaluationContextHttp.envelope(service().recover(subject, context,
                FormalEvaluationContextHttp.body(request, FormalEvaluationContextHttpSchema.MAXIMUM_BYTES)), response, json);
    }
    private FormalEvaluationContextService service() {
        var service = services.getIfUnique();
        if (service == null) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return service;
    }
    @ExceptionHandler(Exception.class)
    void failure(Exception failure, HttpServletResponse response) throws IOException { FormalEvaluationContextHttp.failure(failure, response); }
}
