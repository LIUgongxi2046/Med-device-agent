package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.io.IOException;

/** Separate normal authoring input; review/publication remain the genuine B14 APIs. */
@RestController public final class ProductTechnicalRequirementController {
 private final ProductTechnicalRequirementResults results;private final ApplicationSessionIdentity identities;private final ScenarioSpecificationService scenarios;private final ScenarioSpecificationSchema scenarioSchema;private final ProductTechnicalRequirementSchema schema;private final PermissionSchema json;
 public ProductTechnicalRequirementController(ApplicationSessionIdentity identities,ScenarioSpecificationService scenarios,ScenarioSpecificationSchema scenarioSchema,ProductTechnicalRequirementSchema schema,PermissionSchema json,ProductTechnicalRequirementResults results){this.results=results;this.identities=identities;this.scenarios=scenarios;this.scenarioSchema=scenarioSchema;this.schema=schema;this.json=json;}
 @PostMapping(value="/api/product-technical-requirements/specifications",consumes="application/json")
 public JsonNode author(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{
  String context=FormalAgentPublicationController.headers(request);String key=FormalAgentPublicationController.key(request);
  var body=scenarioSchema.read(new String(FormalAgentPublicationController.body(request,ScenarioSpecificationSchema.MAXIMUM_BYTES),java.nio.charset.StandardCharsets.UTF_8));
  if(!body.isObject()||body.size()!=3||!body.has("schema_version")||!body.has("scenario")||!body.has("selection")||!"1.0.0".equals(body.required("schema_version").asText()))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var scenario=(ObjectNode)scenarioSchema.validate("Author",body.required("scenario"));
  // All eight human-authored fields/evidence are required by B14. Only the typed INPUTS definition is encoded.
  ((ObjectNode)scenario.required("specification").required("INPUTS")).put("definition",schema.definition(body.required("selection")));schema.author(scenario);
  var result=scenarios.write(identities.requireCurrent(request,auth),context,key,"Author",ContentReadAuthorization.canonicalBytes(scenario));
  String id=UUID.randomUUID().toString();response.setHeader("Cache-Control","no-store");response.setHeader("X-Request-Id",id);response.setHeader("X-Schema-Version","1.0.0");return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,"1",result));
 }
 @GetMapping("/api/product-technical-requirements/tasks/{taskId}/runs/{runId}/results/{actionId}")
 public JsonNode result(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String taskId,@PathVariable String runId,@PathVariable String actionId)throws Exception{
  String context=FormalAgentPublicationController.headers(request);FormalAgentPublicationController.noBody(request);response.setHeader("Cache-Control","no-store");
  return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",results.read(identities.requireCurrent(request,auth),context,taskId,runId,actionId)));
 }
 @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
 @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})void malformed(HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
}
