package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.workflow.FormalEvaluationDelivery;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Public normal path: real input preflight -> atomic complete obligations -> durable one-shot
 * dispatch -> actual acceptance. Recovery uses the original command only and never re-prepares. */
public final class FormalEvaluationBootstrapService {
    private final EnterpriseAccess access;private final FormalEvaluationExecutionInputs inputs;private final FormalEvaluationBoundExecutions bound;
    private final FormalEvaluationJobs jobs;private final FormalEvaluationLaunchLedger launches;private final FormalEvaluationDelivery delivery;
    private final FormalEvaluationLaunchLedger.Configuration configuration;private final PermissionSchema json;private final ResourceReadAuthority resources;
    private final JsonMapper decoder=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public FormalEvaluationBootstrapService(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,FormalEvaluationBoundExecutions bound,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,FormalEvaluationDelivery delivery,FormalEvaluationLaunchLedger.Configuration configuration,PermissionSchema json,ResourceReadAuthority resources){this.access=access;this.inputs=inputs;this.bound=bound;this.jobs=jobs;this.launches=launches;this.delivery=delivery;this.configuration=configuration;this.json=json;this.resources=resources;}
    public record Start(String schemaVersion,String executionId,FormalEvaluationExecutionProtocol.Start start){public Start{if(!"1.0.0".equals(schemaVersion))invalid();WireValues.uuid(executionId);Objects.requireNonNull(start);}}
    public record Recovery(String schemaVersion,String runId){public Recovery{if(!"1.0.0".equals(schemaVersion))invalid();WireValues.uuid(runId);}}
    public JsonNode bootstrap(ApplicationSessionIdentity.Verified actor,String context,String key,byte[] bytes)throws Exception {
        outside();json.headers(context,key);var request=decode(bytes,Start.class);var prepared=inputs.prepare(actor,context,request.start());
        var matrix=access.execute(actor,a->ThrowingSupplier.of(()->{current(a,context);var approved=bound.require(a,request.executionId(),prepared);return jobs.queued(a,key,prepared,approved);}).get());
        var result=json.object().put("schema_version","1.0.0").put("execution_id",request.executionId()).put("professional_completion",false).put("publication_eligible",false);var cells=result.putArray("cells");
        for(var job:matrix) {
            // Recheck current fixed scope before any first transport; after an uncertain attempt,
            // recovery below is query-only and does not send a second workflow-start command.
            var attempt=access.execute(actor,a->ThrowingSupplier.of(()->{current(a,context);prepared.requireCurrent(a);bound.require(a,request.executionId(),prepared).requireUnchanged();return launches.attempt(a,launches.prepare(a,job,configuration));}).get());
            cells.add(transfer(actor,context,attempt.intent(),attempt.claimInitialSend()));
        }
        return result;
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified actor,String context,byte[] bytes)throws Exception {
        outside();var request=decode(bytes,Recovery.class);var intent=access.execute(actor,a->ThrowingSupplier.of(()->{current(a,context);var job=jobs.byRun(a,request.runId());resources.require(a,new PermissionProtocol.Scope(job.tenantId(),job.projectId(),"EVALUATION",jobs.cell(job).evaluationId()),"evaluation.read",EvaluationReferences.PURPOSE);return launches.recoveryOriginal(a,job);}).get());
        return transfer(actor,context,intent,false);
    }
    private JsonNode transfer(ApplicationSessionIdentity.Verified actor,String context,FormalEvaluationLaunchLedger.Intent intent,boolean first)throws Exception {
        final FormalEvaluationDelivery.Accepted accepted;
        try {accepted=first?delivery.start(intent.reference(),intent.commandId()):delivery.recover(intent.reference(),intent.commandId());}
        catch(RuntimeException uncertain){return json.object().put("schema_version","1.0.0").put("run_id",intent.job().runId()).put("state","UNKNOWN").put("recovery_required",true).put("professional_completion",false).put("publication_eligible",false);}
        var receipt=access.execute(actor,a->ThrowingSupplier.of(()->{current(a,context);var job=jobs.byRun(a,intent.job().runId());resources.require(a,new PermissionProtocol.Scope(job.tenantId(),job.projectId(),"EVALUATION",jobs.cell(job).evaluationId()),"evaluation.read",EvaluationReferences.PURPOSE);return launches.accepted(a,intent,accepted);}).get());
        var result=json.object().put("schema_version","1.0.0").put("run_id",intent.job().runId()).put("state","ACCEPTED").put("recovery_required",false).put("professional_completion",false).put("publication_eligible",false);result.set("receipt",receipt);return result;
    }
    private <T>T decode(byte[] bytes,Class<T> type)throws Exception{if(bytes==null||bytes.length==0||bytes.length>65536)invalid();return decoder.readValue(bytes,type);}
    private static void current(EnterpriseAccess.Admission a,String context){FormalEvaluationExecutionAdmission.current(a);if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void invalid(){throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);}
}
