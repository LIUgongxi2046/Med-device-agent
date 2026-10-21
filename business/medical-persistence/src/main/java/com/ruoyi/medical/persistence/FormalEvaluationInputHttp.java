package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
/** Local header handling; no dependency on simulation controllers. */
final class FormalEvaluationInputHttp {
 private FormalEvaluationInputHttp(){}
 static String one(HttpServletRequest request,String name){var values=Collections.list(request.getHeaders(name));if(values.size()!=1||values.get(0)==null||values.get(0).isBlank()||values.get(0).contains(","))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return values.get(0);}
 static String headers(HttpServletRequest request,HttpServletResponse response,boolean write){return headers(request,response,write,"1.0.0");}
 static String headers(HttpServletRequest request,HttpServletResponse response,boolean write,String version){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version",version);response.setHeader("X-Resource-Revision","1");if(!version.equals(one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=one(request,"X-Context-Revision");PermissionSchema.revision(context);if(write)one(request,"X-CSRF-TOKEN");if(request.getQueryString()!=null||!write&&request.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
 static void failure(Exception failure,HttpServletResponse response)throws IOException{var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;for(Throwable cause=failure;cause!=null&&seen.add(cause);cause=cause.getCause()){
  if(cause instanceof TenantAccessException denied){code=denied.code();status=switch(code){case ACCESS_DENIED->403;case FILE_TOO_LARGE->413;case SOURCE_MISSING,OBJECT_UNAVAILABLE->404;default->409;};break;}
  if(cause instanceof SchemaRejection rejected){code=rejected.code();status=422;break;}
  if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
  if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
 }new IdentityErrorResponseWriter().write(response,status,code);}
}
