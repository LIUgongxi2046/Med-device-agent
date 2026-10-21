package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class CustomerConnectorController {
 private final ApplicationSessionIdentity identities;private final CustomerConnectorService service;private final PermissionSchema json;
 public CustomerConnectorController(ApplicationSessionIdentity identities,CustomerConnectorService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @PostMapping(value="/api/connector-configs",consumes="application/json")
 public JsonNode author(HttpServletRequest r,HttpServletResponse s,Authentication auth)throws Exception{return service.author(subject(r,s,auth,true),context(r),key(r),body(r));}
 @GetMapping("/api/connector-configs/{revision}")
 public JsonNode read(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.read(subject(r,s,auth,false),context(r),revision);}
 @PostMapping(value="/api/connector-configs/{revision}/decisions",consumes="application/json")
 public JsonNode decide(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.decide(subject(r,s,auth,true),context(r),revision,key(r),body(r));}
 @PostMapping(value="/api/connector-configs/{revision}/verifications",consumes="application/json")
 public JsonNode verify(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.run(subject(r,s,auth,true),context(r),revision,key(r),body(r),"VERIFY",false);}
 @PostMapping(value="/api/connector-configs/{revision}/verification-recovery",consumes="application/json")
 public JsonNode verifyRecovery(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.run(subject(r,s,auth,true),context(r),revision,key(r),body(r),"VERIFY",true);}
 @PostMapping(value="/api/connector-configs/{revision}/sync-runs",consumes="application/json")
 public JsonNode sync(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.run(subject(r,s,auth,true),context(r),revision,key(r),body(r),"SYNC",false);}
 @PostMapping(value="/api/connector-configs/{revision}/sync-recovery",consumes="application/json")
 public JsonNode syncRecovery(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String revision)throws Exception{return service.run(subject(r,s,auth,true),context(r),revision,key(r),body(r),"SYNC",true);}
 @GetMapping("/api/connector-runs/{run}")
 public JsonNode runRead(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String run)throws Exception{return service.runRead(subject(r,s,auth,false),context(r),run);}
 @PostMapping(value="/api/connector-items/{item}/source-import",consumes="application/json")
 public JsonNode ingest(HttpServletRequest r,HttpServletResponse s,Authentication auth,@PathVariable String item)throws Exception{return service.ingest(subject(r,s,auth,true),context(r),key(r),item,body(r));}
 private ApplicationSessionIdentity.Verified subject(HttpServletRequest request,HttpServletResponse response,Authentication auth,boolean mutation){
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");if(request.getQueryString()!=null)invalid();single(request,"X-Schema-Version");single(request,"X-Context-Revision");if(!"1.0.0".equals(request.getHeader("X-Schema-Version")))invalid();PermissionSchema.revision(context(request));if(mutation){single(request,"Idempotency-Key");json.headers(context(request),key(request));}return identities.requireCurrent(request,auth);
 }
 private static void single(HttpServletRequest r,String name){if(java.util.Collections.list(r.getHeaders(name)).size()!=1)invalid();}
 private static byte[] body(HttpServletRequest r)throws Exception{if(r.getContentLengthLong()>65536)invalid();var bytes=r.getInputStream().readNBytes(65537);if(bytes.length==0||bytes.length>65536)invalid();return bytes;}
 private static void empty(HttpServletRequest r)throws Exception{if(r.getInputStream().read()!=-1)invalid();}
 private static String context(HttpServletRequest r){return r.getHeader("X-Context-Revision");}private static String key(HttpServletRequest r){return r.getHeader("Idempotency-Key");}
 private static void invalid(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler({TenantAccessException.class,IllegalArgumentException.class,com.fasterxml.jackson.core.JsonProcessingException.class})
 public void error(Exception e,HttpServletResponse response)throws java.io.IOException{var code=e instanceof TenantAccessException t?t.code():ErrorCode.SCHEMA_INVALID;new IdentityErrorResponseWriter().write(response,code==ErrorCode.ACCESS_DENIED?403:code==ErrorCode.DEPENDENCY_UNAVAILABLE?503:409,code);}
}
