package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Concrete persisted START_RUN consumer for replay materialization only. This does not launch a
 * workflow or mark a run RUNNING. It is intentionally not a bean until the complete TASK consumer
 * and its explicit operational configuration are available. Each physical segment reacquires
 * current mTLS/publication/policy/retention/delegation authority in a fresh short transaction. */
public final class TaskRunReplayBootstrap {
 private final FormalRunControlAccess control;private final AgentPublicationAuthority publications;
 private final AgentExecutionPolicyAuthority policies;private final RuntimeRetentionAuthority retention;
 private final TaskRunBootstrapAuthority authority;private final TaskRunReplayLedger ledger;
 private final RuntimeObjectTransport objects;private final TaskRunReplayRegistration registration;
 private final IdentityDirectoryPolicy identity;private final TaskRunReplayContentAuthority content;
 public TaskRunReplayBootstrap(FormalRunControlAccess control,AgentPublicationAuthority publications,
   AgentExecutionPolicyAuthority policies,RuntimeRetentionAuthority retention,TaskRunBootstrapAuthority authority,
   TaskRunReplayLedger ledger,RuntimeObjectTransport objects,TaskRunReplayRegistration registration,IdentityDirectoryPolicy identity,TaskRunReplayContentAuthority content){
  this.control=Objects.requireNonNull(control);this.publications=Objects.requireNonNull(publications);this.policies=Objects.requireNonNull(policies);
  this.retention=Objects.requireNonNull(retention);this.authority=Objects.requireNonNull(authority);this.ledger=Objects.requireNonNull(ledger);
  this.objects=Objects.requireNonNull(objects);this.registration=Objects.requireNonNull(registration);this.identity=Objects.requireNonNull(identity);this.content=Objects.requireNonNull(content);
 }
 public TaskRunReplayRegistration.Registered consume(HttpServletRequest http,String tenantId,String runId,String commandId)throws Exception{
  return control.withReads(http,tenantId,runId,commandId,reads->ThrowingSupplier.of(()->{
   var release=reads.replay().required("agent_release_id").asText();
   var prepared=publications.prepareControl(reads,release,"RUN_CREATION");
   var intent=reads.execute(a->ThrowingSupplier.of(()->ledger.prepare(current(reads,a,prepared,runId))).get());
   var attempt=reads.execute(a->ThrowingSupplier.of(()->ledger.attempt(current(reads,a,prepared,runId),intent)).get());
   var transferred=objects.transferVerified(attempt,()->reads.execute(a->ThrowingSupplier.of(()->{
    var proof=current(reads,a,prepared,runId);ledger.requireAttempt(proof,intent);proof.requireReplayRegistration();return null;
   }).get()));
   return reads.execute(a->ThrowingSupplier.of(()->registration.register(current(reads,a,prepared,runId),attempt,transferred)).get());
  }).get());
 }
 private TaskRunBootstrapAuthority.Verified current(FormalRunControlAccess.Reads reads,EnterpriseAccess.Admission admission,
   AgentPublicationAuthority.PreparedPublication prepared,String runId)throws Exception{
  var replay=reads.replay();String project=replay.required("project_id").asText();
  var publication=publications.require(admission,replay.required("agent_release_id").asText(),"RUN_CREATION",prepared);
  var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),project,identity.classification(),"RUN_CREATION");
  var fixed=replay.required("retention");
  var retained=retention.requireFixedForRun(admission,fixed.required("policy_revision").asText(),fixed.required("content_sha256").asText(),project,identity.classification(),"RUN_CREATION");
  var proof=authority.require(admission,publication,policy,retained,runId);
  content.require(proof).requireUnchanged();
  return proof;
 }
}
