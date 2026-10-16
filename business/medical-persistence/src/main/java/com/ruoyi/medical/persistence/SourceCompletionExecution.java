package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceCompletionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="medical.source-completion",name="enabled",havingValue="true")
public final class SourceCompletionExecution {
    private final SourceCompletionMapper mapper;
    private final SourceUploadMapper uploads;
    private final SourceCompletionLeaseStore leases;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceProcessingPolicy policy;
    private final SourceUploadSchema schema;

    SourceCompletionExecution(SourceCompletionMapper mapper,SourceUploadMapper uploads,SourceCompletionLeaseStore leases,
            PermissionMapper permissions,PermissionSchema json,SourceProcessingPolicy policy,SourceUploadSchema schema) {
        this.mapper=mapper; this.uploads=uploads; this.leases=leases; this.permissions=permissions; this.json=json; this.policy=policy;
        this.schema=schema;
    }

    LocalDateTime start(SourceCompletionAccess.Admission admission,SourceCompletionLeaseStore.Lease lease) throws Exception {
        leases.requireCurrent(lease); var values=values(admission); var before=one(mapper.execution(values));
        String manifest=manifest(admission);
        if(before.startedAt()==null) {
            if(before.deadline()!=null || before.manifest()!=null) mismatch();
            LocalDateTime deadline=admission.now().plus(policy.timeout());
            if(admission.session().expiresAt().isBefore(deadline)) deadline=admission.session().expiresAt();
            if(!deadline.isAfter(admission.now())) throw new TenantAccessException(ErrorCode.ACTION_STALE);
            owned(values,lease); values.put("deadline",deadline); values.put("completionManifest",manifest);
            changed(mapper.startExecution(values)); var saved=one(mapper.execution(values));
            if(!admission.now().equals(saved.startedAt()) || !deadline.equals(saved.deadline()) || !manifest.equals(json.canonical(schema.readJson(saved.manifest())))) mismatch();
            return deadline;
        }
        if(before.deadline()==null || !before.deadline().isAfter(before.startedAt()) || before.manifest()==null
                || !manifest.equals(json.canonical(schema.readJson(before.manifest())))) mismatch();
        return before.deadline();
    }

    boolean expire(SourceCompletionAccess.Admission admission) throws Exception {
        var values=values(admission); var delivery=one(uploads.completionDelivery(values));
        if("DELIVERING".equals(delivery.status()) && delivery.leaseUntil().isAfter(admission.now())) return false;
        var timer=one(mapper.execution(values));
        String reason=!admission.session().expiresAt().isAfter(admission.now())?"UPLOAD_EXPIRED":
            timer.deadline()!=null && !timer.deadline().isAfter(admission.now())?"EXECUTION_DEADLINE":
            delivery.attemptNo().compareTo(BigInteger.valueOf(6))>=0?"ATTEMPT_LIMIT":null;
        if(reason==null) return false;
        values.put("leaseOwner",delivery.leaseOwner()); values.put("generation",delivery.leaseGeneration());
        values.put("leaseRevision",delivery.revision()); values.put("liveFailure",false);
        terminal(admission,values,reason,null); return true;
    }

    void fail(SourceCompletionAccess.Admission admission,SourceCompletionLeaseStore.Lease lease,String reason,Integer exit) throws Exception {
        leases.requireCurrent(lease); var values=values(admission); owned(values,lease); values.put("liveFailure",true);
        terminal(admission,values,reason,exit);
    }

