package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.*;
/** N07 C07 HTTP projection with separate MODEL_CALL token issuance. */
public final class DocumentReviewInvocationBridge {
 private final DocumentReviewInvocationContextOrchestrator contexts;private final DocumentReviewInvocationService service;private final TaskInvocationLocalBridge adapter;private final SchemaCatalogue schemas;private final Duration lifetime;
 public DocumentReviewInvocationBridge(DocumentReviewInvocationContextOrchestrator contexts,DocumentReviewInvocationService service,TaskInvocationLocalBridge adapter,Duration lifetime)throws Exception {this.contexts=contexts;this.service=service;this.adapter=adapter;this.lifetime=lifetime;schemas=SchemaCatalogue.fromClasspath();if(lifetime.isZero()||lifetime.isNegative()||lifetime.compareTo(Duration.ofMinutes(5))>0)throw new IllegalArgumentException("DOCUMENT_REVIEW_MODEL_TOKEN_LIFETIME_REQUIRED");}
 public JsonNode forward(HttpServletRequest http,DocumentReviewHostAccess.Handle handle,String session,String method,String route,JsonNode body,String key,JsonNode observation)throws Exception {
  WireValues.uuid(session);
  if("POST".equals(method)&&"/v1/model-contexts".equals(route)){if(key!=null||observation==null||body==null||!body.isObject())bad();ProfessionalRuntimeObservations.closed(observation,Set.of("request_id","ordinal","receipt_id","sha256"));WireValues.uuid(text(observation,"request_id"));WireValues.uuid(text(observation,"receipt_id"));WireValues.hash(text(observation,"sha256"));return contexts.prepare(handle,session,text(observation,"request_id"),observation).streamRequest();}
  if(observation!=null)bad();
  if("POST".equals(method)&&"/v1/model-streams".equals(route)){schemas.validate("model-stream",body,"StreamRequest");WireValues.uuid(key);var bound=contexts.bindExisting(handle,session,text(body,"context_manifest_id"));if(!key.equals(bound.action().actionId())||!bound.streamRequest().equals(body))bad();adapter.requireReady();var prepared=service.issueAndPrepare(http,bound,lifetime);var reserved=service.reserve(prepared);adapter.execute(service.dispatchCall(prepared,reserved.invocationId()));return handle.execute(s->service.status(s,reserved.invocationId()));}
  if("GET".equals(method)&&route!=null&&route.startsWith("/v1/model-results/")){if(body!=null&&!body.isNull()||key!=null)bad();String artifact=route.substring("/v1/model-results/".length());WireValues.uuid(artifact);return handle.execute(s->service.readResult(s,artifact));}
  throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
 }
 private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
}
