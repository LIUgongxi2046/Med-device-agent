package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="medical.professional-runtime.source-verifier-enabled",havingValue="true",matchIfMissing=false)
public final class TaskSourceVerifierReviewController {
    private final TaskSourceVerifierHostBootstrap bootstrap;private final TaskSourceVerifierFinishOrchestrator finish;private final TaskSourceVerifierSchema json;private final TaskSourceVerifierDispatchJournal dispatches;
    public TaskSourceVerifierReviewController(TaskSourceVerifierHostBootstrap bootstrap,TaskSourceVerifierFinishOrchestrator finish,TaskSourceVerifierSchema json,TaskSourceVerifierDispatchJournal dispatches){this.dispatches=dispatches;this.bootstrap=bootstrap;this.finish=finish;this.json=json;}
    @PostMapping("/internal/v1/source-verifier-runtime/finish")
    public JsonNode finish(HttpServletRequest request,HttpServletResponse response)throws Exception {
        response.setHeader("Cache-Control","no-store");if(request.getQueryString()!=null||request.getHeader("Origin")!=null||request.getHeader("Sec-Fetch-Site")!=null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var body=json.read(request.getInputStream().readNBytes(TaskSourceVerifierSchema.MAXIMUM_BYTES+1));ProfessionalRuntimeObservations.validate(body,"HOST_FINISH");
        var host=bootstrap.open(request,ProfessionalRuntimeObservations.jsonBinding(body.required("binding")));
        host.prepare(envelope->{dispatches.requireWorkerLaunch(envelope);if(!envelope.sessionId().equals(body.required("session_id").textValue())||!envelope.binding().equals(body.required("binding")))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return null;});
        // Observation/final_response/files never confer successful closure or model settlement.
        return finish.finish(request,host);
    }
}
