package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Genuine human pre-plan jobs. No launch, worker, source or confirmation capability is exposed. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.plan-draft-enabled",havingValue="true",matchIfMissing=false)
public final class TaskPlanDraftController {
    private final ApplicationSessionIdentity identities;private final TaskPlanDraftService service;
    public TaskPlanDraftController(ApplicationSessionIdentity identities,TaskPlanDraftService service){this.identities=identities;this.service=service;}
    @PostMapping(value="/api/projects/{projectId}/tasks/{taskId}/plan-drafts",consumes="application/json")
    public JsonNode start(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String taskId)throws Exception {String context=headers(q),key=SimulationInputArtifactController.one(q,"Idempotency-Key");return respond(r,service.start(identities.requireCurrent(q,auth),context,projectId,taskId,key,body(q)));}
    @GetMapping("/api/projects/{projectId}/tasks/{taskId}/plan-drafts/{runId}")
    public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String taskId,@PathVariable String runId)throws Exception {String context=headers(q);if(q.getContentLengthLong()>0||q.getHeader("Transfer-Encoding")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return respond(r,service.read(identities.requireCurrent(q,auth),context,projectId,taskId,runId));}
    @PostMapping(value="/api/projects/{projectId}/tasks/{taskId}/plan-drafts/{runId}/adoptions",consumes="application/json")
    public JsonNode adopt(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String taskId,@PathVariable String runId)throws Exception {String context=headers(q),key=SimulationInputArtifactController.one(q,"Idempotency-Key");return respond(r,service.adopt(identities.requireCurrent(q,auth),context,projectId,taskId,runId,key,body(q)));}
    private static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
    private static byte[] body(HttpServletRequest q)throws IOException {if(q.getContentLengthLong()>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(1048577);if(bytes.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
    private static JsonNode respond(HttpServletResponse r,TaskPlanDraftService.Reply reply){r.setStatus(reply.status());r.setHeader("X-Schema-Version","1.0.0");r.setHeader("Cache-Control","no-store");return reply.envelope();}
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
