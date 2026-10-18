package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true")
public final class ExpertQualificationController {
 private final ApplicationSessionIdentity identities;private final ExpertQualificationService service;private final ExpertQualificationSchema schema;private final PermissionSchema json;
 public ExpertQualificationController(ApplicationSessionIdentity identities,ExpertQualificationService service,ExpertQualificationSchema schema,PermissionSchema json){this.identities=identities;this.service=service;this.schema=schema;this.json=json;}
 @PostMapping(value="/api/expert-qualifications",consumes="application/json") public JsonNode author(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{return write(request,response,authentication,"Author");}
 @PostMapping(value="/api/expert-qualifications/decisions",consumes="application/json") public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{return write(request,response,authentication,"Decide");}
 @PostMapping(value="/api/expert-qualifications/score-bindings",consumes="application/json") public JsonNode bind(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{return write(request,response,authentication,"BindScore");}
 private JsonNode write(HttpServletRequest request,HttpServletResponse response,Authentication authentication,String kind)throws Exception{
  String context=ExpertQualificationHttp.headers(request,response,true),key=ExpertQualificationHttp.one(request,"Idempotency-Key");ExpertQualificationSchema.key(key);
  if(request.getContentLengthLong()>ExpertQualificationSchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] raw=request.getInputStream().readNBytes(ExpertQualificationSchema.MAXIMUM_REQUEST_BYTES+1);if(raw.length>ExpertQualificationSchema.MAXIMUM_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  var input=schema.request(kind,raw);var subject=identities.requireCurrent(request,authentication);var result=switch(kind){case "Author"->service.author(subject,context,key,input);case "Decide"->service.decide(subject,context,key,input);default->service.bindScore(subject,context,key,input);};response.setStatus(201);return envelope(result,response);
 }
 @GetMapping("/api/expert-qualifications/{qualificationId}") public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String qualificationId)throws Exception{String context=ExpertQualificationHttp.headers(request,response,false);WireValues.uuid(qualificationId);return envelope(service.read(identities.requireCurrent(request,authentication),context,qualificationId),response);}
 @GetMapping("/api/expert-qualifications/operations/{operationId}") public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String operationId)throws Exception{String context=ExpertQualificationHttp.headers(request,response,false);WireValues.uuid(operationId);return envelope(service.recover(identities.requireCurrent(request,authentication),context,operationId),response);}
 private JsonNode envelope(JsonNode data,HttpServletResponse response){String requestId=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",requestId);String revision=data.has("revision")?data.path("revision").asText():data.has("qualification_revision")?data.path("qualification_revision").asText():data.path("receipt").path("qualification_revision").asText("1");response.setHeader("X-Resource-Revision",revision);return json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,revision,data));}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{ExpertQualificationHttp.failure(failure,response);}
}
