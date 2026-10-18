package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="medical.agent-execution-policy",name="enabled",havingValue="true",matchIfMissing=false)
public final class AgentExecutionPolicyController {
 private final ApplicationSessionIdentity identities;private final AgentExecutionPolicyOperations service;
 public AgentExecutionPolicyController(ApplicationSessionIdentity identities,AgentExecutionPolicyOperations service){this.identities=identities;this.service=service;}
 @PostMapping(value="/api/agent-execution-policies",consumes="application/json") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode author(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{String context=headers(q),key=key(q);var subject=identities.requireCurrent(q,auth);return response(r,service.author(subject,context,key,body(q)));}
 @PostMapping(value="/api/agent-execution-policy-decisions",consumes="application/json") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode decide(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{String context=headers(q),key=key(q);var subject=identities.requireCurrent(q,auth);return response(r,service.decide(subject,context,key,body(q)));}
 @GetMapping("/api/agent-execution-policies/{versionId}")
 public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String versionId){String context=headers(q);noBody(q);return response(r,service.read(identities.requireCurrent(q,auth),context,versionId));}
 @GetMapping("/api/agent-execution-policies/{versionId}/records/{recordId}/operation-receipts/{operationId}")
 public JsonNode receipt(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String versionId,@PathVariable String recordId,@PathVariable String operationId){String context=headers(q);noBody(q);return response(r,service.receipt(identities.requireCurrent(q,auth),context,versionId,recordId,operationId));}
 @GetMapping("/api/agent-execution-policy-records/{recordId}")
 public JsonNode record(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId){String context=headers(q);noBody(q);return response(r,service.record(identities.requireCurrent(q,auth),context,recordId));}
 @PostMapping(value="/api/agent-execution-policies/query",consumes="application/json")
 public JsonNode query(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{String context=headers(q);var subject=identities.requireCurrent(q,auth);return response(r,service.query(subject,context,body(q)));}
 private static String key(HttpServletRequest q){String key=SimulationInputArtifactController.one(q,"Idempotency-Key");SimulationInputArtifactSchema.key(key);return key;}
 private static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
 private static void noBody(HttpServletRequest q){if(q.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static byte[] body(HttpServletRequest q)throws IOException{if(q.getContentLengthLong()>AgentExecutionPolicySchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(AgentExecutionPolicySchema.MAXIMUM_BYTES+1);if(bytes.length>AgentExecutionPolicySchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
 private static JsonNode response(HttpServletResponse r,JsonNode value){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",value.path("request_id").asText());return value;}
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
 @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
