package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="medical.document-history",name="enabled",havingValue="true")
public final class DocumentRevisionDirectoryController{
 private final ApplicationSessionIdentity identities;private final DocumentRevisionDirectoryService service;private final PermissionSchema json;
 public DocumentRevisionDirectoryController(ApplicationSessionIdentity identities,DocumentRevisionDirectoryService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @GetMapping("/api/documents/{documentId}/revisions")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String documentId)throws Exception{
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");
  if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
  String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);
  for(var e:request.getParameterMap().entrySet())if(!java.util.Set.of("limit","cursor").contains(e.getKey())||e.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  String size=request.getParameter("limit");if(size==null||!size.matches("[1-9][0-9]{0,8}"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  if(request.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var data=service.read(identities.requireCurrent(request,authentication),context,documentId,Integer.parseInt(size),request.getParameter("cursor"));
  String revision=data.required("document_head_revision").textValue(),id=java.util.UUID.randomUUID().toString();response.setHeader("X-Resource-Revision",revision);response.setHeader("X-Request-Id",id);
  return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,revision,data));
 }
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
