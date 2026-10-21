package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;
/** Authenticated SDK tool requests only. Caller-provided output bytes are never accepted. */
@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-execution",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationToolController {
 private final FormalEvaluationControlAccess access;private final FormalEvaluationNodeInputs inputs;private final FormalEvaluationNodeRuntime runtime;private final FormalEvaluationInvocationService calls;private final FormalEvaluationToolReceipts tools;
 private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationToolController(FormalEvaluationControlAccess access,FormalEvaluationNodeInputs inputs,FormalEvaluationNodeRuntime runtime,FormalEvaluationInvocationService calls,FormalEvaluationToolReceipts tools){this.access=access;this.inputs=inputs;this.runtime=runtime;this.calls=calls;this.tools=tools;}
 private record Route(String schemaVersion,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,String sessionId,String toolId,JsonNode request){}
 @PostMapping(value="/internal/v1/evaluation-runtime/tool",consumes="application/json")
 public JsonNode execute(HttpServletRequest http,HttpServletResponse response)throws Exception {
  DocumentReviewInvocationController.recoveryHeaders(http);var raw=json.readTree(FormalInvocationController.body(http));ProfessionalRuntimeObservations.closed(raw,Set.of("schema_version","reference","step","session_id","tool_id","request"));var r=json.treeToValue(raw,Route.class);if(!"1.0.0".equals(r.schemaVersion)||!r.reference.steps().contains(r.step))throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);WireValues.uuid(r.sessionId);
  var value=access.withReads(http,r.reference.tenantId(),r.reference.runId(),reads->ThrowingSupplier.of(()->{var input=inputs.read(reads,r.step);return reads.execute(a->ThrowingSupplier.of(()->{var session=runtime.requireSession(a,r.reference,r.step,input,reads.workerService(),r.sessionId);var proof=calls.toolCall(session,r.request.required("call_id").asText());return tools.execute(session,proof,r.toolId,r.request);}).get());}).get());return FormalInvocationResponses.envelope(value,"1",response);
 }
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws java.io.IOException {new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:e.code()==ErrorCode.DEPENDENCY_UNAVAILABLE?503:422,e.code());}
}
