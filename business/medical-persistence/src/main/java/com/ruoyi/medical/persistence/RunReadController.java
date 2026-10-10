package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class RunReadController {
    private final ApplicationSessionIdentity identities;
    private final RunReadService runs;
    private final RunCancellationService cancellations;
    public RunReadController(ApplicationSessionIdentity identities, RunReadService runs, RunCancellationService cancellations) {
        this.identities = identities; this.runs = runs; this.cancellations = cancellations;
    }

    @PostMapping("/api/runs/{runId}/cancel-requests")
    public JsonNode cancel(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String runId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        for (String header : java.util.List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (java.util.Collections.list(request.getHeaders(header)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = cancellations.cancel(identities.requireCurrent(request, authentication), context, key, runId, body);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @GetMapping("/api/runs/{runId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String runId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        WireValues.uuid(runId); PermissionSchema.revision(context);
        var result = runs.read(identities.requireCurrent(request, authentication), context, runId);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
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
