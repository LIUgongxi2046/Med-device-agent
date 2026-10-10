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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PermissionDecisionController {
    private final ApplicationSessionIdentity identities;
    private final PermissionDecisionService decisions;
    private final PermissionSchema schema;
    public PermissionDecisionController(ApplicationSessionIdentity identities, PermissionDecisionService decisions, PermissionSchema schema) {
        this.identities = identities; this.decisions = decisions; this.schema = schema;
    }

    @PostMapping(value="/api/permission-decisions", consumes="application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        schema.headers(context, key);
        var result = decisions.decide(identities.requireCurrent(request, authentication), body, context, key);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
