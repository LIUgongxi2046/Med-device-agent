package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Restricted mTLS/token worker routes. Application sessions are neither required nor accepted as authority.
 * Dispatch permission is intentionally never serialized over HTTP. */
@RestController
@ConditionalOnProperty(prefix="medical.simulation-execution",name="enabled",havingValue="true")
public final class SimulationInvocationController {
    private final SimulationInvocationService ledger;
    private final PermissionSchema json;
    public SimulationInvocationController(ApplicationSessionIdentity identities,SimulationInvocationService ledger,PermissionSchema json) {
        this.ledger=ledger; this.json=json;
    }
    @PostMapping(value="/internal/v1/simulation-invocations",consumes="application/json")
    public JsonNode reserve(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false)String version) throws Exception {
        headers(request,version); return output(response,ledger.reserve(request,body(request)));
    }
    @PostMapping(value="/internal/v1/simulation-invocations/status",consumes="application/json")
    public JsonNode status(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false)String version) throws Exception {
        headers(request,version); return output(response,ledger.read(request,body(request)));
    }
    @PostMapping(value="/internal/v1/simulation-invocations/cancel",consumes="application/json")
    public JsonNode cancel(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false)String version) throws Exception {
        headers(request,version); return output(response,ledger.cancel(request,body(request)));
    }
    static byte[] body(HttpServletRequest request) throws java.io.IOException {
        int maximum=16_777_216;
        if(request.getContentLengthLong()>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(maximum+1);
        if(bytes.length>maximum)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        return bytes;
    }
    private static void headers(HttpServletRequest request,String version) {
        if(!"1.0.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        if(request.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        for(String name:java.util.List.of("X-Schema-Version","X-Simulation-Run-Token")) {
            var headers=request.getHeaders(name);if(headers==null||!headers.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            headers.nextElement();if(headers.hasMoreElements())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
    }
    private JsonNode output(HttpServletResponse response,SimulationInvocationService.View view) {
        String requestId=java.util.UUID.randomUUID().toString(); response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",requestId);
        var value=json.object();value.put("invocation_id",view.invocationId());value.put("status",view.status());value.put("dispatch_generation",view.dispatchGeneration());
        value.put("execution_mode","SIMULATION");value.put("production_eligible",false);value.put("settlement_status","PENDING");
        return json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,view.revision(),value));
    }
    @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException failure,HttpServletResponse response)throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response,switch(failure.code()) {case ACCESS_DENIED->403;case OBJECT_UNAVAILABLE->404;
            case REVISION_CONFLICT,IDEMPOTENCY_CONFLICT,ACTION_STALE,LEASE_STALE,SCOPE_STALE,CONTEXT_CHANGED->409;
            case DEPENDENCY_UNAVAILABLE->503;case FILE_TOO_LARGE->413;default->422;},failure.code());
    }
    @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection failure,HttpServletResponse response)throws java.io.IOException { new IdentityErrorResponseWriter().write(response,422,failure.code()); }
    @ExceptionHandler(SecurityException.class) void token(SecurityException failure,HttpServletResponse response)throws java.io.IOException { new IdentityErrorResponseWriter().write(response,403,ErrorCode.ACCESS_DENIED); }
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,java.nio.charset.CharacterCodingException.class})
    void malformed(Exception failure,HttpServletResponse response)throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);
    }

}
