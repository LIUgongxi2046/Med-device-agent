package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class ApprovedWebAcquisitionController {
 private final ApplicationSessionIdentity identities;private final ApprovedWebAcquisitionService service;private final PermissionSchema json;
 public ApprovedWebAcquisitionController(ApplicationSessionIdentity identities,ApprovedWebAcquisitionService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @PostMapping(value="/api/web-query-revisions",consumes="application/json")
 public JsonNode author(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{var user=subject(request,response,auth,true);return service.author(user,context(request),key(request),body(request));}
 @GetMapping("/api/web-query-revisions/{revision}")
 public JsonNode query(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String revision)throws Exception{return service.query(subject(request,response,auth,false),context(request),revision);}
 @PostMapping(value="/api/web-query-revisions/{revision}/decisions",consumes="application/json")
 public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String revision)throws Exception{var user=subject(request,response,auth,true);return service.decide(user,context(request),revision,key(request),body(request));}
 @PostMapping("/api/web-query-revisions/{revision}/acquisitions")
 public JsonNode execute(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String revision)throws Exception{var user=subject(request,response,auth,true);empty(request);return service.execute(user,context(request),revision,key(request),false);}
 @PostMapping("/api/web-query-revisions/{revision}/acquisition-recovery")
 public JsonNode recover(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String revision)throws Exception{var user=subject(request,response,auth,true);empty(request);return service.execute(user,context(request),revision,key(request),true);}
 @GetMapping("/api/web-acquisitions/{acquisition}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String acquisition)throws Exception{return service.read(subject(request,response,auth,false),context(request),acquisition,false);}
 @GetMapping("/api/web-acquisitions/{acquisition}/originals")
 public JsonNode originals(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String acquisition)throws Exception{return service.read(subject(request,response,auth,false),context(request),acquisition,true);}
 @PostMapping(value="/api/web-candidates/{candidate}/source-import",consumes="application/json")
 public JsonNode ingest(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String candidate)throws Exception{var user=subject(request,response,auth,true);return service.ingest(user,context(request),candidate,body(request));}
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
