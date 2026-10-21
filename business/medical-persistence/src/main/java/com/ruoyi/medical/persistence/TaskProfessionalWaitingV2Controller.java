package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Separate default-off wire, never changes V1 completion semantics. */
@RestController
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled"},havingValue="true",matchIfMissing=false)
public final class TaskProfessionalWaitingV2Controller {
 private final ServiceCertificateIdentity identities;private final TaskHostControlBootstrap bootstrap;private final TaskProfessionalHostOrchestrator runtime;private final TaskRegisteredToolWaitingFinish finish;
 private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
 public TaskProfessionalWaitingV2Controller(@org.springframework.beans.factory.annotation.Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identities,TaskHostControlBootstrap bootstrap,TaskProfessionalHostOrchestrator runtime,TaskRegisteredToolWaitingFinish finish){this.identities=identities;this.bootstrap=bootstrap;this.runtime=runtime;this.finish=finish;}
 @PostMapping(value="/internal/v1/professional-runtime/open-v2",consumes="application/json",produces="application/json")
 public JsonNode open(HttpServletRequest http,HttpServletResponse response)throws Exception{
  var body=body(http,response,65536);ProfessionalRuntimeObservations.closed(body,Set.of("binding","reference","dispatch"));var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));
  var reference=record(body.required("reference"),WorkflowProtocol.Reference.class);var dispatch=record(body.required("dispatch"),WorkflowProtocol.Dispatch.class);bootstrap.requireDispatch(http,binding,reference,dispatch);
  var handle=bootstrap.open(http,binding);finish.requireProtocol(handle,binding.run_id());var prepared=runtime.prepare(handle,dispatch.commandId());if(!dispatch.equals(prepared.dispatch()))bad();var launch=prepared.launch();if(!FormalTaskNodeAuthority.sameEvidence(body.required("binding"),launch.required("binding")))bad();return launch;
 }
 @PostMapping(value="/internal/v1/professional-runtime/finish-v2",consumes="application/json",produces="application/json")
 public JsonNode finish(HttpServletRequest http,HttpServletResponse response)throws Exception{var body=body(http,response,16777216);ProfessionalRuntimeObservations.validate(body,"HOST_FINISH");var binding=ProfessionalRuntimeObservations.jsonBinding(body.required("binding"));return finish.finish(http,bootstrap.open(http,binding),body);}
 private JsonNode body(HttpServletRequest http,HttpServletResponse response,int maximum)throws Exception{response.setHeader("Cache-Control","no-store");identities.require(http);if(http.getQueryString()!=null)bad();if(http.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=http.getInputStream().readNBytes(maximum+1);if(bytes.length<1||bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return json.readTree(bytes);}
 private <T>T record(JsonNode value,Class<T> type)throws Exception{var fields=new HashSet<String>();for(var field:type.getRecordComponents())fields.add(field.getName());ProfessionalRuntimeObservations.closed(value,fields);return json.treeToValue(value,type);}
 @ExceptionHandler({TenantAccessException.class,SecurityException.class,com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})
 public void denied(Exception error,HttpServletResponse response)throws java.io.IOException{var code=error instanceof TenantAccessException e?e.code():error instanceof SecurityException?ErrorCode.ACCESS_DENIED:ErrorCode.SCHEMA_INVALID;response.setStatus(code==ErrorCode.ACCESS_DENIED?403:code==ErrorCode.DEPENDENCY_UNAVAILABLE?503:422);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");json.writeValue(response.getOutputStream(),json.createObjectNode().put("error",code.name()));}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
