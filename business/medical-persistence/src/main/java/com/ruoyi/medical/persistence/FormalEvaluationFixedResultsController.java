package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** This read is available independently of scoring/finalization, but incomplete execution fails closed. */
@RestController
public final class FormalEvaluationFixedResultsController {
 private final ApplicationSessionIdentity identities;private final ObjectProvider<FormalEvaluationFixedResults> results;
 public FormalEvaluationFixedResultsController(ApplicationSessionIdentity identities,ObjectProvider<FormalEvaluationFixedResults> results){this.identities=identities;this.results=results;}
 @GetMapping("/api/evaluation-batches/{evaluation}/candidates/{candidate}/execution-results")
 public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String evaluation,@PathVariable String candidate,@RequestHeader(value="X-Schema-Version",required=false)String version,@RequestHeader(value="X-Context-Revision",required=false)String context)throws Exception{
  if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);PermissionSchema.revision(context);if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  for(String name:java.util.List.of("X-Schema-Version","X-Context-Revision")){var headers=request.getHeaders(name);int count=0;while(headers!=null&&headers.hasMoreElements()){headers.nextElement();if(++count>1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}}
  var actor=identities.requireCurrent(request,authentication);var service=results.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var value=service.read(actor,context,evaluation,candidate);response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",value.required("request_id").asText());return value;
 }
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException{new IdentityErrorResponseWriter().write(r,EvaluationController.status(e.code()),e.code());}
}
