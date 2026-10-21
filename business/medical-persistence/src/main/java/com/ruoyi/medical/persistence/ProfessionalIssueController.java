package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public final class ProfessionalIssueController {
 private final ApplicationSessionIdentity identities;private final CheckQueryService checks;private final ProfessionalIssueService issues;private final PermissionSchema json;
 public ProfessionalIssueController(ApplicationSessionIdentity identities,CheckQueryService checks,ProfessionalIssueService issues,PermissionSchema json){this.identities=identities;this.checks=checks;this.issues=issues;this.json=json;}
 @GetMapping("/api/checks/{id}") public JsonNode check(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String version=SimulationInputArtifactController.one(q,"X-Schema-Version");if(!java.util.Set.of("1.0.0","1.1.0").contains(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String c=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(c);r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version",version);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);var data=checks.read(identities.requireCurrent(q,auth),c,id,version);r.setHeader("X-Resource-Revision","1");if("1.0.0".equals(version))return envelope(data,r);var out=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version",version).put("request_id",java.util.UUID.randomUUID().toString()).put("resource_revision","1");out.set("data",data);return out;}
 @GetMapping("/api/documents/{id}/issues") public JsonNode list(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String c=headers(q,r,false);for(String key:q.getParameterMap().keySet())if(!java.util.Set.of("document_revision","status","severity","cursor","limit").contains(key)||q.getParameterValues(key).length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String limit=q.getParameter("limit");int count;try{count=limit==null?50:Integer.parseInt(limit);}catch(NumberFormatException bad){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}return envelope(issues.list(identities.requireCurrent(q,auth),c,id,q.getParameter("document_revision"),q.getParameter("status"),q.getParameter("severity"),q.getParameter("cursor"),count),r);}
 @PostMapping(value="/api/issues/{id}/decisions",consumes="application/json") public JsonNode decide(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable("id")String id)throws Exception{String c=headers(q,r,true);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String key=SimulationInputArtifactController.one(q,"Idempotency-Key");var stream=q.getInputStream();byte[] bytes=stream.readNBytes(32768);if(stream.read()!=-1)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var value=issues.decide(identities.requireCurrent(q,auth),c,id,key,bytes);r.setStatus(201);return envelope(value,r);}
 private JsonNode envelope(JsonNode n,HttpServletResponse r){String rev=n.path("issue_revision").asText("1");r.setHeader("X-Resource-Revision",rev);return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),rev,n));}
 private static String headers(HttpServletRequest q,HttpServletResponse r,boolean write){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String c=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(c);if(write)SimulationInputArtifactController.one(q,"X-CSRF-TOKEN");return c;}
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
        for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
            if(cause instanceof TenantAccessException denial){code=denial.code();status=switch(code){case ACCESS_DENIED->403;case FILE_TOO_LARGE->413;case OBJECT_UNAVAILABLE,SOURCE_MISSING->404;default->409;};break;}
            if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}
