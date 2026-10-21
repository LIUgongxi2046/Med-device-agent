package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Normal current-session endpoints. Existing security configuration verifies CSRF and Origin. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.professional-runtime",name="task-enabled",havingValue="true")
public final class TaskResearchWaitingController {
    private final ApplicationSessionIdentity identities;private final TaskResearchWaitingService service;private final PermissionSchema json;
    public TaskResearchWaitingController(ApplicationSessionIdentity identities,TaskResearchWaitingService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
    @GetMapping("/api/work-items/{waiting}/research")
    public JsonNode read(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,false,false);return FormalEvaluationContextHttp.envelope(service.read(identities.requireCurrent(q,auth),context,waiting),s,json);
    }
    @PostMapping(value="/api/work-items/{waiting}/research/decisions",consumes="application/json")
    public JsonNode decide(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,true);return FormalEvaluationContextHttp.envelope(service.decide(identities.requireCurrent(q,auth),context,waiting,FormalEvaluationContextHttp.key(q),FormalEvaluationContextHttp.body(q,16384)),s,json);
    }
    @PostMapping(value="/api/work-items/{waiting}/research/recovery",consumes="application/json")
    public JsonNode recover(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);return FormalEvaluationContextHttp.envelope(service.recover(identities.requireCurrent(q,auth),context,waiting,FormalEvaluationContextHttp.body(q,16384)),s,json);
    }
    @ExceptionHandler(Exception.class)public void failure(Exception e,HttpServletResponse s)throws java.io.IOException{FormalEvaluationContextHttp.failure(e,s);}
}
