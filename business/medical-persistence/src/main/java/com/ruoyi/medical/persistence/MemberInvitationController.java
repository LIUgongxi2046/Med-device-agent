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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MemberInvitationController {
    private final ApplicationSessionIdentity identities;
    private final MemberInvitationService service;
    private final PermissionSchema schema;
    public MemberInvitationController(ApplicationSessionIdentity identities, MemberInvitationService service, PermissionSchema schema) {
        this.identities = identities; this.service = service; this.schema = schema;
    }
    @PostMapping(value="/api/member-invitations", consumes="application/json")
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(request, version); schema.headers(context, key);
        JsonNode result = service.create(identities.requireCurrent(request, authentication), body, context, key);
        response.setStatus(201); response(response, result); return result;
    }
    @PostMapping(value="/api/member-invitations/{id}/acceptances", consumes="application/json")
    public JsonNode accept(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable("id") String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(request, version); schema.headers("0", key); WireValues.uuid(id);
        JsonNode result = service.accept(identities.requireCurrent(request, authentication), id, body, key);
        response(response, result); return result;
    }
    private static void headers(HttpServletRequest request, String version) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static void response(HttpServletResponse response, JsonNode result) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue());
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
