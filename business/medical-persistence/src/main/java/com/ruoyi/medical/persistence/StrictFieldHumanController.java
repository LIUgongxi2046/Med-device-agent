package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController
@ConditionalOnProperty(name="medical.professional-runtime.document-review-enabled",havingValue="true")
public final class StrictFieldHumanController {
 private final ApplicationSessionIdentity identities;private final StrictFieldHumanService service;private final PermissionSchema schema;
 public StrictFieldHumanController(ApplicationSessionIdentity identities,StrictFieldHumanService service,PermissionSchema schema){this.identities=identities;this.service=service;this.schema=schema;}
 @GetMapping("/api/documents/{document}/fields/{field}/decision-basis")
 public JsonNode basis(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String document,@PathVariable String field,@RequestParam String document_revision,@RequestParam String check_id,@RequestParam(required=false)String row_id,@RequestParam(required=false)String strict_artifact_id,@RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context)throws Exception{headers(response,version,context);query(r,java.util.Set.of("document_revision","check_id","row_id","strict_artifact_id"));return service.basis(identities.requireCurrent(r,auth),context,new StrictFieldHumanBasis.Target(document,document_revision,field,row_id,check_id,strict_artifact_id));}
 @PostMapping(value="/api/documents/{document}/field-decisions",consumes="application/json")
 @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public JsonNode decide(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String document,@RequestBody byte[] body,@RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context,@RequestHeader(value="Idempotency-Key",required=false)String key)throws Exception{headers(response,version,context);query(r,java.util.Set.of());schema.headers(context,key);return service.decide(identities.requireCurrent(r,auth),context,document,key,body);}
 @GetMapping("/api/documents/{document}/field-decisions/{decision}")
 public JsonNode read(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String document,@PathVariable String decision,@RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context)throws Exception{headers(response,version,context);query(r,java.util.Set.of());return service.read(identities.requireCurrent(r,auth),context,document,decision);}
 @GetMapping("/api/documents/{document}/field-evidence-questions")
 public JsonNode questions(HttpServletRequest r,HttpServletResponse response,Authentication auth,@PathVariable String document,@RequestParam String document_revision,@RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context)throws Exception{headers(response,version,context);query(r,java.util.Set.of("document_revision"));return service.questions(identities.requireCurrent(r,auth),context,document,document_revision);}
 private static void query(HttpServletRequest r,java.util.Set<String> allowed){for(var e:r.getParameterMap().entrySet())if(!allowed.contains(e.getKey())||e.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static void headers(HttpServletResponse response,String version,String context){if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");}
 @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})void invalid(HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class)void schema(SchemaRejection e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:SimulationRuleVersionController.errorStatus(e.code()),e.code());}
}
