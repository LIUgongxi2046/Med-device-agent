package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.node.ObjectNode;import com.ruoyi.medical.contracts.*;import jakarta.servlet.http.HttpServletRequest;import java.util.*;import org.springframework.util.function.ThrowingSupplier;
/** Reads the real input outside a transaction and fixes the precise allowed provider JSON body before dispatch. */
public final class FormalInvocationSendPreparation {
 private final FormalWorkerAccess workers;private final FormalVerificationJobs jobs;private final FormalInvocationCurrent current;private final FormalInvocationInputs inputs;private final FormalInvocationSchema schema;private final int maximumBytes;
 public FormalInvocationSendPreparation(FormalWorkerAccess workers,FormalVerificationJobs jobs,FormalInvocationCurrent current,FormalInvocationInputs inputs,FormalInvocationSchema schema,int maximumBytes){this.workers=workers;this.jobs=jobs;this.current=current;this.inputs=inputs;this.schema=schema;if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;}
 public final class Prepared {
  private final FormalVerificationJobs.Job job;private final FormalInvocationInputs.Prepared input;private final JsonNode payload;private final String payloadSha,authorizationSha;private final HttpServletRequest http;private final String token;private final RunTokenAuthority.ActionRequest action;
  private Prepared(FormalVerificationJobs.Job job,FormalInvocationInputs.Prepared input,JsonNode payload,String authorizationSha,HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action)throws Exception{this.http=http;this.token=token;this.action=action;this.job=job;this.input=input;this.payload=payload.deepCopy();payloadSha=schema.hash(payload);this.authorizationSha=authorizationSha;}
  FormalVerificationJobs.Job job(){return job;}public JsonNode providerBody(){return payload.deepCopy();}public String canonicalProviderBodySha256(){return payloadSha;}
  public void requireCurrentOutside()throws Exception{workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{requireCurrent(a,w);return null;}).get());}
  public void requireCurrent(EnterpriseAccess.Admission a,RunTokenAuthority.Admission worker)throws Exception{if(!job.run().equals(worker.runId())||!job.step().equals(worker.stepId())||!job.action().equals(worker.actionId())||!job.actor().equals(worker.initiatorId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);if(!job.equals(jobs.byRun(worker.tenantId(),worker.runId())))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);input.requireCurrent(a);if(!authorizationSha.equals(current.require(a,job,true).authorizationSha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 private record Initial(FormalVerificationJobs.Job job,FormalInvocationCurrent.Verified authorization){}
 public Prepared prepare(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action)throws Exception{
  var initial=workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{var j=jobs.byRun(w.tenantId(),w.runId());return new Initial(j,current.require(a,j,true));}).get());var definition=initial.authorization.model().required("definition");
  var input=inputs.prepareWorker(workers,http,token,action,initial.authorization.request(),definition.required("provider").textValue(),definition.required("region").textValue());
  var exact=schema.parse(new String(input.bytes(),java.nio.charset.StandardCharsets.UTF_8));var payload=providerBody(definition,initial.authorization.request().required("request_parameters"),exact);if(ContentReadAuthorization.canonicalBytes(payload).length>maximumBytes)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var result=new Prepared(initial.job,input,payload,initial.authorization.authorizationSha256(),http,token,action);workers.executeAction(http,token,action,(a,w)->ThrowingSupplier.of(()->{result.requireCurrent(a,w);return null;}).get());return result;
 }
 static ObjectNode providerBody(JsonNode definition,JsonNode parameters,JsonNode exact){
  // Matches the existing DeepSeek ProviderSettings.request_options. Other transports require an explicit profile.
  if(!"deepseek".equalsIgnoreCase(definition.required("provider").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
  if(!"none".equals(parameters.required("reasoning_effort").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);
  var result=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();result.set("model",definition.required("model_id"));result.set("max_tokens",parameters.required("max_output_tokens"));result.set("messages",exact.required("messages").deepCopy());result.put("stream",true);result.putObject("stream_options").put("include_usage",true);
  if(!parameters.required("reasoning_effort").isNull())result.set("reasoning_effort",parameters.required("reasoning_effort"));if(!parameters.required("temperature").isNull())result.set("temperature",parameters.required("temperature"));if(!parameters.required("stop").isEmpty())result.set("stop",parameters.required("stop"));if(!exact.required("tools").isEmpty()){if(!"none".equals(parameters.required("reasoning_effort").textValue()))throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);result.set("tools",exact.required("tools").deepCopy());}return result;
 }
}
