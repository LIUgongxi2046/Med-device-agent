package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="medical.document-comparison",name="enabled",havingValue="true")
public final class DocumentComparisonController{
 private final ApplicationSessionIdentity identities;private final DocumentComparisonService service;private final PermissionSchema json;private final DocumentComparisonPolicy policy;
 public DocumentComparisonController(ApplicationSessionIdentity identities,DocumentComparisonService service,PermissionSchema json,DocumentComparisonPolicy policy){this.identities=identities;this.service=service;this.json=json;this.policy=policy;}
 @PostMapping(value="/api/document-comparisons",consumes="application/json")
 public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response,true);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");DocumentComparisonSchema.key(key);
  var subject=identities.requireCurrent(request,authentication);
  int max=policy.maximumInputBytes();if(request.getContentLengthLong()>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] raw=request.getInputStream().readNBytes(max+1);if(raw.length>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  return envelope(response,service.create(subject,context,key,raw));
 }
 @GetMapping("/api/document-comparisons/{comparisonId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String comparisonId)throws Exception{
  String context=headers(request,response,false);return envelope(response,service.read(identities.requireCurrent(request,authentication),context,comparisonId));
 }
 @GetMapping("/api/document-comparison-operations")
 public JsonNode recovery(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response,false);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");DocumentComparisonSchema.key(key);return envelope(response,service.recovery(identities.requireCurrent(request,authentication),context,key));
 }
 private String headers(HttpServletRequest request,HttpServletResponse response,boolean mutation){
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
  if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
  if(request.getQueryString()!=null||!mutation&&request.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(mutation)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");return context;
 }
 private JsonNode envelope(HttpServletResponse response,JsonNode data){String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);response.setHeader("X-Resource-Revision","1");return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",data));}
 @ExceptionHandler(Exception.class) void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
  var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
  for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
   if(cause instanceof TenantAccessException denial){code=denial.code();status=SimulationRuleVersionController.errorStatus(code);break;}
   if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
   if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
   if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
  }new IdentityErrorResponseWriter().write(response,status,code);
 }
}
