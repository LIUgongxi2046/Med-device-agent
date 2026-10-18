package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Reconciliation-only server endpoints. Does not imply open/bridge/heartbeat are ready. */
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.observations-enabled",havingValue="true",matchIfMissing=false)
public final class ProfessionalRuntimeObservationController {
 private final ServiceCertificateIdentity identities;private final ProfessionalRuntimeObservations observations;
 private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public ProfessionalRuntimeObservationController(ServiceCertificateIdentity identities,ProfessionalRuntimeObservations observations){this.identities=identities;this.observations=observations;}
 @PostMapping(value="/internal/v1/professional-runtime/close-admission",consumes="application/json",produces="application/json")
 public JsonNode close(HttpServletRequest request,HttpServletResponse response)throws Exception{return observations.close(request,body(request,response));}
 @PostMapping(value="/internal/v1/professional-runtime/failure",consumes="application/json",produces="application/json")
 public JsonNode failure(HttpServletRequest request,HttpServletResponse response)throws Exception{return observations.failure(request,body(request,response));}
 @PostMapping(value="/internal/v1/professional-runtime/finish",consumes="application/json",produces="application/json")
 public JsonNode finish(HttpServletRequest request,HttpServletResponse response)throws Exception{
  observations.finishObservation(request,body(request,response));
  // The host contract requires original registered Completion, not an observation ACK.
  throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
 }
 private JsonNode body(HttpServletRequest request,HttpServletResponse response)throws Exception{
  identities.require(request);response.setHeader("Cache-Control","no-store");
  if(request.getQueryString()!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
  int maximum=16777216;if(request.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  byte[] bytes=request.getInputStream().readNBytes(maximum+1);if(bytes.length==0||bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  return json.readTree(bytes);
 }
 @ExceptionHandler(TenantAccessException.class)
 public void denied(TenantAccessException error,HttpServletResponse response)throws java.io.IOException{
  response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setStatus(switch(error.code()){case ACCESS_DENIED->403;case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE,CONTEXT_LIMIT->413;case REVISION_CONFLICT,SCOPE_STALE,CONTEXT_CHANGED->409;default->422;});
  json.writeValue(response.getOutputStream(),json.createObjectNode().put("error",error.code().name()));
 }
}
