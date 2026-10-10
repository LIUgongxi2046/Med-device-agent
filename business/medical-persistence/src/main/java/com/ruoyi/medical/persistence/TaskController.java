package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class TaskController {
    private final ApplicationSessionIdentity identities;
    private final TaskService tasks;
    private final TaskListSchema listing;
    private final TaskPlanService plans;
    private final TaskPlanReturnService decisions;
    private final TaskCreateService creation;
    private final TaskCreateDraftService drafts;
    private final TaskCreationSelectionService selection;
    private final TaskCreationSelectionSchema selectionSchema;
    private final TaskScopeSelectionService scopeSelection;
    private final TaskScopeSelectionSchema scopeSelectionSchema;

    public TaskController(ApplicationSessionIdentity identities, TaskService tasks, TaskListSchema listing, TaskPlanService plans,
            TaskPlanReturnService decisions, TaskCreateService creation, TaskCreateDraftService drafts,
            TaskCreationSelectionService selection, TaskCreationSelectionSchema selectionSchema,
            TaskScopeSelectionService scopeSelection, TaskScopeSelectionSchema scopeSelectionSchema) {
        this.identities = identities; this.tasks = tasks; this.listing = listing; this.plans = plans;
        this.decisions = decisions;
        this.creation = creation;
        this.drafts = drafts;
        this.selection = selection; this.selectionSchema = selectionSchema;
        this.scopeSelection = scopeSelection; this.scopeSelectionSchema = scopeSelectionSchema;
    }

    @GetMapping("/api/projects/{projectId}/task-creation-owners")
    public JsonNode creationOwners(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        var query = selectionSchema.query(request.getParameterMap());
        return decisionResponse(response, selection.owners(identities.requireCurrent(request, authentication), context, projectId, query));
    }

    @GetMapping({"/api/projects/{projectId}/task-creation-sources", "/api/projects/{projectId}/task-creation-collections"})
    public JsonNode creationScope(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        var query = scopeSelectionSchema.query(request.getParameterMap());
        String kind = request.getServletPath().endsWith("/task-creation-sources") ? "SOURCE" : "COLLECTION";
        return decisionResponse(response, scopeSelection.choices(identities.requireCurrent(request, authentication), context, projectId, kind, query));
    }

    @PostMapping(value="/api/projects/{projectId}/task-create-drafts", consumes="application/json")
    public JsonNode saveCreationDraft(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) throws IOException {
        decisionHeaders(request, version);
        return draftResponse(response, drafts.save(identities.requireCurrent(request, authentication), context, key, projectId, body));
    }

    @GetMapping("/api/projects/{projectId}/task-create-drafts")
    public JsonNode originalCreationDraft(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        decisionHeaders(request, version);
        return draftResponse(response, drafts.original(identities.requireCurrent(request, authentication), context, key, projectId));
    }

    private static JsonNode draftResponse(HttpServletResponse response, TaskCreateDraftService.Reply reply) {
        response.setStatus(reply.httpStatus()); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", reply.envelope().required("request_id").textValue()); return reply.envelope();
    }

    @PostMapping(value="/api/tasks", consumes="application/json")
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) {
        decisionHeaders(request, version);
        return creationResponse(response, creation.create(identities.requireCurrent(request, authentication), context, key, body));
    }

    @GetMapping("/api/projects/{projectId}/task-create-operations")
    public JsonNode originalCreation(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        decisionHeaders(request, version);
        return creationResponse(response, creation.original(identities.requireCurrent(request, authentication), context, key, projectId));
    }

    private static JsonNode creationResponse(HttpServletResponse response, TaskCreateService.Reply reply) {
        response.setStatus(reply.httpStatus()); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", reply.envelope().required("request_id").textValue());
        if (reply.taskId() != null) response.setHeader("Location", "/api/tasks/" + reply.taskId()); return reply.envelope();
    }

    @PostMapping(value="/api/tasks/{taskId}/plan-decisions", consumes="application/json")
    public JsonNode decidePlan(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) {
        decisionHeaders(request, version); WireValues.uuid(taskId);
        return decisionResponse(response, decisions.decide(identities.requireCurrent(request, authentication), context, key, taskId, body));
    }

    @GetMapping("/api/tasks/{taskId}/plan-decision-basis")
    public JsonNode planDecisionBasis(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="X-Plan-Decision", required=false) String decision) {
        decisionHeaders(request, version); WireValues.uuid(taskId);
        return decisionResponse(response, decisions.basis(identities.requireCurrent(request, authentication), context, taskId, decision == null ? "RETURN" : decision));
    }

    @GetMapping("/api/tasks/{taskId}/plan-decision-operations")
    public JsonNode originalPlanDecision(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        decisionHeaders(request, version); WireValues.uuid(taskId);
        return decisionResponse(response, decisions.original(identities.requireCurrent(request, authentication), context, key, taskId));
    }

    private static void decisionHeaders(HttpServletRequest request, String version) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static JsonNode decisionResponse(HttpServletResponse response, JsonNode result) {
        response.setStatus(200); response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @PostMapping(value="/api/tasks/{taskId}/plan-revisions", consumes="application/json")
    public JsonNode savePlan(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key, @RequestBody byte[] body) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        WireValues.uuid(taskId);
        var result = plans.save(identities.requireCurrent(request, authentication), context, key, taskId, body);
        return planResponse(response, taskId, result);
    }

    @GetMapping("/api/tasks/{taskId}/plan-revisions/{planRevision}")
    public JsonNode readPlan(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @PathVariable String planRevision,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        WireValues.uuid(taskId); WireValues.uuid(planRevision);
        return planResponse(response, taskId, plans.read(identities.requireCurrent(request, authentication), context, taskId, planRevision));
    }

    @GetMapping("/api/tasks/{taskId}/plan-revisions")
    public JsonNode currentPlan(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return planResponse(response, taskId, plans.currentPlan(identities.requireCurrent(request, authentication), context, taskId));
    }

    @GetMapping("/api/tasks/{taskId}/plan-operations")
    public JsonNode savedPlanOperation(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return planResponse(response, taskId, plans.savedOperation(identities.requireCurrent(request, authentication), context, key, taskId));
    }

    @GetMapping("/api/tasks/{taskId}/plan-edit-basis")
    public JsonNode planEditBasis(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = plans.editBasis(identities.requireCurrent(request, authentication), context, taskId);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    private static JsonNode planResponse(HttpServletResponse response, String taskId, TaskPlanService.Reply reply) {
        response.setStatus(reply.httpStatus()); response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Schema-Version", "1.0.0"); response.setHeader("X-Request-Id", reply.envelope().required("request_id").textValue());
        if (reply.planRevision() != null) response.setHeader("Location", "/api/tasks/" + taskId + "/plan-revisions/" + reply.planRevision());
        return reply.envelope();
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String projectId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context); CollectionSchema.id(projectId);
        var query = listing.query(request.getParameterMap());
        var result = tasks.list(identities.requireCurrent(request, authentication), context, projectId, query);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @GetMapping("/api/tasks/{taskId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String taskId, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        if (request.getQueryString() != null || !taskId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
        WireValues.uuid(taskId);
        var result = tasks.read(identities.requireCurrent(request, authentication), context, taskId);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = switch (failure.code()) {
            case ACCESS_DENIED -> 403;
            case SOURCE_NOT_READY, RIGHTS_PENDING, CONDITION_UNKNOWN, PLAN_UNCONFIRMED, PLAN_CYCLE,
                    LINEAGE_CYCLE, LINEAGE_REVIEW_REQUIRED, CONTEXT_LIMIT, BUDGET_EXCEEDED, DEPENDENCY_UNAVAILABLE -> 422;
            default -> 409;
        };
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }

    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }

    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    void invalidTimestamp(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }

    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)
    void invalidJson(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
