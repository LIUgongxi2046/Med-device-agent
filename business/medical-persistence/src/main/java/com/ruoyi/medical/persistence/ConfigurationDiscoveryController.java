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
public final class ConfigurationDiscoveryController {
    private final ApplicationSessionIdentity identities;
    private final ConfigurationDiscoveryService listings;
    private final ConfigurationDiscoverySchema schema;
    public ConfigurationDiscoveryController(ApplicationSessionIdentity identities, ConfigurationDiscoveryService listings, ConfigurationDiscoverySchema schema) {
        this.identities = identities; this.listings = listings; this.schema = schema;
    }
    @GetMapping("/api/model-config-revisions")
    public JsonNode models(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = schema.query(request.getParameterMap());
        return response(response, listings.models(identities.requireCurrent(request, authentication), context, query));
    }
    @GetMapping("/api/simulation-model-config-revisions")
    public JsonNode simulationModels(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = schema.query(request.getParameterMap());
        return response(response, listings.simulationModels(identities.requireCurrent(request, authentication), context, query));
    }
    @GetMapping("/api/rule-versions")
    public JsonNode rules(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(version, context); var query = schema.query(request.getParameterMap());
        return response(response, listings.rules(identities.requireCurrent(request, authentication), context, query));
    }
    private static void headers(String version, String context) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); PermissionSchema.revision(context);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.DEPENDENCY_UNAVAILABLE ? 503 : RuleVersionController.errorStatus(failure.code()), failure.code());
    }
}
