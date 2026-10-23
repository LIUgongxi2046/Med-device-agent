package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.identity.BusinessSessionSchema;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import com.ruoyi.medical.identity.TenantSelectionOptionsSchema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class BusinessSessionController {
    private final ApplicationSessionIdentity identities;
    private final BusinessSessionDirectory directory;
    private final BusinessSessionSchema schema;
    private final TenantSelectionService selections;
    private final IdentityBindingSchema bindings;
    private final TenantSelectionOptionsSchema optionsSchema;
    private final AccountWorkspaceSelection workspace;
    public BusinessSessionController(ApplicationSessionIdentity identities, BusinessSessionDirectory directory,
            BusinessSessionSchema schema, TenantSelectionService selections, IdentityBindingSchema bindings,
            TenantSelectionOptionsSchema optionsSchema, AccountWorkspaceSelection workspace) {
        this.identities = identities; this.directory = directory; this.schema = schema; this.selections = selections; this.bindings = bindings;
        this.optionsSchema = optionsSchema;
        this.workspace = workspace;
    }

    @GetMapping("/api/session/tenant-selection-options")
    public JsonNode selectionOptions(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var identity = identities.requireCurrent(request, authentication);
        var state = directory.selectionOptions(identity);
        String requestId = UUID.randomUUID().toString();
        JsonNode result = optionsSchema.envelope(state.options(), requestId);
        directory.requireOptionsUnchanged(identity, state);
        identities.requireCurrent(request, authentication);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        return result;
    }

    @GetMapping("/api/session/identity-binding")
    public JsonNode identityBinding(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version) {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var identity = identities.requireCurrent(request, authentication);
        String requestId = UUID.randomUUID().toString();
        JsonNode result = bindings.envelope(identity.binding(), requestId);
        var current = identities.requireCurrent(request, authentication);
        if (!identity.sessionId().equals(current.sessionId()) || !identity.binding().matches(current.binding())) {
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        }
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        return result;
    }

    @PostMapping(value = "/api/session/tenant-selections", consumes = "application/json")
    public JsonNode select(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="X-Context-Revision", required=false) String context,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if (request.getQueryString() != null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var identity = identities.requireCurrent(request, authentication);
        var selection = schema.selection(body, context, key);
        var result = selections.select(identity, selection, context, key);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue());
        return result;
    }

    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }

    @GetMapping("/api/session")
    public JsonNode session(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value = "X-Schema-Version", required = false) String version) throws IOException {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        var identity = identities.requireCurrent(request, authentication);
        workspace.initialize(identity);
        var state = directory.read(identity);
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String requestId = UUID.randomUUID().toString();
        var result = schema.envelope(new BusinessSessionProtocol.Session("1.0.0", identity.user(), state.memberships(), state.context(),
                new BusinessSessionProtocol.Csrf(csrf.getHeaderName(), csrf.getToken()), state.actions(),
                new BusinessSessionProtocol.InvitationEntry("POST", "/api/member-invitations/{id}/acceptances")), requestId);
        directory.requireUnchanged(identity, state);
        identities.requireCurrent(request, authentication);
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        return result;
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void schemaRejected(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
}
