package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationReviewController {
    private final ApplicationSessionIdentity identities;
    private final EnterpriseRegistrationReviewService reviews;
    public EnterpriseRegistrationReviewController(ApplicationSessionIdentity identities, EnterpriseRegistrationReviewService reviews) { this.identities=identities; this.reviews=reviews; }
    @GetMapping("/api/enterprise-registration-reviews")
    JsonNode list(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false) String version) {
        version(version);
        if (!Set.of("limit","cursor","status").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)) invalid();
        String limit=request.getParameter("limit"), cursor=request.getParameter("cursor"), state=request.getParameter("status");
        if (limit != null && !limit.matches("(?:[1-9]|[1-9][0-9]|100)")) invalid();
        if (cursor != null) WireValues.uuid(cursor);
        if (state != null && !Set.of("PENDING_REVIEW","APPROVED","REJECTED").contains(state)) invalid();
        JsonNode result=reviews.list(identities.requireCurrent(request,authentication),limit == null ? 50 : Integer.parseInt(limit),cursor,state);
        headers(response,result); return result;
    }
    @GetMapping("/api/enterprise-registration-reviews/{id}")
    JsonNode detail(@PathVariable String id,HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false) String version) {
        version(version); if (request.getQueryString()!=null) invalid(); WireValues.uuid(id);
        JsonNode result=reviews.detail(identities.requireCurrent(request,authentication),id); headers(response,result); return result;
    }
    @PostMapping(value="/api/enterprise-registration-reviews/{id}/decisions",consumes="application/json")
    JsonNode decide(@PathVariable String id,HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false) String version,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        version(version); if (request.getQueryString()!=null) invalid(); WireValues.uuid(id);
        JsonNode result=reviews.decide(identities.requireCurrent(request,authentication),id,body,key); headers(response,result); return result;
    }
    private static void headers(HttpServletResponse response,JsonNode result) { response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0"); response.setHeader("X-Request-Id",result.required("request_id").textValue()); }
    private static void version(String value) { if (!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void rejected(SchemaRejection failure,HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,failure.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void conflict(TenantAccessException failure,HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.ACCESS_DENIED ? 403 : 409,failure.code()); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    void invalidBody(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID); }
}
