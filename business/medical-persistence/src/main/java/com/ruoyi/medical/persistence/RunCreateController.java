package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class RunCreateController {
    private final ApplicationSessionIdentity identities;
    private final RunCreateService runs;
    public RunCreateController(ApplicationSessionIdentity identities, RunCreateService runs) { this.identities = identities; this.runs = runs; }

    @PostMapping(value="/api/tasks/{taskId}/runs", consumes="application/json")
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        for (String header : java.util.List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (java.util.Collections.list(request.getHeaders(header)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = runs.create(identities.requireCurrent(request, authentication), context, key, taskId, body);
        response.setStatus(202); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case SOURCE_NOT_READY, RIGHTS_PENDING, CONDITION_UNKNOWN, PLAN_UNCONFIRMED, PLAN_CYCLE,
                    BUDGET_EXCEEDED, DEPENDENCY_UNAVAILABLE, MODEL_POLICY_INCOMPLETE, RETENTION_UNCONFIGURED -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)
    void malformed(com.fasterxml.jackson.core.JsonProcessingException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
