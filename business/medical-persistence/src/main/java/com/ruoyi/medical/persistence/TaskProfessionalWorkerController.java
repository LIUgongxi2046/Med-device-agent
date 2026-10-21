package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Exact worker protocol. Every action uses the existing server-bound original dispatch and
 * fresh mTLS/token authority. Reconciliation endpoints never mint that authority. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true",matchIfMissing=false)
public final class TaskProfessionalWorkerController {
 private final ServiceCertificateIdentity identities;private final TaskHostControlBootstrap bootstrap;private final TaskProfessionalHostOrchestrator runtime;
 private final TaskInvocationBridge models;private final TaskResearchToolBridge tools;private final TaskResearchFinishOrchestrator finish;private final ProfessionalRuntimeObservations observations;
 private final TaskRegisteredToolBridge registered;
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
 public TaskProfessionalWorkerController(@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskHostControlBootstrap bootstrap,TaskProfessionalHostOrchestrator runtime,TaskInvocationBridge models,TaskResearchToolBridge tools,TaskResearchFinishOrchestrator finish,ProfessionalRuntimeObservations observations){this.identities=identities;this.bootstrap=bootstrap;this.runtime=runtime;this.models=models;this.tools=tools;this.finish=finish;this.observations=observations;this.registered=null;}
 @org.springframework.beans.factory.annotation.Autowired
 public TaskProfessionalWorkerController(@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskHostControlBootstrap bootstrap,TaskProfessionalHostOrchestrator runtime,TaskInvocationBridge models,TaskResearchToolBridge tools,TaskResearchFinishOrchestrator finish,ProfessionalRuntimeObservations observations,TaskRegisteredToolBridge registered){this.identities=identities;this.bootstrap=bootstrap;this.runtime=runtime;this.models=models;this.tools=tools;this.finish=finish;this.observations=observations;this.registered=registered;}
 @PostMapping(value="/internal/v1/professional-runtime/open",consumes="application/json",produces="application/json")
 public JsonNode open(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,65536);closed(body,"binding","reference","dispatch");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));
  var reference=record(body.required("reference"),WorkflowProtocol.Reference.class);var dispatch=record(body.required("dispatch"),WorkflowProtocol.Dispatch.class);
  bootstrap.requireDispatch(http,binding,reference,dispatch);var handle=bootstrap.open(http,binding);var prepared=runtime.prepare(handle,dispatch.commandId());
  if(!dispatch.equals(prepared.dispatch()))bad();var launch=prepared.launch();same(body.required("binding"),launch.required("binding"));return launch;
 }
 @PostMapping(value="/internal/v1/professional-runtime/admit",consumes="application/json",produces="application/json")
 public JsonNode admit(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,32768);closed(body,"binding","session_id");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));String session=session(body);
  String command=bootstrap.commandId(http,binding);var reply=runtime.admit(http,bootstrap.open(http,binding),session,command);same(body.required("binding"),reply.required("binding"));same(session,reply.required("session_id").textValue());return reply;
 }
 @PostMapping(value="/internal/v1/professional-runtime/heartbeat",consumes="application/json",produces="application/json")
 public JsonNode heartbeat(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,32768);closed(body,"binding","session_id");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));String session=session(body);
  var reply=runtime.heartbeat(bootstrap.open(http,binding),session);current(body,reply);return reply;
 }
 @PostMapping(value="/internal/v1/professional-runtime/bridge",consumes="application/json",produces="application/json")
 public JsonNode bridge(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,16777216);closed(body,"binding","session_id","method","route","body","idempotency_key","model_request_observation");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));String session=session(body),method=text(body,"method"),route=text(body,"route");
  String key=nullableText(body.required("idempotency_key"));JsonNode observation=body.required("model_request_observation");if(observation.isNull())observation=null;JsonNode payload=body.required("body");if(payload.isNull())payload=null;
  var handle=bootstrap.open(http,binding);current(body,runtime.heartbeat(handle,session));
  if("POST".equals(method)&&Set.of("/v1/tools/AT-01","/v1/tools/AT-02").contains(route)){
   if(key!=null||observation!=null||payload==null)invalid();return tools.forward(http,handle,session,route.substring("/v1/tools/".length()),payload);
  }
  if("POST".equals(method)&&Set.of("/v1/tools/AT-03","/v1/tools/AT-04","/v1/tools/AT-08","/v1/tools/AT-09").contains(route)){
   if(key!=null||observation!=null||payload==null)invalid();if(registered==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return registered.forward(handle,session,route.substring("/v1/tools/".length()),payload);
  }
  // The C07 adapter itself recognizes only four fixed model operations and authenticates
  // actual MODEL_CALL separately; arbitrary paths cannot become a proxy request.
  return models.forward(http,handle,session,method,route,payload,key,observation);
 }
 @PostMapping(value="/internal/v1/professional-runtime/finish",consumes="application/json",produces="application/json")
 public JsonNode finish(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,16777216);ProfessionalRuntimeObservations.validate(body,"HOST_FINISH");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));return finish.finish(http,bootstrap.open(http,binding),body);
 }
 @PostMapping(value="/internal/v1/professional-runtime/close-admission",consumes="application/json",produces="application/json")
 public JsonNode close(HttpServletRequest http,HttpServletResponse response)throws Exception{return observations.close(http,body(http,response,32768));}
 @PostMapping(value="/internal/v1/professional-runtime/failure",consumes="application/json",produces="application/json")
 public JsonNode failure(HttpServletRequest http,HttpServletResponse response)throws Exception{return observations.failure(http,body(http,response,32768));}
 private JsonNode body(HttpServletRequest http,HttpServletResponse response,int maximum)throws Exception{
  response.setHeader("Cache-Control","no-store");identities.require(http);if(http.getQueryString()!=null)invalid();if(http.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  var bytes=http.getInputStream().readNBytes(maximum+1);if(bytes.length==0||bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return json.readTree(bytes);
 }
 private <T>T record(JsonNode value,Class<T> type)throws Exception{var fields=new HashSet<String>();for(var component:type.getRecordComponents())fields.add(component.getName());ProfessionalRuntimeObservations.closed(value,fields);return json.treeToValue(value,type);}
 private static void current(JsonNode request,JsonNode reply){closed(reply,"binding","session_id","admission");same(request.required("binding"),reply.required("binding"));same(session(request),text(reply,"session_id"));same("CURRENT",text(reply,"admission"));}
 private static void closed(JsonNode node,String... fields){ProfessionalRuntimeObservations.closed(node,Set.of(fields));}
 private static String session(JsonNode body){String value=text(body,"session_id");WireValues.uuid(value);return value;}
 private static String text(JsonNode node,String field){return nullableRequired(node.required(field));}private static String nullableRequired(JsonNode node){if(!node.isTextual())invalid();return node.textValue();}private static String nullableText(JsonNode node){return node.isNull()?null:nullableRequired(node);}
 private static void same(Object expected,Object actual){if(expected instanceof JsonNode a&&actual instanceof JsonNode b){if(!FormalTaskNodeAuthority.sameEvidence(a,b))bad();}else if(!Objects.equals(expected,actual))bad();}
 private static void invalid(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
 @ExceptionHandler({TenantAccessException.class,SecurityException.class,com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})
 public void denied(Exception failure,HttpServletResponse response)throws java.io.IOException{
  var code=failure instanceof TenantAccessException e?e.code():failure instanceof SecurityException?ErrorCode.ACCESS_DENIED:ErrorCode.SCHEMA_INVALID;response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setStatus(switch(code){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,SCOPE_STALE,CONTEXT_CHANGED->409;default->422;});json.writeValue(response.getOutputStream(),json.createObjectNode().put("error",code.name()));
 }
}
