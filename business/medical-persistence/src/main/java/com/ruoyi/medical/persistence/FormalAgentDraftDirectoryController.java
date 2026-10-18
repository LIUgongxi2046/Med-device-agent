package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController public final class FormalAgentDraftDirectoryController {
    private final ApplicationSessionIdentity identities;
    private final FormalAgentDraftDirectory service;
    public FormalAgentDraftDirectoryController(ApplicationSessionIdentity identities,FormalAgentDraftDirectory service){
        this.identities=identities;
        this.service=service;
    }
    @GetMapping("/api/formal-agent-authoring-feature-availability") public JsonNode availability(HttpServletRequest q,HttpServletResponse r,Authentication auth){
        String context=headers(q);
        if(q.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return response(r,service.availability(identities.requireCurrent(q,auth),context));
    }
    @PostMapping(value="/api/formal-agent-drafts/query",consumes="application/json") public JsonNode query(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{
        String context=headers(q);
        var subject=identities.requireCurrent(q,auth);
        if(q.getContentLengthLong()>4096)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        byte[] body=q.getInputStream().readNBytes(4097);
        return response(r,service.query(subject,context,body));
    }
    private static String headers(HttpServletRequest q){
        if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=SimulationInputArtifactController.one(q,"X-Context-Revision");
        PermissionSchema.revision(context);
        if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return context;
    }
    private static JsonNode response(HttpServletResponse r,JsonNode result){
        r.setHeader("Cache-Control","no-store");
        r.setHeader("X-Schema-Version","1.0.0");
        r.setHeader("X-Request-Id",result.path("request_id").asText());
        return result;
    }
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{
        new IdentityErrorResponseWriter().write(r,422,e.code());
    }
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{
        new IdentityErrorResponseWriter().write(r,(e.code()==ErrorCode.CURSOR_EXPIRED||e.code()==ErrorCode.CURSOR_INVALID)?409:e.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:SimulationRuleVersionController.errorStatus(e.code()),e.code());
    }
}
