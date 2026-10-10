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
public final class EnterpriseRoleController {
    private final ApplicationSessionIdentity identities;
    private final EnterpriseRoleService roles;
    private final PermissionSchema schema;
    public EnterpriseRoleController(ApplicationSessionIdentity identities, EnterpriseRoleService roles, PermissionSchema schema) {
        this.identities = identities; this.roles = roles; this.schema = schema;
    }

    @GetMapping("/api/enterprise-roles")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context);
        for (var key : request.getParameterMap().keySet()) {
            if (!java.util.Set.of("limit", "after").contains(key) || request.getParameterValues(key).length != 1) invalid();
        }
        String limitValue = request.getParameter("limit"), after = request.getParameter("after");
        if (limitValue != null && !limitValue.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) id(after);
        return response(response, roles.list(identities.requireCurrent(request, authentication), context, after, limitValue == null ? 50 : Integer.parseInt(limitValue)));
    }

    @GetMapping("/api/enterprise-roles/{roleId}/versions/{versionId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String roleId, @PathVariable String versionId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); query(request); id(roleId); id(versionId);
        return response(response, roles.read(identities.requireCurrent(request, authentication), context, roleId, versionId));
    }

    @PostMapping(value="/api/enterprise-roles/{roleId}/versions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode publish(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String roleId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); query(request); id(roleId);
        return response(response, roles.publish(identities.requireCurrent(request, authentication), context, key, roleId, body));
    }

    @PostMapping(value="/api/enterprise-roles/{roleId}/versions/{versionId}/revocations", consumes="application/json")
    public JsonNode revoke(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String roleId, @PathVariable String versionId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); query(request); id(roleId); id(versionId);
        return response(response, roles.revoke(identities.requireCurrent(request, authentication), context, key, roleId, versionId, body));
    }
    private static void headers(String version, String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
    }
    private static void query(HttpServletRequest request) { if (request.getQueryString() != null) invalid(); }
    private static void id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) invalid();
        com.ruoyi.medical.contracts.WireValues.uuid(value);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
