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
public final class ModelConfigurationController {
    private final ApplicationSessionIdentity identities;
    private final ModelConfigurationService configurations;
    private final PermissionSchema schema;
    public ModelConfigurationController(ApplicationSessionIdentity identities, ModelConfigurationService configurations, PermissionSchema schema) {
        this.identities = identities; this.configurations = configurations; this.schema = schema;
    }
    @PostMapping(value="/api/model-config-revisions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode save(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        headers(request, version, context); singleHeader(request, "Idempotency-Key", key); schema.headers(context, key);
        var subject = identities.requireCurrent(request, authentication);
        return response(response, configurations.save(subject, context, key, boundedBody(request)));
    }
    @GetMapping("/api/model-config-revisions/{revisionId}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String revisionId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(request, version, context);
        if (!revisionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        com.ruoyi.medical.contracts.WireValues.uuid(revisionId);
        return response(response, configurations.read(identities.requireCurrent(request, authentication), context, revisionId));
    }
    @PostMapping(value="/api/simulation-model-config-revisions", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode saveSimulation(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        headers(request, version, context); singleHeader(request, "Idempotency-Key", key); schema.headers(context, key);
        var subject = identities.requireCurrent(request, authentication);
        return response(response, configurations.saveSimulation(subject, context, key, boundedBody(request)));
    }
    @GetMapping("/api/simulation-model-config-revisions/{revisionId}")
    public JsonNode readSimulation(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @PathVariable String revisionId,
            @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        headers(request, version, context);
        if (!revisionId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        com.ruoyi.medical.contracts.WireValues.uuid(revisionId);
        return response(response, configurations.readSimulation(identities.requireCurrent(request, authentication), context, revisionId));
    }
    private static byte[] boundedBody(HttpServletRequest request) throws IOException {
        // Invoked only after session verification. MVC must never buffer an unbounded @RequestBody first.
        if (request.getContentLengthLong() > ModelConfigurationSchema.MAXIMUM_REQUEST_BYTES)
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes = request.getInputStream().readNBytes(ModelConfigurationSchema.MAXIMUM_REQUEST_BYTES + 1);
        if (bytes.length > ModelConfigurationSchema.MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return bytes;
    }
    private static void singleHeader(HttpServletRequest request, String name, String value) {
        var values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements() || !java.util.Objects.equals(values.nextElement(), value) || values.hasMoreElements())
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static void headers(HttpServletRequest request, String version, String context) {
        singleHeader(request, "X-Schema-Version", version); singleHeader(request, "X-Context-Revision", context);
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : failure.code() == ErrorCode.FILE_TOO_LARGE ? 413 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
}
