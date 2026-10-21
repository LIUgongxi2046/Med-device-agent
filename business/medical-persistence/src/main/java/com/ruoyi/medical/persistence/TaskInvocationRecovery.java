package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Observes only an already-admitted TASK attempt. No live execution token, reserve,
 * begin-send, replacement key, provider request or output content is exposed here. */
public final class TaskInvocationRecovery {
 private final FormalInvocationOutcomeAuthority outcomes;private final TaskInvocationRecords records;
 private final TaskNodeProducerAuthority producers;private final FormalInvocationReservations reservations;
 private final FormalInvocationSettlement settlement;private final FormalInvocationObjectRegistration objects;
 private final FormalProviderCaptureEvidence captures;private final TaskInvocationResults results;
 private final TaskInvocationLocalBridge adapter;private final JdbcTemplate db;
 public TaskInvocationRecovery(DataSource data,FormalInvocationOutcomeAuthority outcomes,TaskInvocationRecords records,
    TaskNodeProducerAuthority producers,FormalInvocationReservations reservations,FormalInvocationSettlement settlement,
    FormalInvocationObjectRegistration objects,FormalProviderCaptureEvidence captures,TaskInvocationResults results,TaskInvocationLocalBridge adapter){
  this(new JdbcTemplate(data),outcomes,records,producers,reservations,settlement,objects,captures,results,adapter);
 }
 TaskInvocationRecovery(JdbcTemplate db,FormalInvocationOutcomeAuthority outcomes,TaskInvocationRecords records,
    TaskNodeProducerAuthority producers,FormalInvocationReservations reservations,FormalInvocationSettlement settlement,
    FormalInvocationObjectRegistration objects,FormalProviderCaptureEvidence captures,TaskInvocationResults results,TaskInvocationLocalBridge adapter){
  this.db=db;db.setQueryTimeout(5);this.outcomes=outcomes;this.records=records;this.producers=producers;this.reservations=reservations;
  this.settlement=settlement;this.objects=objects;this.captures=captures;this.results=results;this.adapter=adapter;
 }
 private record Snapshot(FormalInvocationOutcomeAuthority.VerifiedRecovery original,JsonNode metadata){}
 public JsonNode recover(HttpServletRequest http,String tenant,String actor,String run,String step,String invocation,String attempt)throws Exception {
  outside();var before=outcomes.recover(http,tenant,actor,run,step,invocation,attempt,proof->new Snapshot(proof,status(proof)));
  // Terminal, pending or disputed accounting is already an immutable recorded outcome.
  // A repeated recovery only re-verifies it, and never re-posts a substitute capture.
  if(before.metadata.required("recorded").booleanValue())return before.metadata.deepCopy();
  try{adapter.recover(new RecoveryCall(before.original));}catch(TaskInvocationLocalBridge.Unknown ambiguous){
   // A lost stdout/receipt response can still have committed. Always inspect the original
   // backend records; an adapter acknowledgement is never a settlement proof.
  }
  return before.original.current(this::status);
 }
 /** Constructor is private. A caller cannot convert bare invocation identifiers into a
  * process command. It is one-use and revalidates the original authority immediately. */
 public final class RecoveryCall {
  private final FormalInvocationOutcomeAuthority.VerifiedRecovery original;private final AtomicBoolean consumed=new AtomicBoolean();
  private RecoveryCall(FormalInvocationOutcomeAuthority.VerifiedRecovery original){this.original=original;}
  public String invocationId(){return original.invocationId();}public String attemptId(){return original.attemptId();}
  byte[] consume()throws Exception {
   outside();if(!consumed.compareAndSet(false,true))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   return original.current(proof->{binding(proof);var wire=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
    .put("schema_version","1.0.0").put("mode","RECOVER").put("invocation_id",proof.invocationId()).put("dispatch_attempt_id",proof.attemptId());
    proof.requireActive();return ContentReadAuthorization.canonicalBytes(wire);});
  }
  @Override public String toString(){return "TaskInvocationRecoveryCall[original metadata only]";}
 }
 private TaskInvocationRecords.Binding binding(FormalInvocationOutcomeAuthority.VerifiedRecovery proof)throws Exception {
  proof.requireActive();var binding=records.byInvocation(proof.tenantId(),proof.invocationId());
  if(!binding.actor().equals(proof.actorId())||!binding.project().equals(proof.projectId())||!binding.run().equals(proof.runId())
    ||!binding.step().equals(proof.stepId())||!binding.action().equals(proof.actionId())||!binding.context().equals(proof.contextId())
    ||!binding.model().equals(proof.modelConfigRevision())||!binding.inputHash().equals(proof.inputSha256())
    ||!binding.node().lease_generation().equals(proof.leaseGeneration())||!binding.node().worker_service_uri().equals(proof.serviceUri()))bad();
  reservations.requireRegistered(binding,proof.invocationId());var committed=producers.verifyOriginalCommitment(proof.tenantId(),proof.invocationId());
  for(var entry:Map.ofEntries(Map.entry("tenant_id",proof.tenantId()),Map.entry("actor_id",proof.actorId()),Map.entry("project_id",proof.projectId()),
    Map.entry("run_id",proof.runId()),Map.entry("step_id",proof.stepId()),Map.entry("action_id",proof.actionId()),Map.entry("invocation_id",proof.invocationId()),
    Map.entry("context_id",proof.contextId()),Map.entry("model_config_revision",proof.modelConfigRevision()),Map.entry("input_sha256",proof.inputSha256()),
    Map.entry("lease_generation",proof.leaseGeneration()),Map.entry("worker_service_uri",proof.serviceUri())).entrySet())
   if(!entry.getValue().equals(text(committed,entry.getKey())))bad();
  proof.requireActive();return binding;
 }
 private JsonNode status(FormalInvocationOutcomeAuthority.VerifiedRecovery proof)throws Exception {
  var binding=binding(proof);var account=settlement.read(proof.tenantId(),proof.invocationId());
  if(account.isEmpty()){
   var current=one(db.query("SELECT status,settlement_status FROM invocation WHERE tenant_id=? AND invocation_id=? AND created_by=? LIMIT 2 FOR UPDATE",
    (r,n)->List.of(r.getString(1),r.getString(2)),proof.tenantId(),proof.invocationId(),proof.actorId()));
   if(!Set.of("DISPATCHING","IN_FLIGHT","UNKNOWN","CANCEL_REQUESTED").contains(current.get(0))||!"PENDING".equals(current.get(1)))bad();
   var held=db.query("SELECT status,settlement_receipt_id,settled_amount,settled_tokens FROM budget_reservation WHERE tenant_id=? AND invocation_id=? ORDER BY budget_id LIMIT 3 FOR UPDATE",
    (r,n)->Arrays.asList(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),proof.tenantId(),proof.invocationId());
   if(held.size()!=2||held.stream().anyMatch(r->!"RESERVED".equals(r.get(0))||r.subList(1,4).stream().anyMatch(Objects::nonNull)))bad();
   proof.requireActive();return metadata(proof,"UNKNOWN","PENDING",false,null);
  }
  var recorded=account.get();var receipt=recorded.receipt();String state=text(receipt,"status"),settled=text(receipt,"settlement_status");
  if(!proof.attemptId().equals(text(receipt,"attempt_id")))bad();
  if(recorded.settled()){
   // Re-run published ModelResult validation, actual076 capture derivation, original
   // registrations and exact price/usage accounting rather than trusting status strings.
   var verified=results.verifiedAnyResult(binding,proof.invocationId());if(!verified.accounting().equals(recorded.receiptSha256()))bad();
  }else{
   var result=objects.registered(proof.tenantId(),proof.invocationId(),"MODEL_RESULT").orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
   var capture=objects.registered(proof.tenantId(),proof.invocationId(),"PROVIDER_CAPTURE").orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
   result.requireUnchanged();capture.requireUnchanged();var observed=proof.inspect(captures,result.canonicalPayload(),ContentReadAuthorization.canonicalBytes(capture.canonicalPayload()));
   var determined=FormalInvocationAccounting.determine(binding.original().price(),observed.usage(),observed.calls(),observed.terminal());
   if(!"UNKNOWN".equals(state)||!Set.of("PENDING","DISPUTED").contains(settled)||!receipt.required("provider_terminal").isBoolean()
      ||receipt.required("provider_terminal").booleanValue()!=observed.terminal()||("PENDING".equals(settled)==determined.maySettleAll()))bad();
   amount(receipt.required("known_amount"),determined.amount());scalar(receipt.required("known_tokens"),determined.tokens().map(Object::toString));scalar(receipt.required("known_calls"),determined.calls().map(Object::toString));
  }
  proof.requireActive();return metadata(proof,state,settled,true,recorded.receiptSha256());
 }
 private static ObjectNode metadata(FormalInvocationOutcomeAuthority.VerifiedRecovery proof,String status,String settlement,boolean recorded,String hash){
  var result=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("invocation_id",proof.invocationId())
   .put("dispatch_attempt_id",proof.attemptId()).put("status",status).put("settlement_status",settlement).put("recorded",recorded)
   .put("reservations_released","SETTLED".equals(settlement)).put("content_available",false);
  if(hash==null)result.putNull("accounting_receipt_sha256");else result.put("accounting_receipt_sha256",hash);return result;
 }
 private static void amount(JsonNode actual,Optional<BigDecimal> expected){if(expected.isEmpty()){if(!actual.isNull())bad();}else if(!actual.isTextual()||new BigDecimal(actual.textValue()).compareTo(expected.get())!=0)bad();}
 private static void scalar(JsonNode actual,Optional<String> expected){if(expected.isEmpty()){if(!actual.isNull())bad();}else if(!actual.isTextual()||!expected.get().equals(actual.textValue()))bad();}
 private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
 private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)bad();return values.get(0);}
 private static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
