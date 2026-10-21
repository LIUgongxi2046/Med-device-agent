package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Immutable completion producer and original-evidence consumer shared by scoring and publication.
 * A worker FINISH, raw Record or supplied boolean cannot call the writer: only the runtime's private
 * independently closed professional proof or private deterministic N03/N06 registered proof can do so.
 * Deterministic completion never creates a professional session/closure. Completeness is not a PASS score. */
public final class FormalEvaluationCompletionService implements FormalEvaluationProfessionalCompletions {
 private final JdbcTemplate db;private final PermissionSchema json;private final FormalEvaluationNodeRuntime runtime;
 private final JsonMapper decoder=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationCompletionService(DataSource data,PermissionSchema json,FormalEvaluationNodeRuntime runtime){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.runtime=runtime;}
 public JsonNode record(EnterpriseAccess.Admission a,FormalEvaluationNodeRuntime.Completed completed)throws Exception{
  FormalEvaluationExecutionAdmission.current(a);Objects.requireNonNull(completed).requireCurrent();var r=completed.record();
  var original=runtime.requireOriginalCompleted(a,r.runId(),r.stepId());original.requireCurrent();same(identity(completed),identity(original));
  if(completed.deterministic())return deterministicReceipt(a,r,original);
  one(db.queryForList("SELECT step_id FROM formal_evaluation_node WHERE tenant_id=? AND run_id=? AND step_id=? FOR UPDATE",a.context().tenant_id(),r.runId(),r.stepId()));
  var row=result(a,r);same(a.actorId(),text(row,"created_by"));var prior=rows(a,r);if(!prior.isEmpty())return verify(a,r,completed,one(prior));
  String completion=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
  var receipt=identity(completed);receipt.put("schema_version","1.0.0").put("completion_id",completion).put("tenant_id",a.context().tenant_id()).put("project_id",text(row,"project_id")).put("record_sha256",text(row,"record_sha256"));
  String canonical=json.canonical(receipt),digest=hash(canonical);var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class);
  changed(db.update("INSERT INTO audit_event (tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,'evaluation.professional_completed',?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),row.get("project_id"),audit,a.actorId(),canonical,completion,completion,json.manifestSha256(),a.actorId(),now,row.get("classification")));
  changed(db.update("INSERT INTO outbox_event (tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at) VALUES (?,?,?,1,'evaluation.professional_completed',?,?)",a.context().tenant_id(),event,completion,canonical,now));
  var output=completed.output();
  changed(db.update("INSERT INTO formal_evaluation_professional_completion (tenant_id,project_id,completion_id,run_id,step_id,lease_generation,record_sha256,session_id,closed_receipt_id,closed_sha256,output_version,output_sha256,canonical_receipt,receipt_sha256,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),row.get("project_id"),completion,r.runId(),r.stepId(),r.leaseGeneration(),row.get("record_sha256"),completed.sessionId(),completed.closedReceiptId(),completed.closedSha256(),output==null?null:output.version_id(),output==null?null:output.content_sha256(),canonical,digest,audit,event,json.manifestSha256(),a.actorId(),now,row.get("classification")));
  completed.requireCurrent();return verify(a,r,completed,one(rows(a,r)));
 }
 @Override public void requireCurrent(EnterpriseAccess.Admission a,List<FormalEvaluationExecutionProtocol.Record> records,Map<String,FormalEvaluationOriginalObjects.Verified> originals)throws Exception{
  FormalEvaluationExecutionAdmission.current(a);if(records.isEmpty())bad();
  var artifacts=FormalEvaluationCoverage.artifacts(records);if(artifacts.size()!=originals.size())bad();
  for(var ref:artifacts){var original=originals.get(ref.artifactId());if(original==null||!ref.equals(original.reference()))bad();original.requireCurrent();}
  var seen=new HashSet<String>();for(var r:records){if(!seen.add(r.runId()+":"+r.stepId()))bad();var completed=runtime.requireOriginalCompleted(a,r.runId(),r.stepId());completed.requireCurrent();same(r,completed.record());if(completed.deterministic())deterministicReceipt(a,r,completed);else verify(a,r,completed,one(rows(a,r)));}
 }
 @Override public void requireProfessionalOutput(EnterpriseAccess.Admission a,List<FormalEvaluationExecutionProtocol.Record> records,Map<String,FormalEvaluationOriginalObjects.Verified> originals,EvaluationProtocol.Cell cell,PermissionProtocol.Basis output)throws Exception{
  requireCurrent(a,records,originals);if(output==null)bad();boolean matched=false;
  for(var r:records){var c=r.node().cell();if(!c.sampleId().equals(cell.sample_id())||c.repeatIndex()!=cell.repeat_index()||!c.candidate().version_id().equals(cell.agent_version_id()))continue;
   var completion=runtime.requireOriginalCompleted(a,r.runId(),r.stepId());completion.requireCurrent();
   if(output.equals(completion.output())){if("N00".equals(r.node().nodeKind())||r.actualArtifacts().stream().noneMatch(x->x.artifactId().equals(output.version_id())&&x.sha256().equals(output.content_sha256())))bad();matched=true;}
  }if(!matched)bad();
 }
 private JsonNode verify(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Record r,FormalEvaluationNodeRuntime.Completed proof,Map<String,Object> row)throws Exception{
  var result=result(a,r);same(a.context().tenant_id(),text(row,"tenant_id"));for(var field:List.of("project_id","created_by","classification","record_sha256","session_id"))same(result.get(field),row.get(field));same(r.runId(),text(row,"run_id"));same(r.stepId(),text(row,"step_id"));same(r.leaseGeneration(),((Number)row.get("lease_generation")).longValue());same(proof.closedReceiptId(),text(row,"closed_receipt_id"));same(proof.closedSha256(),text(row,"closed_sha256"));same("1.0.0",text(row,"schema_version"));same(json.manifestSha256(),text(row,"schema_manifest_sha256"));if(row.get("created_at")==null)bad();
  var output=proof.output();same(output==null?null:output.version_id(),row.get("output_version"));same(output==null?null:output.content_sha256(),row.get("output_sha256"));
  String canonical=text(row,"canonical_receipt");same(hash(canonical),text(row,"receipt_sha256"));var expected=identity(proof);expected.put("schema_version","1.0.0").put("completion_id",text(row,"completion_id")).put("tenant_id",a.context().tenant_id()).put("project_id",text(row,"project_id")).put("record_sha256",text(result,"record_sha256"));same(json.canonical(expected),canonical);
  var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND project_id=? AND subject_id=? AND created_by=? AND action='evaluation.professional_completed' AND result='SUCCESS' AND request_id=? AND trace_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND created_at=? AND classification=? LIMIT 2",a.context().tenant_id(),row.get("audit_id"),row.get("project_id"),row.get("created_by"),row.get("created_by"),row.get("completion_id"),row.get("completion_id"),json.manifestSha256(),row.get("created_at"),row.get("classification")));
  var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type='evaluation.professional_completed' AND occurred_at=? LIMIT 2",a.context().tenant_id(),row.get("event_id"),row.get("completion_id"),row.get("created_at")));
  same(expected,decoder.readTree(text(audit,"resource_ref")));same(expected,decoder.readTree(text(event,"payload")));proof.requireCurrent();return expected;
 }
 /** The outcome already has its own original receipt/audit/outbox chain. Never copy it into the
  * professional completion ledger or substitute a synthetic supervisor-closed receipt. */
 private JsonNode deterministicReceipt(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Record r,FormalEvaluationNodeRuntime.Completed proof)throws Exception{
  proof.requireCurrent();if(!proof.deterministic()||!FormalEvaluationExecutionProtocol.deterministic(r.node())||r.sessionExport()!=null||proof.sessionId()!=null||proof.closedReceiptId()!=null||proof.closedSha256()!=null)bad();
  same(r,proof.record());var row=result(a,r);same(a.context().tenant_id(),text(row,"tenant_id"));same(r.runId(),text(row,"run_id"));same(r.stepId(),text(row,"step_id"));same(r.leaseGeneration(),((Number)row.get("lease_generation")).longValue());
  same("SUCCEEDED",text(row,"state"));same(proof.deterministicReceiptId(),text(row,"receipt_id"));same(proof.deterministicReceiptSha256(),text(row,"receipt_sha256"));same(r.stepReceiptSha256(),proof.deterministicReceiptSha256());
  String canonical=text(row,"canonical_receipt");same(hash(canonical),proof.deterministicReceiptSha256());
  if(proof.output()==null||r.actualArtifacts().size()!=1)bad();var output=r.actualArtifacts().get(0);same(output.artifactId(),proof.output().version_id());same(output.sha256(),proof.output().content_sha256());
  if(!rows(a,r).isEmpty()||!db.queryForList("SELECT step_id FROM formal_evaluation_node_result WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1",a.context().tenant_id(),r.runId(),r.stepId()).isEmpty()||!db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 1",a.context().tenant_id(),r.runId(),r.stepId()).isEmpty())bad();
  proof.requireCurrent();return decoder.readTree(canonical);
 }
 private Map<String,Object> result(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Record r)throws Exception{var row=one(db.queryForList("SELECT * FROM "+(FormalEvaluationExecutionProtocol.deterministic(r.node())?"formal_evaluation_deterministic_outcome":"formal_evaluation_node_result")+" WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",a.context().tenant_id(),r.runId(),r.stepId()));same(json.tree(r),decoder.readTree(text(row,"canonical_record")));same(hash(text(row,"canonical_record")),text(row,"record_sha256"));return row;}
 private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,FormalEvaluationExecutionProtocol.Record r){return db.queryForList("SELECT * FROM formal_evaluation_professional_completion WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",a.context().tenant_id(),r.runId(),r.stepId());}
 private com.fasterxml.jackson.databind.node.ObjectNode identity(FormalEvaluationNodeRuntime.Completed c){var n=json.object();n.set("record",json.tree(c.record()));if(c.deterministic())n.put("execution_mode","DETERMINISTIC").put("deterministic_receipt_id",c.deterministicReceiptId()).put("deterministic_receipt_sha256",c.deterministicReceiptSha256());else n.put("session_id",c.sessionId()).put("closed_receipt_id",c.closedReceiptId()).put("closed_sha256",c.closedSha256());n.set("output",json.tree(c.output()));return n;}
 private static String hash(String s){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(s);}private static String text(Map<String,Object> r,String k){if(!(r.get(k) instanceof String))bad();return (String)r.get(k);}private static <T>T one(List<T> r){if(r.isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);if(r.size()!=1)bad();return r.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void changed(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
