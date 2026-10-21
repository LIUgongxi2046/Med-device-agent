package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.workflow.FormalEvaluationProtocol;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** Terminal control evidence retains every unexecuted node; it cannot convert partial coverage to PASS. */
public final class FormalEvaluationCellCompletion {
 private final FormalEvaluationInvocationService invocations;private final JdbcTemplate db;private final PermissionSchema json;private final FormalEvaluationJobs jobs;private final FormalEvaluationLaunchLedger launches;private final FormalEvaluationNodeRuntime runtime;private final FormalEvaluationCompletionService completions;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationCellCompletion(DataSource data,PermissionSchema json,FormalEvaluationJobs jobs,FormalEvaluationLaunchLedger launches,FormalEvaluationNodeRuntime runtime,FormalEvaluationCompletionService completions,FormalEvaluationInvocationService invocations){this.invocations=invocations;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.jobs=jobs;this.launches=launches;this.runtime=runtime;this.completions=completions;}
 public FormalEvaluationProtocol.CellReceipt complete(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference reference,List<FormalEvaluationProtocol.NodeReceipt> supplied)throws Exception {
  var job=jobs.byRun(a,reference.runId());launches.requireAccepted(a,job,reference);if(!db.queryForList("SELECT receipt_id FROM formal_evaluation_cancel_intent WHERE tenant_id=? AND run_id=? LIMIT 1",job.tenantId(),job.runId()).isEmpty())bad();if(supplied==null||supplied.isEmpty()||supplied.size()>reference.steps().size())bad();
  String state="SUCCEEDED";for(int i=0;i<supplied.size();i++){var step=reference.steps().get(i);var receipt=runtime.originalNodeReceipt(a,job.runId(),step.stepId());same(receipt,supplied.get(i));receipt.require(reference,step);if("SUCCEEDED".equals(receipt.state()))completions.record(a,runtime.requireOriginalCompleted(a,job.runId(),step.stepId()));else{if(i!=supplied.size()-1||!Set.of("WAITING_INPUT","FAILED").contains(receipt.state()))bad();state=receipt.state();}}
  if("SUCCEEDED".equals(state)&&supplied.size()!=reference.steps().size())bad();var unexecuted=new ArrayList<FormalEvaluationProtocol.Step>();for(int i=supplied.size();i<reference.steps().size();i++){var step=reference.steps().get(i);same("READY",one(db.query("SELECT status FROM run_step WHERE tenant_id=? AND run_id=? AND step_id=? AND lease_generation=0 LIMIT 2",(r,n)->r.getString(1),job.tenantId(),job.runId(),step.stepId())));unexecuted.add(step);}
  invocations.requireQuiescentOriginal(a,job.runId());one(db.queryForList("SELECT run_id FROM system_job_run WHERE tenant_id=? AND run_id=? FOR UPDATE",job.tenantId(),job.runId()));var old=db.queryForList("SELECT * FROM formal_evaluation_cell_outcome WHERE tenant_id=? AND run_id=? LIMIT 2",job.tenantId(),job.runId());
  if(!old.isEmpty())return read(job,one(old),supplied,unexecuted,state);
  String id=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var evidence=evidence(job,id,state,supplied,unexecuted);String body=json.canonical(evidence),hash=hash(body);var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class);
  changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'evaluation.cell.completed',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),audit,job.actorId(),body,id,job.runId(),json.manifestSha256(),job.actorId(),now,job.classification()));changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'evaluation.cell.completed',?,?)",job.tenantId(),event,id,body,now));
  changed(db.update("INSERT INTO formal_evaluation_cell_outcome (tenant_id,project_id,run_id,receipt_id,state,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),id,state,body,hash,audit,event,json.manifestSha256(),job.actorId(),now,job.classification()));changed(db.update("UPDATE system_job_run SET status=?,ended_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND status='RUNNING' AND ended_at IS NULL",state,job.tenantId(),job.runId()));return read(job,one(db.queryForList("SELECT * FROM formal_evaluation_cell_outcome WHERE tenant_id=? AND run_id=?",job.tenantId(),job.runId())),supplied,unexecuted,state);
 }
 public FormalEvaluationProtocol.CellReceipt cancelUnknown(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference ref)throws Exception {
  var proof=runtime.requireCancellation(a,ref);return new FormalEvaluationProtocol.CellReceipt("1.0.0",ref.runId(),ref.admissionSha256(),"UNKNOWN",proof.receiptId(),proof.sha256());
 }
 public FormalEvaluationProtocol.CellReceipt cancel(EnterpriseAccess.Admission a,FormalEvaluationProtocol.Reference ref)throws Exception {
  var proof=runtime.requireCancellation(a,ref);var job=jobs.byRun(a,ref.runId());
  one(db.queryForList("SELECT run_id FROM system_job_run WHERE tenant_id=? AND run_id=? FOR UPDATE",job.tenantId(),job.runId()));
  var terminations=runtime.cancellationTerminations(proof);var accounting=invocations.requireQuiescentOriginal(a,ref.runId());
  var old=db.queryForList("SELECT * FROM formal_evaluation_cell_outcome WHERE tenant_id=? AND run_id=? LIMIT 2",job.tenantId(),job.runId());
  String id=old.isEmpty()?UUID.randomUUID().toString():(String)one(old).get("receipt_id");
  var evidence=json.object().put("schema_version","1.0.0").put("receipt_id",id).put("run_id",job.runId()).put("admission_sha256",job.admissionSha256()).put("state","CANCELLED").put("cancel_receipt_id",proof.receiptId()).put("cancel_sha256",proof.sha256());
  evidence.set("session_terminations",terminations);evidence.set("invocation_dispositions",accounting);
  var completed=evidence.putArray("node_receipts");var unfinished=evidence.putArray("unexecuted_obligations");
  for(var step:ref.steps()){
   var rows=db.queryForList("SELECT step_id FROM formal_evaluation_node_outcome WHERE tenant_id=? AND run_id=? AND step_id=? UNION ALL SELECT step_id FROM formal_evaluation_node_waiting WHERE tenant_id=? AND run_id=? AND step_id=? UNION ALL SELECT step_id FROM formal_evaluation_deterministic_outcome WHERE tenant_id=? AND run_id=? AND step_id=?",job.tenantId(),job.runId(),step.stepId(),job.tenantId(),job.runId(),step.stepId(),job.tenantId(),job.runId(),step.stepId());
   if(rows.isEmpty())unfinished.add(json.tree(step));else{if(rows.size()!=1)bad();completed.add(json.tree(runtime.originalNodeReceipt(a,job.runId(),step.stepId())));}
  }
  String body=json.canonical(evidence),digest=hash(body);
  if(old.isEmpty()){
   String audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class);
   changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'evaluation.cell.cancelled',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),audit,job.actorId(),body,id,job.runId(),json.manifestSha256(),job.actorId(),now,job.classification()));
   changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'evaluation.cell.cancelled',?,?)",job.tenantId(),event,id,body,now));
   changed(db.update("INSERT INTO formal_evaluation_cell_outcome (tenant_id,project_id,run_id,receipt_id,state,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'CANCELLED',?,?,?,?,'1.0.0',?,?,?,?)",job.tenantId(),job.projectId(),job.runId(),id,body,digest,audit,event,json.manifestSha256(),job.actorId(),now,job.classification()));
   // Completed obligations keep their original status. Only independently quiesced unfinished work is cancelled.
   db.update("UPDATE run_step SET status='CANCELLED',lease_expires_at=NULL,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND status IN ('READY','RUNNING')",job.tenantId(),job.runId());
   changed(db.update("UPDATE system_job_run SET status='CANCELLED',ended_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND status='RUNNING' AND ended_at IS NULL",job.tenantId(),job.runId()));
  }
  var row=one(db.queryForList("SELECT * FROM formal_evaluation_cell_outcome WHERE tenant_id=? AND run_id=? LIMIT 2",job.tenantId(),job.runId()));
  for(var entry:Map.of("project_id",job.projectId(),"created_by",job.actorId(),"classification",job.classification(),"state","CANCELLED","schema_version","1.0.0","schema_manifest_sha256",json.manifestSha256(),"canonical_receipt",body,"receipt_sha256",digest).entrySet())same(entry.getValue(),row.get(entry.getKey()));
  same(body,one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action='evaluation.cell.cancelled' AND result='SUCCESS' AND request_id=? AND trace_id=? LIMIT 2",(r,n)->r.getString(1),job.tenantId(),job.projectId(),row.get("audit_id"),job.actorId(),id,job.runId())));
  same(body,one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='evaluation.cell.cancelled' AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),job.tenantId(),row.get("event_id"),id)));
  same("CANCELLED",one(db.query("SELECT status FROM system_job_run WHERE tenant_id=? AND run_id=? AND ended_at IS NOT NULL LIMIT 2",(r,n)->r.getString(1),job.tenantId(),job.runId())));
  return new FormalEvaluationProtocol.CellReceipt("1.0.0",job.runId(),job.admissionSha256(),"CANCELLED",id,digest);
 }
 private JsonNode evidence(FormalEvaluationJobs.Job j,String id,String state,List<FormalEvaluationProtocol.NodeReceipt> nodes,List<FormalEvaluationProtocol.Step> missing){var e=json.object().put("schema_version","1.0.0").put("receipt_id",id).put("run_id",j.runId()).put("admission_sha256",j.admissionSha256()).put("state",state);e.set("node_receipts",json.tree(nodes));e.set("unexecuted_obligations",json.tree(missing));return e;}
 private FormalEvaluationProtocol.CellReceipt read(FormalEvaluationJobs.Job j,Map<String,Object> row,List<FormalEvaluationProtocol.NodeReceipt> nodes,List<FormalEvaluationProtocol.Step> missing,String state)throws Exception{
  for(var entry:Map.of("tenant_id",j.tenantId(),"project_id",j.projectId(),"run_id",j.runId(),"created_by",j.actorId(),"classification",j.classification(),"state",state,"schema_version","1.0.0","schema_manifest_sha256",json.manifestSha256()).entrySet())same(entry.getValue(),row.get(entry.getKey()));
  String id=(String)row.get("receipt_id");var expected=evidence(j,id,state,nodes,missing);
  same(json.canonical(expected),row.get("canonical_receipt"));same(hash(json.canonical(expected)),row.get("receipt_sha256"));
  String auditJson=one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND project_id=? AND action='evaluation.cell.completed' AND result='SUCCESS' AND request_id=? AND trace_id=? LIMIT 2",(r,n)->r.getString(1),j.tenantId(),row.get("audit_id"),j.actorId(),j.projectId(),id,j.runId()));
  same(expected,parser.readTree(auditJson));
  String eventJson=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='evaluation.cell.completed' AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),j.tenantId(),row.get("event_id"),id));
  same(expected,parser.readTree(eventJson));
  same(state,one(db.query("SELECT status FROM system_job_run WHERE tenant_id=? AND run_id=? AND ended_at IS NOT NULL LIMIT 2",(r,n)->r.getString(1),j.tenantId(),j.runId())));
  return new FormalEvaluationProtocol.CellReceipt("1.0.0",j.runId(),j.admissionSha256(),state,id,(String)row.get("receipt_sha256"));
 }
 private static String hash(String s){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void changed(int n){if(n!=1)bad();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
