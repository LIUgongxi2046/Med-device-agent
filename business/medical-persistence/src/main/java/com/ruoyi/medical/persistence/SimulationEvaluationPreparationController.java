package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="medical.simulation-evaluation-preparation",name="enabled",havingValue="true")
public final class SimulationEvaluationPreparationController {
 private final ApplicationSessionIdentity identities;private final SimulationEvaluationPreparationService service;
 private final PermissionSchema json;private final SimulationEvaluationPreparationSchema schema;
 public SimulationEvaluationPreparationController(ApplicationSessionIdentity identities,SimulationEvaluationPreparationService service,PermissionSchema json)throws Exception{
  this.identities=identities;this.service=service;this.json=json;schema=new SimulationEvaluationPreparationSchema();
 }
 @PostMapping(value="/api/simulation-evaluation-preparations/preview",consumes="application/json")
 public JsonNode preview(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response,true);return envelope(service.preview(identities.requireCurrent(request,authentication),context,schema.request("Preview",body(request))));
 }
 @PostMapping(value="/api/simulation-evaluation-preparations",consumes="application/json")
 public JsonNode register(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response,true),key=SimulationInputArtifactController.one(request,"Idempotency-Key");SimulationEvaluationPreparationSchema.key(key);
  var receipt=service.register(identities.requireCurrent(request,authentication),context,key,schema.request("Register",body(request)));response.setStatus(201);return envelope(receipt);
 }
 @GetMapping("/api/simulation-evaluation-preparations/{artifactId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String artifactId)throws Exception{
  String context=headers(request,response,false);WireValues.uuid(artifactId);return envelope(service.read(identities.requireCurrent(request,authentication),context,artifactId));
 }
 private JsonNode envelope(JsonNode value){return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),"1",value));}
 private String headers(HttpServletRequest request,HttpServletResponse response,boolean mutation){
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Resource-Revision","1");
  if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
  if(mutation)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;
 }
 private byte[] body(HttpServletRequest request)throws java.io.IOException{
  if(request.getContentLengthLong()>SimulationEvaluationPreparationSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  byte[] bytes=request.getInputStream().readNBytes(SimulationEvaluationPreparationSchema.MAX_BYTES+1);
  if(bytes.length>SimulationEvaluationPreparationSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
 }
 @ExceptionHandler(Exception.class) void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
  var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
  for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
   if(cause instanceof TenantAccessException denial){code=denial.code();status=switch(code){case ACCESS_DENIED->403;case FILE_TOO_LARGE->413;case OBJECT_UNAVAILABLE,SOURCE_MISSING->404;default->409;};break;}
   if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
   if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
   if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
  }
  new IdentityErrorResponseWriter().write(response,status,code);
 }
}
