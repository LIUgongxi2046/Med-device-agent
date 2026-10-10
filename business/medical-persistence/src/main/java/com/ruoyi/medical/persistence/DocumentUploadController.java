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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public final class DocumentUploadController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DocumentUploadController.class);
    private final ApplicationSessionIdentity identities;
    private final DocumentUploadService uploads;
    private final PermissionSchema json;
    private final DocumentUploadCompletionService completions;
    public DocumentUploadController(ApplicationSessionIdentity identities, DocumentUploadService uploads, PermissionSchema json,
            DocumentUploadCompletionService completions) {
        this.identities = identities; this.uploads = uploads; this.json = json; this.completions = completions;
    }

    @PostMapping(value="/api/artifact-upload-grants/{grantId}/completions", consumes="application/json")
    public ResponseEntity<JsonNode> complete(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String grantId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (Collections.list(request.getHeaders(name)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = completions.complete(identities.requireCurrent(request, authentication), context, key, grantId, body);
        var value = result.response();
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue());
        return ResponseEntity.status(result.httpStatus()).body(value);
    }

    @PostMapping(value="/api/artifact-upload-grants", consumes="application/json")
    public ResponseEntity<JsonNode> create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (Collections.list(request.getHeaders(name)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = uploads.create(identities.requireCurrent(request, authentication), context, key, body);
        var value = result.response();
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue());
        return ResponseEntity.status(result.httpStatus()).body(value);
    }

    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, java.nio.charset.CharacterCodingException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        LOG.warn("DOCUMENT_UPLOAD_REJECTED", failure);
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case FILE_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
}
