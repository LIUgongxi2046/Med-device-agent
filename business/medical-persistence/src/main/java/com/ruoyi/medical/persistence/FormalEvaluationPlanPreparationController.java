package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.io.IOException;

@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-plan",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationPlanPreparationController {
    private final ApplicationSessionIdentity identities;
    private final ObjectProvider<FormalEvaluationPlanPreparationService> services;
    private final ObjectProvider<FormalEvaluationPlanPreparationReadService> readers;
    private final PermissionSchema json;
    public FormalEvaluationPlanPreparationController(ApplicationSessionIdentity identities,ObjectProvider<FormalEvaluationPlanPreparationService> services,ObjectProvider<FormalEvaluationPlanPreparationReadService> readers,PermissionSchema json){this.identities=identities;this.services=services;this.readers=readers;this.json=json;}
    @PostMapping(value="/api/formal-evaluation-plan-preparations/executions",consumes="application/json")
    public JsonNode execution(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,true);var actor=identities.requireCurrent(q,auth);
        var data=service().prepareExecution(actor,context,FormalEvaluationContextHttp.key(q),body(q));s.setStatus(201);return FormalEvaluationContextHttp.envelope(data,s,json);
    }
    @PostMapping(value="/api/formal-evaluation-plan-preparations/executions/recovery",consumes="application/json")
    public JsonNode recoverExecution(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);var actor=identities.requireCurrent(q,auth);
        return FormalEvaluationContextHttp.envelope(reader().recoverExecution(actor,context,body(q)),s,json);
    }
    @PostMapping(value="/api/formal-evaluation-plan-preparations/preview",consumes="application/json")
    public JsonNode preview(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);var actor=identities.requireCurrent(q,auth);
        return FormalEvaluationContextHttp.envelope(service().preview(actor,context,body(q)),s,json);
    }
    @PostMapping(value="/api/formal-evaluation-plan-preparations",consumes="application/json")
    public JsonNode prepare(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,true);var actor=identities.requireCurrent(q,auth);
        var data=service().prepare(actor,context,FormalEvaluationContextHttp.key(q),body(q));s.setStatus(201);return FormalEvaluationContextHttp.envelope(data,s,json);
    }
    @PostMapping(value="/api/formal-evaluation-plan-preparations/status",consumes="application/json")
    public JsonNode status(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);var actor=identities.requireCurrent(q,auth);
        return FormalEvaluationContextHttp.envelope(reader().status(actor,context,body(q)),s,json);
    }
    @PostMapping(value="/api/formal-evaluation-plan-preparations/recovery",consumes="application/json")
    public JsonNode recover(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);var actor=identities.requireCurrent(q,auth);
        return FormalEvaluationContextHttp.envelope(service().recover(actor,context,body(q)),s,json);
    }
    private byte[] body(HttpServletRequest q)throws IOException{return FormalEvaluationContextHttp.body(q,FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES);}
    private FormalEvaluationPlanPreparationReadService reader(){var service=readers.getIfUnique();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return service;}
    private FormalEvaluationPlanPreparationService service(){var service=services.getIfUnique();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return service;}
    @ExceptionHandler(Exception.class) public void failure(Exception failure,HttpServletResponse response)throws IOException{FormalEvaluationContextHttp.failure(failure,response);}
}
