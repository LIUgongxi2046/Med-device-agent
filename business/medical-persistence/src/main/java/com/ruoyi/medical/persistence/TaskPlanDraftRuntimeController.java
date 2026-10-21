package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.PlanDraftProtocol;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.plan-draft-enabled",havingValue="true",matchIfMissing=false)
public final class TaskPlanDraftRuntimeController {
 private final TaskPlanDraftRuntimeObservations observations;private final TaskPlanDraftLaunchConsumer consumer;private final TaskPlanDraftHostBootstrap bootstrap;private final TaskPlanDraftHostOrchestrator host;private final TaskPlanDraftFinishOrchestrator finish;private final TaskPlanDraftCompletionLedger completions;private final TaskPlanDraftSupervisorGate gate;private final TaskPlanDraftInvocationBridge models;private final TaskPlanDraftRuntimeAdapter runtime;private final TaskPlanDraftSchema schema;private final ServiceCertificateIdentity services;
 public TaskPlanDraftRuntimeController(TaskPlanDraftRuntimeObservations observations,TaskPlanDraftLaunchConsumer consumer,TaskPlanDraftHostBootstrap bootstrap,TaskPlanDraftHostOrchestrator host,TaskPlanDraftFinishOrchestrator finish,TaskPlanDraftCompletionLedger completions,TaskPlanDraftSupervisorGate gate,TaskPlanDraftInvocationBridge models,TaskPlanDraftRuntimeAdapter runtime,TaskPlanDraftSchema schema,@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity services){this.observations=observations;this.consumer=consumer;this.bootstrap=bootstrap;this.host=host;this.finish=finish;this.completions=completions;this.gate=gate;this.models=models;this.runtime=runtime;this.schema=schema;this.services=services;}
 @PostMapping(value="/internal/v1/plan-draft-runtime/queued",consumes="application/json",produces="application/json")
 public JsonNode queued(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"tenant_id");return consumer.queued(q,text(b,"tenant_id"));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/launch",consumes="application/json",produces="application/json")
 public JsonNode launch(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"tenant_id","run_id","original_operation_id");return consumer.launch(q,text(b,"tenant_id"),text(b,"run_id"),text(b,"original_operation_id"));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/open",consumes="application/json",produces="application/json")
 public JsonNode open(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"reference");var reference=new JsonMapper().treeToValue(b.required("reference"),PlanDraftProtocol.Reference.class);return bootstrap.open(q,reference,host::prepare);}
 @PostMapping(value="/internal/v1/plan-draft-runtime/admit",consumes="application/json",produces="application/json")
 public JsonNode admit(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"binding","session_id");return bootstrap.existing(q,b.required("binding"),h->{session(h,b);var start=host.admit(q,h);var out=reply(b,"CURRENT");out.put("isolation_receipt_id",text(start,"isolation_receipt_id")).put("supervision_sha256",text(start,"supervision_sha256"));return out;});}
 @PostMapping(value="/internal/v1/plan-draft-runtime/heartbeat",consumes="application/json",produces="application/json")
 public JsonNode heartbeat(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"binding","session_id");return bootstrap.existing(q,b.required("binding"),h->{session(h,b);h.execute(s->{if(!"OPEN".equals(s.state()))deny();return null;});h.heartbeat();return reply(b,"CURRENT");});}
 @PostMapping(value="/internal/v1/plan-draft-runtime/bridge",consumes="application/json",produces="application/json")
 public JsonNode bridge(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"binding","session_id","method","route","body","idempotency_key","model_request_observation");return bootstrap.existing(q,b.required("binding"),h->{session(h,b);return models.forward(q,h,text(b,"session_id"),text(b,"method"),text(b,"route"),nullable(b.required("body")),nullableText(b.required("idempotency_key")),nullable(b.required("model_request_observation")));});}
 @PostMapping(value="/internal/v1/plan-draft-runtime/finish",consumes="application/json",produces="application/json")
 public JsonNode finish(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"binding","session_id","observation","files");var saved=bootstrap.existing(q,b.required("binding"),h->{session(h,b);return finish.save(q,h);});var receipt=completions.complete(q,text(b.required("binding"),"tenant_id"),text(b.required("binding"),"run_id"),saved);return new JsonMapper().valueToTree(receipt);}
 @PostMapping(value="/internal/v1/plan-draft-runtime/close-admission",consumes="application/json",produces="application/json")
 public JsonNode close(HttpServletRequest q,HttpServletResponse r)throws Exception{return observations.close(q,body(q,r));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/failure",consumes="application/json",produces="application/json")
 public JsonNode failure(HttpServletRequest q,HttpServletResponse r)throws Exception{return observations.failure(q,body(q,r));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/status",consumes="application/json",produces="application/json")
 public JsonNode status(HttpServletRequest q,HttpServletResponse r)throws Exception{return observations.status(q,body(q,r));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/recover",consumes="application/json",produces="application/json")
 public JsonNode recover(HttpServletRequest q,HttpServletResponse r)throws Exception{var b=body(q,r);closed(b,"tenant_id","run_id");return new JsonMapper().valueToTree(completions.recover(q,text(b,"tenant_id"),text(b,"run_id")));}
 @PostMapping(value="/internal/v1/plan-draft-runtime/supervisor-plan",consumes="application/json",produces="application/json")
 public JsonNode plan(HttpServletRequest q,HttpServletResponse r)throws Exception{return gate.read(q,body(q,r),false);}
 @PostMapping(value="/internal/v1/plan-draft-runtime/supervisor-admission",consumes="application/json",produces="application/json")
 public JsonNode gate(HttpServletRequest q,HttpServletResponse r)throws Exception{return gate.read(q,body(q,r),true);}
 private void session(TaskPlanDraftHostAccess.Handle h,JsonNode b)throws Exception{String session=text(b,"session_id");WireValues.uuid(session);h.prepare(e->{if(!session.equals(e.sessionId()))deny();return null;});}
 private JsonNode body(HttpServletRequest q,HttpServletResponse r)throws Exception{r.setHeader("Cache-Control","no-store");services.require(q);if(q.getQueryString()!=null||q.getHeader("Origin")!=null||q.getHeader("Sec-Fetch-Site")!=null)deny();int max=16777216;if(q.getContentLengthLong()>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(max+1);if(bytes.length==0||bytes.length>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return schema.read(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));}
 private static com.fasterxml.jackson.databind.node.ObjectNode reply(JsonNode b,String state){var out=new JsonMapper().createObjectNode().put("session_id",text(b,"session_id")).put("admission",state);out.set("binding",b.required("binding").deepCopy());return out;}
 private static void closed(JsonNode n,String... fields){ProfessionalRuntimeObservations.closed(n,Set.of(fields));}private static JsonNode nullable(JsonNode n){return n.isNull()?null:n;}private static String nullableText(JsonNode n){return n.isNull()?null:n.textValue();}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())deny();return n.required(k).textValue();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 @ExceptionHandler({TenantAccessException.class,SecurityException.class})public void denied(Exception error,HttpServletResponse response)throws java.io.IOException{var code=error instanceof TenantAccessException e?e.code():ErrorCode.ACCESS_DENIED;response.setHeader("Cache-Control","no-store");new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(response,code==ErrorCode.ACCESS_DENIED?403:code==ErrorCode.DEPENDENCY_UNAVAILABLE?503:422,code);}
}
