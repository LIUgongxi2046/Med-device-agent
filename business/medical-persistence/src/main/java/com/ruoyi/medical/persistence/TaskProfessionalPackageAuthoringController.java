package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Exact browser contract; only a durable pre-intent tombstone emits rejection proof headers. */
@RestController
@ConditionalOnProperty(prefix="medical.task-professional-package-authoring",name="enabled",havingValue="true")
public final class TaskProfessionalPackageAuthoringController {
    private final ApplicationSessionIdentity identities;private final TaskProfessionalPackageAuthoringService service;
    public TaskProfessionalPackageAuthoringController(ApplicationSessionIdentity identities,TaskProfessionalPackageAuthoringService service){this.identities=identities;this.service=service;}
    @GetMapping("/api/projects/{projectId}/task-professional-package-preparation")
    public JsonNode preparation(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q);noBody(q);return respond(r,service.preparation(identities.requireCurrent(q,auth),context,projectId));}
    @PostMapping(value="/api/projects/{projectId}/task-professional-packages",consumes="application/json")
    public JsonNode create(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q),key=SimulationInputArtifactController.one(q,"Idempotency-Key");return respond(r,service.create(identities.requireCurrent(q,auth),context,projectId,key,body(q)));}
    @GetMapping("/api/projects/{projectId}/task-professional-packages/{operationId}")
    public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String operationId)throws Exception{String context=headers(q);noBody(q);return respond(r,service.read(identities.requireCurrent(q,auth),context,projectId,operationId));}
    @PostMapping(value="/api/projects/{projectId}/task-professional-package-recoveries",consumes="application/json")
    public JsonNode recover(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q);if(q.getHeader("Idempotency-Key")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return respond(r,service.recover(identities.requireCurrent(q,auth),context,projectId,body(q)));}
    private static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
    private static void noBody(HttpServletRequest q){if(q.getContentLengthLong()>0||q.getHeader("Transfer-Encoding")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static byte[] body(HttpServletRequest q)throws IOException{int maximum=TaskProfessionalPackageAuthoringSchema.MAXIMUM_REQUEST_BYTES;if(q.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(maximum+1);if(bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
    static JsonNode respond(HttpServletResponse r,TaskProfessionalPackageAuthoringService.Response result)throws IOException{return respond(r,result,null);}
    static JsonNode respond(HttpServletResponse r,TaskProfessionalPackageAuthoringService.Response result,TaskProfessionalPackageAuthoringSchema selected)throws IOException{
        r.setStatus(result.httpStatus());r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");
        if(result.rejection()==null){r.setHeader("X-Request-Id",result.body().required("request_id").textValue());return result.body();}
        var p=result.rejection();if(selected!=null)selected.requireProfile(p,p.required("project_id").textValue());r.setCharacterEncoding(StandardCharsets.UTF_8.name());r.setContentType("application/json");r.setHeader("X-Request-Id",p.required("request_id").textValue());
        r.setHeader("X-Task-Package-Outcome","REJECTED_PRE_INTENT");r.setHeader("X-Task-Package-Project",p.required("project_id").textValue());r.setHeader("X-Task-Package-Operation",selected==null?TaskProfessionalPackageAuthoringSchema.OPERATION:selected.profile().proofOperation);r.setHeader("X-Task-Package-Context-Revision",p.required("context_revision").textValue());r.setHeader("X-Task-Package-Key-Sha256",p.required("key_sha256").textValue());r.setHeader("X-Task-Package-Wire-Sha256",p.required("wire_sha256").textValue());
        if(selected!=null){r.setHeader("X-Task-Package-Route",p.required("authoring_route").textValue());r.setHeader("X-Task-Package-Profile",p.required("authoring_profile").textValue());r.setHeader("X-Task-Package-Contract-Sha256",p.required("authoring_contract_sha256").textValue());}
        r.getOutputStream().write(new IdentityErrorResponseWriter().encode(ErrorCode.valueOf(p.required("error_code").textValue()),p.required("request_id").textValue()));return null;
    }
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
