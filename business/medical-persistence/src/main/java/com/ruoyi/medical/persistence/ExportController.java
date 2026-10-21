package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ExportController {
    private final ApplicationSessionIdentity identities;
    private final ExportService service;
    private final ExportExecutionService execution;
    private final ExportSchema schema;
    private final PermissionSchema headers;
    private final ExportLayoutService layouts;
    public ExportController(ApplicationSessionIdentity identities, ExportService service, ExportExecutionService execution,
            ExportSchema schema, PermissionSchema headers, ExportLayoutService layouts) {
        this.layouts = layouts; this.identities = identities; this.service = service; this.execution = execution; this.schema = schema; this.headers = headers;
    }
    @PostMapping(value="/api/documents/{documentId}/export-layout-profiles", consumes="application/json")
    public JsonNode authorLayout(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String documentId,
            @RequestBody byte[] body, @RequestHeader("X-Schema-Version") String version,
            @RequestHeader("X-Context-Revision") String context, @RequestHeader("Idempotency-Key") String key) {
        mutationHeaders(request, version, context, key); noQuery(request);
        return response(response, schema.envelope("LayoutProfileView", layouts.author(identities.requireCurrent(request, authentication), context, documentId, key, body), java.util.UUID.randomUUID().toString(), null));
    }
    @GetMapping("/api/documents/{documentId}/export-layout-profiles/{profileId}")
    public JsonNode readLayout(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String documentId,
            @PathVariable String profileId, @RequestHeader("X-Schema-Version") String version,
            @RequestHeader("X-Context-Revision") String context) {
        readHeaders(request, version, context);
        if (!request.getParameterMap().keySet().equals(java.util.Set.of("document_revision"))
                || request.getParameterValues("document_revision").length != 1) invalid();
        return response(response, schema.envelope("LayoutProfileView", layouts.read(identities.requireCurrent(request, authentication), context, documentId, request.getParameter("document_revision"), profileId), java.util.UUID.randomUUID().toString(), null));
    }
    @PostMapping(value="/api/export-items/{exportItemId}/layout-verifications", consumes="application/json")
    public JsonNode verifyLayout(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String exportItemId,
            @RequestBody byte[] body, @RequestHeader("X-Schema-Version") String version,
            @RequestHeader("X-Context-Revision") String context, @RequestHeader("Idempotency-Key") String key) {
        mutationHeaders(request, version, context, key); noQuery(request);
        return response(response, schema.envelope("VerificationView", layouts.verify(identities.requireCurrent(request, authentication), context, exportItemId, key, body), java.util.UUID.randomUUID().toString(), null));
    }
    @GetMapping("/api/export-items/{exportItemId}/layout-verifications")
    public JsonNode readLayoutVerifications(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String exportItemId,
            @RequestHeader("X-Schema-Version") String version, @RequestHeader("X-Context-Revision") String context) {
        readHeaders(request, version, context); noQuery(request);
        return response(response, schema.envelope("LayoutVerificationHistory", layouts.verifications(identities.requireCurrent(request, authentication), context, exportItemId), java.util.UUID.randomUUID().toString(), null));
    }
    @PostMapping(value="/api/export-items/{exportItemId}/executions", consumes="application/json")
    public JsonNode execute(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String exportItemId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        mutationHeaders(request, version, context, key); noQuery(request);
        var subject = identities.requireCurrent(request, authentication);
        String exportId = execution.execute(subject, context, exportItemId, schema.process(body));
        return response(response, service.read(subject, context, exportId));
    }
    @PostMapping(value="/api/documents/{documentId}/exports", consumes="application/json")
    public ResponseEntity<JsonNode> create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        mutationHeaders(request, version, context, key); noQuery(request);
        var value = service.create(identities.requireCurrent(request, authentication), context, key, documentId, schema.create(body));
        response(response, value); return ResponseEntity.accepted().body(value);
    }
    @GetMapping("/api/exports/{exportId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String exportId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        readHeaders(request, version, context); noQuery(request);
        return response(response, service.read(identities.requireCurrent(request, authentication), context, exportId));
    }
    @GetMapping("/api/documents/{documentId}/exports")
    public JsonNode history(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        readHeaders(request, version, context);
        if (!request.getParameterMap().keySet().equals(java.util.Set.of("document_revision", "page", "size"))
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) invalid();
        int page = positive(request.getParameter("page"), 1000000), size = positive(request.getParameter("size"), 50);
        return response(response, service.history(identities.requireCurrent(request, authentication), context,
                documentId, request.getParameter("document_revision"), page, size));
    }
    @GetMapping("/api/documents/{documentId}/export-basis")
    public JsonNode basis(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String documentId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        readHeaders(request, version, context);
        if (!request.getParameterMap().keySet().equals(java.util.Set.of("document_revision"))
                || request.getParameterValues("document_revision").length != 1) invalid();
        return response(response, service.basis(identities.requireCurrent(request, authentication), context,
                documentId, request.getParameter("document_revision")));
    }
    @PostMapping(value="/api/export-items/{exportItemId}/attempts", consumes="application/json")
    public ResponseEntity<JsonNode> retry(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String exportItemId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        mutationHeaders(request, version, context, key); noQuery(request);
        var value = service.retry(identities.requireCurrent(request, authentication), context, key, exportItemId, schema.retry(body));
        response(response, value); return ResponseEntity.accepted().body(value);
    }
    @GetMapping("/api/export-items/{exportItemId}/content")
    public ResponseEntity<byte[]> content(HttpServletRequest request, Authentication authentication,
            @PathVariable String exportItemId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Range", required=false) String range) throws Exception {
        readHeaders(request, version, context); noQuery(request); var value = service.content(
                identities.requireCurrent(request, authentication), context, exportItemId, range);
        var response = ResponseEntity.status(value.start() == 0 && value.end() == value.size() ? 200 : 206)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_TYPE, value.mediaType())
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(value.end() - value.start()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(value.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Artifact-Id", value.artifactId()).header("X-Content-SHA256", value.sha256());
        if (value.start() != 0 || value.end() != value.size())
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + value.start() + "-" + (value.end() - 1) + "/" + value.size());
        return response.body(value.bytes());
    }
    @GetMapping("/api/export-items/{exportItemId}/review-content")
    public ResponseEntity<byte[]> reviewContent(HttpServletRequest request, Authentication authentication,
            @PathVariable String exportItemId, @RequestHeader("X-Schema-Version") String version,
            @RequestHeader("X-Context-Revision") String context) throws Exception {
        readHeaders(request, version, context); noQuery(request);
        var value = service.content(identities.requireCurrent(request, authentication), context, exportItemId, null, true);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.CONTENT_TYPE, value.mediaType()).header("X-Content-SHA256", value.sha256())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("REVIEW-ONLY-" + value.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(value.bytes());
    }
    private void mutationHeaders(HttpServletRequest request, String version, String context, String key) {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision", "Idempotency-Key"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        version(version); headers.headers(context, key);
    }
    private static void readHeaders(HttpServletRequest request, String version, String context) {
        for (String name : List.of("X-Schema-Version", "X-Context-Revision"))
            if (Collections.list(request.getHeaders(name)).size() != 1) invalid();
        version(version); PermissionSchema.revision(context);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static int positive(String value, int maximum) {
        if (value == null || !value.matches("[1-9][0-9]{0,6}")) { invalid(); return 0; }
        int parsed = Integer.parseInt(value); if (parsed > maximum) invalid(); return parsed;
    }
    private static void noQuery(HttpServletRequest request) { if (request.getQueryString() != null) invalid(); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, TemplateController.errorStatus(failure.code()), failure.code());
    }
}
