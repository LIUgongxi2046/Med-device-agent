package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** An explicit producer endpoint, not a path for posting professional outcomes or score claims. */
@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationBootstrapController {
    private final ApplicationSessionIdentity identities;private final FormalEvaluationBootstrapService service;private final PermissionSchema json;
    public FormalEvaluationBootstrapController(ApplicationSessionIdentity identities,FormalEvaluationBootstrapService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
    @PostMapping(value="/api/formal-evaluation-executions/bootstrap",consumes="application/json")
    public JsonNode bootstrap(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        String context=FormalEvaluationContextHttp.headers(request,response,true,true);var actor=identities.requireCurrent(request,authentication);
        var result=service.bootstrap(actor,context,FormalEvaluationContextHttp.key(request),FormalEvaluationContextHttp.body(request,65536));response.setStatus(202);return FormalEvaluationContextHttp.envelope(result,response,json);
    }
    @PostMapping(value="/api/formal-evaluation-executions/recovery",consumes="application/json")
    public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        String context=FormalEvaluationContextHttp.headers(request,response,true,false);var actor=identities.requireCurrent(request,authentication);
        return FormalEvaluationContextHttp.envelope(service.recover(actor,context,FormalEvaluationContextHttp.body(request,65536)),response,json);
    }
}
