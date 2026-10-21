package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Original durable N07 job bound to fresh saved-document, publication and policy proofs. */
public final class DocumentReviewJobAuthority {
 private final DocumentReviewExecutionService jobs;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 public DocumentReviewJobAuthority(DocumentReviewExecutionService jobs){this.jobs=jobs;}
 Verified require(DocumentReviewAdmission.Verified admitted,DocumentReviewExecutionService.Job job)throws Exception{check(admitted,job);return new Verified(admitted,job);}
 private void check(DocumentReviewAdmission.Verified admitted,DocumentReviewExecutionService.Job job)throws Exception{
  admitted.requireUnchanged();var a=admitted.admission();var d=admitted.document();d.requireCurrent(a);
  if(!job.equals(jobs.byRun(a.context().tenant_id(),job.run()))||!job.actor().equals(a.actorId())||!job.project().equals(d.header().projectId())||!job.document().equals(d.header().documentId())||!job.documentRevision().equals(d.header().documentRevision())||!json.read(job.baselineJson()).equals(d.baseline()))throw new TenantAccessException(ErrorCode.REVIEW_STALE);
 }
 public final class Verified {
  private final DocumentReviewAdmission.Verified current;private final DocumentReviewExecutionService.Job job;private DocumentReviewResultService.VerifiedCompletion result;
  private Verified(DocumentReviewAdmission.Verified current,DocumentReviewExecutionService.Job job){this.current=current;this.job=job;}
  public void requireUnchanged()throws Exception{if(result==null)check(current,job);else requireResult();}
  void acceptedResult(DocumentReviewResultService.VerifiedCompletion completed)throws Exception {
   if(result!=null)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);completed.requireActive();
   if(!job.run().equals(completed.runId())||!job.step().equals(completed.stepId())||!job.inputHash().equals(completed.inputSha256())||!job.document().equals(completed.receipt().required("document_id").asText())||!job.documentRevision().equals(completed.receipt().required("document_revision").asText())||!current.document().header().bodySha256().equals(completed.receipt().required("body_sha256").asText()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   result=completed;requireResult();
  }
  private void requireResult()throws Exception{result.requireFor(job,current.admission());if(!job.equals(jobs.byRun(job.tenant(),job.run()))||!result.receiptSha256().equals(json.hash(result.receipt())))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
  boolean hasAcceptedResult(){return result!=null;}
  DocumentReviewResultService.VerifiedCompletion acceptedResult(){if(result==null)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);result.requireActive();return result;}EnterpriseAccess.Admission admission(){return current.admission();}DocumentReviewExecutionService.Job job(){return job;}
  List<RunTokenAuthoritySchema.Permission> nodePermissions(){return current.permissions();}AgentExecutionPolicyProtocol.Worker worker(){return current.worker();}
  DocumentReviewAdmission.Verified current(){return current;}
 }
}
