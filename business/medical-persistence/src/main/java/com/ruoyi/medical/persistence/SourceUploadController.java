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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class SourceUploadController {
    private final ApplicationSessionIdentity identities;
    private final SourceUploadService uploads;
    private final PermissionSchema json;
    public SourceUploadController(ApplicationSessionIdentity identities, SourceUploadService uploads, PermissionSchema json) {
        this.identities = identities; this.uploads = uploads; this.json = json;
    }
    @PostMapping(value="/api/uploads", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        headers(version, context); json.headers(context, key); if (request.getQueryString() != null) invalid();
        return response(response, uploads.create(identities.requireCurrent(request, authentication), context, key, body));
    }
    @GetMapping("/api/uploads/{uploadId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String uploadId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); WireValues.uuid(uploadId);
        for (var parameter : request.getParameterMap().keySet())
            if (!Set.of("after_part", "limit").contains(parameter) || request.getParameterValues(parameter).length != 1) invalid();
        String after = request.getParameter("after_part"), limit = request.getParameter("limit");
        if (after != null && (!after.matches("(?:0|[1-9][0-9]{0,9})") || Long.parseLong(after) > 4294967295L)) invalid();
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        return response(response, uploads.read(identities.requireCurrent(request, authentication), context, uploadId,
            after == null ? 0 : Long.parseLong(after), limit == null ? 100 : Integer.parseInt(limit)));
    }
    @PostMapping(value="/api/uploads/{uploadId}/completions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public JsonNode requestCompletion(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String uploadId,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        for (String name : java.util.List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (java.util.Collections.list(request.getHeaders(name)).size() != 1) invalid();
        headers(version, context); json.headers(context, key); WireValues.uuid(uploadId); if (request.getQueryString() != null) invalid();
        return response(response, uploads.requestCompletion(identities.requireCurrent(request, authentication), context, key, uploadId, body));
    }
    private static void headers(String version, String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); PermissionSchema.revision(context);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, java.nio.charset.CharacterCodingException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case FILE_TOO_LARGE -> 413;
            case FILE_TYPE_UNSUPPORTED -> 415;
            case SCHEMA_INVALID, CONTENT_MISMATCH -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
