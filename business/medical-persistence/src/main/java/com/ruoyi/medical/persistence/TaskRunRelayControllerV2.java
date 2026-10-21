package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Caller supplies only an existing tenant/event or bounded read cursor. No actor,
 * new operation, workflow input, decision receipt or credential can be injected. */
@RestController
@ConditionalOnProperty(name={"medical.professional-runtime.task-enabled","medical.professional-runtime.waiting-v2-enabled","medical.task-run.waiting-v2.relay-enabled"},havingValue="true",matchIfMissing=false)
public final class TaskRunRelayControllerV2 {
    static final String ROUTE="/internal/task-run/waiting-v2/outbox";
    private final TaskRunOutboxRelayV2 relay;private final ServiceCertificateIdentity identity;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskRunRelayControllerV2(TaskRunOutboxRelayV2 relay,@Qualifier("formalRunServiceIdentity")ServiceCertificateIdentity identity){this.relay=relay;this.identity=identity;}
    @PostMapping(value=ROUTE,consumes="application/json",produces="application/json")
    public JsonNode relay(HttpServletRequest http,HttpServletResponse response)throws Exception{
        response.setHeader("Cache-Control","no-store");
        if(!TaskRunActivitySecurityConfiguration.authorize(identity,http))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(http.getQueryString()!=null)throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if(http.getContentLengthLong()>4096)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=http.getInputStream().readNBytes(4097);if(bytes.length==0||bytes.length>4096)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var request=json.readTree(bytes);String operation=text(request,"operation");
        if("DELIVER_ORIGINAL".equals(operation)){
            ProfessionalRuntimeObservations.closed(request,Set.of("schema_version","operation","tenant_id","event_id"));version(request);
            return relay.deliver(http,text(request,"tenant_id"),text(request,"event_id"));
        }
        if(!"LIST_ORIGINALS".equals(operation))throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        ProfessionalRuntimeObservations.closed(request,Set.of("schema_version","operation","tenant_id","after_event_id"));version(request);
        String cursor=request.has("after_event_id")&&!request.get("after_event_id").isNull()?text(request,"after_event_id"):null;
        var values=relay.candidates(http,text(request,"tenant_id"),cursor);var result=json.createObjectNode().put("schema_version","2.0.0");result.set("event_ids",json.valueToTree(values));
        if(!values.isEmpty())result.put("next_after_event_id",values.get(values.size()-1));else result.putNull("next_after_event_id");return result;
    }
    private static String text(JsonNode n,String field){var v=n.required(field);if(!v.isTextual()||v.textValue().isBlank())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);return v.textValue();}
    private static void version(JsonNode n){if(!"2.0.0".equals(text(n,"schema_version")))throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler({TenantAccessException.class,SecurityException.class,com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class})
    public void denied(Exception failure,HttpServletResponse response)throws java.io.IOException{
        var code=failure instanceof TenantAccessException e?e.code():failure instanceof SecurityException?ErrorCode.ACCESS_DENIED:ErrorCode.SCHEMA_INVALID;
        response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setStatus(code==ErrorCode.ACCESS_DENIED?403:code==ErrorCode.DEPENDENCY_UNAVAILABLE?503:code==ErrorCode.FILE_TOO_LARGE?413:422);
        response.getOutputStream().write(("{\"error\":\""+code.name()+"\"}").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
