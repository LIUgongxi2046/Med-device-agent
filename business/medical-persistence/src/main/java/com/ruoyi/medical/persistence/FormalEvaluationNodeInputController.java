package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.contracts.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@ConditionalOnProperty(prefix="medical.formal-evaluation-node-input",name="enabled",havingValue="true",matchIfMissing=false)
public final class FormalEvaluationNodeInputController {
    private final ApplicationSessionIdentity identities;private final ObjectProvider<FormalEvaluationNodeInputObjectService> writes;
    private final ObjectProvider<FormalEvaluationNodeInputReadService> reads;private final FormalEvaluationNodeInputObjectSchema schema;private final PermissionSchema json;
    public FormalEvaluationNodeInputController(ApplicationSessionIdentity i,ObjectProvider<FormalEvaluationNodeInputObjectService> w,ObjectProvider<FormalEvaluationNodeInputReadService> r,FormalEvaluationNodeInputObjectSchema s,PermissionSchema j){identities=i;writes=w;reads=r;schema=s;json=j;}
    @PostMapping(value="/api/formal-evaluation-node-inputs",consumes="application/json")
    public JsonNode prepare(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,true);var subject=identities.requireCurrent(q,auth);
        var receipt=writer().register(subject,context,FormalEvaluationContextHttp.key(q),FormalEvaluationContextHttp.body(q,1048576));
        s.setStatus(201);return FormalEvaluationContextHttp.envelope(identity(receipt),s,json);
    }
    @PostMapping(value="/api/formal-evaluation-node-inputs/recovery",consumes="application/json")
    public JsonNode recover(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {
        String context=FormalEvaluationContextHttp.headers(q,s,true,false);var subject=identities.requireCurrent(q,auth);var original=schema.recovery(FormalEvaluationContextHttp.body(q,2097152));
        return FormalEvaluationContextHttp.envelope(identity(writer().recover(subject,context,original.originalKey(),schema.canonical(original.originalRequest()).getBytes(StandardCharsets.UTF_8))),s,json);
    }
    @PostMapping(value="/api/formal-evaluation-node-inputs/status",consumes="application/json")
    public JsonNode status(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {String context=FormalEvaluationContextHttp.headers(q,s,true,false);var subject=identities.requireCurrent(q,auth);byte[] body=FormalEvaluationContextHttp.body(q,2097152);return FormalEvaluationContextHttp.envelope(reader().status(subject,context,body),s,json);}
    @PostMapping(value="/api/formal-evaluation-node-inputs/content",consumes="application/json")
    public JsonNode content(HttpServletRequest q,HttpServletResponse s,Authentication auth)throws Exception {String context=FormalEvaluationContextHttp.headers(q,s,true,false);var subject=identities.requireCurrent(q,auth);byte[] body=FormalEvaluationContextHttp.body(q,2097152);return FormalEvaluationContextHttp.envelope(reader().content(subject,context,body),s,json);}
    private JsonNode identity(FormalEvaluationNodeInputObjectRegistration.Receipt r){return json.object().put("schema_version","1.0.0").put("state","PREPARED").put("runnable",false).put("professional_completion",false).put("operation_id",r.operationId()).put("write_id",r.writeId()).put("attempt_id",r.attemptId()).put("execution_id",r.executionId()).put("artifact_id",r.artifact().artifactId()).put("artifact_sha256",r.artifact().sha256()).put("object_version",r.objectVersion()).put("receipt_sha256",r.receiptSha256());}
    private FormalEvaluationNodeInputObjectService writer(){var v=writes.getIfUnique();if(v==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return v;}
    private FormalEvaluationNodeInputReadService reader(){var v=reads.getIfUnique();if(v==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return v;}
    @ExceptionHandler(Exception.class)public void failure(Exception e,HttpServletResponse s)throws IOException{FormalEvaluationContextHttp.failure(e,s);}
}
