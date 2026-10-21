package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.DocumentCheckProtocol;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
/** The result authority is the sole N07 completion capability. Never alters a TASK run or grants approval. */
public final class DocumentReviewCompletionLedger {
    private final JdbcTemplate db;private final DocumentReviewExecutionService jobs;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentReviewCompletionLedger(DataSource data,DocumentReviewExecutionService jobs){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.jobs=jobs;}
    public DocumentCheckProtocol.Receipt finish(EnterpriseAccess.Admission a,DocumentReviewExecutionService.Job job,DocumentReviewResultService.VerifiedCompletion completed)throws Exception {
        completed.requireActive();same(job,jobs.byRun(job.tenant(),job.run()));same(a.context().tenant_id(),job.tenant());same(a.actorId(),job.actor());same(job.run(),completed.runId());same(job.step(),completed.stepId());same(job.inputHash(),completed.inputSha256());
        var r=completed.receipt();same(job.document(),text(r,"document_id"));same(job.documentRevision(),text(r,"document_revision"));var baseline=json.read(job.baselineJson());same(baseline.required("body_sha256").asText(),text(r,"body_sha256"));same(json.hash(baseline),text(r,"baseline_sha256"));same(completed.receiptSha256(),json.hash(r));
        var checks=new ArrayList<String>();r.required("check_ids").forEach(id->checks.add(id.asText()));var receipt=new DocumentCheckProtocol.Receipt("1.0.0",job.run(),job.step(),job.inputHash(),text(r,"receipt_id"),completed.receiptSha256(),text(r,"status"),job.document(),job.documentRevision(),text(r,"body_sha256"),text(r,"baseline_sha256"),checks,false);
        var prior=db.queryForList("SELECT * FROM document_review_completion WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",job.tenant(),job.run());if(!prior.isEmpty()){var row=one(prior);same(json.canonical(receipt),row.get("receipt_json"));same(json.hash(receipt),row.get("receipt_sha256"));same(job.inputHash(),row.get("input_sha256"));same(receipt.state(),one(db.query("SELECT status FROM system_job_run WHERE tenant_id=? AND run_id=? AND job_type='CHECK_DOCUMENT'",(rs,n)->rs.getString(1),job.tenant(),job.run())));return receipt;}
        if(!Set.of("SUCCEEDED","WAITING_INPUT").contains(receipt.state()))deny();
        // Do not re-run source/issue-watermark checks after result authority atomically creates issues.
        // Its private transaction-bound completion is the evidence of that exact valid transition.
        if(db.update("UPDATE run_step SET status=?,output_ref=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND input_sha256=? AND status='RUNNING'",receipt.state(),json.canonical(receipt),job.tenant(),job.project(),job.run(),job.step(),job.inputHash())!=1)deny();
        if(db.update("UPDATE system_job_run SET status=?,ended_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND project_id=? AND run_id=? AND execution_kind='SYSTEM_JOB' AND job_type='CHECK_DOCUMENT' AND status='RUNNING' AND workflow_id IS NOT NULL AND started_at IS NOT NULL AND ended_at IS NULL AND input_sha256=?",receipt.state(),job.tenant(),job.project(),job.run(),job.inputHash())!=1)deny();
        if(db.update("INSERT INTO document_review_completion(tenant_id,project_id,run_id,input_sha256,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)",job.tenant(),job.project(),job.run(),job.inputHash(),json.canonical(receipt),json.hash(receipt),DocumentReviewExecutionService.MANIFEST,job.actor(),job.classification())!=1)deny();
        completed.requireActive();return receipt;
    }
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())deny();return n.required(key).asText();}private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
