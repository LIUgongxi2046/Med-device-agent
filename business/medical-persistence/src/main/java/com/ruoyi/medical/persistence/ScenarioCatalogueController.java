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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ScenarioCatalogueController {
    private final ApplicationSessionIdentity identities;
    private final ScenarioCatalogueService service;
    private final ScenarioCatalogueSchema schema;
    public ScenarioCatalogueController(ApplicationSessionIdentity identities, ScenarioCatalogueService service, ScenarioCatalogueSchema schema) {
        this.identities = identities; this.service = service; this.schema = schema;
    }

    @GetMapping("/api/scenarios")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        var query = schema.query(request.getParameterMap());
        var result = service.read(identities.requireCurrent(request, authentication), context, query);
        response.setStatus(200); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
}
