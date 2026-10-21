package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Human author/decision/read routes. No route accepts a caller-supplied approval proof. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="medical.task-strict-field.enabled",havingValue="true",matchIfMissing=false)
public final class TaskStrictFieldTypedTargetControllerV2 {
    private final ApplicationSessionIdentity identities;private final PermissionSchema json;
    private final TaskStrictFieldTypedTargetService contexts;private final TaskStrictFieldTypedTargetProducer producer;
    public TaskStrictFieldTypedTargetControllerV2(ApplicationSessionIdentity identities,PermissionSchema json,TaskStrictFieldTypedTargetService contexts,TaskStrictFieldTypedTargetProducer producer){this.producer=producer;this.identities=identities;this.json=json;this.contexts=contexts;}
    @PostMapping(value="/api/v2/projects/{project}/strict-field-typed-targets",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode author(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);StrictFieldWireVersion.require(body,version);return contexts.author(identities.requireCurrent(r,auth),context,project,key,body);
    }
    @PostMapping(value="/api/v2/projects/{project}/strict-field-typed-targets/{id}/decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);StrictFieldWireVersion.require(body,version);return contexts.decide(identities.requireCurrent(r,auth),context,project,id,key,body);
    }
    @GetMapping("/api/v2/projects/{project}/strict-field-typed-targets/{id}")
    public JsonNode read(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return StrictFieldWireVersion.response(contexts.read(identities.requireCurrent(r,auth),context,project,id),version,"/manifest/schema_version");
    }
    @GetMapping("/api/v2/projects/{project}/strict-field-typed-targets/{id}/approval-baseline")
    public JsonNode baseline(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return contexts.baseline(identities.requireCurrent(r,auth),context,project,id);
    }
    @PostMapping(value="/api/v2/projects/{project}/strict-field-typed-targets/produce",consumes="application/json")
    public JsonNode produce(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);StrictFieldWireVersion.require(body,version);return producer.produce(identities.requireCurrent(r,auth),context,project,key,body);
    }
    private static void headers(HttpServletRequest request,HttpServletResponse response,String version,String context){
        if(!"2.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);
        for(String header:java.util.List.of("X-Schema-Version","X-Context-Revision","Idempotency-Key"))if(java.util.Collections.list(request.getHeaders(header)).size()>1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","2.0.0");
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})
    void invalid(HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(SchemaRejection.class)
    void rejected(SchemaRejection failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}
