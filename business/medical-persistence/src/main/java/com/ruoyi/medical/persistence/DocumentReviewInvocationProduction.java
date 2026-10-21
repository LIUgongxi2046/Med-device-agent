package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Bounded CHECK_DOCUMENT production authority. Never impersonates a TASK producer rule. */
public final class DocumentReviewInvocationProduction {
 private final DocumentReviewExecutionService jobs;private final JdbcTemplate db;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 public DocumentReviewInvocationProduction(DocumentReviewExecutionService jobs,DataSource data){this.jobs=jobs;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public Verified require(DocumentReviewRuntimeAuthority.Verified authority,EnterpriseAccess.Admission admission)throws Exception {
  authority.requireUnchanged();var j=authority.job();if(!admission.formalWorker())deny();var i=admission.formalProof().identity();
  if(!"CHECK_DOCUMENT".equals(i.executionKind())||!j.tenant().equals(i.tenant())||!j.project().equals(i.project())||!j.run().equals(i.run())||!j.step().equals(i.step())||!j.principal().equals(i.principal())||!j.actor().equals(i.actor())||!authority.leaseGeneration().toString().equals(i.generation()))deny();
  return new Verified(authority,pins(j));
 }
 private JsonNode pins(DocumentReviewExecutionService.Job j)throws Exception {var n=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("execution_kind","SYSTEM_JOB").put("job_type","CHECK_DOCUMENT").put("node_kind","N07").put("tenant_id",j.tenant()).put("project_id",j.project()).put("run_id",j.run()).put("step_id",j.step()).put("document_id",j.document()).put("document_revision",j.documentRevision()).put("input_sha256",j.inputHash()).put("admission_sha256",json.hash(json.read(j.admissionJson()))).put("maximum_bytes",DocumentReviewInvocationSchema.MAXIMUM_BYTES).put("maximum_invocations",1);n.set("admission",json.read(j.admissionJson()));return n;}
 public final class Verified {
  private final DocumentReviewRuntimeAuthority.Verified a;private final JsonNode pins;private Verified(DocumentReviewRuntimeAuthority.Verified a,JsonNode pins){this.a=a;this.pins=pins;}
  public void requireUnchanged()throws Exception{a.requireUnchanged();if(!pins.equals(pins(jobs.byRun(a.job().tenant(),a.job().run()))))deny();}
  public JsonNode canonicalPins()throws Exception{requireUnchanged();return pins.deepCopy();}
  public PermissionProtocol.Basis enterpriseBudgetBasis()throws Exception{requireUnchanged();return a.current().current().enterpriseBudget();}
  public void requireBytes(String kind,long size)throws Exception{requireUnchanged();if(!Set.of("DOCUMENT_REVIEW_EXACT_INPUT","MODEL_RESULT","PROVIDER_CAPTURE").contains(kind)||size<=0||size>DocumentReviewInvocationSchema.MAXIMUM_BYTES)deny();}
  public void requireModelInputConsume(int size)throws Exception{requireBytes("DOCUMENT_REVIEW_EXACT_INPUT",size);}
  public void requireInvocationCapacity()throws Exception{requireUnchanged();var j=a.job();var rows=db.queryForList("SELECT invocation_id,action_id FROM invocation WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",j.tenant(),j.project(),j.run(),j.step());if(rows.size()>1||!rows.isEmpty()&&!a.actionId().equals(rows.get(0).get("action_id")))deny();}
 }
 /** Original evidence may register already dispatched outputs after lease expiry or revocation. No execution authority is returned. */
 public void requireOriginalOutput(FormalInvocationOutcomeAuthority.VerifiedOutcome outcome,JsonNode expected,String kind,int size)throws Exception {
  outcome.requireActive();if(!Set.of("MODEL_RESULT","PROVIDER_CAPTURE").contains(kind)||size<=0||size>DocumentReviewInvocationSchema.MAXIMUM_BYTES)deny();var j=jobs.byRun(outcome.tenantId(),outcome.binding().required("run_id").asText());if(!j.actor().equals(outcome.actorId())||!expected.equals(pins(j)))deny();
 }
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
