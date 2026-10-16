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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SourceIndexViewController {
    private final ApplicationSessionIdentity identities;
    private final SourceIndexViewService service;
    public SourceIndexViewController(ApplicationSessionIdentity identities,SourceIndexViewService service) {
        this.identities=identities; this.service=service;
    }
    @GetMapping("/api/source-versions/{id}/index-builds")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable String id,@RequestHeader(value="X-Schema-Version",required=false) String version,
            @RequestHeader(value="X-Context-Revision",required=false) String context) {
        for(String name:java.util.List.of("X-Schema-Version","X-Context-Revision"))
            if(java.util.Collections.list(request.getHeaders(name)).size()!=1) invalid();
        if(!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        WireValues.revision(context); WireValues.uuid(id);
        var parameters=request.getParameterMap();
        if(!parameters.keySet().equals(java.util.Set.of("project_id")) || parameters.get("project_id").length!=1) invalid();
        String project=parameters.get("project_id")[0]; WireValues.uuid(project);
        var result=service.read(identities.requireCurrent(request,authentication),context,id,project);
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Request-Id",result.required("request_id").textValue()); return result;
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection error,HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response,422,error.code());
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException error,HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(error.code()),error.code());
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
