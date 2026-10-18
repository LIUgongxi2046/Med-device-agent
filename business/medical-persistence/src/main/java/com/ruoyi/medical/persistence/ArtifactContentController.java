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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** BX-37 currently exposes only domain-verified document body/field/evidence artifacts. */
@RestController
public final class ArtifactContentController {
    private final ApplicationSessionIdentity identities;
    private final DocumentReadService documents;

    public ArtifactContentController(ApplicationSessionIdentity identities, DocumentReadService documents) {
        this.identities = identities; this.documents = documents;
    }

    @GetMapping("/api/artifacts/{id}/content")
    public JsonNode content(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context); CollectionSchema.id(id);
        var query = query(request.getParameterMap());
        var result = documents.artifactContent(identities.requireCurrent(request, authentication), context, id,
            query.limitBytes(), query.cursor());
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue());
        response.setHeader("X-Content-Type-Options", "nosniff"); return result;
    }

    static Query query(Map<String,String[]> parameters) {
        if (!Set.of("limit", "cursor").containsAll(parameters.keySet())) invalid();
        String limit = scalar(parameters, "limit"), cursor = scalar(parameters, "cursor");
        if (limit != null && !limit.matches("[1-9][0-9]{0,6}")) invalid();
        int bytes = limit == null ? ArtifactContentReadPolicy.DEFAULT_LIMIT_BYTES : Integer.parseInt(limit);
        if (bytes > ArtifactContentReadPolicy.MAXIMUM_LIMIT_BYTES || cursor != null && cursor.length() > 8192) invalid();
        return new Query(bytes, cursor);
    }

    private static String scalar(Map<String,String[]> values, String key) {
        var entry = values.get(key);
        if (entry == null) { if (values.containsKey(key)) invalid(); return null; }
        if (entry.length != 1 || entry[0] == null || entry[0].isEmpty()) invalid();
        return entry[0];
    }

    record Query(int limitBytes, String cursor) { }

    @ExceptionHandler(SchemaRejection.class)
    void invalidSchema(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case OBJECT_UNAVAILABLE, SOURCE_MISSING -> 404;
            case FILE_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }

    @ExceptionHandler({java.text.ParseException.class, com.nimbusds.jose.JOSEException.class})
    void invalidCursor(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 409, ErrorCode.CURSOR_INVALID);
    }

    @ExceptionHandler(Exception.class)
    void failed(Exception failure, HttpServletResponse response) throws IOException {
        // SDK/transaction wrappers must not disclose object paths, SQL, credentials, or exception messages.
        var seen = Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof TenantAccessException denial) { denied(denial, response); return; }
            if (cause instanceof SchemaRejection rejection) { invalidSchema(rejection, response); return; }
            if (cause instanceof org.springframework.security.access.AccessDeniedException) {
                new IdentityErrorResponseWriter().write(response, 403, ErrorCode.ACCESS_DENIED); return;
            }
            if (cause instanceof org.springframework.security.core.AuthenticationException) {
                new IdentityErrorResponseWriter().write(response, 401, ErrorCode.AUTHENTICATION_REQUIRED); return;
            }
        }
        new IdentityErrorResponseWriter().write(response, 500, ErrorCode.INTERNAL_ERROR);
    }

    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
