package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Narrow local USER record export. No official GR-08 compatibility claim or generic artifact fallback. */
@RestController
@ConditionalOnProperty(prefix="medical",name={"gru-records.enabled","gru-record-export.enabled"},havingValue="true")
public final class GruRecordExportController {
 private final ApplicationSessionIdentity identities;private final GruRecordExportService service;private final PermissionSchema json;
 public GruRecordExportController(ApplicationSessionIdentity identities,GruRecordExportService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @GetMapping("/api/gru-conversations/{conversationId}/export-basis")
 public JsonNode basis(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId)throws Exception{
  String context=headers(request,false);return envelope(service.basis(identities.requireCurrent(request,authentication),context,conversationId),response);
 }
 @PostMapping(value="/api/gru-conversations/{conversationId}/exports",consumes="application/json")
 @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId)throws Exception{
  String context=headers(request,true),key=SimulationInputArtifactController.one(request,"Idempotency-Key");
  var subject=identities.requireCurrent(request,authentication);
  if(request.getContentLengthLong()>GruRecordExportSchema.MAX_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  byte[] bytes=request.getInputStream().readNBytes(GruRecordExportSchema.MAX_REQUEST_BYTES+1);
  if(bytes.length>GruRecordExportSchema.MAX_REQUEST_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  return envelope(service.create(subject,context,conversationId,key,bytes),response);
 }
 @GetMapping("/api/gru-record-exports/{exportId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String exportId)throws Exception{
  String context=headers(request,false);return envelope(service.read(identities.requireCurrent(request,authentication),context,exportId),response);
 }
 @GetMapping("/api/gru-conversations/{conversationId}/export-operations")
 public JsonNode recovery(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId)throws Exception{
  String context=headers(request,false),key=SimulationInputArtifactController.one(request,"Idempotency-Key");return envelope(service.recovery(identities.requireCurrent(request,authentication),context,conversationId,key),response);
 }
 @GetMapping("/api/gru-record-exports/{exportId}/content")
 public void content(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String exportId)throws Exception{
  String context=headers(request,false);
  // This bounded v1 route returns a whole verified file; unsupported Range is never silently treated as a partial export.
  if(request.getHeader("Range")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var value=service.content(identities.requireCurrent(request,authentication),context,exportId);
  String requestId=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",requestId);
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Resource-Revision","1");
  response.setHeader("X-Content-Type-Options","nosniff");response.setContentType("application/json");response.setContentLength(value.bytes().length);
  response.setHeader("X-Content-SHA256",value.sha256());
  // Name comes from a validated resource UUID, never user text or a source URL.
  WireValues.uuid(exportId);response.setHeader("Content-Disposition","attachment; filename=\"gru-records-"+exportId+".json\"");
  response.getOutputStream().write(value.bytes());
 }
 private JsonNode envelope(JsonNode data,HttpServletResponse response){String id=java.util.UUID.randomUUID().toString();
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",id);response.setHeader("X-Resource-Revision","1");response.setHeader("X-Content-Type-Options","nosniff");
  return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",data));
 }
 private static String headers(HttpServletRequest request,boolean write){
  if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
  if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(write)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");else if(request.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  return context;
 }
 @ExceptionHandler(Exception.class) void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
  var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
  for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
   if(cause instanceof TenantAccessException denial){code=denial.code();status=switch(code){case ACCESS_DENIED->403;case FILE_TOO_LARGE->413;case OBJECT_UNAVAILABLE,SOURCE_MISSING->404;default->409;};break;}
   if(cause instanceof SchemaRejection rejection){code=rejection.code();status=422;break;}
   if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
   if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
  }new IdentityErrorResponseWriter().write(response,status,code);
 }
}
