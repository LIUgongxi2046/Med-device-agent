package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="medical.review-return",name="enabled",havingValue="true",matchIfMissing=false)
public final class DocumentRevisionAssignmentQueueController {
    private final ApplicationSessionIdentity identities;
    private final DocumentRevisionAssignmentQueueService service;
    private final DocumentReviewReturnController errors;
    public DocumentRevisionAssignmentQueueController(ApplicationSessionIdentity identities,
            DocumentRevisionAssignmentQueueService service, DocumentReviewReturnController errors) {
        this.identities=identities; this.service=service; this.errors=errors;
    }
    @GetMapping("/api/my/document-revision-assignments")
    public JsonNode read(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws Exception {
        if (!"1.0.0".equals(header(request,"X-Schema-Version"))) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=header(request,"X-Context-Revision"); PermissionSchema.revision(context);
        if (request.getInputStream().read()!=-1) invalid();
        var parameters=request.getParameterMap();
        if (!Set.of("limit","cursor").containsAll(parameters.keySet())) invalid();
        for (var values:parameters.values()) if(values.length!=1 || values[0]==null || values[0].isBlank()) invalid();
        String raw=request.getParameter("limit"); int limit=10;
        if (raw!=null) {
            if (!raw.matches("[1-9][0-9]?")) invalid();
            limit=Integer.parseInt(raw);
        }
        var result=service.read(identities.requireCurrent(request,authentication),context,limit,request.getParameter("cursor"));
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Request-Id",result.required("request_id").asText()); response.setHeader("X-Content-Type-Options","nosniff");
        return result;
    }
    private static String header(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        if(values.size()!=1 || values.get(0)==null || values.get(0).isBlank()) invalid(); return values.get(0);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response) throws java.io.IOException { errors.failed(failure,response); }
}
