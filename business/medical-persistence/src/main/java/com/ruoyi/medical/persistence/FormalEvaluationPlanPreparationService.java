package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Ordinary-session entry. Bound original preparation and persisted directory drafts remain non-runnable. */
public final class FormalEvaluationPlanPreparationService {
    private final EnterpriseAccess access;
    private final FormalEvaluationExecutionInputs inputs;
    private final FormalEvaluationBoundExecutions bounds;
    private final FormalEvaluationExecutionRegistrations roots;
    private final FormalEvaluationPlanPreparationAuthority authority;
    private final FormalEvaluationPlanPreparationLedger ledger;
    private final FormalEvaluationPlanPreparationRegistration registrations;
    private final ObjectProvider<RuntimeObjectTransport> transport;
    private final FormalEvaluationPlanPreparationSchema schema;
    private final ResourceReadAuthority resources;
    private final PermissionSchema json;
    public FormalEvaluationPlanPreparationService(EnterpriseAccess access,FormalEvaluationExecutionInputs inputs,
            FormalEvaluationBoundExecutions bounds,FormalEvaluationExecutionRegistrations roots,
            FormalEvaluationPlanPreparationAuthority authority,FormalEvaluationPlanPreparationLedger ledger,
            FormalEvaluationPlanPreparationRegistration registrations,ObjectProvider<RuntimeObjectTransport> transport,
            FormalEvaluationPlanPreparationSchema schema,ResourceReadAuthority resources,PermissionSchema json) {
        this.access=access;this.inputs=inputs;this.bounds=bounds;this.roots=roots;this.authority=authority;this.ledger=ledger;
        this.registrations=registrations;this.transport=transport;this.schema=schema;this.resources=resources;this.json=json;
    }
    public JsonNode prepareExecution(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        outside();json.headers(context,key);var request=schema.boundStart(bytes);var input=inputs.prepare(subject,context,request.start());
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var root=input.requireCurrent(a);FormalEvaluationPlanExpansion.exactN01(root);
            // Exact supported matrix is checked before either083 root or088 binding is persisted.
            return registration(bounds.prepare(a,key,input,request.contextBinding()));
        }).get());
    }
    private JsonNode registration(FormalEvaluationExecutionRegistrations.Registration r) {
        var data=json.object().put("schema_version","1.0.0").put("execution_id",r.executionId()).put("request_sha256",r.requestSha256())
                .put("expected_cells",r.expectedCells()).put("expected_nodes",r.expectedNodes());return schema.validate("Registration",data);
    }
    public JsonNode preview(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        outside();var proof=authority.prepare(subject,context,schema.request(bytes),false);
        return access.execute(subject,a->ThrowingSupplier.of(()->proof.current(a).preview()).get());
    }
    public JsonNode prepare(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        outside();json.headers(context,key);return transfer(subject,context,key,schema.request(bytes),false);
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception {
        outside();var r=schema.recovery(bytes);return transfer(subject,context,r.originalKey(),original(r),true);
    }
    private JsonNode transfer(ApplicationSessionIdentity.Verified subject,String context,String key,FormalEvaluationPlanPreparationSchema.Original original,boolean recovery)throws Exception {
        var actual=transport.getIfUnique();if(actual==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var proof=authority.prepare(subject,context,original,true);
        var attempt=access.execute(subject,a->ThrowingSupplier.of(()->recovery?ledger.recover(a,key,proof):ledger.prepare(a,key,proof)).get());
        var existing=access.execute(subject,a->ThrowingSupplier.of(()->registrations.read(a,proof,attempt)).get());
        if(existing.isPresent())return access.execute(subject,a->ThrowingSupplier.of(()->receipt(existing.get(),proof.current(a))).get());
        var physical=actual.transferVerified(attempt,()->access.execute(subject,a->ThrowingSupplier.of(()->{ledger.requireTransfer(a,attempt,proof);return null;}).get()));
        return access.execute(subject,a->ThrowingSupplier.of(()->receipt(registrations.register(a,proof,attempt,physical),proof.current(a))).get());
    }
    private JsonNode receipt(FormalEvaluationPlanPreparationRegistration.Receipt r,FormalEvaluationPlanPreparationAuthority.Checked p) {
        var data=json.object().put("schema_version","1.0.0").put("operation_id",r.operationId()).put("write_id",r.writeId()).put("attempt_id",r.attemptId())
                .put("execution_id",r.executionId()).put("artifact_id",r.artifact().artifactId()).put("artifact_sha256",r.artifact().sha256())
                .put("object_version",r.objectVersion()).put("receipt_sha256",r.receiptSha256()).put("state","PREPARED").put("runnable",false).put("professional_completion",false);
        data.set("preview",p.preview());return schema.validate("Prepared",data);
    }
    private FormalEvaluationPlanPreparationSchema.Original original(FormalEvaluationPlanPreparationSchema.Recovery r)throws Exception {
        return schema.request(schema.canonical(r.originalRequest()).getBytes(StandardCharsets.UTF_8));
    }
    private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
}
