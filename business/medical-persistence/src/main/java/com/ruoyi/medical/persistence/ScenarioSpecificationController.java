package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController public final class ScenarioSpecificationController {
 private final ApplicationSessionIdentity identities;private final ScenarioSpecificationService service;private final PermissionSchema json;
 public ScenarioSpecificationController(ApplicationSessionIdentity identities,ScenarioSpecificationService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @PostMapping(value="/api/scenario-specifications",consumes="application/json") public JsonNode author(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws Exception{return write(q,r,auth,"Author");}
 @PostMapping(value="/api/scenario-specifications/reviews",consumes="application/json") public JsonNode review(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws Exception{return write(q,r,auth,"Review");}
 @PostMapping(value="/api/scenario-specifications/publications",consumes="application/json") public JsonNode publish(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws Exception{return write(q,r,auth,"Publish");}
 @PostMapping(value="/api/scenario-specifications/disable",consumes="application/json") public JsonNode disable(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws Exception{return write(q,r,auth,"Disable");}
 @GetMapping("/api/scenario-specifications/records/{recordId}") public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId)throws Exception{String context=FormalAgentPublicationController.headers(q);FormalAgentPublicationController.noBody(q);return response(r,service.read(identities.requireCurrent(q,auth),context,recordId));}
 @GetMapping("/api/scenario-specifications/projects/{projectId}/releases/{releaseId}/document-types/{documentType}") public JsonNode entry(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String projectId,@PathVariable String releaseId,@PathVariable String documentType)throws Exception{String context=FormalAgentPublicationController.headers(q);FormalAgentPublicationController.noBody(q);return response(r,service.readEntry(identities.requireCurrent(q,auth),context,projectId,releaseId,documentType));}
 private JsonNode write(HttpServletRequest q,HttpServletResponse r,Authentication auth,String kind)throws Exception{String context=FormalAgentPublicationController.headers(q);return response(r,service.write(identities.requireCurrent(q,auth),context,FormalAgentPublicationController.key(q),kind,FormalAgentPublicationController.body(q,ScenarioSpecificationSchema.MAXIMUM_BYTES)));}
 private JsonNode response(HttpServletResponse r,JsonNode data){String id=UUID.randomUUID().toString();r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",data));}
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
 @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void malformed(HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
