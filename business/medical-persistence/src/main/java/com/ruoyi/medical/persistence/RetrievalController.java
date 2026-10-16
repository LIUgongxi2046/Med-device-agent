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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RetrievalController {
    private final ApplicationSessionIdentity identities;
    private final RetrievalService retrievals;
    private final RetrievalSchema schema;
    private final PermissionSchema headers;

    public RetrievalController(ApplicationSessionIdentity identities, RetrievalService retrievals,
            RetrievalSchema schema, PermissionSchema headers) {
        this.identities = identities; this.retrievals = retrievals; this.schema = schema; this.headers = headers;
    }

    @PostMapping(value="/api/retrievals", consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        version(version); headers.headers(context, key); noQuery(request);
        return response(response, retrievals.create(identities.requireCurrent(request, authentication), context, key, schema.input(body)));
    }

    @GetMapping("/api/retrievals/{id}")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @PathVariable String id, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context) {
        version(version); PermissionSchema.revision(context); CollectionSchema.id(id);
        return response(response, retrievals.read(identities.requireCurrent(request, authentication), context, id,
                schema.page(request.getParameterMap())));
    }

    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void noQuery(HttpServletRequest request) { if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static JsonNode response(HttpServletResponse response, JsonNode value) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", value.required("request_id").textValue()); return value;
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure, HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response, 422, failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, TemplateController.errorStatus(failure.code()), failure.code());
    }
}
