package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.simulation-rule",name="enabled",havingValue="true")
public final class SimulationRuleVersionController {
    private final ApplicationSessionIdentity identities;
    private final SimulationRuleVersionService rules;
    private final PermissionSchema json;
    private final SimulationRuleVersionApprovalService approvals;

    public SimulationRuleVersionController(ApplicationSessionIdentity identities, SimulationRuleVersionService rules, PermissionSchema json, SimulationRuleVersionApprovalService approvals) {
        this.identities = identities; this.rules = rules; this.json = json; this.approvals = approvals;
    }

    @PostMapping(value="/api/simulation-rule-versions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = rules.create(identities.requireCurrent(request, authentication), context, key, body);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @PostMapping(value="/api/simulation-rule-versions/{id}/approval-decisions", consumes="application/json")
    public JsonNode decide(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = approvals.decide(identities.requireCurrent(request, authentication), context, key, id, body);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @GetMapping("/api/simulation-rule-versions/{id}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = rules.read(identities.requireCurrent(request, authentication), context, id);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @GetMapping("/api/simulation-rule-versions/{id}/approval-baseline")
    public JsonNode baseline(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var result = approvals.baseline(identities.requireCurrent(request, authentication), context, id);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }

    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, errorStatus(failure.code()), failure.code());
    }

    static int errorStatus(ErrorCode code) {
        return switch (code) {
            case ACCESS_DENIED -> 403;
            case OBJECT_UNAVAILABLE, SOURCE_MISSING -> 404;
            case FILE_TOO_LARGE -> 413;
            case RATE_LIMITED -> 429;
            case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, SOURCE_NOT_READY, RIGHTS_PENDING, CONTEXT_LIMIT,
                    CONTENT_MISMATCH, RETENTION_UNCONFIGURED, LOCATOR_INVALID, CURSOR_INVALID -> 422;
            case CONTEXT_CHANGED, SCOPE_STALE, IDEMPOTENCY_CONFLICT, REVISION_CONFLICT, FOLDER_NOT_EMPTY,
                    ACTION_STALE, REFERENCE_HELD -> 409;
            default -> throw new IllegalArgumentException("RULE_ERROR_CODE_INVALID");
        };
    }
}
