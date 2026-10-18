package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Fixed-version review reads and whole-document RETURN. No confirmation or signing route. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.review-return",name="enabled",havingValue="true",matchIfMissing=false)
public final class DocumentReviewReturnController {
    private final ApplicationSessionIdentity identities;
    private final DocumentReviewReturnService service;
    private final PermissionSchema json;
    public DocumentReviewReturnController(ApplicationSessionIdentity identities, DocumentReviewReturnService service, PermissionSchema json) {
        this.identities=identities; this.service=service; this.json=json;
    }
    @GetMapping("/api/documents/{id}/revision-return-basis")
    public JsonNode basis(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); noBody(request);
        return response(response,service.basis(identities.requireCurrent(request,authentication),headers.context(),id,headers.revision()));
    }
    @GetMapping("/api/documents/{id}/revision-return-history")
    public JsonNode history(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); noBody(request);
        return response(response,service.history(identities.requireCurrent(request,authentication),headers.context(),id,headers.revision()));
    }
    @GetMapping("/api/documents/{id}/revision-return-assignees")
    public JsonNode assignees(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); noBody(request);
        return response(response,service.assignees(identities.requireCurrent(request,authentication),headers.context(),id,headers.revision()));
    }
    @GetMapping("/api/documents/{id}/revision-return-assignments")
    public JsonNode assignments(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); noBody(request);
        return response(response,service.assignments(identities.requireCurrent(request,authentication),headers.context(),id,headers.revision()));
    }
    @GetMapping("/api/documents/{id}/revision-return-operations")
    public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); noBody(request); String key=oneHeader(request,"Idempotency-Key"); json.headers(headers.context(),key);
        return response(response,service.recover(identities.requireCurrent(request,authentication),headers.context(),key,id,headers.revision()));
    }
    @PostMapping(value="/api/documents/{id}/revision-returns",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var headers=headers(request,id); String key=oneHeader(request,"Idempotency-Key"); json.headers(headers.context(),key);
        var subject=identities.requireCurrent(request,authentication);
        if(request.getContentLengthLong()>262144) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(262145);
        if(bytes.length>262144) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        // The fixed document header must agree with the request baseline before any service write.
        var input=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
        if(input==null || !headers.revision().equals(input.path("document_revision").asText())) invalid();
        return response(response,service.decide(subject,headers.context(),key,id,bytes));
    }
    private Headers headers(HttpServletRequest request,String id) {
        json.requireId(id); if(request.getQueryString()!=null) invalid();
        if(!"1.0.0".equals(oneHeader(request,"X-Schema-Version"))) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=oneHeader(request,"X-Context-Revision"), revision=oneHeader(request,"X-Document-Revision");
        PermissionSchema.revision(context); json.requireId(revision); return new Headers(context,revision);
    }
    private static String oneHeader(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name)); if(values.size()!=1 || values.get(0)==null || values.get(0).isBlank()) invalid();
        return values.get(0);
    }
    private static void noBody(HttpServletRequest request) throws IOException { if(request.getInputStream().read()!=-1) invalid(); }
    private record Headers(String context,String revision) { }
    private static JsonNode response(HttpServletResponse response,JsonNode result) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Request-Id",result.required("request_id").asText()); response.setHeader("X-Content-Type-Options","nosniff"); return result;
    }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure, HttpServletResponse response) throws IOException {
        // Unwrap without logging request text, SQL, object locations, or provider exception messages.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SchemaRejection rejection) { write(response, 422, rejection.code()); return; }
            if (cause instanceof TenantAccessException denial) {
                int status = switch (denial.code()) {
                    case ACCESS_DENIED -> 403;
                    case OBJECT_UNAVAILABLE, SOURCE_MISSING -> 404;
                    case FILE_TOO_LARGE -> 413;
                    case RATE_LIMITED -> 429;
                    case SCHEMA_INVALID, SCHEMA_UNSUPPORTED, CONTENT_MISMATCH, DEPENDENCY_UNAVAILABLE, CONTEXT_LIMIT -> 422;
                    default -> 409;
                };
                write(response, status, denial.code()); return;
            }
            if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException
                    || cause instanceof org.springframework.http.converter.HttpMessageNotReadableException) {
                write(response, 422, ErrorCode.SCHEMA_INVALID); return;
            }
            if (cause instanceof org.springframework.security.core.AuthenticationException) {
                write(response, 401, ErrorCode.AUTHENTICATION_REQUIRED); return;
            }
            if (cause instanceof org.springframework.security.access.AccessDeniedException) {
                write(response, 403, ErrorCode.ACCESS_DENIED); return;
            }
        }
        write(response, 500, ErrorCode.INTERNAL_ERROR);
    }
    private static void write(HttpServletResponse response, int status, ErrorCode code) throws IOException {
        new IdentityErrorResponseWriter().write(response, status, code);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
