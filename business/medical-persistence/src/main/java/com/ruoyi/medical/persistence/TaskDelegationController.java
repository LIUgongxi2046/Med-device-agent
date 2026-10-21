package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Ordinary authenticated browser-session/CSRF routes, never a worker or security exemption. */
@RestController
@ConditionalOnProperty(prefix="medical.task-delegation",name="enabled",havingValue="true")
public final class TaskDelegationController {
    private final ApplicationSessionIdentity identities;private final TaskDelegationService service;private final PermissionSchema json;
    public TaskDelegationController(ApplicationSessionIdentity identities,TaskDelegationService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
    @PostMapping(value="/api/task-delegation-rules",consumes="application/json")
    public JsonNode author(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        String context=headers(request);return envelope(service.author(identities.requireCurrent(request,auth),context,
                SimulationInputArtifactController.one(request,"Idempotency-Key"),body(request)),response);
    }
    @PostMapping(value="/api/task-delegation-decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
        String context=headers(request);return envelope(service.decide(identities.requireCurrent(request,auth),context,
                SimulationInputArtifactController.one(request,"Idempotency-Key"),body(request)),response);
    }
    @GetMapping("/api/task-delegation-rules/{id}")
    public JsonNode read(@PathVariable String id,HttpServletRequest request,HttpServletResponse response,Authentication auth){
        String context=headers(request);if(request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return envelope(service.read(identities.requireCurrent(request,auth),context,id),response);
    }
    private static String headers(HttpServletRequest request){
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);return context;
    }
    private static byte[] body(HttpServletRequest request)throws java.io.IOException{
        if(request.getContentLengthLong()>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
    }
    private JsonNode envelope(JsonNode value,HttpServletResponse response){
        String requestId=java.util.UUID.randomUUID().toString();String revision=value.required("revision").asText();
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",requestId);response.setHeader("X-Resource-Revision",revision);
        var result=json.object().put("schema_version","1.0.0").put("request_id",requestId).put("resource_revision",revision);result.set("data",value);return result;
    }
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,422,e.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws java.io.IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
}
