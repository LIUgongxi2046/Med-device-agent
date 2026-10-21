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

@RestController
public final class DenseRetrievalController {
    private final ApplicationSessionIdentity identities;private final DenseRetrievalService service;private final PermissionSchema headers;
    public DenseRetrievalController(ApplicationSessionIdentity identities,DenseRetrievalService service,PermissionSchema headers){this.identities=identities;this.service=service;this.headers=headers;}
    @PostMapping(value="/api/dense-index-configurations",consumes="application/json")
    public JsonNode configuration(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context,@RequestHeader("Idempotency-Key") String key)throws Exception {
        validate(request,version,context,key);return output(response,service.configure(identities.requireCurrent(request,authentication),context,key,service.decode(body,DenseRetrievalProtocol.Configuration.class)));
    }
    @GetMapping("/api/dense-index-configurations/{id}")
    public JsonNode configuration(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context) {
        validate(request,version,context,null);return output(response,service.configuration(identities.requireCurrent(request,authentication),context,id));
    }
    @PostMapping(value="/api/dense-index-builds",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public JsonNode build(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context,@RequestHeader("Idempotency-Key") String key)throws Exception {
        validate(request,version,context,key);return output(response,service.build(identities.requireCurrent(request,authentication),context,key,service.decode(body,DenseRetrievalProtocol.Build.class)));
    }
    @PostMapping(value="/api/dense-retrievals",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public JsonNode search(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context,@RequestHeader("Idempotency-Key") String key)throws Exception {
        validate(request,version,context,key);return output(response,service.search(identities.requireCurrent(request,authentication),context,key,service.decode(body,DenseRetrievalProtocol.Search.class)));
    }
    @PostMapping("/api/dense-operations/{id}/execute")
    public JsonNode execute(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context) {
        validate(request,version,context,null);return output(response,service.execute(identities.requireCurrent(request,authentication),context,id));
    }
    @GetMapping("/api/dense-operations/{id}")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader("X-Schema-Version") String version,@RequestHeader("X-Context-Revision") String context) {
        validate(request,version,context,null);return output(response,service.read(identities.requireCurrent(request,authentication),context,id));
    }
    private void validate(HttpServletRequest request,String version,String context,String key){
        if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context);if(key!=null)headers.headers(context,key);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static JsonNode output(HttpServletResponse response,JsonNode result){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",result.required("request_id").textValue());return result;}
    @ExceptionHandler({SchemaRejection.class,IllegalArgumentException.class,com.fasterxml.jackson.core.JsonProcessingException.class})
    void invalid(HttpServletResponse response)throws Exception{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response)throws Exception{new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(failure.code()),failure.code());}
}
