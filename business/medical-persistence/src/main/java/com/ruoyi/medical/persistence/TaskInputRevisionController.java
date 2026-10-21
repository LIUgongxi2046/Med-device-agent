package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public final class TaskInputRevisionController {
    private final TaskInputRevisionService service;private final ApplicationSessionIdentity identities;
    public TaskInputRevisionController(TaskInputRevisionService service,ApplicationSessionIdentity identities){this.service=service;this.identities=identities;}
    @PostMapping(value="/api/tasks/{taskId}/input-proposals",consumes="application/json")
    public JsonNode propose(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String taskId,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody byte[] body){
        headers(request,version);return result(response,service.propose(identities.requireCurrent(request,authentication),context,key,taskId,body));
    }
    @PostMapping(value="/api/tasks/{taskId}/input-decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String taskId,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody byte[] body){
        headers(request,version);return result(response,service.decide(identities.requireCurrent(request,authentication),context,key,taskId,body));
    }
    @GetMapping("/api/tasks/{taskId}/input-operations/{operation}")
    public JsonNode original(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String taskId,@PathVariable String operation,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key){
        headers(request,version);return result(response,service.original(identities.requireCurrent(request,authentication),context,key,taskId,operation));
    }
    private static void headers(HttpServletRequest r,String v){if(!"1.0.0".equals(v))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);if(r.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static JsonNode result(HttpServletResponse r,JsonNode n){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",n.required("request_id").asText());return n;}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:409,e.code());}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
}
