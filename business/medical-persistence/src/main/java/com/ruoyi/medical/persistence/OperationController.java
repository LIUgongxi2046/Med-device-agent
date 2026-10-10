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
public final class OperationController {
    private final ApplicationSessionIdentity identities;
    private final OperationService operations;
    private final OperationSchema schema;

    public OperationController(ApplicationSessionIdentity identities, OperationService operations, OperationSchema schema) {
        this.identities = identities; this.operations = operations; this.schema = schema;
    }

    @PostMapping(value="/api/operation-lookups", consumes="application/json")
    public JsonNode lookup(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws IOException {
        headers(request, version, context); var value = schema.lookup(body);
        return response(response, operations.lookup(identities.requireCurrent(request, authentication), context, value));
    }

    @GetMapping("/api/operations/{id}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String id,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(request, version, context);
        if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) invalid();
        WireValues.uuid(id);
        return response(response, operations.read(identities.requireCurrent(request, authentication), context, id));
    }

    private static void headers(HttpServletRequest request, String version, String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context); if (request.getQueryString() != null) invalid();
    }

    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }

    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }

    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) { case ACCESS_DENIED -> 403; case OBJECT_UNAVAILABLE -> 404; default -> 409; };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
}
