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
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationController {
    private final ApplicationSessionIdentity identities;
    private final EnterpriseRegistrationService registrations;
    public EnterpriseRegistrationController(ApplicationSessionIdentity identities, EnterpriseRegistrationService registrations) {
        this.identities = identities; this.registrations = registrations;
    }
    @GetMapping("/api/enterprise-registrations")
    JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version) {
        version(version);
        if (!Set.of("limit", "cursor").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) invalid();
        String limit = request.getParameter("limit"), cursor = request.getParameter("cursor");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (cursor != null) WireValues.uuid(cursor);
        JsonNode result = registrations.list(identities.requireCurrent(request, authentication), limit == null ? 50 : Integer.parseInt(limit), cursor);
        headers(response, result); return result;
    }
    @GetMapping("/api/enterprise-registrations/{id}")
    JsonNode detail(@PathVariable String id, HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version) {
        version(version); if (request.getQueryString() != null) invalid(); WireValues.uuid(id);
        JsonNode result = registrations.detail(identities.requireCurrent(request, authentication), id);
        headers(response, result); return result;
    }
    @PostMapping(value="/api/enterprise-registrations", consumes="application/json")
    JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); if (request.getQueryString() != null) invalid();
        JsonNode result = registrations.create(identities.requireCurrent(request, authentication), body, key);
        response.setStatus(201); headers(response, result); return result;
    }
    private static void headers(HttpServletResponse response, JsonNode result) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue());
    }
    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void rejected(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void conflict(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
