package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class OperationsFeedbackController {
    private final OperationsFeedbackService service;
    private final ApplicationSessionIdentity identities;
    private final PermissionSchema json;
    public OperationsFeedbackController(OperationsFeedbackService service,ApplicationSessionIdentity identities,PermissionSchema json){this.service=service;this.identities=identities;this.json=json;}
    @PostMapping(value="/api/business-operations/feedback",consumes="application/json")
    public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] bytes)throws Exception{
        String context=headers(request,response);var result=service.create(identities.requireCurrent(request,authentication),context,SimulationInputArtifactController.one(request,"Idempotency-Key"),bytes);return envelope(response,context,result);
    }
    @PostMapping(value="/api/business-operations/feedback/{id}/transitions",consumes="application/json")
    public JsonNode transition(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,@RequestBody byte[] bytes)throws Exception{
        String context=headers(request,response);var result=service.transition(identities.requireCurrent(request,authentication),context,id,SimulationInputArtifactController.one(request,"Idempotency-Key"),bytes);return envelope(response,context,result);
    }
    @GetMapping("/api/business-operations/feedback")
    public JsonNode list(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);
        for(var e:request.getParameterMap().entrySet())if(!Set.of("project_id","after_id").contains(e.getKey())||e.getValue().length!=1)BusinessOperationsSchema.invalid();
        return envelope(response,context,service.list(identities.requireCurrent(request,authentication),context,request.getParameter("project_id"),request.getParameter("after_id")));
    }
    @GetMapping("/api/business-operations/feedback/{id}")
    public JsonNode history(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id)throws Exception{
        String context=BusinessAuditController.headers(request,response);if(request.getQueryString()!=null)BusinessOperationsSchema.invalid();return envelope(response,context,service.history(identities.requireCurrent(request,authentication),context,id));
    }
    @GetMapping("/api/business-operations/feedback/operations/{key}")
    public JsonNode lookup(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String key){
        String context=BusinessAuditController.headers(request,response);if(request.getQueryString()!=null)BusinessOperationsSchema.invalid();return envelope(response,context,service.lookup(identities.requireCurrent(request,authentication),context,key));
    }
    private JsonNode envelope(HttpServletResponse response,String context,JsonNode result){String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,result));}
    private static String headers(HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if(request.getQueryString()!=null)BusinessOperationsSchema.invalid();String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);return context;
    }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof TenantAccessException e){code=e.code();status=SimulationRuleVersionController.errorStatus(code);break;}
            if(cause instanceof SchemaRejection e){code=e.code();status=422;break;}
            if(cause instanceof com.fasterxml.jackson.core.JsonProcessingException || cause instanceof org.springframework.http.converter.HttpMessageNotReadableException){code=ErrorCode.SCHEMA_INVALID;status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}
