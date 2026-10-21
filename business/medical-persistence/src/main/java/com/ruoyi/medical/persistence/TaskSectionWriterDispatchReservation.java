package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable scheduler command reservation, NOT a monetary reservation. Actual paid
 * cost/tokens/calls remain reserved by the existing invocation boundary. Neither an
 * incomplete reservation nor an expired lease is proof that a dispatch succeeded. */
public final class TaskSectionWriterDispatchReservation {
    static final String RESERVED="task.section_writer.dispatch.reserved", CLAIMED="task.section_writer.dispatch.claimed";
    private static final Contract CONTRACT=contract();
    static final String MANIFEST=CONTRACT.hash();
    private final JdbcTemplate db;
    private final TaskSectionWriterInputAuthority inputs;
    private final ServiceCertificateIdentity services;
    private final PermissionSchema json;
    private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public TaskSectionWriterDispatchReservation(DataSource data,TaskSectionWriterInputAuthority inputs,ServiceCertificateIdentity services,PermissionSchema json){
        this(new JdbcTemplate(data),inputs,services,json);
    }
    TaskSectionWriterDispatchReservation(JdbcTemplate db,TaskSectionWriterInputAuthority inputs,ServiceCertificateIdentity services,PermissionSchema json){
        this.db=Objects.requireNonNull(db);db.setQueryTimeout(5);this.inputs=Objects.requireNonNull(inputs);this.services=Objects.requireNonNull(services);this.json=Objects.requireNonNull(json);
    }

