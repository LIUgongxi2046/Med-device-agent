package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;

/** mTLS DOCUMENT_REVIEW adapter endpoints. A generic host request cannot supply a MODEL_CALL token. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true",matchIfMissing=false)
public final class DocumentReviewInvocationController {
 private final ServiceCertificateIdentity identities;private final DocumentReviewInvocationService service;private final TaskInvocationHttpSchema schema;private final FormalInvocationUncertainty uncertainty;
 public DocumentReviewInvocationController(@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity") ServiceCertificateIdentity identities,DocumentReviewInvocationService service,@org.springframework.beans.factory.annotation.Qualifier("documentReviewInvocationHttpSchema") TaskInvocationHttpSchema schema,FormalInvocationUncertainty uncertainty){this.identities=identities;this.service=service;this.schema=schema;this.uncertainty=uncertainty;}
 @PostMapping(value="/internal/v1/document-review-invocations",consumes="application/json")
 public JsonNode reserve(HttpServletRequest q,HttpServletResponse response)throws Exception{identities.require(q);headers(q,true);var b=schema.read("Reserve",FormalInvocationController.body(q));var result=service.reserveExpected(q,q.getHeader("X-Run-Token"),schema.action(b),b.required("model_config_revision").textValue(),b.required("context_manifest_id").textValue(),b.required("request_parameters"));return output(response,new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("invocation_id",result.invocationId()).put("verification_id",result.verificationId()).put("status",result.status()).put("lease_generation",result.leaseGeneration().toString()));}
 @PostMapping(value="/internal/v1/document-review-invocations/{id}/begin-send",consumes="application/json")
 public JsonNode begin(HttpServletRequest q,HttpServletResponse response,@PathVariable("id")String id)throws Exception{
  identities.require(q);headers(q,true);WireValues.uuid(id);var body=schema.read("BeginSend",FormalInvocationController.body(q));var prepared=service.prepareInvocation(q,q.getHeader("X-Run-Token"),schema.action(body),id);var permit=service.beginDispatch(prepared,id);
  var data=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("invocation_id",id).put("new_send_admission",permit.isPresent()).put("dispatch_generation","1");
  if(permit.isPresent()){var p=permit.get();var actual=p.claimProviderBody();data.put("tenant_id",p.tenantId()).put("original_actor_id",p.originalActorId()).put("context_manifest_id",p.contextManifestId()).put("model_config_revision",p.modelConfigRevision()).put("dispatch_attempt_id",p.attemptId()).put("service_uri",p.serviceUri()).put("deadline_at",p.deadline().toInstant(java.time.ZoneOffset.UTC).toString()).put("credential_reference_id",p.credentialReferenceId()).put("credential_revision",p.credentialRevision()).put("canonical_provider_body_sha256",p.canonicalProviderBodySha256());data.set("provider_body",actual);}
  else for(String key:List.of("tenant_id","original_actor_id","context_manifest_id","model_config_revision","dispatch_attempt_id","service_uri","deadline_at","credential_reference_id","credential_revision","canonical_provider_body_sha256","provider_body"))data.putNull(key);
  return output(response,data);
 }
 @PostMapping(value="/internal/v1/document-review-invocations/{id}/receipts",consumes="application/json")
 public JsonNode receipts(HttpServletRequest q,HttpServletResponse response,@PathVariable("id")String id)throws Exception{identities.require(q);headers(q,false);WireValues.uuid(id);var body=schema.read("Receipt",FormalInvocationController.body(q));return output(response,service.recordOutcome(q,body.required("tenant_id").textValue(),body.required("original_actor_id").textValue(),id,ContentReadAuthorization.canonicalBytes(body.required("model_result")),ContentReadAuthorization.canonicalBytes(body.required("provider_capture"))).receipt());}
 @PostMapping(value="/internal/v1/document-review-invocations/{id}/reconciliations",consumes="application/json")
 public JsonNode unknown(HttpServletRequest q,HttpServletResponse response,@PathVariable("id")String id)throws Exception{identities.require(q);headers(q,false);WireValues.uuid(id);var b=schema.read("Unknown",FormalInvocationController.body(q));return output(response,service.recordUncertainty(uncertainty,q,b.required("tenant_id").textValue(),b.required("original_actor_id").textValue(),id,b.required("dispatch_attempt_id").textValue()));}
 static void recoveryHeaders(HttpServletRequest q){headers(q,false);for(String header:List.of("X-Run-Token","Idempotency-Key"))if(q.getHeader(header)!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
 static void headers(HttpServletRequest q,boolean token){if(q.getQueryString()!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);for(String key:token?List.of("X-Schema-Version","X-Run-Token"):List.of("X-Schema-Version")){var values=q.getHeaders(key);if(values==null||!values.hasMoreElements())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);var first=values.nextElement();if(first==null||first.isBlank()||values.hasMoreElements())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}if(!"1.0.0".equals(q.getHeader("X-Schema-Version")))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);}
 private JsonNode output(HttpServletResponse response,JsonNode body)throws Exception{return FormalInvocationResponses.envelope(body,"1",response);}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,switch(e.code()){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,IDEMPOTENCY_CONFLICT,ACTION_STALE,LEASE_STALE,SCOPE_STALE,CONTEXT_CHANGED->409;default->422;},e.code());}
}
