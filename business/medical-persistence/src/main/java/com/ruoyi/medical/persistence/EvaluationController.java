package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class EvaluationController {
    private final ApplicationSessionIdentity identities;private final EvaluationService evaluations;private final PermissionSchema json;
    public EvaluationController(ApplicationSessionIdentity identities,EvaluationService evaluations,PermissionSchema json){this.identities=identities;this.evaluations=evaluations;this.json=json;}
    @PostMapping(value="/api/evaluation-batches",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key){
        headers(request,version,context,key,true);return output(response,evaluations.create(identities.requireCurrent(request,authentication),context,key,body));
    }
    @PostMapping(value="/api/evaluation-batches/{id}/scores",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode score(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key){
        headers(request,version,context,key,true);return output(response,evaluations.append(identities.requireCurrent(request,authentication),context,key,id,body));
    }
    @PostMapping(value="/api/evaluation-batches/{id}/finalize",consumes="application/json")
    public JsonNode finalizeResult(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key){
        headers(request,version,context,key,true);return output(response,evaluations.finalizeResult(identities.requireCurrent(request,authentication),context,key,id,body));
    }
    @GetMapping("/api/evaluation-batches/{id}")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(request,version,context,null,false);return output(response,evaluations.read(identities.requireCurrent(request,authentication),context,id,false));
    }
    @GetMapping("/api/evaluation-batches/{id}/scores")
    public JsonNode scores(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context){
        headers(request,version,context,null,false);return output(response,evaluations.read(identities.requireCurrent(request,authentication),context,id,true));
    }
    private void headers(HttpServletRequest request,String version,String context,String key,boolean writing){
        if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if(writing)json.headers(context,key);else PermissionSchema.revision(context);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        for(String name:writing?java.util.List.of("X-Schema-Version","X-Context-Revision","Idempotency-Key"):java.util.List.of("X-Schema-Version","X-Context-Revision")) {
            var headers=request.getHeaders(name);int count=0;while(headers!=null&&headers.hasMoreElements()){headers.nextElement();if(++count>1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
        }
    }
    private static JsonNode output(HttpServletResponse response,JsonNode result){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",result.required("request_id").textValue());return result;}
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,status(failure.code()),failure.code());}
    static int status(ErrorCode code){return switch(code){
        case ACCESS_DENIED->403;case OBJECT_UNAVAILABLE,SOURCE_MISSING->404;case FILE_TOO_LARGE->413;
        case CONTEXT_CHANGED,SCOPE_STALE,IDEMPOTENCY_CONFLICT,REVISION_CONFLICT,ACTION_STALE->409;
        case DEPENDENCY_UNAVAILABLE->503;
        case SCHEMA_INVALID,SCHEMA_UNSUPPORTED,SOURCE_NOT_READY,RIGHTS_PENDING,CONTENT_MISMATCH,CONTEXT_LIMIT->422;
        default->throw new IllegalArgumentException("EVALUATION_ERROR_CODE_INVALID");};}
}
