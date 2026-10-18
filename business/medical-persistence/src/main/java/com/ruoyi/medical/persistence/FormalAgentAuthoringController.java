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
@ConditionalOnProperty(prefix="medical.formal-agent-authoring",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalAgentAuthoringController {
 private final ApplicationSessionIdentity identities;private final FormalAgentAuthoringOperations service;
 public FormalAgentAuthoringController(ApplicationSessionIdentity identities,FormalAgentAuthoringOperations service){this.identities=identities;this.service=service;}
 @PostMapping(value="/api/formal-agents",consumes="application/json") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode author(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{String context=headers(q),key=key(q);return response(r,service.author(identities.requireCurrent(q,auth),context,key,body(q)));}
 @PostMapping(value="/api/formal-runtime-capabilities",consumes="application/json") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode capability(HttpServletRequest q,HttpServletResponse r,Authentication auth)throws IOException{String context=headers(q),key=key(q);return response(r,service.capability(identities.requireCurrent(q,auth),context,key,body(q)));}
 @GetMapping("/api/formal-agent-records/{recordId}")
 public JsonNode agentRecord(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId){String context=headers(q);noBody(q);return response(r,service.record(identities.requireCurrent(q,auth),context,recordId,false));}
 @GetMapping("/api/formal-runtime-capability-records/{recordId}")
 public JsonNode capabilityRecord(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId){String context=headers(q);noBody(q);return response(r,service.record(identities.requireCurrent(q,auth),context,recordId,true));}
 @GetMapping("/api/formal-agent-records/{recordId}/operation-receipts/{operationId}")
 public JsonNode receipt(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId,@PathVariable String operationId){String context=headers(q);noBody(q);return response(r,service.receipt(identities.requireCurrent(q,auth),context,recordId,operationId,false));}
 @GetMapping("/api/formal-runtime-capability-records/{recordId}/operation-receipts/{operationId}")
 public JsonNode runtimeReceipt(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String recordId,@PathVariable String operationId){String context=headers(q);noBody(q);return response(r,service.receipt(identities.requireCurrent(q,auth),context,recordId,operationId,true));}
 private static String key(HttpServletRequest q){String key=SimulationInputArtifactController.one(q,"Idempotency-Key");SimulationInputArtifactSchema.key(key);return key;}
 private static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
 private static void noBody(HttpServletRequest q){if(q.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static byte[] body(HttpServletRequest q)throws IOException{if(q.getContentLengthLong()>FormalAgentPublicationSchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(FormalAgentPublicationSchema.MAXIMUM_BYTES+1);if(bytes.length>FormalAgentPublicationSchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
 private static JsonNode response(HttpServletResponse r,JsonNode value){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",value.path("request_id").asText());return value;}
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
 @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
