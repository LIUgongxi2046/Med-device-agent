package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class ExternalErasureController {
    private final ExternalErasureService service;private final ApplicationSessionIdentity identities;
    public ExternalErasureController(ExternalErasureService service,ApplicationSessionIdentity identities){this.service=service;this.identities=identities;}
    @PostMapping(value="/api/source-versions/{source}/external-erasure",consumes="application/json")
    public JsonNode erase(@PathVariable String source,@RequestBody byte[] body,HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception {
        String context=headers(request,response,true);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");
        return service.erase(identities.requireCurrent(request,auth),context,source,key,body);
    }
    @GetMapping("/api/source-versions/{source}/external-erasure/{authority}/{scope}/inventory")
    public JsonNode inventory(@PathVariable String source,@PathVariable String authority,@PathVariable String scope,HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception {
        String context=headers(request,response,false);return service.inventory(identities.requireCurrent(request,auth),context,source,authority,scope);
    }
    @GetMapping("/api/source-versions/{source}/external-erasure/{authority}/{scope}/recovery/{key}")
    public JsonNode recovery(@PathVariable String source,@PathVariable String authority,@PathVariable String scope,@PathVariable String key,HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception {
        String context=headers(request,response,false);return service.recover(identities.requireCurrent(request,auth),context,source,authority,scope,key);
    }
    private String headers(HttpServletRequest request,HttpServletResponse response,boolean body) {
        if(request.getQueryString()!=null||!body&&(request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Resource-Revision",context);
        response.setHeader("X-Request-Id",java.util.UUID.randomUUID().toString());return context;
    }
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection error,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,422,error.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException error,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(error.code()),error.code());}
    @ExceptionHandler({IllegalStateException.class,IllegalArgumentException.class,java.io.IOException.class})void unavailable(Exception error,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,503,ErrorCode.DEPENDENCY_UNAVAILABLE);}
}
