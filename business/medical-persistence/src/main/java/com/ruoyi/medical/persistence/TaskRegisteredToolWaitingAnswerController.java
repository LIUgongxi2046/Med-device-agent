package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Current human session, normal CSRF/Origin enforcement, no worker credentials. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.server-enabled"},havingValue="true",matchIfMissing=false)
public final class TaskRegisteredToolWaitingAnswerController {
 private final ApplicationSessionIdentity identities;private final TaskRegisteredToolWaitingAnswerService service;private final PermissionSchema json;
 public TaskRegisteredToolWaitingAnswerController(ApplicationSessionIdentity identities,TaskRegisteredToolWaitingAnswerService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @GetMapping("/api/work-items/{waiting}/registered-question") public JsonNode read(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{String context=FormalEvaluationContextHttp.headers(q,s,false,false);return FormalEvaluationContextHttp.envelope(service.read(identities.requireCurrent(q,auth),context,waiting),s,json);}
 @PostMapping(value="/api/work-items/{waiting}/registered-question/answers",consumes="application/json") public JsonNode answer(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{String context=FormalEvaluationContextHttp.headers(q,s,true,true);return FormalEvaluationContextHttp.envelope(service.answer(identities.requireCurrent(q,auth),context,waiting,FormalEvaluationContextHttp.key(q),FormalEvaluationContextHttp.body(q,32768)),s,json);}
 @PostMapping(value="/api/work-items/{waiting}/registered-question/answers/recovery",consumes="application/json") public JsonNode recover(@PathVariable String waiting,HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception{String context=FormalEvaluationContextHttp.headers(q,s,true,false);return FormalEvaluationContextHttp.envelope(service.recover(identities.requireCurrent(q,auth),context,waiting,FormalEvaluationContextHttp.body(q,32768)),s,json);}
 @ExceptionHandler(Exception.class)public void failure(Exception e,HttpServletResponse s)throws java.io.IOException{FormalEvaluationContextHttp.failure(e,s);}
}
