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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class MemberGovernanceReadController {
    private final ApplicationSessionIdentity identities;
    private final MemberGovernanceReadService service;
    private final MemberGovernanceReadSchema schema;
    public MemberGovernanceReadController(ApplicationSessionIdentity identities, MemberGovernanceReadService service, MemberGovernanceReadSchema schema) {
        this.identities = identities; this.service = service; this.schema = schema;
    }
    @GetMapping({"/api/enterprise-members", "/api/permission-grants", "/api/member-invitations"})
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
        @RequestHeader(value="X-Schema-Version", required=false) String version,
        @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        String kind = switch (request.getServletPath()) {
            case "/api/enterprise-members" -> "Members";
            case "/api/permission-grants" -> "Grants";
            case "/api/member-invitations" -> "Invitations";
            default -> throw new IllegalArgumentException("MEMBER_READ_PATH_INVALID");
        };
        JsonNode result = service.list(identities.requireCurrent(request, authentication), context, kind, schema.query(request.getParameterMap()));
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = failure.code() == ErrorCode.ACCESS_DENIED ? 403 : failure.code() == ErrorCode.CONTEXT_LIMIT ? 422 : 409;
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
}
