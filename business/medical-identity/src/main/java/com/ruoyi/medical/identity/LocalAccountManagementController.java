package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.LocalAccountProtocol.Action;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class LocalAccountManagementController {
    private final ApplicationSessionIdentity identities;
    private final LocalAccountManagement accounts;
    private final LocalAccountSchema schema;
    public LocalAccountManagementController(ApplicationSessionIdentity identities, LocalAccountManagement accounts, LocalAccountSchema schema) {
        this.identities = identities; this.accounts = accounts; this.schema = schema;
    }
    @GetMapping("/api/system/user/profile")
    com.ruoyi.common.core.domain.AjaxResult ownProfile(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication, @RequestHeader(value="X-Schema-Version", required=false) String version) {
        version(version);
        if (request.getQueryString() != null) invalid();
        var result = accounts.ownProfile(identities.requireCurrent(request, authentication));
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        return result;
    }
    @PostMapping(value="/api/local-account/profile-revisions", consumes="application/json")
    JsonNode ownProfileRevision(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestBody byte[] body, @RequestHeader(value="X-Schema-Version", required=false) String version,
            @RequestHeader(value="Idempotency-Key", required=false) String key) throws IOException, GeneralSecurityException {
        var subject = identities.requireCurrent(request, authentication);
        return change(Action.OWN_PROFILE, subject.user().user_id(), request, response, authentication, body, version, key);
    }
    @GetMapping("/api/local-accounts")
    JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version", required=false) String version) {
        version(version);
        if (!Set.of("limit", "cursor").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) invalid();
        String limit = request.getParameter("limit");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        String cursor = request.getParameter("cursor");
        if (cursor != null) WireValues.uuid(cursor);
        var result = accounts.list(identities.requireCurrent(request, authentication), limit == null ? 50 : Integer.parseInt(limit), cursor);
        headers(response, result); return result;
    }
    @PostMapping(value="/api/local-accounts", consumes="application/json")
    JsonNode create(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version, @RequestHeader(value="Idempotency-Key", required=false) String key)
            throws IOException, GeneralSecurityException {
        return change(Action.CREATE, null, request, response, authentication, body, version, key);
    }
    @PostMapping(value="/api/local-accounts/{id}/state-decisions", consumes="application/json")
    JsonNode state(@PathVariable String id, HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version, @RequestHeader(value="Idempotency-Key", required=false) String key)
            throws IOException, GeneralSecurityException {
        return change(Action.STATE, id, request, response, authentication, body, version, key);
    }
    @PostMapping(value="/api/local-accounts/{id}/profile-revisions", consumes="application/json")
    JsonNode profile(@PathVariable String id, HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version, @RequestHeader(value="Idempotency-Key", required=false) String key)
            throws IOException, GeneralSecurityException {
        return change(Action.PROFILE, id, request, response, authentication, body, version, key);
    }
    @PostMapping(value="/api/local-accounts/{id}/credential-revisions", consumes="application/json")
    JsonNode reset(@PathVariable String id, HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version, @RequestHeader(value="Idempotency-Key", required=false) String key)
            throws IOException, GeneralSecurityException {
        return change(Action.RESET, id, request, response, authentication, body, version, key);
    }
    @PostMapping(value="/api/local-account/credential-revisions", consumes="application/json")
    JsonNode credential(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version", required=false) String version, @RequestHeader(value="Idempotency-Key", required=false) String key)
            throws IOException, GeneralSecurityException {
        var subject = identities.requireCurrent(request, authentication);
        return change(Action.CREDENTIAL, subject.user().user_id(), request, response, authentication, body, version, key);
    }
    private JsonNode change(Action action, String id, HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            byte[] body, String version, String key) throws IOException, GeneralSecurityException {
        version(version);
        if (request.getQueryString() != null) invalid();
        if (id != null) WireValues.uuid(id);
        var subject = identities.requireCurrent(request, authentication);
        var result = accounts.mutate(action, request.getRequestURI(), id, subject, schema.request(action, body, key), key);
        response.setStatus(result.status()); headers(response, result.body()); return result.body();
    }
    private static void headers(HttpServletResponse response, JsonNode result) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", result.required("request_id").textValue());
    }
    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void rejected(SchemaRejection failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, failure.code());
    }
    @ExceptionHandler(LocalAccountConflict.class)
    void conflict(LocalAccountConflict failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.AUTHENTICATION_FAILED ? 401 : 409, failure.code());
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, IllegalArgumentException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, 422, ErrorCode.SCHEMA_INVALID);
    }
}
