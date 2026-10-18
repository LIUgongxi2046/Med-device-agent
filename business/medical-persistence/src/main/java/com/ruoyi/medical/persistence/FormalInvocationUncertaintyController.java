package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.*;import jakarta.servlet.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
/** IN11 reports only unresolved transport facts; no user supplied bill or second dispatch is accepted. */
@RestController @ConditionalOnExpression("${medical.model-runtime.enabled:false} and ${medical.formal-invocation.enabled:false}")
public final class FormalInvocationUncertaintyController {
 private final ServiceCertificateIdentity identities;private final FormalInvocationUncertainty uncertainty;private final FormalInvocationHttpSchema schema;
 public FormalInvocationUncertaintyController(ServiceCertificateIdentity i,FormalInvocationUncertainty u,FormalInvocationHttpSchema s){identities=i;uncertainty=u;schema=s;}
 @PostMapping(value="/internal/v1/invocations/{id}/reconciliations",consumes="application/json")
 public JsonNode record(HttpServletRequest request,HttpServletResponse response,@PathVariable("id")String id)throws Exception{identities.require(request);WireValues.uuid(id);var headers=request.getHeaders("X-Schema-Version");if(request.getQueryString()!=null||headers==null||!headers.hasMoreElements()||!"1.0.0".equals(headers.nextElement())||headers.hasMoreElements())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);var b=schema.read("Unknown",FormalInvocationController.body(request));var result=uncertainty.record(request,b.required("tenant_id").textValue(),b.required("original_actor_id").textValue(),id,b.required("dispatch_attempt_id").textValue());return FormalInvocationResponses.envelope(result,"1",response);}

 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,e.code()==ErrorCode.ACCESS_DENIED?403:409,e.code());}
}
