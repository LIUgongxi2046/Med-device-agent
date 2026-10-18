package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.*;import jakarta.servlet.http.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(prefix="medical.business-audit",name="enabled",havingValue="true")
public final class BusinessAuditController{
 private final ApplicationSessionIdentity identities;private final BusinessAuditService service;private final BusinessAuditPolicy policy;private final PermissionSchema json;
 public BusinessAuditController(ApplicationSessionIdentity identities,BusinessAuditService service,BusinessAuditPolicy policy,PermissionSchema json){this.identities=identities;this.service=service;this.policy=policy;this.json=json;}
 @GetMapping("/api/audit-events")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
  String context=headers(request,response);for(var e:request.getParameterMap().entrySet())if(!java.util.Set.of("project_id","action","from","to","actor_id","document_id","limit","cursor").contains(e.getKey())||e.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var subject=identities.requireCurrent(request,authentication);String limit=request.getParameter("limit");if(limit==null||!limit.matches("[1-9][0-9]{0,8}"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var query=BusinessAuditQuery.parse(request.getParameter("project_id"),request.getParameter("action"),request.getParameter("from"),request.getParameter("to"),request.getParameter("actor_id"),request.getParameter("document_id"),Integer.parseInt(limit),policy);
  var page=service.read(subject,context,query,request.getParameter("cursor"));String id=java.util.UUID.randomUUID().toString();var envelope=json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,page));if(ContentReadAuthorization.canonicalBytes(envelope).length>policy.maximumResponseBytes())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);response.setHeader("X-Request-Id",id);response.setHeader("X-Resource-Revision",context);return envelope;
 }
 static String headers(HttpServletRequest request,HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);if(request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
 @ExceptionHandler(Exception.class)void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
  var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
   if(cause instanceof TenantAccessException e){code=e.code();status=SimulationRuleVersionController.errorStatus(code);break;}if(cause instanceof SchemaRejection e){code=e.code();status=422;break;}if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}}
  new IdentityErrorResponseWriter().write(response,status,code);
 }
}
