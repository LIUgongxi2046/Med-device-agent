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
import org.springframework.web.bind.annotation.*;

@RestController
public final class ProjectController {
    private final ApplicationSessionIdentity identities;
    private final ProjectService projects;
    private final PermissionSchema schema;
    public ProjectController(ApplicationSessionIdentity identities, ProjectService projects, PermissionSchema schema) {
        this.identities = identities; this.projects = projects; this.schema = schema;
    }
    @GetMapping("/api/projects")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = query(request, false);
        return response(response, projects.list(identities.requireCurrent(request, authentication), context, query));
    }
    @GetMapping("/api/projects/{projectId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String projectId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); noQuery(request); id(projectId);
        return response(response, projects.read(identities.requireCurrent(request, authentication), context, projectId));
    }
    @PostMapping(value="/api/projects", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); noQuery(request);
        return response(response, projects.create(identities.requireCurrent(request, authentication), context, key, body));
    }
    @PostMapping(value="/api/projects/{projectId}/profile-revisions", consumes="application/json")
    public JsonNode revise(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String projectId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); noQuery(request); id(projectId);
        return response(response, projects.revise(identities.requireCurrent(request, authentication), context, key, projectId, body));
    }
    @PostMapping(value="/api/projects/{projectId}/state-decisions", consumes="application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String projectId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); noQuery(request); id(projectId);
        return response(response, projects.decide(identities.requireCurrent(request, authentication), context, key, projectId, body));
    }
    @GetMapping("/api/work-items")
    public JsonNode waiting(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = query(request, true);
        return response(response, projects.waiting(identities.requireCurrent(request, authentication), context, query));
    }
    private static ProjectProtocol.Query query(HttpServletRequest request, boolean waiting) {
        var allowed = waiting ? Set.of("limit", "after", "project_id", "assignee_id", "status") : Set.of("limit", "after", "name", "responsible_user_id", "status");
        for (var key : request.getParameterMap().keySet()) if (!allowed.contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after"), name = request.getParameter("name"), status = request.getParameter("status");
        String responsible = request.getParameter(waiting ? "assignee_id" : "responsible_user_id"), project = request.getParameter("project_id");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) id(after); if (responsible != null) id(responsible); if (project != null) id(project);
        if (name != null) ProjectSchema.text(name, 256);
        if (status != null && !(waiting ? Set.of("WAITING_INPUT", "RESOLVED", "EXPIRED", "CANCELLED", "STALE") : Set.of("PENDING", "ACTIVE", "PAUSED", "ARCHIVED")).contains(status)) invalid();
        return new ProjectProtocol.Query(limit == null ? 50 : Integer.parseInt(limit), after, name, status, responsible, project);
    }
    private static void headers(String version, String context) { if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); PermissionSchema.revision(context); }
    private static void noQuery(HttpServletRequest request) { if (request.getQueryString() != null) invalid(); }
    private static void id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) invalid();
        com.ruoyi.medical.contracts.WireValues.uuid(value);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
