package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.TaskRunStageSchemaV2;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Independent service-only activity ingress; browser sessions and caller actors
 * never become TASK authority. Disabled until the explicit integration gate opens. */
@RestController
@ConditionalOnProperty(name={"medical.task-run.waiting-v2.server-enabled","medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-strict-field.enabled"},havingValue="true",matchIfMissing=false)
public final class TaskRunStageControllerV2 {
    private final ServiceCertificateIdentity identity;private final TaskRunStageEndpointFactoryV2 factory;
    private final TaskRunStageSchemaV2 schema;private final int maximum;
    public TaskRunStageControllerV2(@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identity,TaskRunStageEndpointFactoryV2 factory,
            @Qualifier("taskRunStageServerSchemaV2")TaskRunStageSchemaV2 schema,@Value("${medical.task-run.waiting-v2.maximum-request-bytes}")int maximum){
        if(maximum<1024||maximum>16777216)throw new IllegalArgumentException("ACTIVITY_CAPACITY_REQUIRED");this.identity=identity;this.factory=factory;this.schema=schema;this.maximum=maximum;
    }
    @PostMapping(value=TaskRunStageSchemaV2.ROUTE,consumes="application/json",produces="application/json")
    public JsonNode activity(HttpServletRequest http,HttpServletResponse response)throws Exception{
        response.setHeader("Cache-Control","no-store");
        if(!TaskRunActivitySecurityConfiguration.authorize(identity,http))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(http.getQueryString()!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if(http.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=http.getInputStream().readNBytes(maximum+1);if(bytes.length==0||bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var request=schema.read(bytes);var activities=factory.create(http);var result=switch(request.operation()){case "EXECUTE_STAGE"->activities.executeStage(request.reference(),request.stageKind(),request.commandId());case "RECOVER_STAGE"->activities.recoverStage(request.reference(),request.stageKind(),request.commandId());case "COMMIT_STAGE"->activities.commitStage(request.reference(),request.completion());default->throw new IllegalArgumentException();};return schema.response(request,result);
    }
    @ExceptionHandler({TenantAccessException.class,SecurityException.class,com.fasterxml.jackson.core.JsonProcessingException.class,java.nio.charset.CharacterCodingException.class,IllegalArgumentException.class})
    public void denied(Exception failure,HttpServletResponse response)throws java.io.IOException{
        var code=failure instanceof TenantAccessException e?e.code():failure instanceof SecurityException?ErrorCode.ACCESS_DENIED:ErrorCode.SCHEMA_INVALID;
        response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setStatus(switch(code){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,SCOPE_STALE,CONTEXT_CHANGED,IDEMPOTENCY_CONFLICT->409;default->422;});
        response.getOutputStream().write(("{\"error\":\""+code.name()+"\"}").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
