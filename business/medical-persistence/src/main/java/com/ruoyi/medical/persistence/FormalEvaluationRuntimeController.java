package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Service-only EVAL host channel. No caller observations, output bytes or success flags accepted. */
@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationRuntimeController {
 private final FormalEvaluationRuntimeService runtime;private final FormalEvaluationSupervisorGate gate;
 private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationRuntimeController(FormalEvaluationRuntimeService runtime,FormalEvaluationSupervisorGate gate){this.runtime=runtime;this.gate=gate;}
 private record Route(FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,String sessionId){}
 private JsonNode body(HttpServletRequest q,HttpServletResponse r)throws Exception{DocumentReviewInvocationController.recoveryHeaders(q);r.setHeader("Cache-Control","no-store");return json.readTree(FormalInvocationController.body(q));}
 private Route route(JsonNode b,boolean session)throws Exception{ProfessionalRuntimeObservations.closed(b,session?Set.of("schema_version","reference","step","session_id"):Set.of("schema_version","reference","step"));if(!"1.0.0".equals(b.required("schema_version").asText()))bad();var copy=(com.fasterxml.jackson.databind.node.ObjectNode)b.deepCopy();copy.remove("schema_version");var route=json.treeToValue(copy,Route.class);if(!route.reference().steps().contains(route.step()))bad();if(session)WireValues.uuid(route.sessionId());return route;}
 @PostMapping("/internal/v1/evaluation-runtime/await-launch") public JsonNode await(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);ProfessionalRuntimeObservations.closed(b,Set.of("schema_version","reference"));if(!"1.0.0".equals(b.required("schema_version").asText()))bad();return FormalInvocationResponses.envelope(runtime.awaitLaunch(q,json.treeToValue(b.required("reference"),FormalEvaluationProtocol.Reference.class)),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/complete-cell") public JsonNode complete(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);ProfessionalRuntimeObservations.closed(b,Set.of("schema_version","reference","receipts"));if(!"1.0.0".equals(b.required("schema_version").asText())||!b.required("receipts").isArray())bad();var receipts=new ArrayList<FormalEvaluationProtocol.NodeReceipt>();for(var receipt:b.required("receipts"))receipts.add(json.treeToValue(receipt,FormalEvaluationProtocol.NodeReceipt.class));return FormalInvocationResponses.envelope(json.valueToTree(runtime.complete(q,json.treeToValue(b.required("reference"),FormalEvaluationProtocol.Reference.class),receipts)),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/cancel-plan") public JsonNode cancelPlan(HttpServletRequest q,HttpServletResponse r)throws Exception{var ref=reference(body(q,r));return FormalInvocationResponses.envelope(runtime.cancelPlan(q,ref),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/cancel-cell") public JsonNode cancel(HttpServletRequest q,HttpServletResponse r)throws Exception{var ref=reference(body(q,r));return FormalInvocationResponses.envelope(json.valueToTree(runtime.cancel(q,ref)),"1",r);}
 private FormalEvaluationProtocol.Reference reference(JsonNode b)throws Exception{ProfessionalRuntimeObservations.closed(b,Set.of("schema_version","reference"));if(!"1.0.0".equals(b.required("schema_version").asText()))bad();return json.treeToValue(b.required("reference"),FormalEvaluationProtocol.Reference.class);}
 @PostMapping("/internal/v1/evaluation-runtime/open") public JsonNode open(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),false);return FormalInvocationResponses.envelope(runtime.open(q,b.reference(),b.step()),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/admit") public JsonNode admit(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),true);return FormalInvocationResponses.envelope(runtime.admit(q,b.reference(),b.step(),b.sessionId()),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/heartbeat") public JsonNode heartbeat(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),true);return FormalInvocationResponses.envelope(runtime.heartbeat(q,b.reference(),b.step(),b.sessionId()),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/finish") public JsonNode finish(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),true);return FormalInvocationResponses.envelope(json.valueToTree(runtime.finish(q,b.reference(),b.step(),b.sessionId())),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/completed-original") public JsonNode original(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),false);return FormalInvocationResponses.envelope(json.valueToTree(runtime.original(q,b.reference(),b.step())),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/close-admission") public JsonNode close(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),true);return FormalInvocationResponses.envelope(runtime.closeAdmission(q,b.reference(),b.step(),b.sessionId()),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/recover-node") public JsonNode recover(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=route(body(q,r),false);return FormalInvocationResponses.envelope(json.valueToTree(runtime.recover(q,b.reference(),b.step())),"1",r);}
 @PostMapping("/internal/v1/evaluation-runtime/supervisor-plan") public JsonNode plan(HttpServletRequest q,HttpServletResponse r)throws Exception{return gate.read(q,body(q,r),false);}
 @PostMapping("/internal/v1/evaluation-runtime/supervisor-admission") public JsonNode admission(HttpServletRequest q,HttpServletResponse r)throws Exception{return gate.read(q,body(q,r),true);}
 private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(TenantAccessException.class)public void denied(TenantAccessException error,HttpServletResponse response)throws java.io.IOException{response.setHeader("Cache-Control","no-store");new IdentityErrorResponseWriter().write(response,switch(error.code()){case ACCESS_DENIED->403;case SOURCE_NOT_READY->404;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case LEASE_STALE,SCOPE_STALE,CONTEXT_CHANGED,REVISION_CONFLICT->409;default->422;},error.code());}
}
