package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.*;
/** Deterministic validation of actual fixed model limits. This utility never grants model-send authority. */
public final class FormalInvocationModelPolicy {
 private FormalInvocationModelPolicy(){}
 public static void require(JsonNode model,JsonNode request,String classification,BigInteger actualInput)throws Exception{
  var definition=model.required("definition");var limits=definition.required("limits");var parameters=request.required("request_parameters");
  // First raw-receipt transport cannot retain internal reasoning; reject unsupported mode before any paid send.
  if(!"none".equals(parameters.required("reasoning_effort").textValue()))bad();
  BigInteger maxInput=new BigInteger(request.required("maximum_input_tokens").textValue()),output=parameters.required("max_output_tokens").bigIntegerValue(),window=new BigInteger(limits.required("context_window").textValue()),maximumOutput=new BigInteger(limits.required("max_output_tokens").textValue());
  if(actualInput==null||actualInput.signum()<0||actualInput.compareTo(maxInput)>0||output.signum()<=0||output.compareTo(maximumOutput)>0||maxInput.add(output).compareTo(window)>0)bad();
  if(!parameters.required("temperature").isNull()&&!limits.required("temperature_enabled").booleanValue())bad();
  if(!parameters.required("reasoning_effort").isNull()&&!contains(limits.required("reasoning_efforts"),parameters.required("reasoning_effort").textValue()))bad();
  if(parameters.required("stop").size()>limits.required("stop_limit").intValue())bad();
  var policy=definition.required("data_policy");if(!contains(policy.required("allowed_classifications"),classification)||!contains(policy.required("allowed_regions"),definition.required("region").textValue()))bad();
  var catalogue=SchemaCatalogue.fromClasspath();catalogue.bindPublished(definition.required("request_parameter_schema_ref").textValue(),definition.required("request_parameter_schema_sha256").textValue(),ContentReadAuthorization.canonicalBytes(parameters));
 }
 private static boolean contains(JsonNode values,String text){for(var n:values)if(n.isTextual()&&text.equals(n.textValue()))return true;return false;}
 private static void bad(){throw new TenantAccessException(ErrorCode.MODEL_POLICY_INCOMPLETE);}
}
