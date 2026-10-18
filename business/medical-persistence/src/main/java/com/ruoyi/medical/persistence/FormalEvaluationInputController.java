package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @ConditionalOnProperty(prefix="medical.formal-evaluation-input",name="enabled",havingValue="true")
public final class FormalEvaluationInputController {
 private final ApplicationSessionIdentity identities;private final FormalEvaluationInputService service;private final PermissionSchema json;private final FormalEvaluationInputWorkflowSchema schema;
 public FormalEvaluationInputController(ApplicationSessionIdentity identities,FormalEvaluationInputService service,PermissionSchema json,FormalEvaluationInputWorkflowSchema schema){this.identities=identities;this.service=service;this.json=json;this.schema=schema;}
 @PostMapping(value="/api/formal-evaluation-inputs",consumes="application/json")
 public JsonNode register(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=FormalEvaluationInputHttp.headers(request,response,true),key=FormalEvaluationInputHttp.one(request,"Idempotency-Key");FormalEvaluationInputWorkflowSchema.key(key);
  if(request.getContentLengthLong()>FormalEvaluationInputWorkflowSchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  byte[] raw=request.getInputStream().readNBytes(FormalEvaluationInputWorkflowSchema.MAXIMUM_REQUEST_BYTES+1);if(raw.length>FormalEvaluationInputWorkflowSchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  var receipt=service.register(identities.requireCurrent(request,authentication),context,key,schema.request(raw));response.setStatus(201);return envelope(receipt,response);
 }
 @GetMapping("/api/formal-evaluation-inputs/{artifactId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String artifactId)throws Exception{
  String context=FormalEvaluationInputHttp.headers(request,response,false);WireValues.uuid(artifactId);return envelope(service.read(identities.requireCurrent(request,authentication),context,artifactId),response);
 }
 @GetMapping("/api/formal-evaluation-inputs/operations/{operationId}")
 public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String operationId)throws Exception{
  String context=FormalEvaluationInputHttp.headers(request,response,false);WireValues.uuid(operationId);return envelope(service.recover(identities.requireCurrent(request,authentication),context,operationId),response);
 }
 private JsonNode envelope(JsonNode data,HttpServletResponse response){String requestId=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",requestId);return json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,"1",data));}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{FormalEvaluationInputHttp.failure(failure,response);}
}
