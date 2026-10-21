package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable start intent, committed attempt and actual Temporal acceptance. Uncertainty is query-only. */
public final class DocumentReviewLaunchLedger {
    private final JdbcTemplate db;private final DocumentReviewExecutionService jobs;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final JsonMapper mapper=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    public DocumentReviewLaunchLedger(DataSource data,DocumentReviewExecutionService jobs){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.jobs=jobs;}
    public final class Intent {
        private final DocumentReviewExecutionService.Job job;private final String command,hash;private final DocumentCheckProtocol.Reference reference;
        private Intent(DocumentReviewExecutionService.Job job,String command,String hash,DocumentCheckProtocol.Reference reference){this.job=job;this.command=command;this.hash=hash;this.reference=reference;}
        public DocumentReviewExecutionService.Job job(){return job;}public String commandId(){return command;}public String referenceHash(){return hash;}public DocumentCheckProtocol.Reference reference(){return reference;}
    }
    Intent prepare(DocumentReviewAdmission.Verified proof,DocumentReviewExecutionService.Job job,DocumentReviewLaunchConfiguration config)throws Exception {
        proof.requireUnchanged();same(job,jobs.byRun(job.tenant(),job.run()));same(proof.document().baseline(),json.read(job.baselineJson()));
        var limits=proof.publication().manifest().manifest().limits();if(config.activityTimeoutMillis()>Math.multiplyExact((long)limits.maxWallTimeSeconds(),1000L)||config.heartbeatTimeoutMillis()>Math.multiplyExact((long)limits.heartbeatTimeoutSeconds(),1000L))deny();
        var reference=new DocumentCheckProtocol.Reference("1.0.0","SYSTEM_JOB","CHECK_DOCUMENT","N07",job.tenant(),job.project(),job.run(),job.step(),job.intent(),job.document(),job.documentRevision(),proof.document().header().bodySha256(),proof.document().baselineSha256(),job.inputHash(),json.hash(json.read(job.admissionJson())),proof.policy().versionId(),config.controlTaskQueue(),config.professionalTaskQueue(),config.activityTimeoutMillis(),config.heartbeatTimeoutMillis());
        var rows=db.queryForList("SELECT * FROM document_review_launch_intent WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",job.tenant(),job.run());if(!rows.isEmpty()){var old=read(job,one(rows));same(reference,old.reference);return old;}
        if(one(db.query("SELECT COUNT(*) FROM system_job_run WHERE tenant_id=? AND run_id=? AND job_type='CHECK_DOCUMENT' AND status='QUEUED' AND workflow_id IS NULL AND started_at IS NULL AND ended_at IS NULL",(r,n)->r.getInt(1),job.tenant(),job.run()))!=1)deny();
        var command=UUID.randomUUID().toString();var row=meta(job);row.putAll(Map.of("command_id",command,"reference_json",json.canonical(reference),"reference_sha256",json.hash(reference),"configuration_json",json.canonical(config),"configuration_sha256",json.hash(config)));insert("document_review_launch_intent",row);proof.requireUnchanged();return original(job.tenant(),job.run());
    }
    public record Attempt(Intent intent,boolean recoveryOnly){}
    Attempt attempt(DocumentReviewAdmission.Verified proof,Intent expected)throws Exception {
        proof.requireUnchanged();var actual=original(expected.job.tenant(),expected.job.run());same(expected.reference,actual.reference);same(expected.command,actual.command);
        var rows=db.queryForList("SELECT * FROM document_review_launch_attempt WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",actual.job.tenant(),actual.job.run());if(!rows.isEmpty()){verifyAttempt(actual,one(rows));return new Attempt(actual,true);}
        var row=meta(actual.job);row.putAll(Map.of("command_id",actual.command,"reference_sha256",actual.hash));insert("document_review_launch_attempt",row);return new Attempt(actual,false);
    }
    public boolean attempted(String tenant,String run){current(tenant);return !db.queryForList("SELECT run_id FROM document_review_launch_attempt WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run).isEmpty();}
    public Intent original(String tenant,String run)throws Exception {var job=jobs.byRun(tenant,run);return read(job,one(db.queryForList("SELECT * FROM document_review_launch_intent WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run)));}
    public Intent recoveryOriginal(String tenant,String run)throws Exception {var intent=original(tenant,run);verifyAttempt(intent,one(db.queryForList("SELECT * FROM document_review_launch_attempt WHERE tenant_id=? AND run_id=? LIMIT 2",tenant,run)));return intent;}
    public JsonNode accepted(Intent expected,DocumentCheckDelivery.Accepted accepted)throws Exception {
        var intent=recoveryOriginal(expected.job.tenant(),expected.job.run());same(intent.reference,accepted.reference());same(intent.command,accepted.commandId());same(expected.reference,intent.reference);var j=intent.job;
        var receipt=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("job_type","CHECK_DOCUMENT").put("run_id",j.run()).put("command_id",intent.command).put("reference_sha256",intent.hash).put("workflow_id",intent.reference.workflowId()).put("temporal_run_id",accepted.temporalRunId());
        var rows=db.queryForList("SELECT * FROM document_review_launch_receipt WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",j.tenant(),j.run());if(!rows.isEmpty()){verifyReceipt(intent,receipt,one(rows));return receipt;}
        if(db.update("UPDATE system_job_run SET workflow_id=?,status='RUNNING',started_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='SYSTEM_JOB' AND job_type='CHECK_DOCUMENT' AND status='QUEUED' AND input_sha256=? AND workflow_id IS NULL AND started_at IS NULL AND ended_at IS NULL",intent.reference.workflowId(),j.tenant(),j.project(),j.run(),j.inputHash())!=1)deny();
        var row=meta(j);row.putAll(Map.of("command_id",intent.command,"reference_sha256",intent.hash,"workflow_id",intent.reference.workflowId(),"temporal_run_id",accepted.temporalRunId(),"receipt_json",json.canonical(receipt),"receipt_sha256",json.hash(receipt)));insert("document_review_launch_receipt",row);
        return receipt;
    }
    /** Worker bootstrap requires real original start evidence, not merely a queued intent. */
    public void requireAccepted(DocumentCheckProtocol.Reference reference)throws Exception {var intent=recoveryOriginal(reference.tenantId(),reference.runId());same(reference,intent.reference);var row=one(db.queryForList("SELECT * FROM document_review_launch_receipt WHERE tenant_id=? AND run_id=? LIMIT 2",reference.tenantId(),reference.runId()));verifyReceipt(intent,json.read(text(row,"receipt_json")),row);}
    private Intent read(DocumentReviewExecutionService.Job job,Map<String,Object> row)throws Exception {
        metadata(job,row);var n=json.read(text(row,"reference_json"));var ref=mapper.treeToValue(n,DocumentCheckProtocol.Reference.class);same(json.canonical(ref),text(row,"reference_json"));same(json.hash(ref),text(row,"reference_sha256"));same(json.hash(json.read(text(row,"configuration_json"))),text(row,"configuration_sha256"));
        same(job.run(),ref.runId());same(job.tenant(),ref.tenantId());same(job.project(),ref.projectId());same(job.step(),ref.stepId());same(job.intent(),ref.intentId());same(job.document(),ref.documentId());same(job.documentRevision(),ref.documentRevision());same(job.inputHash(),ref.inputSha256());same(json.hash(json.read(job.admissionJson())),ref.admissionSha256());same(json.hash(json.read(job.baselineJson())),ref.baselineSha256());same(json.read(job.baselineJson()).required("body_sha256").asText(),ref.bodySha256());
        WireValues.uuid(text(row,"command_id"));return new Intent(job,text(row,"command_id"),text(row,"reference_sha256"),ref);
    }
    private void verifyAttempt(Intent i,Map<String,Object> row){metadata(i.job,row);same(i.command,text(row,"command_id"));same(i.hash,text(row,"reference_sha256"));}
    private void verifyReceipt(Intent i,JsonNode receipt,Map<String,Object> row)throws Exception{verifyAttempt(i,row);same(i.reference.workflowId(),text(row,"workflow_id"));WireValues.uuid(text(row,"temporal_run_id"));same(text(row,"temporal_run_id"),receipt.required("temporal_run_id").asText());same(json.canonical(receipt),text(row,"receipt_json"));same(json.hash(receipt),text(row,"receipt_sha256"));if(one(db.query("SELECT COUNT(*) FROM system_job_run WHERE tenant_id=? AND run_id=? AND job_type='CHECK_DOCUMENT' AND workflow_id=? AND started_at IS NOT NULL",(r,n)->r.getInt(1),i.job.tenant(),i.job.run(),i.reference.workflowId()))!=1)bad();}
    private Map<String,Object> meta(DocumentReviewExecutionService.Job j){var m=new LinkedHashMap<String,Object>();m.putAll(Map.of("tenant_id",j.tenant(),"project_id",j.project(),"run_id",j.run(),"schema_version","1.0.0","schema_manifest_sha256",DocumentReviewExecutionService.MANIFEST,"created_by",j.actor(),"created_at",db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.time.LocalDateTime.class),"classification",j.classification()));return m;}
    private void metadata(DocumentReviewExecutionService.Job j,Map<String,Object> r){current(j.tenant());for(var e:Map.of("tenant_id",j.tenant(),"project_id",j.project(),"run_id",j.run(),"schema_version","1.0.0","schema_manifest_sha256",DocumentReviewExecutionService.MANIFEST,"created_by",j.actor(),"classification",j.classification()).entrySet())same(e.getValue(),r.get(e.getKey()));}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private static void current(String tenant){if(!TransactionSynchronizationManager.isActualTransactionActive()||!tenant.equals(TenantScope.requireCurrent().tenantId()))deny();}private static String text(Map<String,Object> row,String key){if(row.get(key)==null)bad();return row.get(key).toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
