package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Additive endpoint; the published RV-04/BX-11 Record endpoints are not replaced. Session/CSRF filters remain in force. */
@RestController
public final class AnnotationThreadController {
    private final ApplicationSessionIdentity identities;
    private final AnnotationThreadService threads;
    private final PermissionSchema json;
    public AnnotationThreadController(ApplicationSessionIdentity identities, AnnotationThreadService threads, PermissionSchema json) {
        this.identities = identities; this.threads = threads; this.json = json;
    }
    @GetMapping("/api/documents/{id}/annotation-threads")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        headers(request, version, context); json.requireId(id);
        var query = request.getParameterMap();
        if (!java.util.Set.of("document_revision", "status").containsAll(query.keySet())) invalid();
        String revision = scalar(query, "document_revision", true), status = scalar(query, "status", false); json.requireId(revision);
        if (status != null && !java.util.Set.of("OPEN", "RESOLVED", "RETURNED", "REANCHOR_REQUIRED").contains(status)) invalid();
        return response(response, threads.list(identities.requireCurrent(request, authentication), context, id, revision, status));
    }
    @PostMapping(value="/api/documents/{id}/annotation-threads", consumes="application/json")
    public ResponseEntity<JsonNode> append(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        headers(request, version, context); json.requireId(id);
        if (request.getQueryString() != null) invalid();
        if (Collections.list(request.getHeaders("Idempotency-Key")).size() != 1) invalid(); json.headers(context, key);
        var subject = identities.requireCurrent(request, authentication);
        // Read the servlet stream with a hard bound; @RequestBody byte[] would buffer unbounded JSON first.
        if (request.getContentLengthLong() > AnnotationThreadSchema.MAXIMUM_REQUEST_BYTES)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] body = request.getInputStream().readNBytes(AnnotationThreadSchema.MAXIMUM_REQUEST_BYTES + 1);
        if (body.length > AnnotationThreadSchema.MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return ResponseEntity.status(201).body(response(response, threads.create(subject, context, key, id, body)));
    }
    private static void headers(HttpServletRequest request, String version, String context) {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);

    }
    private static String scalar(java.util.Map<String,String[]> query, String name, boolean required) {
        var values = query.get(name);
        if (values == null) { if (required) invalid(); return null; }
        if (values.length != 1 || values[0] == null || values[0].isEmpty()) invalid(); return values[0];
    }
    private static JsonNode response(HttpServletResponse response, JsonNode result) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").asText()); response.setHeader("X-Content-Type-Options", "nosniff"); return result;
    }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure, HttpServletResponse response) throws IOException {
        // Unwrap without logging request text, SQL, object locations, or provider exception messages.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SchemaRejection rejection) { write(response, 422, rejection.code()); return; }
            if (cause instanceof TenantAccessException denial) {
                int status = switch (denial.code()) {
                    case ACCESS_DENIED -> 403;
                    case OBJECT_UNAVAILABLE, SOURCE_MISSING -> 404;
                    case FILE_TOO_LARGE -> 413;
                    case RATE_LIMITED -> 429;
                    case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT, SELECTION_INVALID -> 422;
                    default -> 409;
                };
                write(response, status, denial.code()); return;
            }
            if (cause instanceof java.nio.charset.CharacterCodingException
                    || cause instanceof com.fasterxml.jackson.core.JsonProcessingException
                    || cause instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
                write(response, 422, ErrorCode.SCHEMA_INVALID); return;
            }
            if (cause instanceof org.springframework.security.core.AuthenticationException) {
                write(response, 401, ErrorCode.AUTHENTICATION_REQUIRED); return;
            }
            if (cause instanceof org.springframework.security.access.AccessDeniedException) {
                write(response, 403, ErrorCode.ACCESS_DENIED); return;
            }
        }
        write(response, 500, ErrorCode.INTERNAL_ERROR);
    }
    private static void write(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        new IdentityErrorResponseWriter().write(response, status, code);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
