package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{project}/shared-facts")
public final class SharedFactLifecycleController {
 private final ApplicationSessionIdentity identities;private final SharedFactLifecycleService service;
 public SharedFactLifecycleController(ApplicationSessionIdentity identities,SharedFactLifecycleService service){this.identities=identities;this.service=service;}
 @PostMapping(value="/rules",consumes="application/json")
 public JsonNode rule(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.rule(identities.requireCurrent(request,auth),context,project,key,body);}
 @PostMapping(value="/proposals/{id}/checks",consumes="application/json")
 public JsonNode check(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.check(identities.requireCurrent(request,auth),context,project,id,key,body);}
 @PostMapping(value="/policies",consumes="application/json")
 public JsonNode policy(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.policy(identities.requireCurrent(request,auth),context,project,key,body);}
 @PostMapping(value="/proposals",consumes="application/json")
 public JsonNode propose(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.propose(identities.requireCurrent(request,auth),context,project,key,body);}
 @GetMapping("/proposals/{id}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context)throws Exception{headers(request,response,schema,context,project);return service.read(identities.requireCurrent(request,auth),context,project,id);}
 @PostMapping(value="/proposals/{id}/verifications",consumes="application/json")
 public JsonNode verify(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.verify(identities.requireCurrent(request,auth),context,project,id,key,body);}
 @PostMapping(value="/proposals/{id}/adoptions",consumes="application/json")
 public JsonNode adopt(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String project,@PathVariable String id,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context,project);return service.adopt(identities.requireCurrent(request,auth),context,project,id,key,body);}
 private static void headers(HttpServletRequest request,HttpServletResponse response,String schema,String context,String project){if(!"1.0.0".equals(schema))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);WireValues.uuid(project);if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");}
 @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})void body(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,e.code());}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(e.code()),e.code());}
}
