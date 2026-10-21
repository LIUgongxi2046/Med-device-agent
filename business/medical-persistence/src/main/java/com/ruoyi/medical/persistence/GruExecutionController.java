package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Execution is opt-in via explicit action confirmation; record-only APIs are untouched. */
@RestController
@ConditionalOnProperty(name="medical.gru-records.enabled",havingValue="true")
@RequestMapping("/api/gru-conversations/{conversationId}/execution")
public final class GruExecutionController {
 private final ApplicationSessionIdentity identities;private final GruExecutionService service;private final PermissionSchema json;
 public GruExecutionController(ApplicationSessionIdentity identities,GruExecutionService service,PermissionSchema json){this.identities=identities;this.service=service;this.json=json;}
 @PostMapping(value="/actions",consumes="application/json")
 public JsonNode propose(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,true);return answer(service.propose(identities.requireCurrent(q,auth),context,conversationId,SimulationInputArtifactController.one(q,"Idempotency-Key"),body(q)),r);}
 @PostMapping(value="/adoption-preview",consumes="application/json")
 public JsonNode prepareAdoption(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,true);return answer(service.prepareAdoption(identities.requireCurrent(q,auth),context,conversationId,SimulationInputArtifactController.one(q,"Idempotency-Key"),body(q)),r);}
 @PostMapping(value="/actions/{actionId}/confirm",consumes="application/json")
 public JsonNode confirm(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String actionId)throws Exception {String context=headers(q,r,true);return answer(service.confirm(identities.requireCurrent(q,auth),context,conversationId,actionId,body(q)),r);}
 @PostMapping("/actions/{actionId}/recover")
 public JsonNode recover(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String actionId)throws Exception {String context=headers(q,r,true);return answer(service.recover(identities.requireCurrent(q,auth),context,conversationId,actionId),r);}
 @PostMapping(value="/actions/{actionId}/cancel",consumes="application/json")
 public JsonNode cancel(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String actionId)throws Exception {String context=headers(q,r,true);return answer(service.cancel(identities.requireCurrent(q,auth),context,conversationId,actionId,body(q)),r);}
 @GetMapping("/actions/{actionId}")
 public JsonNode read(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String actionId)throws Exception {String context=headers(q,r,false);return answer(service.read(identities.requireCurrent(q,auth),context,conversationId,actionId),r);}
 @GetMapping("/actions")
 public JsonNode list(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,false);return answer(service.history(identities.requireCurrent(q,auth),context,conversationId),r);}
 @GetMapping("/owner-memory")
 public JsonNode memories(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,false);return answer(service.memory(identities.requireCurrent(q,auth),context,conversationId,null),r);}
 @PostMapping(value="/owner-memory",consumes="application/json")
 public JsonNode memory(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,true);return answer(service.memory(identities.requireCurrent(q,auth),context,conversationId,body(q)),r);}
 @PutMapping(value="/owner-memory/{memoryId}",consumes="application/json")
 public JsonNode editMemory(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String memoryId)throws Exception {String context=headers(q,r,true);return answer(service.changeMemory(identities.requireCurrent(q,auth),context,conversationId,memoryId,body(q),false),r);}
 @PostMapping(value="/owner-memory/{memoryId}/remove",consumes="application/json")
 public JsonNode removeMemory(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId,@PathVariable String memoryId)throws Exception {String context=headers(q,r,true);return answer(service.changeMemory(identities.requireCurrent(q,auth),context,conversationId,memoryId,body(q),true),r);}
 @PostMapping("/owner-summary")
 public JsonNode summarize(HttpServletRequest q,HttpServletResponse r,Authentication auth,@PathVariable String conversationId)throws Exception {String context=headers(q,r,true);return answer(service.summarize(identities.requireCurrent(q,auth),context,conversationId),r);}
 private JsonNode answer(JsonNode value,HttpServletResponse response){String revision=value.path("revision").asText("1");response.setHeader("X-Resource-Revision",revision);return json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),revision,value));}
 private String headers(HttpServletRequest q,HttpServletResponse r,boolean write){r.setHeader("Cache-Control","no-store");r.setHeader("X-Schema-Version","1.0.0");if(q.getQueryString()!=null||!"1.0.0".equals(SimulationInputArtifactController.one(q,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String context=SimulationInputArtifactController.one(q,"X-Context-Revision");PermissionSchema.revision(context);if(write)SimulationInputArtifactController.one(q,"X-CSRF-TOKEN");return context;}
 private JsonNode body(HttpServletRequest q)throws Exception {if(q.getContentLengthLong()>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return service.parse(q.getInputStream().readNBytes(65537));}
 @ExceptionHandler(TenantAccessException.class)public void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:e.code()==ErrorCode.OBJECT_UNAVAILABLE?404:409,e.code());}
 @ExceptionHandler({IllegalArgumentException.class,com.fasterxml.jackson.core.JsonProcessingException.class})public void invalid(Exception e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class)public void invalidSchema(SchemaRejection e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
}
