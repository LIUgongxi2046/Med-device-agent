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
public final class TemplateApplicationController {
    private final ApplicationSessionIdentity identities;
    private final TemplateApplicationService service;
    private final PermissionSchema json;
    public TemplateApplicationController(ApplicationSessionIdentity identities,TemplateApplicationService service,PermissionSchema json) {
        this.identities=identities;this.service=service;this.json=json;
    }
    @GetMapping("/api/template-versions/{id}/application-view")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context) {
        headers(version,context);CollectionSchema.id(id);
        return response(response,service.read(identities.requireCurrent(request,authentication),context,id,service.query(request.getParameterMap())));
    }
    @PostMapping(value="/api/template-versions/{id}/application-decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,
            @RequestHeader(value="Idempotency-Key",required=false)String key) {
        headers(version,context);json.headers(context,key);CollectionSchema.id(id);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return response(response,service.decide(identities.requireCurrent(request,authentication),context,key,id,body));
    }
    private static void headers(String version,String context){if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);}
    private static JsonNode response(HttpServletResponse response,JsonNode value){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",value.required("request_id").asText());return value;}
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.RESULT_VERSION_MISMATCH?409:TemplateController.errorStatus(failure.code()),failure.code());}
}