    /** Called only in the actual accepted-running preparation transaction. Returned
     * proof cannot be consumed until that transaction commits successfully. */
    Pending reserve(TaskRunActivityPreparation.Current current,HttpServletRequest http,WorkflowProtocol.Reference reference,String step,String command)throws Exception{
        Objects.requireNonNull(current).requireUnchanged();WireValues.uuid(step);WireValues.uuid(command);
        var p=current.production();var a=current.admission();active(a);same(p.runId(),reference.runId());same(p.tenantId(),reference.tenantId());same(p.projectId(),reference.projectId());
        if(!current.graph().requiredStages().contains("N04"))deny();
        var input=inputs.require(a,p.runId(),step,current.publication(),current.policy(),p);input.requireUnchanged();
        var service=services.require(http);var row=step(input);requireReady(row);
        var request=request(input,a,reference,command,service,row);
        String id=reservationId(input.tenantId(),input.runId(),step);var rows=operations(input.tenantId(),id);
        JsonNode original;
        if(rows.isEmpty()){
            if(!db.queryForList("SELECT audit_id FROM audit_event WHERE tenant_id=? AND trace_id=? AND action=? AND request_id=? LIMIT 1 FOR UPDATE",input.tenantId(),input.runId(),RESERVED,command).isEmpty())throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            // Locking the actual READY step serializes command conflicts before either
            // immutable witness is written. The unique operation key is the second guard.
            var receipt=json.object().put("schema_version","1.0.0").put("kind","TASK_SECTION_WRITER_DISPATCH_RESERVED").put("receipt_id",id)
                .put("created_at",now().toInstant(ZoneOffset.UTC).toString());receipt.set("request",request);
            validate(receipt);write(receipt,RESERVED,hash(request),text(row,"run_classification"));original=verify(one(operations(input.tenantId(),id)),RESERVED);
        }else {verify(one(rows),RESERVED);unavailable();return null;}
        same(request,original.required("request"));
        if(!operations(input.tenantId(),derived(id,"claimed")).isEmpty())unavailable();
        // A partial original dispatch is never repaired by claiming a new generation.
        noRuntimeAttempt(input.tenantId(),input.runId(),step);
        input.requireUnchanged();current.requireUnchanged();sameService(service,services.require(http));
        var proof=new Pending(this,original);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){proof.committed=true;}
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)proof.invalid=true;}
        });
        return proof;
    }

    /** Historical command binding only; caller must independently verify the original
     * accepted launch and dispatch. No lease/token/provider operations are reachable. */
    Optional<Original> original(EnterpriseAccess.Admission recovery,WorkflowProtocol.Reference reference,String step,String command)throws Exception{
        if(!recovery.recoveryWorker())deny();activeRecovery(recovery,reference);WireValues.uuid(step);WireValues.uuid(command);
        String id=reservationId(reference.tenantId(),reference.runId(),step);var rows=operations(reference.tenantId(),id);
        if(rows.isEmpty())return Optional.empty();
        var reserved=verify(one(rows),RESERVED);var request=reserved.required("request");
        same(reference.tenantId(),text(request,"tenant_id"));same(reference.projectId(),text(request,"project_id"));same(reference.runId(),text(request,"run_id"));same(step,text(request,"step_id"));
        same(command,text(request,"command_id"));same(hash(json.tree(reference)),text(request,"reference_sha256"));same(recovery.actorId(),text(request,"initiator_id"));
        var identity=recovery.recoveryProof().identity();same(identity.taskId(),text(request,"task_id"));same(identity.principalId(),text(request,"principal_id"));same(identity.serviceUri(),text(request,"service_uri"));
        var claims=operations(reference.tenantId(),derived(id,"claimed"));
        if(claims.isEmpty()){
            noRuntimeAttempt(reference.tenantId(),reference.runId(),step);recovery.recoveryProof().requireActive();return Optional.of(new Original(reserved,null));
        }
        var claimed=verify(one(claims),CLAIMED);same(request,claimed.required("request"));same(hash(reserved),text(claimed,"reservation_sha256"));
        same(new BigInteger(text(request,"expected_generation")).add(BigInteger.ONE).toString(),text(claimed,"lease_generation"));
        same(new BigInteger(text(request,"expected_revision")).add(BigInteger.ONE).toString(),text(claimed,"step_revision"));
        same(text(request,"service_uri"),text(claimed,"lease_owner"));
        recovery.recoveryProof().requireActive();return Optional.of(new Original(reserved,claimed));
    }
    /** Separate current/lifecycle read facts. This never creates a recovery Original or lease. */
    void requireStageFacts(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch dispatch,String task,String principal)throws Exception{requireStageFacts(a,ref,dispatch,task,principal,null);}
    void requireStageFacts(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,WorkflowProtocol.Dispatch dispatch,String task,String principal,TaskRunResumeOriginalAuthority.Source source)throws Exception{
        if(!a.controlWorker())deny();a.controlProof().requireActive();var identity=(FormalRunControlAccess.ReadIdentity)a.controlProof().identity();same(ref.tenantId(),identity.tenantId());if(source==null)same(ref.runId(),identity.runId());else source.requireOriginal(a,ref.runId(),dispatch.stepId());
        String id=reservationId(ref.tenantId(),ref.runId(),dispatch.stepId());var reserved=verify(one(operations(ref.tenantId(),id)),RESERVED);var request=reserved.required("request");
        same(ref.tenantId(),text(request,"tenant_id"));same(ref.projectId(),text(request,"project_id"));same(ref.runId(),text(request,"run_id"));same(dispatch.stepId(),text(request,"step_id"));same(dispatch.commandId(),text(request,"command_id"));same(hash(json.tree(ref)),text(request,"reference_sha256"));same(a.actorId(),text(request,"initiator_id"));same(task,text(request,"task_id"));same(principal,text(request,"principal_id"));same(identity.serviceUri(),text(request,"service_uri"));
        var claimed=verify(one(operations(ref.tenantId(),derived(id,"claimed"))),CLAIMED);same(request,claimed.required("request"));same(hash(reserved),text(claimed,"reservation_sha256"));same(new BigInteger(text(request,"expected_generation")).add(BigInteger.ONE).toString(),text(claimed,"lease_generation"));same(new BigInteger(text(request,"expected_revision")).add(BigInteger.ONE).toString(),text(claimed,"step_revision"));same(text(request,"service_uri"),text(claimed,"lease_owner"));
        same(text(request,"input_sha256"),dispatch.inputSha256());same(new BigInteger(text(claimed,"lease_generation")).longValueExact(),dispatch.leaseGeneration());same(TaskSectionWriterHostBootstrap.actionId(ref.tenantId(),ref.runId(),dispatch.stepId(),BigInteger.valueOf(dispatch.leaseGeneration())),dispatch.actionId());a.controlProof().requireActive();
    }
    List<com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.CommandV1> commands(EnterpriseAccess.Admission recovery,WorkflowProtocol.Reference reference)throws Exception {
        activeRecovery(recovery,reference);var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND target_id=? ORDER BY operation_id FOR UPDATE",reference.tenantId(),recovery.actorId(),RESERVED,reference.runId());
        if(rows.size()>reference.maximumSteps())bad();var result=new ArrayList<com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.CommandV1>();
        for(var row:rows){var reserved=verify(row,RESERVED);var r=reserved.required("request");String step=text(r,"step_id"),command=text(r,"command_id");original(recovery,reference,step,command).orElseThrow();
            var input=one(db.queryForList("SELECT canonical_input,input_sha256 FROM task_section_writer_input WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",reference.tenantId(),reference.projectId(),reference.runId(),step));var document=reader.readTree(text(input,"canonical_input"));same(hash(document),text(input,"input_sha256"));same(text(r,"input_sha256"),text(input,"input_sha256"));
            var chapters=new ArrayList<String>();for(var chapter:document.required("target_sections")){if(!chapter.isTextual())bad();chapters.add(chapter.textValue());}
            result.add(new com.ruoyi.medical.workflow.TaskSectionWriterStageProtocolV1.CommandV1(reference.runId(),step,text(r,"input_sha256"),command,chapters));
        }return List.copyOf(result);
    }
    static final class Original {
        private final JsonNode reserved,claimed;
        private Original(JsonNode reserved,JsonNode claimed){this.reserved=reserved.deepCopy();this.claimed=claimed==null?null:claimed.deepCopy();}
        boolean claimed(){return claimed!=null;}
        long generation(){if(claimed==null)unavailable();return new BigInteger(text(claimed,"lease_generation")).longValueExact();}
        String inputSha256(){return text(reserved.required("request"),"input_sha256");}
        void requireDispatch(WorkflowProtocol.Dispatch dispatch){
            if(claimed==null)unavailable();var r=reserved.required("request");same(text(r,"run_id"),dispatch.runId());same(text(r,"step_id"),dispatch.stepId());same(text(r,"command_id"),dispatch.commandId());same(inputSha256(),dispatch.inputSha256());same(generation(),dispatch.leaseGeneration());
            same(TaskSectionWriterHostBootstrap.actionId(text(r,"tenant_id"),dispatch.runId(),dispatch.stepId(),BigInteger.valueOf(dispatch.leaseGeneration())),dispatch.actionId());
        }
    }

    /** Only this owner can mint a committed command capability. It is single-use,
     * same-thread, and binds its before/after hooks to one actual claim transaction. */
    static final class Pending {
        private final TaskSectionWriterDispatchReservation owner;private final JsonNode original;private final Thread thread=Thread.currentThread();
        private volatile boolean committed,invalid;private boolean used;private Map<Object,Object> transaction;private TenantScope scope;
        private FormalTaskNodeAuthority.Verified node;
        private Pending(TaskSectionWriterDispatchReservation owner,JsonNode original){this.owner=owner;this.original=original.deepCopy();}
        String actor(){usable();return text(original.required("request"),"initiator_id");}
        BigInteger revision(){usable();return new BigInteger(text(original.required("request"),"expected_revision"));}
        void before(FormalTaskNodeAuthority.Verified node,RunTokenAuthority.ClaimRequest claim,ServiceCertificateIdentity.Verified service,
                RunTokenAuthorityStore.Run run,RunTokenAuthorityStore.Step step)throws Exception{
            usable();if(used||transaction!=null||node==null)deny();node.requireUnchanged();
            this.node=node;this.scope=TenantScope.requireCurrent();transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());if(transaction.isEmpty())deny();
            owner.before(this,claim,service,run,step);
        }
        void after(FormalTaskNodeAuthority.Verified node,RunTokenAuthority.Lease lease)throws Exception{
            usable();if(used||this.node!=node||scope!=TenantScope.requireCurrent()||transaction==null)deny();
            var current=TransactionSynchronizationManager.getResourceMap();if(transaction.size()!=current.size()||transaction.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();
            node.requireUnchanged();owner.after(this,lease);node.requireUnchanged();used=true;
        }
        private void usable(){if(!committed||invalid||thread!=Thread.currentThread())deny();}
        @Override public String toString(){return "CommittedTaskDispatchReservation";}
    }
    private void before(Pending pending,RunTokenAuthority.ClaimRequest claim,ServiceCertificateIdentity.Verified service,RunTokenAuthorityStore.Run run,RunTokenAuthorityStore.Step step)throws Exception{
        var input=pending.node.currentWriterInput();var a=pending.node.controlAdmission();var original=pending.original;var r=original.required("request");
        same(claim.tenantId(),text(r,"tenant_id"));same(claim.initiatorId(),text(r,"initiator_id"));same(claim.runId(),text(r,"run_id"));same(claim.stepId(),text(r,"step_id"));same(claim.expectedRevision().toString(),text(r,"expected_revision"));
        same(run.tenant(),text(r,"tenant_id"));same(run.project(),text(r,"project_id"));same(run.id(),text(r,"run_id"));same(run.actor(),text(r,"initiator_id"));same(run.principal(),text(r,"principal_id"));same(run.task(),text(r,"task_id"));
        same("READY",step.status());if(step.owner()!=null||step.expires()!=null)deny();same(step.revision().toString(),text(r,"expected_revision"));same(step.generation().toString(),text(r,"expected_generation"));same(step.input(),text(r,"input_sha256"));
        same(service.serviceUri(),text(r,"service_uri"));same(service.certificateSha256(),text(r,"service_certificate_sha256"));same(service.trustRevision(),text(r,"service_trust_revision"));
        var fresh=request(input,a,null,text(r,"command_id"),service,step(input));fresh.put("reference_sha256",text(r,"reference_sha256"));same(r,fresh);
        same(original,verify(one(operations(input.tenantId(),text(original,"receipt_id"))),RESERVED));
        if(!operations(input.tenantId(),derived(text(original,"receipt_id"),"claimed")).isEmpty())unavailable();noRuntimeAttempt(input.tenantId(),input.runId(),input.stepId());
        input.requireUnchanged();pending.node.requireUnchanged();
    }
    private void after(Pending pending,RunTokenAuthority.Lease lease)throws Exception{
        var r=pending.original.required("request");same(text(r,"run_id"),lease.runId());same(text(r,"step_id"),lease.stepId());same(text(r,"service_uri"),lease.owner());
        same(new BigInteger(text(r,"expected_generation")).add(BigInteger.ONE),lease.generation());same(new BigInteger(text(r,"expected_revision")).add(BigInteger.ONE),lease.revision());
        var row=step(pending.node.currentWriterInput());same("RUNNING",row.get("status"));same(lease.generation().toString(),number(row,"lease_generation"));same(lease.revision().toString(),number(row,"revision"));same(lease.owner(),row.get("lease_owner"));same(lease.expiresAt(),time(row,"lease_expires_at").toInstant(ZoneOffset.UTC));
        var receipt=json.object().put("schema_version","1.0.0").put("kind","TASK_SECTION_WRITER_DISPATCH_CLAIMED").put("receipt_id",derived(text(pending.original,"receipt_id"),"claimed"))
            .put("created_at",now().toInstant(ZoneOffset.UTC).toString()).put("reservation_sha256",hash(pending.original)).put("lease_owner",lease.owner())
            .put("lease_generation",lease.generation().toString()).put("step_revision",lease.revision().toString()).put("lease_expires_at",lease.expiresAt().toString());receipt.set("request",r);
        validate(receipt);write(receipt,CLAIMED,hash(pending.original),text(row,"run_classification"));same(receipt,verify(one(operations(text(r,"tenant_id"),text(receipt,"receipt_id"))),CLAIMED));
    }
    private ObjectNode request(TaskSectionWriterInputAuthority.Verified input,EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref,String command,ServiceCertificateIdentity.Verified service,Map<String,Object> step)throws Exception{
        input.requireUnchanged();active(a);same("N04",text(input.manifest(),"node_kind"));
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_input WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND created_by=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId(),a.actorId()));
        var run=one(db.queryForList("SELECT r.*,p.principal_id FROM task_run r JOIN run_principal p ON p.tenant_id=r.tenant_id AND p.project_id=r.project_id AND p.run_id=r.run_id AND p.initiator_id=r.created_by WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.created_by=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),a.actorId()));
        RunContinuationService.requireSuccessor(db,a,input.runId());same("RUNNING",run.get("status"));for(String f:List.of("ended_at","dispatch_closed_at","superseded_by_run_id"))if(run.get(f)!=null)deny();
        same(input.inputSha256(),step.get("input_sha256"));var replay=reader.readTree(text(run,"replay_manifest"));
        var value=json.object().put("tenant_id",input.tenantId()).put("project_id",input.projectId()).put("task_id",input.taskId()).put("run_id",input.runId()).put("step_id",input.stepId())
            .put("principal_id",text(run,"principal_id")).put("initiator_id",a.actorId()).put("command_id",command).put("reference_sha256",ref==null?"0".repeat(64):hash(json.tree(ref)))
            .put("graph_id",input.input().graphId()).put("graph_sha256",input.input().graphSha256()).put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256())
            .put("input_object_version",input.objectReference().objectVersion()).put("expected_revision",number(step,"revision")).put("expected_generation",number(step,"lease_generation"))
            .put("budget_revision",text(replay,"budget_revision")).put("service_uri",service.serviceUri()).put("service_certificate_sha256",service.certificateSha256()).put("service_trust_revision",service.trustRevision());
        for(String f:List.of("producer_rule_id","producer_sha256","producer_decision_id","producer_decision_sha256"))value.put(f,text(intent,f));
        return value;
    }
    private Map<String,Object> step(TaskSectionWriterInputAuthority.Verified input){return one(db.queryForList("SELECT s.*,r.classification run_classification FROM run_step s JOIN task_run r ON r.tenant_id=s.tenant_id AND r.project_id=s.project_id AND r.run_id=s.run_id WHERE s.tenant_id=? AND s.project_id=? AND s.run_id=? AND s.step_id=? LIMIT 2 FOR UPDATE",input.tenantId(),input.projectId(),input.runId(),input.stepId()));}
    private static void requireReady(Map<String,Object> row){same("READY",row.get("status"));if(row.get("lease_owner")!=null||row.get("lease_expires_at")!=null)deny();}
    private void noRuntimeAttempt(String tenant,String run,String step){if(!db.queryForList("SELECT step_id FROM task_section_writer_runtime_prepare WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1 FOR UPDATE",tenant,run,step).isEmpty())unavailable();if(!db.queryForList("SELECT step_id FROM step_runtime_attempt WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1 FOR UPDATE",tenant,run,step).isEmpty())unavailable();}
    private List<Map<String,Object>> operations(String tenant,String id){return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",tenant,id);}
    private void write(JsonNode value,String action,String requestHash,String classification)throws Exception{
        var r=value.required("request");String tenant=text(r,"tenant_id"),project=text(r,"project_id"),actor=text(r,"initiator_id"),run=text(r,"run_id"),id=text(value,"receipt_id"),canonical=json.canonical(value);var at=LocalDateTime.ofInstant(java.time.Instant.parse(text(value,"created_at")),ZoneOffset.UTC);
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,'1.0.0',?,?,?,?,1,?)",tenant,id,actor,action,"task-section-writer-dispatch:"+run+":"+text(r,"step_id"),requestHash,canonical,run,MANIFEST,actor,at,classification,at));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",tenant,project,derived(id,"audit"),actor,action,canonical,text(r,"command_id"),run,MANIFEST,actor,at,classification));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",tenant,derived(id,"event"),id,action,canonical,at));
    }
    private JsonNode verify(Map<String,Object> op,String action)throws Exception{
        var value=reader.readTree(text(op,"response_ref"));validate(value);var r=value.required("request");String id=text(value,"receipt_id"),tenant=text(r,"tenant_id"),actor=text(r,"initiator_id"),run=text(r,"run_id"),canonical=json.canonical(value);
        same(RESERVED.equals(action)?"TASK_SECTION_WRITER_DISPATCH_RESERVED":"TASK_SECTION_WRITER_DISPATCH_CLAIMED",text(value,"kind"));String reserved=reservationId(tenant,run,text(r,"step_id"));same(RESERVED.equals(action)?reserved:derived(reserved,"claimed"),id);
        for(var p:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("operation_id",id),Map.entry("subject_id",actor),Map.entry("operation_name",action),Map.entry("idempotency_key","task-section-writer-dispatch:"+run+":"+text(r,"step_id")),Map.entry("request_sha256",RESERVED.equals(action)?hash(r):text(value,"reservation_sha256")),Map.entry("state","COMPLETED"),Map.entry("response_ref",canonical),Map.entry("target_id",run),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",MANIFEST),Map.entry("created_by",actor)).entrySet())same(p.getValue(),op.get(p.getKey()));
        same("200",number(op,"http_status"));same("1",number(op,"revision"));if(op.get("error_code")!=null||op.get("dedup_expires_at")!=null||op.get("dedup_policy_revision")!=null)bad();
        var at=LocalDateTime.ofInstant(java.time.Instant.parse(text(value,"created_at")),ZoneOffset.UTC);same(at,time(op,"created_at"));same(at,time(op,"updated_at"));
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"audit")));
        for(var p:Map.ofEntries(Map.entry("tenant_id",tenant),Map.entry("project_id",text(r,"project_id")),Map.entry("audit_id",derived(id,"audit")),Map.entry("subject_id",actor),Map.entry("action",action),Map.entry("resource_ref",canonical),Map.entry("result","SUCCESS"),Map.entry("request_id",text(r,"command_id")),Map.entry("trace_id",run),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",MANIFEST),Map.entry("created_by",actor),Map.entry("classification",text(op,"classification"))).entrySet())same(p.getValue(),audit.get(p.getKey()));
        if(audit.get("reason_code")!=null)bad();same(at,time(audit,"created_at"));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"event")));same(tenant,event.get("tenant_id"));same(derived(id,"event"),event.get("event_id"));same(id,event.get("aggregate_id"));same("1",number(event,"aggregate_revision"));same(action,event.get("event_type"));same(canonical,event.get("payload"));same(at,time(event,"occurred_at"));
        return value;
    }
    private static void active(EnterpriseAccess.Admission a){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.isSynchronizationActive()||!a.controlWorker())deny();a.controlProof().requireActive();}
    private static void activeRecovery(EnterpriseAccess.Admission a,WorkflowProtocol.Reference ref){if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.recoveryWorker())deny();var p=a.recoveryProof();p.requireActive();same(ref.tenantId(),p.identity().tenantId());same(ref.projectId(),p.identity().projectId());same(ref.runId(),p.identity().runId());}
    private static void sameService(ServiceCertificateIdentity.Verified a,ServiceCertificateIdentity.Verified b){same(a.serviceUri(),b.serviceUri());same(a.certificateSha256(),b.certificateSha256());same(a.trustRevision(),b.trustRevision());}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private String hash(JsonNode value)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(value));}
    static String reservationId(String tenant,String run,String step){for(String id:List.of(tenant,run,step))WireValues.uuid(id);return derived(tenant+"|"+run+"|"+step,"task-section-writer-dispatch");}
    private static String derived(String id,String part){return UUID.nameUUIDFromBytes((id+"|"+part).getBytes(StandardCharsets.US_ASCII)).toString();}
    private static String text(JsonNode n,String f){var v=n.required(f);if(!v.isTextual())bad();return v.textValue();}
    private static String text(Map<String,Object> row,String f){Object v=row.get(f);if(!(v instanceof String))bad();return (String)v;}
    private static String number(Map<String,Object> row,String f){return new java.math.BigDecimal(String.valueOf(row.get(f))).toBigIntegerExact().toString();}
    private static LocalDateTime time(Map<String,Object> row,String f){var v=row.get(f);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
    private record Contract(com.networknt.schema.JsonSchema validator,String hash){}
    private static Contract contract(){try(var in=Objects.requireNonNull(TaskSectionWriterDispatchReservation.class.getResourceAsStream("/task-section-writer-dispatch-reservation.schema.json"))){byte[] bytes=in.readAllBytes();var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);var validator=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(node,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());validator.initializeValidators();return new Contract(validator,org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void validate(JsonNode n){if(!CONTRACT.validator().validate(n).isEmpty())bad();}
}
