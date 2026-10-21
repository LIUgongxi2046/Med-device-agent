package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Single original EVAL dispatch. No TASK lease conversion, replacement action or new generation
 * is permitted on recovery. An expired original remains visible and cannot send again. */
public final class FormalEvaluationNodeLeases {
    private final JdbcTemplate db;private final FormalEvaluationJobs jobs;private final FormalEvaluationLaunchLedger launches;
    private final PermissionSchema json;private final Duration duration;
    public FormalEvaluationNodeLeases(DataSource data,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,PermissionSchema json,Duration duration){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.jobs=jobs;this.launches=launches;this.json=json;
        if(duration==null||duration.isNegative()||duration.isZero()||duration.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("EVALUATION_LEASE_BOUND_REQUIRED");this.duration=duration;
    }
    public Lease acquire(EnterpriseAccess.Admission a,FormalEvaluationJobs.Job job,FormalEvaluationProtocol.Reference reference,
            FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service)throws Exception {
        current(a,job,reference,step,input,service,false);lock(job,step);
        var old=rows(job,step);if(!old.isEmpty())return require(a,reference,step,input,service);
        var now=now();String action=id(),command=id(),audit=id(),event=id();long generation=1;
        var node=input.node();var evidence=json.object().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","EVALUATION_VERIFICATION")
            .put("tenant_id",job.tenantId()).put("project_id",job.projectId()).put("run_id",job.runId()).put("step_id",step.stepId()).put("node_sha256",step.nodeSha256())
            .put("command_id",command).put("action_id",action).put("lease_generation",generation).put("worker_service",service)
            .put("input_artifact_id",input.artifact().artifactId()).put("input_sha256",input.sha256()).put("admission_sha256",job.admissionSha256());
        evidence.set("input",json.tree(input.artifact()));evidence.set("compiler",input.evidence());String canonical=json.canonical(evidence),hash=sha(canonical);
        changed(db.update("UPDATE run_step SET status='RUNNING',input_sha256=?,lease_generation=1,lease_owner=?,lease_expires_at=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND status='READY' AND lease_generation=0 AND role_version=?",input.sha256(),service,now.plus(duration),now,job.tenantId(),job.projectId(),job.runId(),step.stepId(),node.node().role().version_id()));
        changed(db.update("INSERT INTO step_action_binding (tenant_id,project_id,run_id,step_id,action_id,input_sha256,lease_generation,kind,arguments_sha256,tool_version,state,revision,schema_version,schema_manifest_sha256,created_by,created_at,classification,updated_at) VALUES (?,?,?,?,?,?,1,'HOST_CONTROL',?,'evaluation-runtime-v1','BOUND',1,'1.0.0',?,?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),step.stepId(),action,input.sha256(),hash,json.manifestSha256(),job.actorId(),now,job.classification(),now));
        journal(job,audit,event,command,canonical,now);
        changed(db.update("INSERT INTO formal_evaluation_node_dispatch (tenant_id,project_id,run_id,step_id,command_id,action_id,lease_generation,node_sha256,input_artifact_id,input_sha256,worker_service,canonical_dispatch,dispatch_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,1,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),step.stepId(),command,action,step.nodeSha256(),input.artifact().artifactId(),input.sha256(),service,canonical,hash,audit,event,json.manifestSha256(),job.actorId(),now,job.classification()));
        return require(a,reference,step,input,service);
    }
    public Lease require(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service)throws Exception {
        var job=jobs.byRun(a,reference.runId());current(a,job,reference,step,input,service,false);var row=one(rows(job,step));verify(job,step,input,service,row,true);
        return new Lease(this,a,job,input.node(),reference,step,input,service,row,false);
    }
    Lease requireRecovery(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service)throws Exception {
        var job=jobs.byRun(a,reference.runId());current(a,job,reference,step,input,service,true);var row=one(rows(job,step));verify(job,step,input,service,row,false);return new Lease(this,a,job,input.node(),reference,step,input,service,row,true);
    }
    public void renew(Lease lease)throws Exception {
        if(lease==null||lease.owner!=this)deny();lease.requireDispatchCurrent();var j=lease.job();
        changed(db.update("UPDATE run_step SET lease_expires_at=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=? AND lease_owner=? AND status='RUNNING' AND lease_expires_at>UTC_TIMESTAMP(6)",now().plus(duration),j.tenantId(),j.runId(),lease.stepId(),lease.generation(),lease.workerService()));lease.requireCurrent();
    }
    private void current(EnterpriseAccess.Admission a,FormalEvaluationJobs.Job job,FormalEvaluationProtocol.Reference ref,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service,boolean recovery)throws Exception {
        FormalEvaluationExecutionAdmission.current(a);if(!a.evaluationWorker()||!a.evaluationProof().job().equals(job))deny();same(job,jobs.byRun(a,job.runId()));launches.requireAccepted(a,job,ref);
        if(!ref.steps().contains(step)||!job.runId().equals(ref.runId()))bad();input.requireCurrent(a);same(input.node().stepId(),step.stepId());same(input.reads().workerService(),service);
        var j=one(db.queryForList("SELECT status,ended_at FROM system_job_run WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='SYSTEM_JOB' AND job_type='EVALUATION_VERIFICATION' AND workflow_id=? FOR UPDATE",job.tenantId(),job.projectId(),job.runId(),ref.workflowId()));if(!"RUNNING".equals(j.get("status"))||j.get("ended_at")!=null)deny();
        if(!recovery&&!db.queryForList("SELECT receipt_id FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 1",job.tenantId(),job.runId()).isEmpty())deny();
        // A later node may not run before all preceding approved obligations completed. No missing
        // node or WAITING_INPUT decision is silently treated as applicable success.
        for(var prior:ref.steps()){if(prior.equals(step))break;var p=one(db.queryForList("SELECT status FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=?",job.tenantId(),job.runId(),prior.stepId()));if(!"SUCCEEDED".equals(p.get("status")))deny();}
    }
    private void verify(FormalEvaluationJobs.Job job,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service,Map<String,Object> row,boolean live)throws Exception {
        same(job.projectId(),text(row,"project_id"));same(job.actorId(),text(row,"created_by"));same(step.nodeSha256(),text(row,"node_sha256"));same(input.artifact().artifactId(),text(row,"input_artifact_id"));same(input.sha256(),text(row,"input_sha256"));same(service,text(row,"worker_service"));same(json.manifestSha256(),text(row,"schema_manifest_sha256"));same("1.0.0",text(row,"schema_version"));same(sha(text(row,"canonical_dispatch")),text(row,"dispatch_sha256"));if(number(row,"lease_generation")!=1)bad();
        var n=new com.fasterxml.jackson.databind.ObjectMapper().readTree(text(row,"canonical_dispatch"));same("EVALUATION_VERIFICATION",n.required("job_type").asText());same("SYSTEM_JOB",n.required("execution_kind").asText());same(json.tree(input.artifact()),n.required("input"));same(input.evidence(),n.required("compiler"));
        for(String k:List.of("tenant_id","project_id","run_id","step_id","command_id","action_id","node_sha256","input_artifact_id","input_sha256","worker_service"))same(row.get(k),n.required(k).asText());same(job.admissionSha256(),n.required("admission_sha256").asText());
        var s=one(db.queryForList("SELECT * FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? FOR UPDATE",job.tenantId(),job.projectId(),job.runId(),step.stepId()));same(input.sha256(),s.get("input_sha256"));same(service,s.get("lease_owner"));if(number(s,"lease_generation")!=1)bad();
        if(live&&(!"RUNNING".equals(s.get("status"))||!now().isBefore(((java.sql.Timestamp)s.get("lease_expires_at")).toLocalDateTime())))throw new TenantAccessException(ErrorCode.LEASE_STALE);
        var action=one(db.queryForList("SELECT * FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND kind='HOST_CONTROL' LIMIT 2",job.tenantId(),job.runId(),step.stepId(),row.get("action_id")));same(input.sha256(),action.get("input_sha256"));same(row.get("dispatch_sha256"),action.get("arguments_sha256"));if(number(action,"lease_generation")!=1)bad();
        same(text(row,"canonical_dispatch"),one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action='evaluation.node.dispatched' AND request_id=? AND trace_id=? AND result='SUCCESS' AND schema_manifest_sha256=? LIMIT 2",(r,i)->r.getString(1),job.tenantId(),job.projectId(),row.get("audit_id"),job.actorId(),row.get("command_id"),job.runId(),json.manifestSha256())));
        same(text(row,"canonical_dispatch"),one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='evaluation.node.dispatched' AND aggregate_revision=1 LIMIT 2",(r,i)->r.getString(1),job.tenantId(),row.get("event_id"),row.get("command_id"))));
    }
    public static final class Lease {
        private final FormalEvaluationNodeLeases owner;private final EnterpriseAccess.Admission admission;private final FormalEvaluationJobs.Job job;private final FormalEvaluationJobs.NodeRow node;
        private final FormalEvaluationProtocol.Reference reference;private final FormalEvaluationProtocol.Step step;private final FormalEvaluationNodeInputs.Verified input;private final String service;private final Map<String,Object> row;private final boolean recovery;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Lease(FormalEvaluationNodeLeases owner,EnterpriseAccess.Admission a,FormalEvaluationJobs.Job job,FormalEvaluationJobs.NodeRow node,FormalEvaluationProtocol.Reference reference,FormalEvaluationProtocol.Step step,FormalEvaluationNodeInputs.Verified input,String service,Map<String,Object> row,boolean recovery){this.recovery=recovery;this.owner=owner;admission=a;this.job=job;this.node=node;this.reference=reference;this.step=step;this.input=input;this.service=service;this.row=Map.copyOf(row);}
        public void requireCurrent()throws Exception{active();owner.current(admission,job,reference,step,input,service,recovery);var now=one(owner.rows(job,step));same(row,now);owner.verify(job,step,input,service,now,!recovery);}
        public void requireDispatchCurrent()throws Exception{requireCurrent();if(recovery)deny();owner.verify(job,step,input,service,row,true);}
        private void active(){if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive()||resources.isEmpty())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=resources.size()||resources.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();}
        public EnterpriseAccess.Admission admission(){active();return admission;}public FormalEvaluationJobs.Job job(){active();return job;}public FormalEvaluationJobs.NodeRow node(){active();return node;}
        public String stepId(){active();return step.stepId();}public long generation(){active();return 1;}public String actionId(){active();return text(row,"action_id");}
        public String commandId(){active();return text(row,"command_id");}public String workerService(){active();return service;}public FormalEvaluationNodeInputs.Verified input(){active();return input;}
    }
    private void lock(FormalEvaluationJobs.Job j,FormalEvaluationProtocol.Step s){one(db.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? FOR UPDATE",j.tenantId(),j.runId(),s.stepId()));}
    private List<Map<String,Object>> rows(FormalEvaluationJobs.Job j,FormalEvaluationProtocol.Step s){return db.queryForList("SELECT * FROM formal_evaluation_node_dispatch WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",j.tenantId(),j.runId(),s.stepId());}
    private void journal(FormalEvaluationJobs.Job j,String audit,String event,String command,String body,LocalDateTime at){changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'evaluation.node.dispatched',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",j.tenantId(),j.projectId(),audit,j.actorId(),body,command,j.runId(),json.manifestSha256(),j.actorId(),at,j.classification()));changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'evaluation.node.dispatched',?,?)",j.tenantId(),event,command,body,at));}
    private LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    private static String text(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}private static long number(Map<String,Object> r,String k){if(!(r.get(k) instanceof Number))bad();return ((Number)r.get(k)).longValue();}
    private static String sha(String s){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);}private static String id(){return UUID.randomUUID().toString();}
    private static <T>T one(List<T> r){if(r.size()!=1||r.get(0)==null)bad();return r.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
