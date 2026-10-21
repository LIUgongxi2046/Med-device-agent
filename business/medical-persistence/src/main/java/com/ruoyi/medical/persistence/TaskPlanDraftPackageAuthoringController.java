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

/** Explicit N01 package authoring route. Uses the original immutable object pipeline, never a TASK role alias. */
@RestController
@ConditionalOnProperty(prefix="medical.task-professional-package-authoring",name="enabled",havingValue="true")
public final class TaskPlanDraftPackageAuthoringController {
    private final ApplicationSessionIdentity identities;private final TaskProfessionalPackageAuthoringService service;private final TaskProfessionalPackageAuthoringSchema schema;
    public TaskPlanDraftPackageAuthoringController(ApplicationSessionIdentity identities,TaskProfessionalPackageAuthoringService service,TaskProfessionalPackageAuthoringSchema schema)throws Exception{this.identities=identities;this.service=service.planDraft();this.schema=schema.planDraft();}
    @GetMapping("/api/projects/{projectId}/task-plan-draft-package-preparation")
    public JsonNode preparation(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q);noBody(q);return respond(r,service.preparation(identities.requireCurrent(q,auth),context,projectId));}
    @PostMapping(value="/api/projects/{projectId}/task-plan-draft-packages",consumes="application/json")
    public JsonNode create(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q),key=SimulationInputArtifactController.one(q,"Idempotency-Key");return respond(r,service.create(identities.requireCurrent(q,auth),context,projectId,key,body(q)));}
    @GetMapping("/api/projects/{projectId}/task-plan-draft-packages/{operationId}")
    public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String operationId)throws Exception{String context=headers(q);noBody(q);return respond(r,service.read(identities.requireCurrent(q,auth),context,projectId,operationId));}
    @PostMapping(value="/api/projects/{projectId}/task-plan-draft-package-recoveries",consumes="application/json")
    public JsonNode recover(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId)throws Exception{String context=headers(q);if(q.getHeader("Idempotency-Key")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return respond(r,service.recover(identities.requireCurrent(q,auth),context,projectId,body(q)));}
    private static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
    private static void noBody(HttpServletRequest q){if(q.getContentLengthLong()>0||q.getHeader("Transfer-Encoding")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    private static byte[] body(HttpServletRequest q)throws IOException{int maximum=TaskProfessionalPackageAuthoringSchema.MAXIMUM_REQUEST_BYTES;if(q.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(maximum+1);if(bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
    private JsonNode respond(HttpServletResponse r,TaskProfessionalPackageAuthoringService.Response result)throws IOException{return TaskProfessionalPackageAuthoringController.respond(r,result,schema);}
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
