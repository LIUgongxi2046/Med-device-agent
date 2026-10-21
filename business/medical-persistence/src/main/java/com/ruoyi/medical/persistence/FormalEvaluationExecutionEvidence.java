package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Two-phase original evidence reader. Missing original professional authority is a hard blocker. */
public final class FormalEvaluationExecutionEvidence {
 private final FormalEvaluationFactualCompletions facts;private final EnterpriseAccess access;private final FormalEvaluationExecutionLedger ledger;private final FormalEvaluationOriginalObjects objects;
 private final ObjectProvider<FormalEvaluationProfessionalCompletions> completions;
 public FormalEvaluationExecutionEvidence(EnterpriseAccess access,DataSource data,PermissionSchema json,EvaluationService evaluations,
   FormalAgentCandidateAuthority candidates,AgentExecutionPolicyAuthority policies,ResourceReadAuthority resources,
   FormalEvaluationOriginalObjects objects,ObjectProvider<FormalEvaluationProfessionalCompletions> completions){
  facts=new FormalEvaluationFactualCompletions(data,json);this.access=access;ledger=new FormalEvaluationExecutionLedger(data,json,evaluations,candidates,policies,resources);this.objects=objects;this.completions=completions;
 }
 /** Distinct factual read path. It cannot be supplied to scoring or publication consumers. */
 public FactualPrepared prepareFactual(ApplicationSessionIdentity.Verified actor,String context,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  var subject=EvidenceReadAccess.human(access,actor);var before=subject.execute(a->ThrowingSupplier.of(()->{context(a,context);return ledger.require(a,evaluation,candidate,policy,true);}).get());
  var originals=new TreeMap<String,FormalEvaluationOriginalObjects.Verified>();for(var artifact:FormalEvaluationCoverage.artifacts(before.records())){var read=objects.read(subject,context,before.project(),artifact);if(originals.put(artifact.artifactId(),read)!=null)bad();}
  var prepared=new FactualPrepared(this,subject.actorId(),context,evaluation,candidate,policy,before.canonical(),originals);
  subject.execute(a->ThrowingSupplier.of(()->{requireFactual(a,evaluation,candidate,policy,prepared);return null;}).get());return prepared;
 }
 public FactualEvidence requireFactual(EnterpriseAccess.Admission a,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,FactualPrepared prepared)throws Exception {
  if(prepared==null||prepared.owner!=this||!prepared.actor.equals(a.actorId())||!prepared.evaluation.equals(evaluation)||!prepared.candidate.equals(candidate)||!prepared.policy.equals(policy))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  context(a,prepared.context);var snapshot=ledger.require(a,evaluation,candidate,policy,true);if(!snapshot.canonical().equals(prepared.canonical))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
  var expected=FormalEvaluationCoverage.artifacts(snapshot.records());if(expected.size()!=prepared.originals.size())bad();for(var artifact:expected){var original=prepared.originals.get(artifact.artifactId());if(original==null||!artifact.equals(original.reference()))bad();original.requireCurrent();}
  facts.require(a,snapshot.records(),prepared.originals);return new FactualEvidence(this,a,prepared,snapshot);
 }
 public static final class FactualPrepared {
  private final FormalEvaluationExecutionEvidence owner;private final String actor,context,evaluation,canonical;private final PermissionProtocol.Basis candidate,policy;private final Map<String,FormalEvaluationOriginalObjects.Verified> originals;
  private FactualPrepared(FormalEvaluationExecutionEvidence owner,String actor,String context,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,String canonical,Map<String,FormalEvaluationOriginalObjects.Verified> originals){this.owner=owner;this.actor=actor;this.context=context;this.evaluation=evaluation;this.candidate=candidate;this.policy=policy;this.canonical=canonical;this.originals=Map.copyOf(originals);}
 }
 public static final class FactualEvidence {
  private final FormalEvaluationExecutionEvidence owner;private final EnterpriseAccess.Admission admission;private final FactualPrepared prepared;private final FormalEvaluationExecutionLedger.Snapshot snapshot;
  private FactualEvidence(FormalEvaluationExecutionEvidence owner,EnterpriseAccess.Admission a,FactualPrepared prepared,FormalEvaluationExecutionLedger.Snapshot snapshot){this.owner=owner;admission=a;this.prepared=prepared;this.snapshot=snapshot;}
  public List<FormalEvaluationExecutionProtocol.Record> records(){return snapshot.records();}
  public List<String> originalReadReceiptIds(){var ids=new ArrayList<String>();for(var original:prepared.originals.values())ids.addAll(original.receiptIds());return List.copyOf(ids);}
  public void requireConsumer(EnterpriseAccess.Admission a,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{if(a!=admission||!prepared.evaluation.equals(evaluation)||!prepared.candidate.equals(candidate)||!prepared.policy.equals(policy))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws Exception{owner.requireFactual(admission,prepared.evaluation,prepared.candidate,prepared.policy,prepared);}
 }
 public Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,String evaluationId,
   PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception {
  return prepare(subject,context,evaluationId,candidate,policy,false);
 }
 public Prepared prepareHistorical(ApplicationSessionIdentity.Verified subject,String context,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{return prepare(subject,context,evaluationId,candidate,policy,true);}
 private Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,boolean historical)throws Exception {return prepare(EvidenceReadAccess.human(access,subject),context,evaluationId,candidate,policy,historical);}
 Prepared prepare(EvidenceReadAccess subject,String context,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{return prepare(subject,context,evaluationId,candidate,policy,false);}
 Prepared prepareHistorical(EvidenceReadAccess subject,String context,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{return prepare(subject,context,evaluationId,candidate,policy,true);}
 private Prepared prepare(EvidenceReadAccess subject,String context,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,boolean historical)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  requiredCompletionAuthority();
  var before=subject.execute(a->ThrowingSupplier.of(()->{context(a,context);return ledger.require(a,evaluationId,candidate,policy,historical);}).get());
  var originals=new TreeMap<String,FormalEvaluationOriginalObjects.Verified>();
  for(var artifact:FormalEvaluationCoverage.artifacts(before.records())){var read=objects.read(subject,context,before.project(),artifact);if(originals.put(artifact.artifactId(),read)!=null)bad();}
  var prepared=new Prepared(this,subject.actorId(),context,evaluationId,candidate,policy,before.canonical(),originals,historical);
  subject.execute(a->ThrowingSupplier.of(()->{verify(a,evaluationId,candidate,policy,prepared,historical);return null;}).get());return prepared;
 }
 public VerifiedEvaluationExecutionEvidence require(EnterpriseAccess.Admission a,String evaluationId,
   PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,Prepared prepared)throws Exception {
  var actual=verify(a,evaluationId,candidate,policy,prepared,false);return new VerifiedEvaluationExecutionEvidence(this,a,prepared,actual);
 }
 public HistoricalEvaluationExecutionEvidence requireHistorical(EnterpriseAccess.Admission a,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,Prepared prepared)throws Exception{return new HistoricalEvaluationExecutionEvidence(this,a,prepared,verify(a,evaluationId,candidate,policy,prepared,true));}
 private FormalEvaluationExecutionLedger.Snapshot verify(EnterpriseAccess.Admission a,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,Prepared prepared,boolean historical)throws Exception {
  if(prepared==null||prepared.historical!=historical||prepared.owner!=this||!prepared.evaluation.equals(evaluationId)||!prepared.candidate.equals(candidate)||!prepared.policy.equals(policy)||!a.actorId().equals(prepared.actor))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  context(a,prepared.context);var actual=ledger.require(a,evaluationId,candidate,policy,historical);if(!actual.canonical().equals(prepared.canonical))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
  var expected=FormalEvaluationCoverage.artifacts(actual.records());if(expected.size()!=prepared.originals.size())bad();
  for(var artifact:expected){var original=prepared.originals.get(artifact.artifactId());if(original==null||!artifact.equals(original.reference()))bad();original.requireCurrent();}
  requiredCompletionAuthority().requireCurrent(a,actual.records(),prepared.originals);
  return actual;
 }
 /** No implicit I/O or forged original-object proof inside a publication transaction. */
 public VerifiedEvaluationExecutionEvidence require(EnterpriseAccess.Admission a,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
 public static final class Prepared {
  private final FormalEvaluationExecutionEvidence owner;private final String actor,context,evaluation,canonical;private final PermissionProtocol.Basis candidate,policy;
  private final Map<String,FormalEvaluationOriginalObjects.Verified> originals;private final boolean historical;
  private Prepared(FormalEvaluationExecutionEvidence owner,String actor,String context,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,String canonical,Map<String,FormalEvaluationOriginalObjects.Verified> originals,boolean historical){this.historical=historical;this.owner=owner;this.actor=actor;this.context=context;this.evaluation=evaluation;this.candidate=candidate;this.policy=policy;this.canonical=canonical;this.originals=Map.copyOf(originals);}
 }
 public static final class VerifiedEvaluationExecutionEvidence {
  private final FormalEvaluationExecutionEvidence owner;private final EnterpriseAccess.Admission admission;private final Prepared prepared;private final FormalEvaluationExecutionLedger.Snapshot snapshot;
  private VerifiedEvaluationExecutionEvidence(FormalEvaluationExecutionEvidence owner,EnterpriseAccess.Admission a,Prepared prepared,FormalEvaluationExecutionLedger.Snapshot snapshot){this.owner=owner;admission=a;this.prepared=prepared;this.snapshot=snapshot;}
  public List<FormalEvaluationExecutionProtocol.Record> records(){return snapshot.records();}
  public List<String> dependencyVersions(){var ids=new TreeSet<String>(prepared.originals.keySet());for(var original:prepared.originals.values())ids.addAll(original.dependencyVersions());ids.add(prepared.evaluation);ids.add(prepared.candidate.version_id());ids.add(prepared.policy.version_id());for(var r:snapshot.records()){ids.add(r.node().cell().input().version_id());ids.add(r.node().cell().expected().version_id());ids.add(r.node().role().version_id());if(r.node().model()!=null){ids.add(r.node().model().version_id());ids.add(r.node().tokenizer().version_id());}}return List.copyOf(ids);}
  public void requireProfessionalOutput(EvaluationProtocol.Cell cell,PermissionProtocol.Basis output)throws Exception{requireUnchanged();owner.requiredCompletionAuthority().requireProfessionalOutput(admission,snapshot.records(),prepared.originals,cell,output);}
  public JsonNode authorizations(){return snapshot.authorizations().deepCopy();}
  public List<String> originalReadReceiptIds(){var ids=new ArrayList<String>();for(var original:prepared.originals.values())ids.addAll(original.receiptIds());return List.copyOf(ids);}
  public void requireConsumer(EnterpriseAccess.Admission a,String evaluationId,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{if(a!=admission||!prepared.evaluation.equals(evaluationId)||!prepared.candidate.equals(candidate)||!prepared.policy.equals(policy))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws Exception{var fresh=owner.require(admission,prepared.evaluation,prepared.candidate,prepared.policy,prepared);if(!snapshot.canonical().equals(fresh.snapshot.canonical()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 public static final class HistoricalEvaluationExecutionEvidence {
  private final FormalEvaluationExecutionEvidence owner;private final EnterpriseAccess.Admission admission;private final Prepared prepared;private final FormalEvaluationExecutionLedger.Snapshot snapshot;
  private HistoricalEvaluationExecutionEvidence(FormalEvaluationExecutionEvidence owner,EnterpriseAccess.Admission admission,Prepared prepared,FormalEvaluationExecutionLedger.Snapshot snapshot){this.owner=owner;this.admission=admission;this.prepared=prepared;this.snapshot=snapshot;}
  public List<FormalEvaluationExecutionProtocol.Record> records(){return snapshot.records();}
  public List<String> dependencyVersions(){var ids=new TreeSet<String>(prepared.originals.keySet());for(var original:prepared.originals.values())ids.addAll(original.dependencyVersions());ids.add(prepared.evaluation);ids.add(prepared.candidate.version_id());ids.add(prepared.policy.version_id());for(var r:snapshot.records()){ids.add(r.node().cell().input().version_id());ids.add(r.node().cell().expected().version_id());ids.add(r.node().role().version_id());if(r.node().model()!=null){ids.add(r.node().model().version_id());ids.add(r.node().tokenizer().version_id());}}return List.copyOf(ids);}
  public void requireProfessionalOutput(EvaluationProtocol.Cell cell,PermissionProtocol.Basis output)throws Exception{requireUnchanged();owner.requiredCompletionAuthority().requireProfessionalOutput(admission,snapshot.records(),prepared.originals,cell,output);}public JsonNode authorizations(){return snapshot.authorizations().deepCopy();}
  public void requireConsumer(EnterpriseAccess.Admission a,String evaluation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy)throws Exception{if(a!=admission||!prepared.evaluation.equals(evaluation)||!prepared.candidate.equals(candidate)||!prepared.policy.equals(policy))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws Exception{var fresh=owner.verify(admission,prepared.evaluation,prepared.candidate,prepared.policy,prepared,true);if(!snapshot.canonical().equals(fresh.canonical()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 private FormalEvaluationProfessionalCompletions requiredCompletionAuthority(){var value=completions.getIfAvailable();if(value==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return value;}
 private static void context(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
