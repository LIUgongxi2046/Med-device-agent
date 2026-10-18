package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Start requires a current application session. Claim/issue require genuine registered mTLS service admission. */
@RestController
@ConditionalOnProperty(prefix="medical.simulation-execution",name="enabled",havingValue="true")
public final class SimulationEvaluationController {
    private final SimulationEvaluationService service;private final SimulationEvaluationPolicyService policies;
    private final SimulationEvaluationSchema schema;private final ApplicationSessionIdentity identities;
    public SimulationEvaluationController(SimulationEvaluationService service,SimulationEvaluationPolicyService policies,SimulationEvaluationSchema schema,ApplicationSessionIdentity identities){this.service=service;this.policies=policies;this.schema=schema;this.identities=identities;}
    @PostMapping(value="/api/simulation-evaluations",consumes="application/json")
    public JsonNode start(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        headers(request,response);String context=context(request);return service.start(identities.requireCurrent(request,authentication),context,body(request));
    }
    @PostMapping(value="/api/simulation-evaluations/enterprise-quota",consumes="application/json")
    public JsonNode quota(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        headers(request,response);String context=context(request);return policies.enterprise(identities.requireCurrent(request,authentication),context,schema.read("EnterpriseQuota",body(request)));
    }
    @PostMapping(value="/api/simulation-evaluations/retention",consumes="application/json")
    public JsonNode retention(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception {
        headers(request,response);String context=context(request);return policies.retention(identities.requireCurrent(request,authentication),context,schema.read("Retention",body(request)));
    }
    @PostMapping(value="/internal/v1/simulation-evaluations/claim",consumes="application/json")
    public JsonNode claim(HttpServletRequest request,HttpServletResponse response)throws Exception {headers(request,response);return service.claim(request,body(request));}
    @PostMapping(value="/internal/v1/simulation-evaluations/status",consumes="application/json")
    public JsonNode status(HttpServletRequest request,HttpServletResponse response)throws Exception {headers(request,response);return service.status(request,body(request));}
    @PostMapping(value="/internal/v1/simulation-evaluations/issue",consumes="application/json")
    public JsonNode issue(HttpServletRequest request,HttpServletResponse response)throws Exception {
        headers(request,response);var result=service.issue(request,body(request));var value=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        value.put("schema_version","1.0.0").put("token",result.token());value.set("admission",result.admission());value.set("receipt",result.receipt());return value;
    }
    static byte[] body(HttpServletRequest request)throws java.io.IOException {
        if(request.getContentLengthLong()>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);return bytes;
    }
    private static String one(HttpServletRequest request,String name){var values=request.getHeaders(name);if(values==null||!values.hasMoreElements())invalid();String value=values.nextElement();if(values.hasMoreElements())invalid();return value;}
    private static String context(HttpServletRequest request){String value=one(request,"X-Context-Revision");PermissionSchema.revision(value);return value;}
    private static void headers(HttpServletRequest request,HttpServletResponse response){if(!"1.0.0".equals(one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);if(request.getQueryString()!=null)invalid();response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,switch(failure.code()){case ACCESS_DENIED->403;case OBJECT_UNAVAILABLE->404;case FILE_TOO_LARGE->413;default->409;},failure.code());}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(SecurityException.class)void forbidden(SecurityException failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,403,ErrorCode.ACCESS_DENIED);}
    @ExceptionHandler(com.fasterxml.jackson.core.JsonProcessingException.class)void malformed(Exception failure,HttpServletResponse response)throws java.io.IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
}
