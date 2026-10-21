package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Separate additive v2 authority workflow, never a permissive interpretation of a v1 receipt. */
@RestController
public final class SourceCopyAuthorityController {
    private final SourceCopyAuthority service;
    private final ApplicationSessionIdentity identities;
    public SourceCopyAuthorityController(SourceCopyAuthority service,ApplicationSessionIdentity identities){this.service=service;this.identities=identities;}
    @GetMapping("/api/source-versions/{source}/copy-authority/configuration")
    public JsonNode configuration(@PathVariable String source,HttpServletRequest request,HttpServletResponse response,Authentication auth){
        String context=headers(request,response,false);return service.configuration(identities.requireCurrent(request,auth),context,source);
    }
    @GetMapping("/api/source-versions/{source}/copy-authority/{id}")
    public JsonNode read(@PathVariable String source,@PathVariable String id,HttpServletRequest request,HttpServletResponse response,Authentication auth){
        String context=headers(request,response,false);return service.read(identities.requireCurrent(request,auth),context,source,id);
    }
    @PostMapping(value="/api/source-versions/{source}/copy-authority/decisions",consumes="application/json")
    public JsonNode decide(@PathVariable String source,@RequestBody byte[] body,HttpServletRequest request,HttpServletResponse response,Authentication auth){
        String context=headers(request,response,true);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");
        return service.command(identities.requireCurrent(request,auth),context,source,key,body);
    }
    @GetMapping("/api/source-versions/{source}/copy-authority/recovery/{key}")
    public JsonNode recover(@PathVariable String source,@PathVariable String key,HttpServletRequest request,HttpServletResponse response,Authentication auth){
        String context=headers(request,response,false);return service.recover(identities.requireCurrent(request,auth),context,source,key);
    }
    private static String headers(HttpServletRequest request,HttpServletResponse response,boolean body){
        if(request.getQueryString()!=null||!body&&(request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if(!"2.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","2.0.0");response.setHeader("X-Resource-Revision",context);
        response.setHeader("X-Request-Id",java.util.UUID.randomUUID().toString());return context;
    }
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection failure,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException failure,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}
