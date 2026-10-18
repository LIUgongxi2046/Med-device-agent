package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Session route: existing local/OIDC security filter chains enforce CSRF; no worker route or bypass. */
@RestController
@ConditionalOnProperty(prefix="medical.simulation-input-artifact",name="enabled",havingValue="true")
public final class SimulationInputArtifactController {
    private final ApplicationSessionIdentity identities;
    private final SimulationInputArtifactService service;
    private final SimulationInputArtifactSchema schema;
    private final PermissionSchema json;
    public SimulationInputArtifactController(ApplicationSessionIdentity identities,SimulationInputArtifactService service,
            PermissionSchema json)throws Exception {
        this.identities=identities;this.service=service;this.json=json;this.schema=new SimulationInputArtifactSchema();
    }
    @PostMapping(value="/api/simulation-fixtures/{caseId}/input-artifact",consumes="application/json")
    public JsonNode register(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable String caseId)throws Exception {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        if(!"1.0.0".equals(one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=one(request,"X-Context-Revision"),key=one(request,"Idempotency-Key");
        PermissionSchema.revision(context);SimulationInputArtifactSchema.key(key);WireValues.uuid(caseId);
        // Reject duplicate CSRF headers here; token validity remains the security filter's responsibility.
        one(request,"X-CSRF-TOKEN");if(request.getQueryString()!=null)invalid();
        var subject=identities.requireCurrent(request,authentication);
        var receipt=service.register(subject,context,caseId,key,schema.request(body(request)));
        var envelope=json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),"1",receipt));
        response.setHeader("X-Resource-Revision","1");return envelope;
    }
    @PostMapping(value="/api/simulation-fixtures/{caseId}/input-artifact-lookups",consumes="application/json")
    public JsonNode lookup(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @PathVariable String caseId)throws Exception {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
        if(!"1.0.0".equals(one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String context=one(request,"X-Context-Revision");PermissionSchema.revision(context);WireValues.uuid(caseId);
        one(request,"X-CSRF-TOKEN");if(request.getQueryString()!=null)invalid();
        var subject=identities.requireCurrent(request,authentication);
        var result=service.lookup(subject,context,caseId,new SimulationInputArtifactLookupSchema().request(lookupBody(request)));
        return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),"1",result));
    }
    static String one(HttpServletRequest request,String name){
        var values=request.getHeaders(name);if(values==null||!values.hasMoreElements())invalid();
        String value=values.nextElement();if(value==null||value.isEmpty()||values.hasMoreElements())invalid();return value;
    }
    static byte[] body(HttpServletRequest request)throws java.io.IOException {
        if(request.getContentLengthLong()>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
    }
    static byte[] lookupBody(HttpServletRequest request)throws java.io.IOException {
        if(request.getContentLengthLong()>131072)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(131073);if(bytes.length>131072)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
    }
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException {
        // IdentityErrorResponseWriter owns safe error headers, including no-store.
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof TenantAccessException denial){code=denial.code();status=switch(code){
                case ACCESS_DENIED -> 403;case FILE_TOO_LARGE -> 413;case OBJECT_UNAVAILABLE,SOURCE_MISSING -> 404;default -> 409;};break;}
            if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}
