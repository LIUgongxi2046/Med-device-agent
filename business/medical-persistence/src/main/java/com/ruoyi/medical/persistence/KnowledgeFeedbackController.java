package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business-operations/knowledge-feedback")
public final class KnowledgeFeedbackController {
    private final KnowledgeFeedbackService service;
    private final ApplicationSessionIdentity identities;
    private final PermissionSchema json;
    public KnowledgeFeedbackController(KnowledgeFeedbackService service,ApplicationSessionIdentity identities,PermissionSchema json){this.service=service;this.identities=identities;this.json=json;}
    @PostMapping(consumes="application/json")
    public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication auth,@RequestBody byte[] body)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.create(identities.requireCurrent(request,auth),context,key(request),body));
    }
    @PostMapping(value="/{id}/proposals",consumes="application/json")
    public JsonNode proposal(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.linkProposal(identities.requireCurrent(request,auth),context,id,key(request),body));
    }
    @PostMapping(value="/{id}/reviews",consumes="application/json")
    public JsonNode review(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.linkReview(identities.requireCurrent(request,auth),context,id,key(request),body));
    }
    @PostMapping(value="/{id}/closures",consumes="application/json")
    public JsonNode close(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.close(identities.requireCurrent(request,auth),context,id,key(request),body));
    }
    @PostMapping(value="/{id}/rejections",consumes="application/json")
    public JsonNode reject(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.reject(identities.requireCurrent(request,auth),context,id,key(request),body));
    }
    @GetMapping
    public JsonNode list(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception {
        String context=headers(request,response,true);
        for(var entry:request.getParameterMap().entrySet())if(!Set.of("project_id","after_id").contains(entry.getKey())||entry.getValue().length!=1)KnowledgeFeedbackSchema.invalid();
        return envelope(response,context,service.list(identities.requireCurrent(request,auth),context,request.getParameter("project_id"),request.getParameter("after_id")));
    }
    @GetMapping("/{id}")
    public JsonNode history(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception {
        String context=headers(request,response,false);return envelope(response,context,service.history(identities.requireCurrent(request,auth),context,id));
    }
    private static String key(HttpServletRequest request){return SimulationInputArtifactController.one(request,"Idempotency-Key");}
    private static String headers(HttpServletRequest request,HttpServletResponse response,boolean query){
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if(!query&&request.getQueryString()!=null)KnowledgeFeedbackSchema.invalid();
        String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);return context;
    }
    private JsonNode envelope(HttpServletResponse response,String context,JsonNode data){String request=UUID.randomUUID().toString();response.setHeader("X-Request-Id",request);return json.tree(new InterfaceProtocol.Envelope("1.0.0",request,context,data));}
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException {
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof TenantAccessException e){code=e.code();status=SimulationRuleVersionController.errorStatus(code);break;}
            if(cause instanceof SchemaRejection e){code=e.code();status=422;break;}
            if(cause instanceof com.fasterxml.jackson.core.JsonProcessingException||cause instanceof org.springframework.http.converter.HttpMessageNotReadableException){code=ErrorCode.SCHEMA_INVALID;status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}
