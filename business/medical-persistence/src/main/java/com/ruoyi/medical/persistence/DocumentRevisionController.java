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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class DocumentRevisionController {
    private final ApplicationSessionIdentity identities;
    private final DocumentRevisionService documents;
    private final PermissionSchema json;
    public DocumentRevisionController(ApplicationSessionIdentity identities, DocumentRevisionService documents, PermissionSchema json) {
        this.identities = identities; this.documents = documents; this.json = json;
    }
    @PostMapping(value="/api/documents/{documentId}/revisions", consumes="application/json")
    public ResponseEntity<JsonNode> save(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (Collections.list(request.getHeaders(name)).size() != 1) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key); CollectionSchema.id(documentId);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var value = documents.save(identities.requireCurrent(request, authentication), context, key, documentId, body);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue());
        return ResponseEntity.status(201).body(value);
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, java.nio.charset.CharacterCodingException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403; case FILE_TOO_LARGE -> 413; case RATE_LIMITED -> 429;
            case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
}
