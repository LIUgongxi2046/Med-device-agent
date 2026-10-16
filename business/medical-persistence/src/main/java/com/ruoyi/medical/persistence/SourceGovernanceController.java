package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SourceGovernanceController {
    private final ApplicationSessionIdentity identities;
    private final SourceGovernanceService service;
    private final SourceGovernanceSchema schema;
    private final PermissionSchema headers;

    public SourceGovernanceController(ApplicationSessionIdentity identities, SourceGovernanceService service,
            SourceGovernanceSchema schema, PermissionSchema headers) {
        this.identities = identities; this.service = service; this.schema = schema; this.headers = headers;
    }

    @GetMapping("/api/source-versions")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context);
        return response(response, service.list(identities.requireCurrent(request, authentication), context, query(request)));
    }

    @GetMapping("/api/processing-attempts")
    public JsonNode processingQueue(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context);
        return response(response, service.processingQueue(identities.requireCurrent(request, authentication), context,
                processingQueueQuery(request)));
    }

    @GetMapping("/api/artifacts")
    public JsonNode artifacts(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context);
        return response(response, service.artifacts(identities.requireCurrent(request, authentication), context,
                artifactCatalogueQuery(request)));
    }

    @PostMapping(value="/api/artifacts/{id}/source-submissions", consumes="application/json")
    public JsonNode submitArtifact(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        com.ruoyi.medical.contracts.WireValues.uuid(id);
        response.setStatus(201);
        return response(response, service.submitArtifact(identities.requireCurrent(request, authentication), context, key,
                id, schema.artifactSubmission(body)));
    }

    @GetMapping("/api/source-versions/{id}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context); noQuery(request);
        return response(response, service.read(identities.requireCurrent(request, authentication), context, id));
    }

    @GetMapping("/api/source-versions/{id}/content")
    public JsonNode content(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) throws Exception {
        version(version); PermissionSchema.revision(context);
        return response(response, service.content(identities.requireCurrent(request, authentication), context, id, contentQuery(request)));
    }

    @GetMapping("/api/source-versions/{id}/usages")
    public JsonNode usages(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context);
        return response(response, service.usages(identities.requireCurrent(request, authentication), context, id, usageQuery(request)));
    }

    @GetMapping("/api/source-folders")
    public JsonNode folders(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context);
        var keys = request.getParameterMap().keySet();
        if (!keys.isEmpty() && (!keys.equals(Set.of("project_id")) || request.getParameterValues("project_id").length != 1)) invalid();
        String projectId = request.getParameter("project_id");
        if (projectId != null) com.ruoyi.medical.contracts.WireValues.uuid(projectId);
        return response(response, service.folders(identities.requireCurrent(request, authentication), context, projectId));
    }

    @PostMapping(value="/api/source-folders", consumes="application/json")
    public JsonNode createFolder(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.createFolder(identities.requireCurrent(request, authentication), context, key,
                schema.folderCreate(body)));
    }

    @PostMapping(value="/api/source-folders/{id}/decisions", consumes="application/json")
    public JsonNode decideFolder(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.decideFolder(identities.requireCurrent(request, authentication), context, key,
                id, schema.folderDecision(body)));
    }

    @PostMapping(value="/api/source-organization-decisions", consumes="application/json")
    public JsonNode organize(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.organize(identities.requireCurrent(request, authentication), context, key,
                schema.organization(body)));
    }

    @PostMapping(value="/api/deletion-items/{id}/receipts", consumes="application/json")
    public JsonNode confirmDeletion(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.confirmDeletion(identities.requireCurrent(request, authentication), context, key,
                id, schema.deletionReceipt(body)));
    }

    @GetMapping("/api/deletion-jobs/{id}")
    public JsonNode deletionJob(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context); noQuery(request);
        return response(response, service.deletionJob(identities.requireCurrent(request, authentication), context, id));
    }

    @PostMapping(value="/api/source-versions/{id}/approval-decisions", consumes="application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.decide(identities.requireCurrent(request, authentication), context, key, id, schema.input(body)));
    }

    @PostMapping(value="/api/source-versions/{id}/processing-attempts", consumes="application/json")
    public JsonNode process(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.process(identities.requireCurrent(request, authentication), context, key,
                id, schema.processing(body)));
    }

    @PostMapping(value="/api/source-versions/{id}/lifecycle-decisions", consumes="application/json")
    public JsonNode lifecycle(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws Exception {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, service.lifecycle(identities.requireCurrent(request, authentication), context, key,
                id, schema.lifecycle(body)));
    }

    @PostMapping(value="/api/projects/{projectId}/source-bindings", consumes="application/json")
    public JsonNode bind(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        com.ruoyi.medical.contracts.WireValues.uuid(projectId);
        return response(response, service.bind(identities.requireCurrent(request, authentication), context, key,
                projectId, schema.binding(body)));
    }

    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static SourceGovernanceProtocol.CatalogueQuery query(HttpServletRequest request) {
        var allowed = Set.of("limit", "after", "project_id", "ownership", "approval_status", "lifecycle",
                "bookmarked", "category", "tag", "folder_id", "collection_version");
        for (var key : request.getParameterMap().keySet()) if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after"), project = request.getParameter("project_id");
        String ownership = request.getParameter("ownership"), approval = request.getParameter("approval_status");
        String lifecycle = request.getParameter("lifecycle"), bookmarked = request.getParameter("bookmarked");
        String category = request.getParameter("category"), tag = request.getParameter("tag");
        String folder = request.getParameter("folder_id"), collection = request.getParameter("collection_version");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) com.ruoyi.medical.contracts.WireValues.uuid(after);
        if (project != null) com.ruoyi.medical.contracts.WireValues.uuid(project);
        if (collection != null) com.ruoyi.medical.contracts.WireValues.uuid(collection);
        if (folder != null) com.ruoyi.medical.contracts.WireValues.uuid(folder);
        if (ownership != null && !Set.of("ENTERPRISE", "PROJECT", "SESSION").contains(ownership)) invalid();
        if (approval != null && !Set.of("PENDING", "APPROVED", "DISABLED").contains(approval)) invalid();
        if (lifecycle != null && !Set.of("ACTIVE", "RECYCLED", "PURGE_PENDING", "PURGED").contains(lifecycle)) invalid();
        if (bookmarked != null && !Set.of("true", "false").contains(bookmarked)) invalid();
        if (category != null) ProjectSchema.text(category, 32);
        if (tag != null) ProjectSchema.text(tag, 64);
        return new SourceGovernanceProtocol.CatalogueQuery(limit == null ? 50 : Integer.parseInt(limit), after,
                project, ownership, approval, lifecycle, bookmarked == null ? null : Boolean.valueOf(bookmarked),
                category, tag, folder, collection);
    }
    private static SourceGovernanceProtocol.ProcessingQueueQuery processingQueueQuery(HttpServletRequest request) {
        var allowed = Set.of("limit", "after", "project_id", "status");
        for (var key : request.getParameterMap().keySet())
            if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after");
        String project = request.getParameter("project_id"), status = request.getParameter("status");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) com.ruoyi.medical.contracts.WireValues.uuid(after);
        if (project != null) com.ruoyi.medical.contracts.WireValues.uuid(project);
        if (status != null && !Set.of("QUEUED", "PROCESSING", "COMPLETE", "PARTIAL_FAILED", "FAILED").contains(status)) invalid();
        return new SourceGovernanceProtocol.ProcessingQueueQuery(limit == null ? 50 : Integer.parseInt(limit),
                after, project, status);
    }
    private static SourceGovernanceProtocol.ArtifactCatalogueQuery artifactCatalogueQuery(HttpServletRequest request) {
        var allowed = Set.of("limit", "after", "project_id", "artifact_kind");
        for (var key : request.getParameterMap().keySet())
            if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after");
        String project = request.getParameter("project_id"), kind = request.getParameter("artifact_kind");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) com.ruoyi.medical.contracts.WireValues.uuid(after);
        if (project != null) com.ruoyi.medical.contracts.WireValues.uuid(project);
        if (kind != null && !kind.matches("[A-Z][A-Z0-9_]{0,31}")) invalid();
        return new SourceGovernanceProtocol.ArtifactCatalogueQuery(limit == null ? 50 : Integer.parseInt(limit),
                after, project, kind);
    }
    private static SourceGovernanceProtocol.ContentQuery contentQuery(HttpServletRequest request) {
        var allowed = Set.of("parse_version", "representation", "purpose", "start_byte", "end_byte",
                "chunk_id", "locator_sha256", "after_ordinal", "limit");
        for (var key : request.getParameterMap().keySet()) if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String parse = request.getParameter("parse_version"), representation = request.getParameter("representation");
        String purpose = request.getParameter("purpose"), start = request.getParameter("start_byte"), end = request.getParameter("end_byte");
        String chunk = request.getParameter("chunk_id"), locator = request.getParameter("locator_sha256");
        String after = request.getParameter("after_ordinal"), limit = request.getParameter("limit");
        if (parse == null || representation == null || purpose == null
                || !Set.of("ORIGINAL", "PARSED").contains(representation)
                || !purpose.matches("[A-Z][A-Z0-9_]{0,127}")) invalid();
        com.ruoyi.medical.contracts.WireValues.uuid(parse);
        if (chunk != null) com.ruoyi.medical.contracts.WireValues.uuid(chunk);
        if (locator != null) com.ruoyi.medical.contracts.WireValues.hash(locator);
        if ((chunk == null) != (locator == null)) invalid();
        if (start != null && !start.matches("(?:0|[1-9][0-9]{0,19})")
                || end != null && !end.matches("[1-9][0-9]{0,19}")
                || after != null && !after.matches("(?:0|[1-9][0-9]{0,19})")
                || limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        var startValue = start == null ? null : com.ruoyi.medical.contracts.WireValues.revision(start);
        var endValue = end == null ? null : com.ruoyi.medical.contracts.WireValues.revision(end);
        var afterValue = after == null ? null : com.ruoyi.medical.contracts.WireValues.revision(after);
        if ("ORIGINAL".equals(representation)) {
            if ((startValue == null) != (endValue == null) || chunk != null || afterValue != null || limit != null) invalid();
        } else if (startValue != null || endValue != null || chunk != null && afterValue != null || chunk != null && limit != null) invalid();
        return new SourceGovernanceProtocol.ContentQuery(parse, representation, purpose, startValue, endValue,
                chunk, locator, afterValue, limit == null ? 50 : Integer.parseInt(limit));
    }
    private static SourceGovernanceProtocol.UsageQuery usageQuery(HttpServletRequest request) {
        var allowed = Set.of("limit", "after", "project_id", "usage_kind", "dataset_watermark");
        for (var key : request.getParameterMap().keySet()) if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after");
        String project = request.getParameter("project_id"), kind = request.getParameter("usage_kind");
        String watermark = request.getParameter("dataset_watermark");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null && (!after.matches("[0-9a-f:-]{1,256}") || watermark == null)) invalid();
        if (project != null) com.ruoyi.medical.contracts.WireValues.uuid(project);
        if (kind != null && !Set.of("PROJECT_BINDING", "COLLECTION_MEMBER", "SCOPE_SNAPSHOT", "DOCUMENT_EVIDENCE",
                "LINEAGE_DEPENDENCY", "REFERENCE_HOLD").contains(kind)) invalid();
        if (watermark != null && !watermark.matches("(?:0|[1-9][0-9]{0,19})")) invalid();
        return new SourceGovernanceProtocol.UsageQuery(limit == null ? 50 : Integer.parseInt(limit), after,
                project, kind, watermark);
    }
    private static void noQuery(HttpServletRequest request) { if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
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
