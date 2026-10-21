package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.PlanDraftProtocol;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
/** Commits only an original AT10 save after its signed token transaction has ended.
 * No model-call ID, approval, confirmed plan or successful TASK result is manufactured. */
public final class TaskPlanDraftCompletionLedger {
 private final JdbcTemplate db;private final TaskPlanDraftSchema schema;private final TaskPlanDraftControlAccess controls;
 public TaskPlanDraftCompletionLedger(DataSource data,TaskPlanDraftSchema schema,TaskPlanDraftControlAccess controls){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.controls=controls;}
 public PlanDraftProtocol.Receipt complete(HttpServletRequest http,String tenant,String run,TaskPlanDraftSaveReceipt.Saved saved)throws Exception {
  if(saved==null)deny();return completeOriginal(http,tenant,run,saved.receipt());
 }
 public PlanDraftProtocol.Receipt recover(HttpServletRequest http,String tenant,String run)throws Exception{return completeOriginal(http,tenant,run,null);}
 private PlanDraftProtocol.Receipt completeOriginal(HttpServletRequest http,String tenant,String run,JsonNode expected)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())deny();String command=one(db.query("SELECT operation_id FROM task_plan_draft_job WHERE tenant_id=? AND run_id=? LIMIT 2",(r,n)->r.getString(1),tenant,run));
  return controls.withReads(http,tenant,run,command,reads->reads.execute(a->ThrowingSupplier.of(()->{
   var j=reads.job();var receipt=original(j);if(expected!=null&&!receipt.equals(expected))deny();var prior=db.queryForList("SELECT * FROM task_plan_draft_completion WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",tenant,run);
   if(!prior.isEmpty())return verified(j,receipt,one(prior));
   var row=one(db.queryForList("SELECT revision FROM system_job_run WHERE tenant_id=? AND run_id=? AND job_type='PLAN_DRAFT' AND status='RUNNING' AND ended_at IS NULL LIMIT 2 FOR UPDATE",tenant,run));var before=new BigInteger(row.get("revision").toString());
   String id=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
   var evidence=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("receipt_id",id).put("run_id",run).put("step_id",j.step()).put("principal_id",j.principal()).put("input_sha256",j.inputHash()).put("tool_receipt_sha256",schema.hash(receipt)).put("candidate_artifact_id",text(receipt,"artifact_id")).put("waiting_item_id",text(receipt,"waiting_item_id")).put("before_revision",before.toString()).put("after_revision",before.add(BigInteger.ONE).toString()).put("state","WAITING_INPUT");
   changed(db.update("UPDATE run_step SET status='WAITING_INPUT',lease_owner=NULL,lease_expires_at=NULL,revision=revision+1,updated_at=? WHERE tenant_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND lease_generation=? AND input_sha256=?",now,tenant,run,j.step(),new BigInteger(text(receipt,"lease_generation")),j.inputHash()));
   changed(db.update("UPDATE system_job_run SET status='WAITING_INPUT',revision=revision+1,updated_at=? WHERE tenant_id=? AND run_id=? AND revision=? AND status='RUNNING' AND ended_at IS NULL",now,tenant,run,before));
   String raw=schema.canonical(evidence),hash=schema.hash(evidence);
   changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,'task.plan_draft.waiting.committed',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",tenant,j.project(),audit,j.actor(),raw,id,run,schema.manifest(),j.actor(),now,j.classification()));
   changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,?, 'task.plan_draft.waiting.committed',?,?)",tenant,event,run,before.add(BigInteger.ONE),raw,now));
   changed(db.update("INSERT INTO task_plan_draft_completion(tenant_id,run_id,receipt_id,receipt_json,receipt_sha256,tool_receipt_sha256,audit_id,event_id,created_at,project_id,schema_version,schema_manifest_sha256,created_by,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",tenant,run,id,raw,hash,schema.hash(receipt),audit,event,now,j.project(),"1.0.0",schema.manifest(),j.actor(),j.classification()));
   var transition=new CompletionTransition(j,receipt,evidence,before);a.planDraftProof().completedPlanDraft(transition);return verified(j,receipt,one(db.queryForList("SELECT * FROM task_plan_draft_completion WHERE tenant_id=? AND run_id=?",tenant,run)));
  }).get()));
 }
 private JsonNode original(TaskPlanDraftJobs.Job j)throws Exception {
  var row=one(db.queryForList("SELECT * FROM task_plan_draft_tool_receipt WHERE tenant_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",j.tenant(),j.run(),j.actor()));var receipt=schema.validate("ToolReceipt",schema.read((String)row.get("receipt_json")));same(schema.hash(receipt),row.get("receipt_sha256"));
  for(var p:Map.of("tenant_id",j.tenant(),"project_id",j.project(),"task_id",j.task(),"task_revision",j.taskRevision(),"run_id",j.run(),"step_id",j.step(),"actor_id",j.actor(),"input_sha256",j.inputHash(),"status","WAITING_INPUT").entrySet())same(p.getValue(),text(receipt,p.getKey()));
  same(receipt,schema.read(one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND action='task.plan_draft.save.completed' AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),j.tenant(),row.get("audit_id"),j.actor(),text(receipt,"call_id"),j.run()))));same(receipt,schema.read(one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='task.plan_draft.save.completed' LIMIT 2",(r,n)->r.getString(1),j.tenant(),row.get("event_id"),j.run()))));
  if(one(db.query("SELECT COUNT(*) FROM waiting_item WHERE tenant_id=? AND project_id=? AND waiting_item_id=? AND run_id=? AND step_id=? AND action_id=? AND target_revision=? AND reason_code='CONFIRM_PLAN'",(r,n)->r.getInt(1),j.tenant(),j.project(),text(receipt,"waiting_item_id"),j.run(),j.step(),text(receipt,"action_id"),text(receipt,"artifact_id")))!=1)deny();
  if(one(db.query("SELECT COUNT(*) FROM task_plan_draft_candidate WHERE tenant_id=? AND run_id=? AND artifact_id=? AND receipt_sha256=?",(r,n)->r.getInt(1),j.tenant(),j.run(),text(receipt,"artifact_id"),text(receipt,"candidate_receipt_sha256")))!=1)deny();
  if(one(db.query("SELECT COUNT(*) FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? AND session_id=? AND lease_generation=? AND admission_state='CLOSED'",(r,n)->r.getInt(1),j.tenant(),j.run(),j.step(),text(receipt,"session_id"),new BigInteger(text(receipt,"lease_generation"))))!=1)deny();
  return receipt;
 }
 private PlanDraftProtocol.Receipt verified(TaskPlanDraftJobs.Job j,JsonNode tool,Map<String,Object> row)throws Exception {
  same(j.tenant(),row.get("tenant_id"));same(j.project(),row.get("project_id"));same(j.run(),row.get("run_id"));same(j.actor(),row.get("created_by"));same(j.classification(),row.get("classification"));same("1.0.0",row.get("schema_version"));same(schema.manifest(),row.get("schema_manifest_sha256"));
  var e=schema.read((String)row.get("receipt_json"));same(schema.hash(e),row.get("receipt_sha256"));same(schema.hash(tool),row.get("tool_receipt_sha256"));same(schema.hash(tool),text(e,"tool_receipt_sha256"));same(j.inputHash(),text(e,"input_sha256"));same(j.principal(),text(e,"principal_id"));same(j.run(),text(e,"run_id"));same(j.step(),text(e,"step_id"));same(text(tool,"artifact_id"),text(e,"candidate_artifact_id"));same(text(tool,"waiting_item_id"),text(e,"waiting_item_id"));
  same(e,schema.read(one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND action='task.plan_draft.waiting.committed' AND request_id=? AND trace_id=? AND result='SUCCESS'",(r,n)->r.getString(1),j.tenant(),row.get("audit_id"),text(e,"receipt_id"),j.run()))));same(e,schema.read(one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=? AND event_type='task.plan_draft.waiting.committed'",(r,n)->r.getString(1),j.tenant(),row.get("event_id"),j.run(),new BigInteger(text(e,"after_revision"))))));
  return new PlanDraftProtocol.Receipt("1.0.0",j.run(),j.step(),j.inputHash(),text(e,"receipt_id"),(String)row.get("receipt_sha256"),"WAITING_INPUT",text(e,"candidate_artifact_id"),text(e,"waiting_item_id"));
 }
 public final class CompletionTransition {
  private final TaskPlanDraftJobs.Job job;private final JsonNode tool,evidence;private final BigInteger before;private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();private final Map<Object,Object> resources=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
  private CompletionTransition(TaskPlanDraftJobs.Job job,JsonNode tool,JsonNode evidence,BigInteger before){this.job=job;this.tool=tool.deepCopy();this.evidence=evidence.deepCopy();this.before=before;if(resources.isEmpty())deny();}
  public BigInteger beforeRevision(){return before;}public BigInteger afterRevision(){return before.add(BigInteger.ONE);}
  public void requireFor(String tenant,String actor,String run,String principal,BigInteger expectedBefore,BigInteger expectedAfter)throws Exception {
   if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=resources.size()||resources.entrySet().stream().anyMatch(x->current.get(x.getKey())!=x.getValue()))deny();
   if(!tenant.equals(job.tenant())||!actor.equals(job.actor())||!run.equals(job.run())||!principal.equals(job.principal())||!before.equals(expectedBefore)||!afterRevision().equals(expectedAfter))deny();same(tool,original(job));verified(job,tool,one(db.queryForList("SELECT * FROM task_plan_draft_completion WHERE tenant_id=? AND run_id=?",tenant,run)));
   if(one(db.query("SELECT COUNT(*) FROM system_job_run j JOIN run_step s ON s.tenant_id=j.tenant_id AND s.run_id=j.run_id WHERE j.tenant_id=? AND j.run_id=? AND j.status='WAITING_INPUT' AND j.revision=? AND s.step_id=? AND s.status='WAITING_INPUT' AND s.input_sha256=? AND s.lease_owner IS NULL AND s.lease_expires_at IS NULL",(r,n)->r.getInt(1),tenant,run,afterRevision(),job.step(),job.inputHash()))!=1)deny();
  }
 }
 private static String text(JsonNode n,String k){if(!n.required(k).isTextual())deny();return n.required(k).textValue();}private static void same(Object a,Object b){if(!Objects.equals(a,b))deny();}private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)deny();return rows.get(0);}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
