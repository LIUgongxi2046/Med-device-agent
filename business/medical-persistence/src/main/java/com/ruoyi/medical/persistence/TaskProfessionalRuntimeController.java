package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Narrow independent supervisor gate route. Disabled unless explicitly deployed; it grants no worker action. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.task-enabled",havingValue="true",matchIfMissing=false)
public final class TaskProfessionalRuntimeController {
 private final TaskProfessionalSupervisorGate gate;
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public TaskProfessionalRuntimeController(TaskProfessionalSupervisorGate gate){this.gate=gate;}
 @PostMapping(value="/internal/v1/professional-runtime/supervisor-admission",consumes="application/json",produces="application/json")
 public JsonNode supervisor(HttpServletRequest request,HttpServletResponse response)throws Exception{
  response.setHeader("Cache-Control","no-store");if(request.getQueryString()!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
  int maximum=32768;if(request.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=request.getInputStream().readNBytes(maximum+1);if(bytes.length==0||bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  return gate.check(request,json.readTree(bytes));
 }
 @PostMapping(value="/internal/v1/professional-runtime/supervisor-plan",consumes="application/json",produces="application/json")
 public JsonNode plan(HttpServletRequest request,HttpServletResponse response)throws Exception{response.setHeader("Cache-Control","no-store");if(request.getQueryString()!=null||request.getContentLengthLong()>32768)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);var bytes=request.getInputStream().readNBytes(32769);if(bytes.length==0||bytes.length>32768)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return gate.plan(request,json.readTree(bytes));}
 @ExceptionHandler({TenantAccessException.class,SecurityException.class})
 public void denied(Exception error,HttpServletResponse response)throws java.io.IOException{
  var code=error instanceof TenantAccessException e?e.code():ErrorCode.ACCESS_DENIED;response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setStatus(switch(code){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,SCOPE_STALE,CONTEXT_CHANGED->409;default->422;});json.writeValue(response.getOutputStream(),json.createObjectNode().put("error",code.name()));
 }
}
