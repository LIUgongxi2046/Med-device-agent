package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
@RestController @ConditionalOnBean(CandidateComparisonService.class)
public final class CandidateComparisonController {
 private final ApplicationSessionIdentity identities;private final CandidateComparisonService service;private final PermissionSchema json;private final DocumentComparisonPolicy policy;
 public CandidateComparisonController(ApplicationSessionIdentity identities,CandidateComparisonService service,PermissionSchema json,DocumentComparisonPolicy policy){this.identities=identities;this.service=service;this.json=json;this.policy=policy;}
 @PostMapping(value="/api/candidate-comparisons",consumes="application/json")
 public JsonNode create(HttpServletRequest request,HttpServletResponse response,Authentication auth)throws Exception{String context=headers(request,response,true),key=SimulationInputArtifactController.one(request,"Idempotency-Key");DocumentComparisonSchema.key(key);int max=policy.maximumInputBytes();if(request.getContentLengthLong()>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var bytes=request.getInputStream().readNBytes(max+1);if(bytes.length>max)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);response.setStatus(201);return envelope(response,context,service.create(identities.requireCurrent(request,auth),context,key,bytes));}
 @GetMapping("/api/candidate-comparisons/{id}")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception{String context=headers(request,response,false);return envelope(response,context,service.read(identities.requireCurrent(request,auth),context,id));}
 private String headers(HttpServletRequest request,HttpServletResponse response,boolean write){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");if(!"1.0.0".equals(SimulationInputArtifactController.one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=SimulationInputArtifactController.one(request,"X-Context-Revision");PermissionSchema.revision(context);if(request.getQueryString()!=null||!write&&request.getContentLengthLong()>0)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(write)SimulationInputArtifactController.one(request,"X-CSRF-TOKEN");return context;}
 private JsonNode envelope(HttpServletResponse response,String context,JsonNode data){String id=java.util.UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,data));}
 @ExceptionHandler(Exception.class) void failed(Exception e,HttpServletResponse response)throws java.io.IOException{var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;for(Throwable c=e;c!=null&&seen.add(c);c=c.getCause()){if(c instanceof TenantAccessException t){code=t.code();status=SimulationRuleVersionController.errorStatus(code);break;}if(c instanceof SchemaRejection s){code=s.code();status=422;break;}if(c instanceof com.fasterxml.jackson.core.JsonProcessingException||c instanceof java.nio.charset.CharacterCodingException){code=ErrorCode.SCHEMA_INVALID;status=422;break;}}new IdentityErrorResponseWriter().write(response,status,code);}
}
