package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Human creation is separate from mutually authenticated original launch/recovery. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true",matchIfMissing=false)
public final class DocumentReviewExecutionController {
    private final ApplicationSessionIdentity identities;private final DocumentReviewStartService starts;private final DocumentReviewLaunchService launches;
    public DocumentReviewExecutionController(ApplicationSessionIdentity identities,DocumentReviewStartService starts,DocumentReviewLaunchService launches){this.identities=identities;this.starts=starts;this.launches=launches;}
    @PostMapping(value="/api/document-review-intents/{intentId}/executions",consumes="application/json")
    public JsonNode start(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String intentId)throws Exception {
        if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version"))||q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getContentLengthLong()>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var body=q.getInputStream().readNBytes(1048577);if(body.length>1048576)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var value=starts.start(identities.requireCurrent(q,auth),context,intentId,body);r.setStatus(202);r.setHeader("Cache-Control","no-store");return value;
    }
    public record Launch(String tenantId,String runId,String originalOperationId){}
    @PostMapping(value="/internal/v1/document-review-executions/launch",consumes="application/json")
    public JsonNode launch(HttpServletRequest q,@RequestBody Launch command)throws Exception{return launches.consume(q,command.tenantId(),command.runId(),command.originalOperationId());}
    @PostMapping(value="/internal/v1/document-review-executions/recover",consumes="application/json")
    public JsonNode recover(HttpServletRequest q,@RequestBody Launch command)throws Exception{return launches.recover(q,command.tenantId(),command.runId(),command.originalOperationId());}
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
}
