package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Authenticated HTTP boundary for fixed-body technical checks; never advances review state. */
@RestController
@ConditionalOnProperty(prefix="medical.document-technical-check",name="enabled",havingValue="true")
public final class DocumentTechnicalCheckController {
    private final ApplicationSessionIdentity identities;
    private final DocumentTechnicalCheckService service;
    private final PermissionSchema json;
    private final DocumentTechnicalCheckPolicy policy;
    public DocumentTechnicalCheckController(ApplicationSessionIdentity identities,DocumentTechnicalCheckService service,
            PermissionSchema json,DocumentTechnicalCheckPolicy policy){
        this.identities=identities;this.service=service;this.json=json;this.policy=policy;
    }
    @PostMapping(value="/api/documents/{documentId}/technical-checks",consumes="application/json")
    public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable("documentId") String documentId)throws Exception{
        String context=headers(request,response,true);noQuery(request);
        String key=SimulationInputArtifactController.one(request,"Idempotency-Key");
        var subject=identities.requireCurrent(request,authentication);
        var value=service.create(subject,context,documentId,key,body(request));
        response.setStatus(201);return envelope(value,response);
    }
    @GetMapping("/api/document-technical-checks/{checkId}")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable("checkId") String checkId)throws Exception{
        String context=headers(request,response,false);noQuery(request);
        return envelope(service.read(identities.requireCurrent(request,authentication),context,checkId),response);
    }
    @GetMapping("/api/documents/{documentId}/technical-check-operations/{key}")
    public JsonNode recovery(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable("documentId") String documentId,@PathVariable("key") String key)throws Exception{
        String context=headers(request,response,false);noQuery(request);
        SimulationInputArtifactSchema.key(key);
        return envelope(service.recovery(identities.requireCurrent(request,authentication),context,documentId,key),response);
    }
    private JsonNode envelope(JsonNode value,HttpServletResponse response){
        response.setHeader("X-Resource-Revision","1");
        return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),"1",value));
    }
    private static String headers(HttpServletRequest request,HttpServletResponse response,boolean mutation){
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
        if(mutation)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");return context;
    }
    private static void noQuery(HttpServletRequest request){if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private byte[] body(HttpServletRequest request)throws java.io.IOException{
        policy.requireConfigured();int limit=policy.maximumBytes();
        if(request.getContentLengthLong()>limit)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var stream=request.getInputStream();byte[] bytes=stream.readNBytes(limit);
        // A separate sentinel read avoids overflowing an explicitly configured Integer.MAX_VALUE limit.
        if(stream.read()!=-1)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
    }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof TenantAccessException denial){code=denial.code();status=switch(code){case ACCESS_DENIED->403;case FILE_TOO_LARGE->413;case OBJECT_UNAVAILABLE,SOURCE_MISSING->404;default->409;};break;}
            if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}
