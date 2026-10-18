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

/** Additive endpoint; the published BX-12 RecordPage endpoint is not replaced. Session/CSRF filters remain in force. */
@RestController
public final class AnnotationReplyController {
    private final ApplicationSessionIdentity identities;
    private final AnnotationReplyService replies;
    private final PermissionSchema json;
    public AnnotationReplyController(ApplicationSessionIdentity identities, AnnotationReplyService replies, PermissionSchema json) {
        this.identities = identities; this.replies = replies; this.json = json;
    }
    @GetMapping("/api/annotation-threads/{id}/replies")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        headers(request, version, context); json.requireId(id);
        return response(response, replies.list(identities.requireCurrent(request, authentication), context, id));
    }
    @PostMapping(value="/api/annotation-threads/{id}/replies", consumes="application/json")
    public ResponseEntity<JsonNode> append(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        headers(request, version, context); json.requireId(id);
        if (Collections.list(request.getHeaders("Idempotency-Key")).size() != 1) invalid(); json.headers(context, key);
        var subject = identities.requireCurrent(request, authentication);
        // Read the servlet stream with a hard bound; @RequestBody byte[] would buffer unbounded JSON first.
        if (request.getContentLengthLong() > AnnotationReplySchema.MAXIMUM_REQUEST_BYTES)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] body = request.getInputStream().readNBytes(AnnotationReplySchema.MAXIMUM_REQUEST_BYTES + 1);
        if (body.length > AnnotationReplySchema.MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return ResponseEntity.status(201).body(response(response, replies.append(subject, context, key, id, body)));
    }
    private static void headers(HttpServletRequest request, String version, String context) {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        // This extension returns a bounded complete immutable view, never an unstable partial page.
        if (request.getQueryString() != null) invalid();
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
                    case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
                    default -> 409;
                };
                write(response, status, denial.code()); return;
            }
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException
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
