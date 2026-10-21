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
public final class StrictComparisonController {
    private final ApplicationSessionIdentity identities;private final PermissionSchema json;
    private final StrictComparisonPolicyService policies;private final StrictDecimalEvidenceService decimals;
    public StrictComparisonController(ApplicationSessionIdentity identities,PermissionSchema json,StrictComparisonPolicyService policies,StrictDecimalEvidenceService decimals){this.identities=identities;this.json=json;this.policies=policies;this.decimals=decimals;}
    @GetMapping("/api/projects/{project}/strict-comparison-context")
    public JsonNode context(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return policies.context(identities.requireCurrent(r,auth),context,project);
    }
    @PostMapping(value="/api/projects/{project}/strict-comparison-policies",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode author(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);return policies.author(identities.requireCurrent(r,auth),context,project,key,body);
    }
    @PostMapping(value="/api/projects/{project}/strict-comparison-policies/{id}/decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);return policies.decide(identities.requireCurrent(r,auth),context,project,id,key,body);
    }
    @GetMapping("/api/projects/{project}/strict-comparison-policies/{id}")
    public JsonNode read(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return policies.read(identities.requireCurrent(r,auth),context,project,id);
    }
    @GetMapping("/api/projects/{project}/strict-comparison-policies/{id}/approval-baseline")
    public JsonNode baseline(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return policies.baseline(identities.requireCurrent(r,auth),context,project,id);
    }
    @PostMapping(value="/api/projects/{project}/strict-decimal-evidence",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode parse(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception {
        headers(r,response,version,context);json.headers(context,key);StrictFieldWireVersion.require(body,version);return decimals.produce(identities.requireCurrent(r,auth),context,project,key,body);
    }
    @GetMapping("/api/projects/{project}/strict-decimal-evidence/{id}")
    public JsonNode decimal(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(r,response,version,context);return StrictFieldWireVersion.response(decimals.read(identities.requireCurrent(r,auth),context,project,id),version,"/schema_version");
    }
    private static void headers(HttpServletRequest request,HttpServletResponse response,String version,String context){
        if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})
    void invalid(HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(SchemaRejection.class)
    void rejected(SchemaRejection failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}
