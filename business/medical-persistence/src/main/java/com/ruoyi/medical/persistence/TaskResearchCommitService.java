package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Atomic local completion only. Physical objects, supervisor closure and declared checks
 * must already exist. This never dispatches, invents outputs or completes later stages. */
public final class TaskResearchCommitService {
 private final com.fasterxml.jackson.databind.json.JsonMapper reader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 private final JdbcTemplate db;private final PermissionSchema json;private final TaskResearchCompletionAuthority completions;private final TaskRuntimeJournal journal;
 public TaskResearchCommitService(DataSource data,PermissionSchema json,TaskResearchCompletionAuthority completions,TaskRuntimeJournal journal){this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.completions=completions;this.journal=journal;}
 public WorkflowProtocol.Receipt commit(FormalTaskNodeAuthority.Verified node,WorkflowProtocol.Dispatch dispatch,WorkflowProtocol.Completion completion)throws Exception {
  node.requireUnchanged();var a=node.controlAdmission();var input=node.currentInput();
  if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.controlWorker())deny();
  same(input.runId(),dispatch.runId());same(input.stepId(),dispatch.stepId());same(input.inputSha256(),dispatch.inputSha256());
  same(dispatch.runId(),completion.runId());same(dispatch.stepId(),completion.stepId());same(dispatch.inputSha256(),completion.inputSha256());same(dispatch.leaseGeneration(),completion.leaseGeneration());
  String tenant=a.context().tenant_id(),project=input.projectId(),run=dispatch.runId(),step=dispatch.stepId();
  same(dispatch,journal.requireExistingStep(node,new ProfessionalRuntimeObservations.Binding(tenant,run,step,dispatch.actionId(),dispatch.leaseGeneration(),dispatch.inputSha256(),dispatch.stepEnvelopeSha256())));
  var attempt=one(db.queryForList("SELECT * FROM step_runtime_attempt WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND lease_generation=? LIMIT 2 FOR UPDATE",tenant,project,run,step,dispatch.leaseGeneration()));
  var original=reader.readTree(text(attempt,"canonical_dispatch"));same(hash(original),text(attempt,"dispatch_sha256"));
  for(var pin:Map.of("command_id",dispatch.commandId(),"action_id",dispatch.actionId(),"input_sha256",dispatch.inputSha256(),"envelope_artifact_id",dispatch.stepEnvelopeArtifactId(),"envelope_sha256",dispatch.stepEnvelopeSha256()).entrySet()){same(pin.getValue(),text(attempt,pin.getKey()));same(pin.getValue(),original.required(pin.getKey()).asText());}
  var existing=db.queryForList("SELECT * FROM run_runtime_receipt WHERE tenant_id=? AND project_id=? AND run_id=? AND command_id=? LIMIT 2 FOR UPDATE",tenant,project,run,dispatch.commandId());
  if(!existing.isEmpty())return readOriginal(a,new Owner(input.projectId(),input.runId(),input.stepId(),input.inputSha256()),dispatch,completion,one(existing));
  var produced=completions.requireProduced(a,run,step,completion.resultArtifactId());produced.requireUnchanged();
  same(dispatch.actionId(),produced.actionId());same(dispatch.leaseGeneration(),produced.leaseGeneration());same(completion.resultSha256(),produced.resultSha256());
  var state="SUCCEEDED".equals(produced.outcome())?WorkflowProtocol.State.SUCCEEDED:WorkflowProtocol.State.WAITING_INPUT;
  same(state,completion.status());if(state==WorkflowProtocol.State.WAITING_INPUT&&produced.waitingItemId()==null)deny();
  String id=derived(dispatch.commandId(),"commit"),actor=a.actorId();
  var receipt=new WorkflowProtocol.Receipt(step,input.inputSha256(),produced.leaseGeneration(),state,id,produced.waitingItemId(),produced.outputs());
  var request=produced.canonicalCommitRequest();TaskResearchCompletionSchema.require("CommitRequest",request);JsonNode value=reader.valueToTree(receipt);TaskResearchCompletionSchema.require("CommitReceipt",value);
  var runRow=one(db.queryForList("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL LIMIT 2 FOR UPDATE",tenant,project,run,actor));
  String classification=text(runRow,"classification");var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
  // All mutable authority is checked before the exact step CAS; the enclosing control
  // transaction then rechecks publication/grants and rolls back the entire write on failure.
  produced.requireUnchanged();node.requireUnchanged();
  changed(db.update("INSERT INTO run_runtime_receipt(tenant_id,project_id,run_id,receipt_id,command_id,command_kind,request_sha256,canonical_request,canonical_receipt,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,'COMMIT',?,?,?,?,'1.0.0',?,?,?,?)",tenant,project,run,id,dispatch.commandId(),hash(request),json.canonical(request),json.canonical(value),hash(value),TaskResearchCompletionSchema.MANIFEST,actor,now,classification));
  var witness=json.object();witness.set("request",request);witness.set("receipt",value);
  changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",tenant,project,derived(id,"audit"),actor,"task.research.committed",json.canonical(witness),dispatch.commandId(),run,TaskResearchCompletionSchema.MANIFEST,actor,now,classification));
  changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES(?,?,?,1,?,?,?)",tenant,derived(id,"event"),id,"task.research.committed",json.canonical(witness),now));
  changed(db.update("UPDATE run_step SET status=?,output_ref=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND revision=? AND lease_generation=? AND lease_owner=? AND lease_expires_at>UTC_TIMESTAMP(6) AND input_sha256=?",state.name(),json.canonical(value.required("outputs")),now,tenant,project,run,step,produced.stepRevision(),produced.leaseGeneration(),attempt.get("worker_service_uri"),input.inputSha256()));
  return receipt;
 }
 /** Factual original receipt recovery. No execution authority or terminal-state reopening. */
 public Optional<WorkflowProtocol.Receipt> loadCommitted(EnterpriseAccess.Admission a,String run,String step)throws Exception {
  WireValues.uuid(run);WireValues.uuid(step);if(!a.recoveryWorker()||!TransactionSynchronizationManager.isActualTransactionActive())deny();
  var proof=a.recoveryProof();proof.requireActive();var identity=proof.identity();same(run,identity.runId());same(a.context().tenant_id(),identity.tenantId());same(a.actorId(),identity.actorId());
  var rows=db.queryForList("SELECT * FROM run_runtime_receipt WHERE tenant_id=? AND project_id=? AND run_id=? AND command_kind='COMMIT' AND canonical_request->>'$.step_id'=? LIMIT 2 FOR UPDATE",identity.tenantId(),identity.projectId(),run,step);
  if(rows.isEmpty()){proof.requireActive();return Optional.empty();}
  var row=one(rows);var request=reader.readTree(text(row,"canonical_request"));TaskResearchCompletionSchema.require("CommitRequest",request);
  long generation=request.required("lease_generation").longValue();var dispatch=journal.readHistoricalStep(a,run,step,generation);
  same(dispatch.commandId(),row.get("command_id"));
  var completion=new WorkflowProtocol.Completion(run,step,request.required("input_sha256").asText(),generation,WorkflowProtocol.State.valueOf(request.required("outcome").asText()),request.required("result").required("artifact_id").asText(),request.required("result").required("sha256").asText());
  var receipt=readOriginal(a,new Owner(identity.projectId(),run,step,dispatch.inputSha256()),dispatch,completion,row);proof.requireActive();return Optional.of(receipt);
 }
 /** Existing receipt facts under a private lifecycle proof, never a completion producer. */
 Optional<WorkflowProtocol.Receipt> loadLifecycleCommitted(TaskRunLifecycleAuthority.Proof p,String step)throws Exception {
  p.require(p.operation());var a=p.admission();var ref=p.reference();WireValues.uuid(step);
  var rows=db.queryForList("SELECT * FROM run_runtime_receipt WHERE tenant_id=? AND project_id=? AND run_id=? AND command_kind='COMMIT' AND canonical_request->>'$.step_id'=? LIMIT 2 FOR UPDATE",ref.tenantId(),ref.projectId(),ref.runId(),step);
  if(rows.isEmpty())return Optional.empty();var row=one(rows);var request=reader.readTree(text(row,"canonical_request"));TaskResearchCompletionSchema.require("CommitRequest",request);
  long generation=Long.parseLong(request.required("lease_generation").asText());var dispatch=journal.readLifecycleStep(p,step,generation);
  var completion=new WorkflowProtocol.Completion(ref.runId(),step,request.required("input_sha256").asText(),generation,WorkflowProtocol.State.valueOf(request.required("outcome").asText()),request.required("result").required("artifact_id").asText(),request.required("result").required("sha256").asText());
  var receipt=readOriginal(a,new Owner(ref.projectId(),ref.runId(),step,dispatch.inputSha256()),dispatch,completion,row);p.require(p.operation());return Optional.of(receipt);
 }
 public WorkflowProtocol.Receipt recover(EnterpriseAccess.Admission a,WorkflowProtocol.Dispatch dispatch,WorkflowProtocol.Completion completion)throws Exception {
  if(!a.recoveryWorker())deny();same(dispatch,journal.requireHistoricalStep(a,dispatch));
  var receipt=loadCommitted(a,dispatch.runId(),dispatch.stepId()).orElseThrow(()->new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
  same(dispatch.runId(),completion.runId());same(dispatch.stepId(),completion.stepId());same(receipt.inputSha256(),completion.inputSha256());same(receipt.leaseGeneration(),completion.leaseGeneration());same(receipt.status(),completion.status());same(receipt.outputs().get(0).artifactId(),completion.resultArtifactId());same(receipt.outputs().get(0).sha256(),completion.resultSha256());return receipt;
 }
 private record Owner(String projectId,String runId,String stepId,String inputSha256){}
 private WorkflowProtocol.Receipt readOriginal(EnterpriseAccess.Admission a,Owner input,WorkflowProtocol.Dispatch dispatch,WorkflowProtocol.Completion completion,Map<String,Object> row)throws Exception {
  var request=reader.readTree(text(row,"canonical_request"));var value=reader.readTree(text(row,"canonical_receipt"));TaskResearchCompletionSchema.require("CommitRequest",request);TaskResearchCompletionSchema.require("CommitReceipt",value);
  same("COMMIT",row.get("command_kind"));same(derived(dispatch.commandId(),"commit"),row.get("receipt_id"));same(dispatch.commandId(),row.get("command_id"));same(a.context().tenant_id(),row.get("tenant_id"));same(input.projectId(),row.get("project_id"));same(input.runId(),row.get("run_id"));same(a.actorId(),row.get("created_by"));same("1.0.0",row.get("schema_version"));same(TaskResearchCompletionSchema.MANIFEST,row.get("schema_manifest_sha256"));same(hash(request),row.get("request_sha256"));same(hash(value),row.get("receipt_sha256"));same(json.canonical(request),row.get("canonical_request"));same(json.canonical(value),row.get("canonical_receipt"));
  same(input.runId(),request.required("run_id").asText());same(input.stepId(),request.required("step_id").asText());same(input.inputSha256(),request.required("input_sha256").asText());same(completion.resultArtifactId(),request.required("result").required("artifact_id").asText());same(completion.resultSha256(),request.required("result").required("sha256").asText());same(Long.toString(dispatch.leaseGeneration()),request.required("lease_generation").asText());same(completion.status().name(),request.required("outcome").asText());
  var receipt=reader.treeToValue(value,WorkflowProtocol.Receipt.class);same(dispatch.stepId(),receipt.stepId());same(dispatch.inputSha256(),receipt.inputSha256());same(dispatch.leaseGeneration(),receipt.leaseGeneration());same(completion.status(),receipt.status());same(row.get("receipt_id"),receipt.receiptId());
  var step=one(db.queryForList("SELECT status,output_ref,lease_generation FROM run_step WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),input.projectId(),input.runId(),input.stepId()));same(receipt.status().name(),step.get("status"));same(value.required("outputs"),reader.readTree(text(step,"output_ref")));same(Long.toString(receipt.leaseGeneration()),String.valueOf(step.get("lease_generation")));
  verifyJournal(a,input,dispatch,row,request,value);return receipt;
 }
 private void verifyJournal(EnterpriseAccess.Admission a,Owner input,WorkflowProtocol.Dispatch dispatch,Map<String,Object> row,JsonNode request,JsonNode receipt)throws Exception {
  String tenant=a.context().tenant_id(),id=text(row,"receipt_id");var witness=json.object();witness.set("request",request);witness.set("receipt",receipt);
  same(row.get("classification"),one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=?",(r,n)->r.getString(1),tenant,input.projectId(),input.runId(),a.actorId())));
  var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"audit")));
  for(var p:Map.ofEntries(Map.entry("project_id",input.projectId()),Map.entry("subject_id",a.actorId()),Map.entry("created_by",a.actorId()),Map.entry("action","task.research.committed"),Map.entry("result","SUCCESS"),Map.entry("request_id",dispatch.commandId()),Map.entry("trace_id",input.runId()),Map.entry("schema_version","1.0.0"),Map.entry("schema_manifest_sha256",TaskResearchCompletionSchema.MANIFEST)).entrySet())same(p.getValue(),audit.get(p.getKey()));
  same(row.get("classification"),audit.get("classification"));same(row.get("created_at"),audit.get("created_at"));if(audit.get("reason_code")!=null)deny();same(witness,reader.readTree(text(audit,"resource_ref")));
  var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",tenant,derived(id,"event")));same(id,event.get("aggregate_id"));same("1",String.valueOf(event.get("aggregate_revision")));same("task.research.committed",event.get("event_type"));same(row.get("created_at"),event.get("occurred_at"));same(witness,reader.readTree(text(event,"payload")));
 }
 private String hash(JsonNode n)throws Exception{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(n));}
 private static String derived(String id,String part){return UUID.nameUUIDFromBytes((id+"|"+part).getBytes(StandardCharsets.UTF_8)).toString();}
 private static String text(Map<String,Object> row,String key){var v=row.get(key);if(v==null)deny();return String.valueOf(v);}private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