    private void terminal(SourceCompletionAccess.Admission admission,Map<String,Object> values,String reason,Integer exit) throws Exception {
        if(!List.of("UPLOAD_EXPIRED","EXECUTION_DEADLINE","ATTEMPT_LIMIT","SOURCE_VALIDATION_FAILED").contains(reason)) mismatch();
        var receipt=json.object(); receipt.put("schema_version","1.0.0"); receipt.put("upload_id",values.get("uploadId").toString());
        receipt.put("operation_id",values.get("operationId").toString()); receipt.put("event_id",values.get("eventId").toString());
        receipt.put("consumer_id",SourceUploadProtocol.COMPLETION_CONSUMER); receipt.put("status","FAILED");
        receipt.put("error_code",ErrorCode.PROCESSING_FAILED.name()); receipt.put("reason",reason);
        if(exit==null) receipt.putNull("exit_code"); else receipt.put("exit_code",exit);
        receipt.put("input_sha256",DigestUtils.sha256Hex(json.canonical(admission.command())));
        schema.completionResult(json.canonical(receipt));
        values.put("resultRef",json.canonical(receipt)); values.put("inputSha256",receipt.required("input_sha256").textValue());
        values.put("schemaManifest",schema.completionResultManifest());
        values.put("sourceRevision",admission.source().revision()); values.put("sessionRevision",admission.session().revision());
        values.put("response",json.canonical(admission.command().required("response"))); values.put("reason",reason);
        values.put("requestSha256",admission.command().required("request_sha256").textValue());
        changed(mapper.failDelivery(values)); changed(mapper.failUpload(values)); changed(mapper.failSession(values));
        changed(mapper.failOperation(values)); changed(mapper.saveReceipt(values));
        String actor=TenantScope.requireCurrent().subjectId(),requestId=UUID.randomUUID().toString();
        changed(permissions.saveAudit(actor,admission.declaration().projectId(),UUID.randomUUID().toString(),"source.upload.failed",
            json.canonical(receipt),requestId,(String)values.get("schemaManifest"),admission.declaration().classification()));
        changed(permissions.saveEvent(actor,UUID.randomUUID().toString(),admission.declaration().uploadId(),
            admission.source().revision().add(BigInteger.ONE).toString(),"source.upload.failed",json.canonical(receipt)));
        if(!json.canonical(receipt).equals(json.canonical(schema.completionResult(one(mapper.receipt(values)))))) mismatch();
        var operation=one(uploads.completionOperationById(values)); var delivery=one(uploads.completionDelivery(values));
        if(!"FAILED".equals(operation.state()) || !BigInteger.TWO.equals(operation.revision())
                || !ErrorCode.PROCESSING_FAILED.name().equals(operation.errorCode()) || !Integer.valueOf(202).equals(operation.httpStatus())
                || !values.get("response").equals(json.canonical(schema.readJson(operation.responseRef())))
                || !"FAILED".equals(delivery.status()) || delivery.leaseOwner()!=null || delivery.leaseUntil()!=null
                || !reason.equals(delivery.lastError())) mismatch();
    }

    private String manifest(SourceCompletionAccess.Admission admission) throws Exception {
        var body=json.object(); body.put("schema_version","1.0.0"); body.put("runtime_revision",policy.revision());
        body.put("python_sha256",policy.pythonSha256()); body.set("source_files",json.tree(policy.sourceFiles()));
        body.put("config_revision",admission.binding().configRevision()); body.put("config_sha256",admission.binding().configSha256());
        body.put("scanner_release_sha256",admission.binding().scannerReleaseSha256()); body.put("timeout_seconds",policy.timeout().toSeconds());
        body.put("maximum_source_bytes",policy.maximumSourceBytes()); body.put("environment",policy.environment()); body.put("region",policy.region());
        return json.canonical(body);
    }

    private static Map<String,Object> values(SourceCompletionAccess.Admission admission) {
        var values=new HashMap<String,Object>(); values.put("userId",TenantScope.requireCurrent().subjectId());
        values.put("projectId",admission.declaration().projectId()); values.put("uploadId",admission.declaration().uploadId());
        values.put("operationId",admission.command().required("operation_id").textValue()); values.put("eventId",admission.command().required("event_id").textValue());
        values.put("consumerId",SourceUploadProtocol.COMPLETION_CONSUMER); values.put("now",admission.now());
        values.put("classification",admission.declaration().classification()); return values;
    }
    private static void owned(Map<String,Object> values,SourceCompletionLeaseStore.Lease lease) {
        values.put("leaseOwner",lease.row().leaseOwner()); values.put("generation",lease.row().leaseGeneration()); values.put("leaseRevision",lease.row().revision());
    }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) mismatch(); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if(count!=1) throw new TenantAccessException(ErrorCode.LEASE_STALE); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    public record Runtime(String eventId,LocalDateTime startedAt,LocalDateTime deadline,String manifest) { }
}
