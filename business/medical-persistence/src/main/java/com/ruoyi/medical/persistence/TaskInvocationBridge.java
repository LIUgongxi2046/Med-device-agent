package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Fixed C07 route adapter. Host controls only private session transport; MODEL_CALL is
 * independently bound/issued from immutable context. Replies come from actual076 journals. */
public final class TaskInvocationBridge {
 private final TaskProfessionalHostAccess hosts;private final TaskInvocationContextOrchestrator contexts;private final TaskInvocationService service;private final TaskInvocationLocalBridge adapter;private final SchemaCatalogue schemas;private final Duration lifetime;
 public TaskInvocationBridge(TaskProfessionalHostAccess hosts,TaskInvocationContextOrchestrator contexts,TaskInvocationService service,TaskInvocationLocalBridge adapter,Duration lifetime)throws Exception{this.hosts=hosts;this.contexts=contexts;this.service=service;this.adapter=adapter;this.lifetime=lifetime;schemas=SchemaCatalogue.fromClasspath();if(lifetime.isZero()||lifetime.isNegative()||lifetime.compareTo(Duration.ofMinutes(5))>0)throw new IllegalArgumentException("TASK_MODEL_TOKEN_LIFETIME_REQUIRED");}
 public JsonNode forward(HttpServletRequest http,TaskProfessionalHostAccess.Handle handle,String session,String method,String route,JsonNode body,String key,JsonNode observation)throws Exception {
  WireValues.uuid(session);
  if("POST".equals(method)&&"/v1/model-contexts".equals(route)){
   if(key!=null||observation==null)invalid();fields(observation,Set.of("request_id","ordinal","receipt_id","sha256"));WireValues.uuid(text(observation,"request_id"));WireValues.uuid(text(observation,"receipt_id"));WireValues.hash(text(observation,"sha256"));if(!observation.required("ordinal").isIntegralNumber()||!observation.required("ordinal").canConvertToLong()||observation.required("ordinal").longValue()<1)invalid();
   // Caller projection is never authority. Only the authenticated supervisor's original
   // complete SDK request is consumed by context orchestration.
   if(body==null||!body.isObject())invalid();return contexts.prepare(handle,session,text(observation,"request_id"),observation).streamRequest();
  }
  if(observation!=null)invalid();
  if("POST".equals(method)&&"/v1/model-streams".equals(route)){
   schemas.validate("model-stream",body,"StreamRequest");WireValues.uuid(key);var bound=contexts.bindExisting(handle,session,text(body,"context_manifest_id"));
   if(!key.equals(bound.action().actionId())||!FormalTaskNodeAuthority.sameEvidence(bound.streamRequest(),body))invalid();
   adapter.requireReady();var prepared=service.issueAndPrepare(http,bound,lifetime);var reserved=service.reserve(prepared);
   // Even when provider transport becomes ambiguous, no retry/new invocation occurs.
   // The same reservation is selected by the original context/action idempotency binding.
   adapter.execute(service.dispatchCall(prepared,reserved.invocationId()));
   return hosts.execute(handle,a->ThrowingSupplier.of(()->service.status(hosts.session(handle,a,session),reserved.invocationId())).get());
  }
  if("GET".equals(method)&&route!=null&&route.startsWith("/v1/model-results/")){
   if(body!=null&&!body.isNull()||key!=null)invalid();String artifact=route.substring("/v1/model-results/".length());WireValues.uuid(artifact);
   return hosts.execute(handle,a->ThrowingSupplier.of(()->service.readResult(hosts.session(handle,a,session),artifact)).get());
  }
  if("POST".equals(method)&&"/v1/model-cancellations".equals(route)){
   schemas.validate("model-stream",body,"CancelRequest");WireValues.uuid(key);String invocation=text(body,"invocation_id");String context=hosts.execute(handle,a->ThrowingSupplier.of(()->service.contextForInvocation(hosts.session(handle,a,session),invocation)).get());
   var bound=contexts.bindExisting(handle,session,context);var action=bound.action();
   if(!key.equals(action.actionId())||!action.actionId().equals(text(body,"action_id"))||!action.runId().equals(text(body,"run_id"))||!action.stepId().equals(text(body,"step_id"))||!action.generation().equals(body.required("lease_generation").bigIntegerValue()))invalid();
   service.cancel(service.issueAndPrepare(http,bound,lifetime),invocation,key,text(body,"reason_code"));
   return hosts.execute(handle,a->ThrowingSupplier.of(()->service.status(hosts.session(handle,a,session),invocation)).get());
  }
  throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
 }
 private static void fields(JsonNode n,Set<String> required){var fields=new HashSet<String>();n.fieldNames().forEachRemaining(fields::add);if(!n.isObject()||!fields.equals(required))invalid();}
 private static String text(JsonNode n,String key){var value=n.required(key);if(!value.isTextual())invalid();return value.textValue();}
 private static void invalid(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
}
