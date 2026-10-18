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
@ConditionalOnProperty(prefix="medical.agent-authoring",name="enabled",havingValue="true",matchIfMissing=false)
public final class AgentAuthoringController {
 private final ApplicationSessionIdentity identities;private final AgentAuthoringOperations service;
 public AgentAuthoringController(ApplicationSessionIdentity identities,AgentAuthoringOperations service){this.identities=identities;this.service=service;}
 @PostMapping(value="/api/agent-authoring/capabilities",consumes="application/json") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode capability(HttpServletRequest q,HttpServletResponse r,Authentication a)throws IOException{return write(q,r,a,"FORMAL_CAPABILITY_AUTHOR");}
 private JsonNode write(HttpServletRequest q,HttpServletResponse r,Authentication a,String kind)throws IOException {String context=headers(q);String key=SimulationInputArtifactController.one(q,"Idempotency-Key");SimulationInputArtifactSchema.key(key);var subject=identities.requireCurrent(q,a);return response(r,service.write(subject,context,key,body(q),kind));}
 @PostMapping(value="/api/agent-authoring/records/query",consumes="application/json")
 public JsonNode query(HttpServletRequest q,HttpServletResponse r,Authentication a)throws IOException{String context=headers(q);var subject=identities.requireCurrent(q,a);return response(r,service.query(subject,context,body(q)));}
 @GetMapping("/api/agent-authoring/records/{recordId}")
 public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String recordId){String context=headers(q);noBody(q);return response(r,service.read(identities.requireCurrent(q,a),context,recordId));}
 @GetMapping("/api/agent-authoring/records/{recordId}/operation-receipts/{operationId}")
 public JsonNode receipt(HttpServletRequest q,HttpServletResponse r,Authentication a,@PathVariable String recordId,@PathVariable String operationId){String context=headers(q);noBody(q);return response(r,service.receipt(identities.requireCurrent(q,a),context,recordId,operationId));}
 static String headers(HttpServletRequest q){if(!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(q.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return context;}
 private static void noBody(HttpServletRequest q){if(q.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static byte[] body(HttpServletRequest q)throws IOException{if(q.getContentLengthLong()>AgentPublicationSchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);byte[] bytes=q.getInputStream().readNBytes(AgentPublicationSchema.MAXIMUM_BYTES+1);if(bytes.length>AgentPublicationSchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;}
 static JsonNode response(HttpServletResponse r,JsonNode value){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");r.setHeader("X-Request-Id",value.path("request_id").asText());return value;}
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.CURSOR_EXPIRED?409:SimulationRuleVersionController.errorStatus(e.code()),e.code());}
 @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class) void json(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
