package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SourceRightsController {
    private final ApplicationSessionIdentity identities;
    private final SourceRightsService service;
    private final SourceRightsDecisionSchema schema;
    private final PermissionSchema headers;
    public SourceRightsController(ApplicationSessionIdentity identities,SourceRightsService service,SourceRightsDecisionSchema schema,PermissionSchema headers) {
        this.identities=identities; this.service=service; this.schema=schema; this.headers=headers;
    }
    @PostMapping(value="/api/source-versions/{id}/rights-reviews",consumes="application/json")
    public JsonNode preview(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestBody byte[] body,@RequestHeader(value="X-Schema-Version",required=false) String version,
            @RequestHeader(value="X-Context-Revision",required=false) String context) throws IOException {
        version(version); PermissionSchema.revision(context); noQuery(request); WireValues.uuid(id);
        return response(response,service.preview(identities.requireCurrent(request,authentication),context,id,schema.preview(body)));
    }
    @PostMapping(value="/api/source-versions/{id}/rights-decisions",consumes="application/json")
    public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestBody byte[] body,@RequestHeader(value="X-Schema-Version",required=false) String version,
            @RequestHeader(value="X-Context-Revision",required=false) String context,@RequestHeader(value="Idempotency-Key",required=false) String key) throws Exception {
        version(version); headers.headers(context,key); noQuery(request); WireValues.uuid(id);
        return response(response,service.decide(identities.requireCurrent(request,authentication),context,key,id,schema.decision(body)));
    }
    private static JsonNode response(HttpServletResponse response,JsonNode value) {
        response.setHeader("X-Schema-Version","1.0.0"); response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Request-Id",value.required("request_id").textValue()); return value;
    }
    private static void version(String value) { if(!"1.0.0".equals(value)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
    private static void noQuery(HttpServletRequest request) { if(request.getQueryString()!=null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection error,HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,error.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException error,HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(error.code()),error.code());
    }
}
