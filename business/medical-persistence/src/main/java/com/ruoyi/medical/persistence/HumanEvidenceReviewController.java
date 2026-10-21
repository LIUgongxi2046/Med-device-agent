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

/** Versioned inline history, original operation recovery and explicit human evidence decisions. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public final class HumanEvidenceReviewController {
    private final ApplicationSessionIdentity identities;
    private final HumanEvidenceReviewService service;
    private final PermissionSchema json;
    public HumanEvidenceReviewController(ApplicationSessionIdentity identities, HumanEvidenceReviewService service, PermissionSchema json) {
        this.identities=identities; this.service=service; this.json=json;
    }
    @PostMapping(value="/api/documents/{id}/evidence-review-decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var h=headers(request,id,"1.0.0",false);String key=oneHeader(request,"Idempotency-Key");json.headers(h.context(),key);
        if(request.getContentLengthLong()>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var bytes=request.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return response(response,service.decide(identities.requireCurrent(request,authentication),h.context(),key,id,bytes));
    }
    @GetMapping("/api/documents/{id}/evidence-review-decisions")
    public JsonNode history(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var h=headers(request,id,"1.1.0",true);noBody(request);
        var names=request.getParameterMap().keySet();if(!java.util.Set.of("page","page_size","watermark").containsAll(names))invalid();
        for(var name:names)if(request.getParameterValues(name).length!=1)invalid();
        int page=number(request.getParameter("page"),1),size=number(request.getParameter("page_size"),50);String watermark=request.getParameter("watermark");if(watermark!=null)PermissionSchema.revision(watermark);
        return response(response,service.history(identities.requireCurrent(request,authentication),h.context(),id,h.revision(),page,size,watermark));
    }
    @GetMapping("/api/documents/{id}/evidence-review-operations")
    public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id) throws Exception {
        var h=headers(request,id,"1.0.0",true);if(request.getQueryString()!=null)invalid();noBody(request);String key=oneHeader(request,"Idempotency-Key");json.headers(h.context(),key);
        return response(response,service.recover(identities.requireCurrent(request,authentication),h.context(),key,id,h.revision()));
    }
    private static int number(String value,int fallback){try{return value==null?fallback:Integer.parseInt(value);}catch(NumberFormatException bad){invalid();return 0;}}
    private Headers headers(HttpServletRequest request,String id,String version,boolean fixed) {
        json.requireId(id);if(!fixed&&request.getQueryString()!=null)invalid();
        if(!version.equals(oneHeader(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=oneHeader(request,"X-Context-Revision"),revision=fixed?oneHeader(request,"X-Document-Revision"):null;
        PermissionSchema.revision(context);if(fixed)json.requireId(revision);return new Headers(context,revision);
    }
    private static String oneHeader(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name)); if(values.size()!=1 || values.get(0)==null || values.get(0).isBlank()) invalid();
        return values.get(0);
    }
    private static void noBody(HttpServletRequest request) throws IOException { if(request.getInputStream().read()!=-1) invalid(); }
    private record Headers(String context,String revision) { }
    private static JsonNode response(HttpServletResponse response,JsonNode result) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version",result.required("schema_version").asText());
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
