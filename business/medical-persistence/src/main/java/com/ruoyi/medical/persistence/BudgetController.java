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
public final class BudgetController {
    private final ApplicationSessionIdentity identities;
    private final BudgetService budgets;
    private final PermissionSchema schema;
    public BudgetController(ApplicationSessionIdentity identities, BudgetService budgets, PermissionSchema schema) {
        this.identities = identities; this.budgets = budgets; this.schema = schema;
    }
    @GetMapping("/api/budgets")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = query(request);
        return response(response, budgets.list(identities.requireCurrent(request, authentication), context, query));
    }
    @GetMapping("/api/budgets/{budgetId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String budgetId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); noQuery(request); id(budgetId);
        return response(response, budgets.read(identities.requireCurrent(request, authentication), context, budgetId));
    }
    @GetMapping("/api/budgets/{budgetId}/revisions")
    public JsonNode history(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String budgetId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); id(budgetId);
        for (String key : request.getParameterMap().keySet()) if (!Set.of("limit", "after").contains(key)) invalid();
        return response(response, budgets.history(identities.requireCurrent(request, authentication), context, budgetId, query(request)));
    }
    @GetMapping("/api/budgets/{budgetId}/revisions/{budgetRevision}")
    public JsonNode historyRevision(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String budgetId, @PathVariable String budgetRevision,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); noQuery(request); id(budgetId); id(budgetRevision);
        return response(response, budgets.historyRevision(identities.requireCurrent(request, authentication), context, budgetId, budgetRevision));
    }
    @PostMapping(value="/api/budgets", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); noQuery(request);
        return response(response, budgets.create(identities.requireCurrent(request, authentication), context, key, body));
    }
    @PostMapping(value="/api/budgets/{budgetId}/decisions", consumes="application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String budgetId, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        headers(version, context); schema.headers(context, key); noQuery(request); id(budgetId);
        return response(response, budgets.decide(identities.requireCurrent(request, authentication), context, key, budgetId, body));
    }
    private static BudgetProtocol.Query query(HttpServletRequest request) {
        for (String key : request.getParameterMap().keySet()) if (!Set.of("limit", "after", "project_id").contains(key) || request.getParameterValues(key).length != 1) invalid();
        String limit = request.getParameter("limit"), after = request.getParameter("after"), project = request.getParameter("project_id");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (after != null) id(after); if (project != null) id(project);
        return new BudgetProtocol.Query(limit == null ? 20 : Integer.parseInt(limit), after, project);
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
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403
                : failure.code() == ErrorCode.BUDGET_EXCEEDED ? 422 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
