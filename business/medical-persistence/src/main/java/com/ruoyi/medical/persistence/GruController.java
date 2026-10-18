package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="medical.gru-records",name="enabled",havingValue="true")
public final class GruController {
 private final ApplicationSessionIdentity identities;private final GruService service;private final PermissionSchema json;private final GruSchema schema;
 public GruController(ApplicationSessionIdentity identities,GruService service,PermissionSchema json)throws Exception{this.identities=identities;this.service=service;this.json=json;schema=new GruSchema();}
 @PostMapping(value="/api/gru-conversations",consumes="application/json")
 public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response,true);noQuery(request);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");
  var result=service.create(identities.requireCurrent(request,authentication),context,key,schema.request("Create",body(request)));response.setStatus(201);return envelope(result,response);
 }
 @PostMapping(value="/api/gru-conversations/{conversationId}/messages",consumes="application/json")
 public JsonNode append(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId)throws Exception{
  String context=headers(request,response,true);noQuery(request);String key=SimulationInputArtifactController.one(request,"Idempotency-Key");
  var result=service.append(identities.requireCurrent(request,authentication),context,conversationId,key,schema.request("Append",body(request)));response.setStatus(201);return envelope(result,response);
 }
 @PostMapping(value="/api/gru-conversations/{conversationId}/append-recovery",consumes="application/json")
 public JsonNode recoverAppend(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId)throws Exception{
  String context=headers(request,response,true);noQuery(request);
  byte[] bytes=request.getInputStream().readNBytes(GruSchema.MAX_RECOVERY_BYTES+1);if(bytes.length>GruSchema.MAX_RECOVERY_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  return envelope(service.recoverAppend(identities.requireCurrent(request,authentication),context,conversationId,schema.request("RecoveryRequest",bytes)),response);
 }
 @GetMapping("/api/gru-conversations/context")
 public JsonNode fixedContext(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestParam String document_id,@RequestParam String document_revision)throws Exception{
  String context=headers(request,response,false);query(request,java.util.Set.of("document_id","document_revision"));return envelope(service.fixedContext(identities.requireCurrent(request,authentication),context,document_id,document_revision),response);
 }
 @GetMapping("/api/gru-conversations")
 public JsonNode list(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@RequestParam String project_id)throws Exception{
  String context=headers(request,response,false);query(request,java.util.Set.of("project_id"));return envelope(service.list(identities.requireCurrent(request,authentication),context,project_id),response);
 }
 @GetMapping("/api/gru-conversations/{conversationId}/receipts/{operationId}")
 public JsonNode receipt(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId,@PathVariable String operationId)throws Exception{
  String context=headers(request,response,false);noQuery(request);return envelope(service.receipt(identities.requireCurrent(request,authentication),context,conversationId,operationId),response);
 }
 @GetMapping("/api/gru-conversations/{conversationId}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String conversationId,
  @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int page_size,@RequestParam(required=false) String dataset_watermark)throws Exception{
  String context=headers(request,response,false);query(request,java.util.Set.of("page","page_size","dataset_watermark"));return envelope(service.read(identities.requireCurrent(request,authentication),context,conversationId,page,page_size,dataset_watermark),response);
 }
 private JsonNode envelope(JsonNode value,HttpServletResponse response){String revision=value.has("revision")?value.required("revision").textValue():value.has("current_revision")?value.required("current_revision").textValue():"1";response.setHeader("X-Resource-Revision",revision);return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),revision,value));}
 private String headers(HttpServletRequest request,HttpServletResponse response,boolean mutation){
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
  if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);if(mutation)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");return context;
 }
 private static void noQuery(HttpServletRequest r){if(r.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static void query(HttpServletRequest r,java.util.Set<String> allowed){for(var entry:r.getParameterMap().entrySet())if(!allowed.contains(entry.getKey())||entry.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private byte[] body(HttpServletRequest request)throws java.io.IOException{
  if(request.getContentLengthLong()>GruSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=request.getInputStream().readNBytes(GruSchema.MAX_BYTES+1);if(bytes.length>GruSchema.MAX_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
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
