package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController @ConditionalOnProperty(prefix="medical.model-enablement-policy-preparation",name="enabled",havingValue="true")
public final class ModelEnablementPolicyController {
 private final ApplicationSessionIdentity identities;private final ModelEnablementPolicyPreparation service;private final ModelEnablementPolicySchema schema;private final PermissionSchema json;
 public ModelEnablementPolicyController(ApplicationSessionIdentity identities,ModelEnablementPolicyPreparation service,ModelEnablementPolicySchema schema,PermissionSchema json){this.identities=identities;this.service=service;this.schema=schema;this.json=json;}
 @GetMapping("/api/model-enablement-policy-preparation-candidates/{modelRevision}")
 public JsonNode candidate(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String modelRevision)throws Exception{String context=ModelVerificationInputHttp.headers(request,response,false);return envelope(service.candidate(identities.requireCurrent(request,authentication),context,modelRevision),response);}
 @PostMapping(value="/api/model-enablement-policy-preparations",consumes="application/json")
 public JsonNode prepare(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{String context=ModelVerificationInputHttp.headers(request,response,true),key=ModelVerificationInputHttp.one(request,"Idempotency-Key");ModelEnablementPolicySchema.key(key);if(request.getContentLengthLong()>ModelEnablementPolicySchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] raw=request.getInputStream().readNBytes(ModelEnablementPolicySchema.MAXIMUM_REQUEST_BYTES+1);if(raw.length>ModelEnablementPolicySchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var result=service.prepare(identities.requireCurrent(request,authentication),context,key,schema.request(raw));response.setStatus(201);return envelope(result,response);}
 @GetMapping("/api/model-enablement-policy-preparations/{preparationId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String preparationId)throws Exception{String context=ModelVerificationInputHttp.headers(request,response,false);return envelope(service.read(identities.requireCurrent(request,authentication),context,preparationId),response);}
 @GetMapping("/api/model-enablement-policy-preparations/operations/{operationId}")
 public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String operationId)throws Exception{String context=ModelVerificationInputHttp.headers(request,response,false);return envelope(service.recover(identities.requireCurrent(request,authentication),context,operationId),response);}
 private JsonNode envelope(JsonNode result,HttpServletResponse response){String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",result));}
 @ExceptionHandler(Exception.class)void failure(Exception failure,HttpServletResponse response)throws java.io.IOException{ModelVerificationInputHttp.failure(failure,response);}
}
