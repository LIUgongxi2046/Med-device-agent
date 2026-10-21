package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only original facts, deliberately not an implementation of the current-use completion
 * authority. Original authenticated receipts remain facts after a compiler or engine upgrade. */
final class FormalEvaluationFactualCompletions {
 private final JdbcTemplate db;private final PermissionSchema json;
 private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 FormalEvaluationFactualCompletions(DataSource data,PermissionSchema json){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;}
 void require(EnterpriseAccess.Admission a,List<FormalEvaluationExecutionProtocol.Record> records,Map<String,FormalEvaluationOriginalObjects.Verified> originals)throws Exception {
  FormalEvaluationExecutionAdmission.current(a);
  for(var record:records){
   String tenant=a.context().tenant_id(),run=record.runId(),step=record.stepId();boolean deterministic=FormalEvaluationExecutionProtocol.deterministic(record.node());
   var result=one(db.queryForList("SELECT * FROM "+(deterministic?"formal_evaluation_deterministic_outcome":"formal_evaluation_node_result")+" WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",tenant,run,step));
   same(hash(json.tree(record)),result.get("record_sha256"));same(json.canonical(json.tree(record)),result.get("canonical_record"));
   var outcome=deterministic?result:one(db.queryForList("SELECT * FROM formal_evaluation_node_outcome WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",tenant,run,step));
   var evidence=parser.readTree(text(outcome,"canonical_receipt"));same(hash(evidence),outcome.get("receipt_sha256"));same(record.stepReceiptSha256(),outcome.get("receipt_sha256"));same("SUCCEEDED",outcome.get("state"));same("SUCCEEDED",evidence.required("state").asText());
   for(String key:List.of("run_id","step_id","receipt_id","state"))same(outcome.get(key),evidence.required(key).asText());
   journal(tenant,outcome,deterministic?"evaluation.deterministic.completed":"evaluation.node.completed",text(outcome,"receipt_id"),run,evidence);
   var envelope=payload(originals,record.stepEnvelope());var terminal=payload(originals,record.stepResult());if(record.actualArtifacts().size()!=1)bad();var output=record.actualArtifacts().get(0);var business=payload(originals,output);
   same(json.tree(record.stepEnvelope()),evidence.required("step_envelope"));same(json.tree(record.stepResult()),evidence.required("step_result"));same(json.tree(output),evidence.required("business_output"));
   for(var invocation:record.invocations()){
    payload(originals,invocation.modelResult());payload(originals,invocation.providerCapture());
    var accounting=one(db.queryForList("SELECT * FROM formal_invocation_accounting WHERE tenant_id=? AND invocation_id=? LIMIT 2",tenant,invocation.invocationId()));var receipt=parser.readTree(text(accounting,"receipt_json"));same(hash(receipt),accounting.get("receipt_sha256"));same(invocation.settlementReceiptSha256(),accounting.get("receipt_sha256"));same(json.canonical(receipt),accounting.get("receipt_json"));
    same("SYSTEM_JOB",receipt.required("execution_kind").asText());same("EVALUATION_VERIFICATION",receipt.required("job_type").asText());same("SUCCEEDED",receipt.required("status").asText());same("SETTLED",receipt.required("settlement_status").asText());if(!receipt.required("provider_terminal").isBoolean()||!receipt.required("provider_terminal").booleanValue())bad();same(invocation.requestSha256(),receipt.required("request_sha256").asText());same(invocation.invocationId(),receipt.required("invocation_id").asText());same(invocation.modelResult().artifactId(),receipt.required("result_artifact").required("artifact_id").asText());same(invocation.modelResult().sha256(),receipt.required("result_artifact").required("sha256").asText());same(invocation.providerCapture().artifactId(),receipt.required("capture_artifact").required("artifact_id").asText());same(invocation.providerCapture().sha256(),receipt.required("capture_artifact").required("sha256").asText());
    String raw=text(accounting,"receipt_json"),id=text(accounting,"receipt_id");same(raw,one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND subject_id=? AND action='model.invocation_accounted' AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),tenant,accounting.get("project_id"),accounting.get("created_by"),id,invocation.invocationId())));same(raw,one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND aggregate_id=? AND event_type='model.invocation_accounted' AND aggregate_revision=1 LIMIT 2",(r,n)->r.getString(1),tenant,id)));
   }
   // The original exact input is physically read and hash checked under current reader rights.
   payload(originals,record.inputManifest());
   if(deterministic){
    if(record.sessionExport()!=null||!record.invocations().isEmpty()||!record.checkIds().isEmpty())bad();
    var executed=one(db.queryForList("SELECT * FROM formal_evaluation_deterministic_execution WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",tenant,run,step));var computation=parser.readTree(text(executed,"canonical_execution"));same(hash(computation),executed.get("execution_sha256"));same(executed.get("execution_sha256"),evidence.required("execution_sha256").asText());same(executed.get("execution_id"),evidence.required("deterministic_execution_id").asText());
    same(envelope,computation.required("envelope"));same(business,computation.required("output"));same("SUCCEEDED",computation.required("state").asText());same(hash(business),terminal.required("output_sha256").asText());same("SUCCEEDED",terminal.required("state").asText());
    for(String key:List.of("schema_version","execution_kind","job_type","node_kind","execution_id","run_id","step_id","action_id","input_sha256","implementation_sha256"))same(envelope.required(key),terminal.required(key));
    same(record.node().nodeKind(),envelope.required("node_kind").asText());same("EVALUATION_VERIFICATION",envelope.required("job_type").asText());same(record.inputManifest().sha256(),envelope.required("input_sha256").asText());same(json.tree(record.inputManifest()),evidence.required("input"));
    journal(tenant,executed,"evaluation.deterministic.executed",text(executed,"execution_id"),run,computation);
   }else{
    String session=text(result,"session_id");same(session,text(outcome,"session_id"));same(session,envelope.required("session_id").asText());same("EVALUATION_VERIFICATION",envelope.required("job_type").asText());same(json.tree(record.inputManifest()),envelope.required("input"));
    var admitted=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='ADMITTED' LIMIT 2",tenant,session));var closed=one(db.queryForList("SELECT * FROM professional_runtime_supervision WHERE tenant_id=? AND session_id=? AND phase='CLOSED' LIMIT 2",tenant,session));
    var admission=parser.readTree(text(admitted,"canonical_evidence"));var closure=parser.readTree(text(closed,"canonical_evidence"));same(hash(admission),admitted.get("evidence_sha256"));same(hash(closure),closed.get("evidence_sha256"));
    FormalEvaluationSupervisor.validateStoredAdmission(admission);FormalEvaluationSupervisor.validateStoredTermination(closure);
    for(String key:List.of("session_id","binding","deployment_revision","deployment_sha256","runtime_manifest_sha256","supervisor_instance_id"))same(admission.required(key),closure.required(key));
    same(admitted.get("receipt_id"),closure.required("admitted_receipt_id").asText());same(admitted.get("evidence_sha256"),closure.required("admitted_sha256").asText());same(closed.get("receipt_id"),evidence.required("closed_receipt_id").asText());same(closed.get("evidence_sha256"),evidence.required("closed_sha256").asText());
    same(record.stepEnvelope().sha256(),closure.required("binding").required("step_envelope_sha256").asText());same(record.inputManifest().sha256(),closure.required("binding").required("input_sha256").asText());same("EXITED",closure.required("exit").required("code").asText());same(0,closure.required("exit").required("status").intValue());
    var exported=payload(originals,record.sessionExport());same(record.sessionExport().sha256(),closure.required("session_export").required("sha256").asText());same(json.tree(record.sessionExport()),evidence.required("session_export"));
    same(session,terminal.required("session_id").asText());same(closure.required("binding"),terminal.required("binding"));same("SUCCEEDED",terminal.required("outcome").asText());same(business,terminal.required("output"));same(json.tree(output),terminal.required("output_artifact"));same(json.tree(record.invocations()),evidence.required("invocations"));
    journal(tenant,admitted,"evaluation.professional.admitted",session,text(admitted,"receipt_id"),admission);journal(tenant,closed,"evaluation.professional.closed",session,text(closed,"receipt_id"),closure);
    var completion=one(db.queryForList("SELECT * FROM formal_evaluation_professional_completion WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2",tenant,run,step));var receipt=parser.readTree(text(completion,"canonical_receipt"));same(hash(receipt),completion.get("receipt_sha256"));same(result.get("record_sha256"),completion.get("record_sha256"));same(json.tree(record),receipt.required("record"));same(session,receipt.required("session_id").asText());same(closed.get("receipt_id"),receipt.required("closed_receipt_id").asText());same(closed.get("evidence_sha256"),receipt.required("closed_sha256").asText());
    journal(tenant,completion,"evaluation.professional_completed",text(completion,"completion_id"),text(completion,"completion_id"),receipt);
   }
  }
 }
 private JsonNode payload(Map<String,FormalEvaluationOriginalObjects.Verified> originals,FormalEvaluationExecutionProtocol.Artifact artifact)throws Exception{var original=originals.get(artifact.artifactId());if(original==null||!artifact.equals(original.reference()))bad();original.requireCurrent();return parser.readTree(original.bytes());}
 private void journal(String tenant,Map<String,Object> row,String action,String target,String trace,JsonNode evidence)throws Exception{
  String body=json.canonical(evidence);same(body,one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND request_id=? AND trace_id=? AND result='SUCCESS' LIMIT 2",(r,n)->r.getString(1),tenant,row.get("project_id"),row.get("audit_id"),row.get("created_by"),action,target,trace)));
  same(body,one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=? LIMIT 2",(r,n)->r.getString(1),tenant,row.get("event_id"),target,action)));
 }
 private String hash(JsonNode n)throws Exception{return WireValues.sha256(json.canonical(n).getBytes(java.nio.charset.StandardCharsets.UTF_8));}private static String text(Map<String,Object> row,String key){if(!(row.get(key) instanceof String))bad();return (String)row.get(key);}private static <T>T one(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);return values.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
