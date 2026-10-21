package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** mTLS EVAL routes. No human session, TASK token or client-supplied provider bytes can begin a send. */
@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationInvocationController {
 private final FormalEvaluationControlAccess access;private final FormalEvaluationNodeInputs inputs;private final FormalEvaluationNodeRuntime runtime;private final FormalEvaluationInvocationService service;private final FormalInvocationUncertainty uncertainty;private final TaskInvocationHttpSchema receipts;
 private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationInvocationController(FormalEvaluationControlAccess access,FormalEvaluationNodeInputs inputs,FormalEvaluationNodeRuntime runtime,FormalEvaluationInvocationService service,FormalInvocationUncertainty uncertainty,FormalInvocationSchema schema)throws Exception{this.access=access;this.inputs=inputs;this.runtime=runtime;this.service=service;this.uncertainty=uncertainty;receipts=new TaskInvocationHttpSchema(schema);}
 private record Route(FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,String sessionId,String requestId){}
 private Route route(JsonNode raw)throws Exception {ProfessionalRuntimeObservations.closed(raw,Set.of("reference","step","session_id","request_id"));var r=json.treeToValue(raw,Route.class);WireValues.uuid(r.sessionId());WireValues.uuid(r.requestId());if(!r.reference().steps().contains(r.step()))bad();return r;}
 @PostMapping(value="/internal/v1/evaluation-runtime/model",consumes="application/json")
 public JsonNode model(HttpServletRequest http,HttpServletResponse response)throws Exception {
  headers(http);var raw=json.readTree(FormalInvocationController.body(http));ProfessionalRuntimeObservations.closed(raw,Set.of("schema_version","reference","step","session_id","request_id"));if(!"1.0.0".equals(raw.required("schema_version").asText()))bad();var action=raw.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)action).remove("schema_version");var r=route(action);
  var result=access.withReads(http,r.reference.tenantId(),r.reference.runId(),reads->ThrowingSupplier.of(()->{var input=inputs.read(reads,r.step);Supplier<FormalEvaluationNodeRuntime.Session> sessions=()->ThrowingSupplier.of(()->runtime.requireSession(currentAdmission(reads),r.reference,r.step,input,reads.workerService(),r.sessionId)).get();return service.execute(reads,http,sessions,r.requestId,action);}).get());return FormalInvocationResponses.envelope(result,"1",response);
 }
 // Suppliers run inside Reads.execute and receive exactly that transaction's admission.
 private EnterpriseAccess.Admission currentAdmission(FormalEvaluationControlAccess.Reads reads){return reads.execute(a->a);}
 @PostMapping(value="/internal/v1/evaluation-invocations/{id}/begin-send",consumes="application/json")
 public JsonNode begin(HttpServletRequest http,HttpServletResponse response,@PathVariable("id")String id)throws Exception {
  headers(http);WireValues.uuid(id);var body=json.readTree(FormalInvocationController.body(http));ProfessionalRuntimeObservations.closed(body,Set.of("schema_version","evaluation_action","expected_dispatch_generation"));if(!"1.0.0".equals(body.required("schema_version").asText())||!"0".equals(body.required("expected_dispatch_generation").asText()))bad();var r=route(body.required("evaluation_action"));
  var data=access.withReads(http,r.reference.tenantId(),r.reference.runId(),reads->ThrowingSupplier.of(()->{var input=inputs.read(reads,r.step);Supplier<FormalEvaluationNodeRuntime.Session> sessions=()->ThrowingSupplier.of(()->runtime.requireSession(currentAdmission(reads),r.reference,r.step,input,reads.workerService(),r.sessionId)).get();var prepared=service.prepare(reads,sessions,r.requestId);var permit=service.begin(http,prepared,id);var out=json.createObjectNode().put("invocation_id",id).put("new_send_admission",permit.isPresent()).put("dispatch_generation","1");if(permit.isPresent()){var p=permit.get();var bytes=p.claimProviderBody();out.put("tenant_id",p.tenantId()).put("original_actor_id",p.originalActorId()).put("context_manifest_id",p.contextManifestId()).put("model_config_revision",p.modelConfigRevision()).put("dispatch_attempt_id",p.attemptId()).put("service_uri",p.serviceUri()).put("deadline_at",p.deadline().toInstant(java.time.ZoneOffset.UTC).toString()).put("credential_reference_id",p.credentialReferenceId()).put("credential_revision",p.credentialRevision()).put("canonical_provider_body_sha256",p.canonicalProviderBodySha256());out.set("provider_body",bytes);}else for(String key:List.of("tenant_id","original_actor_id","context_manifest_id","model_config_revision","dispatch_attempt_id","service_uri","deadline_at","credential_reference_id","credential_revision","canonical_provider_body_sha256","provider_body"))out.putNull(key);return out;}).get());return FormalInvocationResponses.envelope(data,"1",response);
 }
 @PostMapping(value="/internal/v1/evaluation-invocations/{id}/receipts",consumes="application/json")
 public JsonNode receipt(HttpServletRequest http,HttpServletResponse response,@PathVariable("id")String id)throws Exception {headers(http);WireValues.uuid(id);var body=receipts.read("Receipt",FormalInvocationController.body(http));return FormalInvocationResponses.envelope(service.recordOutcome(http,body.required("tenant_id").asText(),body.required("original_actor_id").asText(),id,ContentReadAuthorization.canonicalBytes(body.required("model_result")),ContentReadAuthorization.canonicalBytes(body.required("provider_capture"))).receipt(),"1",response);}
 @PostMapping(value="/internal/v1/evaluation-invocations/{id}/reconciliations",consumes="application/json")
 public JsonNode uncertainty(HttpServletRequest http,HttpServletResponse response,@PathVariable("id")String id)throws Exception {headers(http);WireValues.uuid(id);var body=receipts.read("Unknown",FormalInvocationController.body(http));return FormalInvocationResponses.envelope(service.recordUncertainty(uncertainty,http,body.required("tenant_id").asText(),body.required("original_actor_id").asText(),id,body.required("dispatch_attempt_id").asText()),"1",response);}
 @PostMapping(value="/internal/v1/evaluation-invocations/{id}/recover",consumes="application/json")
 public JsonNode recover(HttpServletRequest http,HttpServletResponse response,@PathVariable("id")String id)throws Exception {headers(http);WireValues.uuid(id);var b=json.readTree(FormalInvocationController.body(http));ProfessionalRuntimeObservations.closed(b,Set.of("schema_version","tenant_id","original_actor_id","run_id","step_id","dispatch_attempt_id"));if(!"1.0.0".equals(b.required("schema_version").asText()))bad();return FormalInvocationResponses.envelope(service.recover(http,b.required("tenant_id").asText(),b.required("original_actor_id").asText(),b.required("run_id").asText(),b.required("step_id").asText(),id,b.required("dispatch_attempt_id").asText()),"1",response);}
 private static void headers(HttpServletRequest q){DocumentReviewInvocationController.recoveryHeaders(q);}
 private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,switch(e.code()){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,IDEMPOTENCY_CONFLICT,ACTION_STALE,LEASE_STALE,SCOPE_STALE,CONTEXT_CHANGED->409;default->422;},e.code());}
}
