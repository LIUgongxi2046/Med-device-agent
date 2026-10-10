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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class DocumentReadController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DocumentReadController.class);
    private final ApplicationSessionIdentity identities;
    private final DocumentReadService documents;
    public DocumentReadController(ApplicationSessionIdentity identities, DocumentReadService documents) {
        this.identities = identities; this.documents = documents;
    }
    @GetMapping("/api/documents/{documentId}")
    public JsonNode snapshot(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        headers(request, version, context); CollectionSchema.id(documentId);
        if (request.getQueryString() != null) invalid();
        return reply(response, documents.snapshot(identities.requireCurrent(request, authentication), context, documentId));
    }
    @GetMapping("/api/documents/{documentId}/body")
    public JsonNode body(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        headers(request, version, context); CollectionSchema.id(documentId);
        var query = request.getParameterMap();
        if (!Set.of("document_revision", "cursor", "limit", "node_ids").containsAll(query.keySet())) invalid();
        String revision = scalar(query, "document_revision", true); CollectionSchema.id(revision);
        String limitText = scalar(query, "limit", false), cursor = scalar(query, "cursor", false);
        if (limitText != null && !limitText.matches("[1-9][0-9]{0,2}")) invalid();
        int limit = limitText == null ? 100 : Integer.parseInt(limitText);
        if (limit > 100 || cursor != null && (cursor.isEmpty() || cursor.length() > 8192)) invalid();
        var ids = query.containsKey("node_ids") ? List.of(query.get("node_ids")) : List.<String>of();
        if (ids.isEmpty() && query.containsKey("node_ids") || ids.size() > 1000 || ids.size() != new HashSet<>(ids).size()) invalid();
        ids.forEach(CollectionSchema::id);
        return reply(response, documents.body(identities.requireCurrent(request, authentication), context, documentId, revision, ids, limit, cursor));
    }
    @GetMapping("/api/documents/{documentId}/revisions/{documentRevision}")
    public JsonNode revision(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @PathVariable String documentRevision,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        headers(request, version, context); CollectionSchema.id(documentId); CollectionSchema.id(documentRevision);
        if (request.getQueryString() != null) invalid();
        return reply(response, documents.snapshotFixed(identities.requireCurrent(request, authentication), context, documentId, documentRevision));
    }
    @GetMapping("/api/documents/{documentId}/dependency-access-set")
    public JsonNode dependency(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(request, version, context); CollectionSchema.id(documentId);
        var query = request.getParameterMap();
        if (!query.keySet().equals(Set.of("document_revision", "expected_revision"))) invalid();
        String revision = scalar(query, "document_revision", true), expected = scalar(query, "expected_revision", true);
        CollectionSchema.id(revision); PermissionSchema.revision(expected);
        return reply(response, documents.dependency(identities.requireCurrent(request, authentication), context, documentId, revision, expected));
    }
    private static String scalar(Map<String,String[]> query, String key, boolean required) {
        String[] values = query.get(key);
        if (values == null) { if (required) invalid(); return null; }
        if (values.length != 1 || values[0] == null || values[0].isEmpty()) invalid(); return values[0];
    }
    private static void headers(HttpServletRequest request, String version, String context) {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
    }
    private static JsonNode reply(HttpServletResponse response, JsonNode result) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalidSchema(SchemaRejection failure, HttpServletResponse response) throws IOException {
        LOG.warn("DOCUMENT_SCHEMA_REJECTED", failure);
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case FILE_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
    @ExceptionHandler({java.text.ParseException.class, com.nimbusds.jose.JOSEException.class})
    void invalidCursor(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 409, ErrorCode.CURSOR_INVALID);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
