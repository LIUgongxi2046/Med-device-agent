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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SourceExactController {
    private final ApplicationSessionIdentity identities;
    private final SourceExactService service;
    private final SourceExactSchema schema;
    private final PermissionSchema headers;
    private final RetrievalSchema reading;
    public SourceExactController(ApplicationSessionIdentity identities,SourceExactService service,SourceExactSchema schema,
            PermissionSchema headers,RetrievalSchema reading) {
        this.identities=identities; this.service=service; this.schema=schema; this.headers=headers; this.reading=reading;
    }
    @PostMapping(value="/api/source-versions/{id}/exact-fields",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode publish(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestBody byte[] body,@RequestHeader(value="X-Schema-Version",required=false) String version,
            @RequestHeader(value="X-Context-Revision",required=false) String context,@RequestHeader(value="Idempotency-Key",required=false) String key) throws IOException {
        version(request,version,context); singleton(request,"Idempotency-Key"); headers.headers(context,key); noQuery(request); WireValues.uuid(id);
        return response(response,service.publish(identities.requireCurrent(request,authentication),context,key,id,schema.publication(body)));
    }
    @GetMapping("/api/source-versions/{id}/exact-fields/{revision}")
    public JsonNode fields(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,@PathVariable String revision,
            @RequestHeader(value="X-Schema-Version",required=false) String version,@RequestHeader(value="X-Context-Revision",required=false) String context) {
        version(request,version,context); WireValues.uuid(id); WireValues.uuid(revision); var parameters=request.getParameterMap();
        if(!parameters.keySet().equals(java.util.Set.of("project_id")) || parameters.get("project_id").length!=1) invalid();
        String project=parameters.get("project_id")[0]; WireValues.uuid(project);
        return response(response,service.fields(identities.requireCurrent(request,authentication),context,id,revision,project));
    }
    @GetMapping("/api/source-versions/{id}/exact-fields")
    public JsonNode currentFields(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false) String version,@RequestHeader(value="X-Context-Revision",required=false) String context) {
        version(request,version,context); WireValues.uuid(id); var parameters=request.getParameterMap();
        if(!parameters.keySet().equals(java.util.Set.of("project_id","parse_version","approval_revision"))) invalid();
        for(var entry:parameters.entrySet()) { if(entry.getValue().length!=1) invalid(); WireValues.uuid(entry.getValue()[0]); }
        return response(response,service.currentFields(identities.requireCurrent(request,authentication),context,id,
            parameters.get("project_id")[0],parameters.get("parse_version")[0],parameters.get("approval_revision")[0]));
    }
    @PostMapping(value="/api/retrievals/exact",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode search(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestBody byte[] body,
            @RequestHeader(value="X-Schema-Version",required=false) String version,@RequestHeader(value="X-Context-Revision",required=false) String context,
            @RequestHeader(value="Idempotency-Key",required=false) String key) throws IOException {
        version(request,version,context); singleton(request,"Idempotency-Key"); headers.headers(context,key); noQuery(request);
        return response(response,service.search(identities.requireCurrent(request,authentication),context,key,schema.search(body)));
    }
    @GetMapping("/api/retrievals/exact/{id}")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String id,
            @RequestHeader(value="X-Schema-Version",required=false) String version,@RequestHeader(value="X-Context-Revision",required=false) String context) {
        version(request,version,context); WireValues.uuid(id);
        return response(response,service.read(identities.requireCurrent(request,authentication),context,id,reading.page(request.getParameterMap())));
    }
    private static void singleton(HttpServletRequest request,String name) {
        if(java.util.Collections.list(request.getHeaders(name)).size()!=1) invalid();
    }
    private static void version(HttpServletRequest request,String version,String context) {
        singleton(request,"X-Schema-Version"); singleton(request,"X-Context-Revision");
        if(!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); WireValues.revision(context);
    }
    private static void noQuery(HttpServletRequest request) { if(request.getQueryString()!=null) invalid(); }
    private static JsonNode response(HttpServletResponse response,JsonNode value) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Request-Id",value.required("request_id").textValue()); return value;
    }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    void body(HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID); }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection error,HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,422,error.code()); }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException error,HttpServletResponse response) throws IOException { new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(error.code()),error.code()); }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
