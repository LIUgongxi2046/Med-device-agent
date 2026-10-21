package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/change-proposals")
public final class VersionChangeImpactController {
 private final ApplicationSessionIdentity identities;private final VersionChangeImpactService service;
 public VersionChangeImpactController(ApplicationSessionIdentity identities,VersionChangeImpactService service){this.identities=identities;this.service=service;}
 @PostMapping(consumes="application/json") public JsonNode propose(HttpServletRequest request,HttpServletResponse response,Authentication auth,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context);return service.propose(identities.requireCurrent(request,auth),context,key,body);}
 @PostMapping(value="/{id}/impact-scans",consumes="application/json") public JsonNode scan(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context);WireValues.uuid(id);return service.scan(identities.requireCurrent(request,auth),context,id,key,body);}
 @GetMapping("/{id}") public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context)throws Exception{headers(request,response,schema,context);return service.read(identities.requireCurrent(request,auth),context,id);}
 @PostMapping(value="/{id}/decisions",consumes="application/json") public JsonNode decide(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body,@RequestHeader("X-Schema-Version")String schema,@RequestHeader("X-Context-Revision")String context,@RequestHeader("Idempotency-Key")String key)throws Exception{headers(request,response,schema,context);WireValues.uuid(id);return service.decide(identities.requireCurrent(request,auth),context,id,key,body);}
 private static void headers(HttpServletRequest request,HttpServletResponse response,String schema,String context){if(!"1.0.0".equals(schema))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");}
 @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})void body(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,e.code());}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,TemplateController.errorStatus(e.code()),e.code());}
}
