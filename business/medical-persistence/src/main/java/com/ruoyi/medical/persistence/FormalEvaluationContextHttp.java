package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/** Strict ordinary-session HTTP boundary; CSRF token verification remains in Spring Security. */
final class FormalEvaluationContextHttp {
    private FormalEvaluationContextHttp() { }
    static String headers(HttpServletRequest request, HttpServletResponse response, boolean post, boolean write) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Schema-Version", "1.0.0");
        if (!"1.0.0".equals(FormalEvaluationInputHttp.one(request, "X-Schema-Version")))
            throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context = FormalEvaluationInputHttp.one(request, "X-Context-Revision");
        PermissionSchema.revision(context);
        if (post) FormalEvaluationInputHttp.one(request, "X-CSRF-TOKEN");
        if (request.getQueryString() != null || !write && request.getHeaders("Idempotency-Key").hasMoreElements())
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (!post && (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1))
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return context;
    }
    static String key(HttpServletRequest request) {
        String key = FormalEvaluationInputHttp.one(request, "Idempotency-Key");
        if (!key.matches("[!-~]{16,128}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return key;
    }
    static byte[] body(HttpServletRequest request, int maximum) throws IOException {
        if (request.getContentLengthLong() > maximum) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes = request.getInputStream().readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        if (bytes.length == 0) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return bytes;
    }
    static JsonNode envelope(JsonNode data, HttpServletResponse response, PermissionSchema json) {
        String request = UUID.randomUUID().toString();
        String revision = data.has("revision") ? data.required("revision").asText() : "1";
        response.setHeader("X-Request-Id", request);
        response.setHeader("X-Resource-Revision", revision);
        return json.tree(new InterfaceProtocol.Envelope("1.0.0", request, revision, data));
    }
    static void failure(Exception failure, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        ErrorCode code = ErrorCode.INTERNAL_ERROR; int status = 500;
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof TenantAccessException denied) {
                code = denied.code();
                status = switch (code) {
                    case ACCESS_DENIED -> 403;
                    case SOURCE_MISSING, OBJECT_UNAVAILABLE -> 404;
                    case FILE_TOO_LARGE -> 413;
                    case DEPENDENCY_UNAVAILABLE -> 503;
                    case SCHEMA_INVALID, SCHEMA_UNSUPPORTED -> 422;
                    default -> 409;
                }; break;
            }
            if (cause instanceof SchemaRejection rejected) { code = rejected.code(); status = 422; break; }
            if (cause instanceof JsonProcessingException) { code = ErrorCode.SCHEMA_INVALID; status = 422; break; }
            if (cause instanceof org.springframework.security.core.AuthenticationException) { code = ErrorCode.AUTHENTICATION_REQUIRED; status = 401; break; }
            if (cause instanceof org.springframework.security.access.AccessDeniedException) { code = ErrorCode.ACCESS_DENIED; status = 403; break; }
        }
        new IdentityErrorResponseWriter().write(response, status, code);
    }
}
