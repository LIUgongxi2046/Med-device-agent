package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="medical.professional-runtime.section-writer-enabled",havingValue="true",matchIfMissing=false)
public final class TaskSectionWriterCandidateController {
    private final TaskSectionWriterHostBootstrap bootstrap;private final TaskSectionWriterFinishOrchestrator finish;private final TaskSectionWriterSchema json;
    public TaskSectionWriterCandidateController(TaskSectionWriterHostBootstrap bootstrap,TaskSectionWriterFinishOrchestrator finish,TaskSectionWriterSchema json){this.bootstrap=bootstrap;this.finish=finish;this.json=json;}
    @PostMapping("/internal/v1/section-writer-runtime/finish")
    public JsonNode finish(HttpServletRequest request,HttpServletResponse response)throws Exception {
        response.setHeader("Cache-Control","no-store");if(request.getQueryString()!=null||request.getHeader("Origin")!=null||request.getHeader("Sec-Fetch-Site")!=null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var body=json.read(request.getInputStream().readNBytes(TaskSectionWriterSchema.MAXIMUM_BYTES+1));ProfessionalRuntimeObservations.validate(body,"HOST_FINISH");
        var host=bootstrap.open(request,ProfessionalRuntimeObservations.jsonBinding(body.required("binding")));
        host.prepare(envelope->{if(!envelope.sessionId().equals(body.required("session_id").textValue())||!envelope.binding().equals(body.required("binding")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return null;});
        // Observation/final_response/files never confer successful closure or model settlement.
        return finish.finish(request,host);
    }
}
