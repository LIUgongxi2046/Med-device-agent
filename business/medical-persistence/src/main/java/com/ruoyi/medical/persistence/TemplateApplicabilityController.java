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
public final class TemplateApplicabilityController {
    private final ApplicationSessionIdentity identities;
    private final TemplateApplicabilityService service;
    private final TemplateApplicabilitySchema schema;
    public TemplateApplicabilityController(ApplicationSessionIdentity identities, TemplateApplicabilityService service, TemplateApplicabilitySchema schema) {
        this.identities = identities; this.service = service; this.schema = schema;
    }
    @GetMapping("/api/projects/{id}/applicable-templates")
    public JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String id,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context); CollectionSchema.id(id);
        var result = service.list(identities.requireCurrent(request, authentication), context, id, schema.query(request.getParameterMap()));
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue()); return result;
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        int status = failure.code() == ErrorCode.RESULT_VERSION_MISMATCH ? 409 : TemplateController.errorStatus(failure.code());
        new IdentityErrorResponseWriter().write(response, status, failure.code());
    }
}
