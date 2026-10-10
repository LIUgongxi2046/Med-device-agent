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
public final class CollectionController {
    private final ApplicationSessionIdentity identities;
    private final CollectionService collections;
    private final CollectionSchema schema;
    private final PermissionSchema json;
    public CollectionController(ApplicationSessionIdentity identities, CollectionService collections, CollectionSchema schema, PermissionSchema json) {
        this.identities = identities; this.collections = collections; this.schema = schema; this.json = json;
    }
    @PostMapping(value="/api/collections", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); json.headers(context, key); noQuery(request);
        return response(response, collections.create(identities.requireCurrent(request, authentication), context, key, body));
    }
    @PostMapping(value="/api/collections/{id}/versions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode version(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String id,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); json.headers(context, key); CollectionSchema.id(id); noQuery(request);
        return response(response, collections.version(identities.requireCurrent(request, authentication), context, key, id, body));
    }
    @GetMapping("/api/collections/{id}/versions")
    public JsonNode history(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String id,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context); CollectionSchema.id(id);
        var query = schema.query(request.getParameterMap());
        return response(response, collections.history(identities.requireCurrent(request, authentication), context, id, query));
    }
    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void noQuery(HttpServletRequest request) {
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, TemplateController.errorStatus(failure.code()), failure.code());
    }
}
