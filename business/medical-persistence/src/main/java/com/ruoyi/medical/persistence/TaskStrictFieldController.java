package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Versioned deterministic host route. mTLS and token action are independently checked by FormalWorkerAccess. */
@RestController
@ConditionalOnProperty(name="medical.task-strict-field.enabled",havingValue="true",matchIfMissing=false)
public final class TaskStrictFieldController {
    private final TaskStrictFieldAt05Adapter adapter;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    public TaskStrictFieldController(TaskStrictFieldAt05Adapter adapter){this.adapter=adapter;}
    @PostMapping(value="/internal/v1/task-strict-field/validate",consumes="application/json",produces="application/json")
    public JsonNode validate(HttpServletRequest request,HttpServletResponse response)throws Exception{
        response.setHeader("Cache-Control","no-store");if(request.getQueryString()!=null)bad();
        for(String header:List.of("X-Schema-Version","X-Run-Token")){var values=request.getHeaders(header);if(values==null||!values.hasMoreElements())bad();String value=values.nextElement();if(value==null||value.isBlank()||values.hasMoreElements())bad();}
        if(!"1.0.0".equals(request.getHeader("X-Schema-Version")))bad();if(request.getContentLengthLong()>1_048_576)bad();
        byte[] bytes=request.getInputStream().readNBytes(1_048_577);if(bytes.length==0||bytes.length>1_048_576)bad();var body=json.readTree(bytes);
        ProfessionalRuntimeObservations.closed(body,Set.of("schema_version","action","arguments"));if(!"1.0.0".equals(body.required("schema_version").textValue()))bad();
        var action=json.treeToValue(body.required("action"),RunTokenAuthority.ActionRequest.class);return adapter.execute(request,request.getHeader("X-Run-Token"),action,body.required("arguments"));
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
}
