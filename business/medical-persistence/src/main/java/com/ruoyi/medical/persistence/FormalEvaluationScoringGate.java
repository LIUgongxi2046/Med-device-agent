package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Admission for the existing human scoring API, not a score generator or an acceptance verdict.
 * Original professional objects are read before entering the score transaction. Qualification and
 * execution are rechecked in that transaction; simulation never acquires this formal permit. */
public final class FormalEvaluationScoringGate {
 private final EnterpriseAccess access;private final EvaluationService evaluations;private final EvaluationSchema schema;
 private final FormalAgentCandidateAuthority candidates;private final FormalEvaluationExecutionEvidence executions;
 private final ExpertQualificationAuthority experts;private final JdbcTemplate db;
 public FormalEvaluationScoringGate(EnterpriseAccess access,EvaluationService evaluations,EvaluationSchema schema,FormalAgentCandidateAuthority candidates,FormalEvaluationExecutionEvidence executions,ExpertQualificationAuthority experts,DataSource data){this.access=access;this.evaluations=evaluations;this.schema=schema;this.candidates=candidates;this.executions=executions;this.experts=experts;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public static final class Prepared {
  private final FormalEvaluationScoringGate owner;private final String actor,context,evaluation;private final EvaluationProtocol.Append observation;private final PermissionProtocol.Basis candidate,policy,qualification;private final FormalEvaluationExecutionEvidence.Prepared execution;
  private Prepared(FormalEvaluationScoringGate owner,String actor,String context,String evaluation,EvaluationProtocol.Append observation,PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,PermissionProtocol.Basis qualification,FormalEvaluationExecutionEvidence.Prepared execution){this.owner=owner;this.actor=actor;this.context=context;this.evaluation=evaluation;this.observation=observation;this.candidate=candidate;this.policy=policy;this.qualification=qualification;this.execution=execution;}
 }
 private record Inputs(PermissionProtocol.Basis candidate,PermissionProtocol.Basis policy,PermissionProtocol.Basis qualification){}
 public Prepared prepare(ApplicationSessionIdentity.Verified actor,String context,String evaluation,byte[] bytes)throws Exception{
  var observation=schema.append(bytes);
  var input=access.execute(actor,a->ThrowingSupplier.of(()->{
   current(a,context);var d=evaluations.scoringDefinition(a,evaluation);if(!"PRODUCTION".equals(d.request().environment()))return null;
   human(a);
   if(!schema.hash(d).equals(observation.definition_sha256()))bad();
   var ref=d.request().candidates().stream().filter(c->c.version_id().equals(observation.cell().agent_version_id())).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
   var candidate=candidates.require(a,ref,d.request().project_id(),"EVALUATION_RESULTS");if(candidate.createdBy().equals(a.actorId()))deny();
   var qualifications=db.query("SELECT q.qualification_id,q.content_sha256 FROM expert_qualification q JOIN expert_qualification_state s ON s.tenant_id=q.tenant_id AND s.qualification_id=q.qualification_id WHERE q.tenant_id=? AND q.expert_id=? AND (q.project_id=? OR q.project_id IS NULL) AND q.purpose='EVALUATION_RESULTS' AND s.status='APPROVED' AND q.valid_from<=UTC_TIMESTAMP(6) AND q.valid_to>UTC_TIMESTAMP(6) AND JSON_CONTAINS(q.dimensions_json,JSON_QUOTE(?)) ORDER BY q.qualification_id LIMIT 2",(r,n)->new PermissionProtocol.Basis(r.getString(1),r.getString(2)),a.context().tenant_id(),a.actorId(),d.request().project_id(),observation.cell().dimension());
   // Ambiguous credentials must be resolved by governance, never silently selected.
   if(qualifications.size()!=1)throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
   qualified(a,qualifications.get(0),d.request().project_id(),observation.cell().dimension());candidate.requireUnchanged();return new Inputs(ref,candidate.executionPolicy(),qualifications.get(0));
  }).get());
  if(input==null)return null;
  var prepared=executions.prepare(actor,context,evaluation,input.candidate(),input.policy());
  return new Prepared(this,actor.user().user_id(),context,evaluation,observation,input.candidate(),input.policy(),input.qualification(),prepared);
 }
 void require(EnterpriseAccess.Admission a,String evaluation,EvaluationProtocol.Append observation,Prepared prepared)throws Exception{
  if(prepared==null||prepared.owner!=this||!a.actorId().equals(prepared.actor)||!evaluation.equals(prepared.evaluation)||!observation.equals(prepared.observation))deny();current(a,prepared.context);
  var d=evaluations.scoringDefinition(a,evaluation);qualified(a,prepared.qualification,d.request().project_id(),observation.cell().dimension());
  var proof=executions.require(a,evaluation,prepared.candidate,prepared.policy,prepared.execution);proof.requireConsumer(a,evaluation,prepared.candidate,prepared.policy);
  var matches=proof.records().stream().filter(r->r.node().cell().sampleId().equals(observation.cell().sample_id())&&r.node().cell().repeatIndex()==observation.cell().repeat_index()&&r.node().cell().candidate().version_id().equals(observation.cell().agent_version_id())).toList();
  if(matches.isEmpty()||matches.stream().anyMatch(r->!r.node().cell().input().equals(observation.input_ref())))bad();
  if("SCORED".equals(observation.status())){
   if(observation.output_ref()==null)bad();proof.requireProfessionalOutput(observation.cell(),observation.output_ref());
   var allowed=new HashMap<String,String>();for(var r:matches){allowed.put(r.node().cell().expected().version_id(),r.node().cell().expected().content_sha256());allowed.put(r.node().cell().input().version_id(),r.node().cell().input().content_sha256());}
   for(var artifact:FormalEvaluationCoverage.artifacts(matches))allowed.put(artifact.artifactId(),artifact.sha256());
   if(observation.evidence_refs().isEmpty())bad();for(var ref:observation.evidence_refs())if(!ref.content_sha256().equals(allowed.get(ref.version_id())))bad();
  }
 }
 public static final class FinalPrepared {
  private final FormalEvaluationScoringGate owner;private final String actor,context,evaluation,definition;private final Map<PermissionProtocol.Basis,PermissionProtocol.Basis> policies;private final Map<PermissionProtocol.Basis,FormalEvaluationExecutionEvidence.Prepared> proofs;
  private FinalPrepared(FormalEvaluationScoringGate o,String actor,String context,String evaluation,String definition,Map<PermissionProtocol.Basis,PermissionProtocol.Basis> policies,Map<PermissionProtocol.Basis,FormalEvaluationExecutionEvidence.Prepared> proofs){owner=o;this.actor=actor;this.context=context;this.evaluation=evaluation;this.definition=definition;this.policies=Map.copyOf(policies);this.proofs=Map.copyOf(proofs);}
 }
 public FinalPrepared prepareFinalization(ApplicationSessionIdentity.Verified actor,String context,String evaluation,byte[] bytes)throws Exception {
  var request=schema.finalizeRequest(bytes);var policies=access.execute(actor,a->ThrowingSupplier.of(()->{
   current(a,context);var d=evaluations.scoringDefinition(a,evaluation);if(!"PRODUCTION".equals(d.request().environment()))return null;if(!schema.hash(d).equals(request.definition_sha256()))bad();human(a);
   var values=new LinkedHashMap<PermissionProtocol.Basis,PermissionProtocol.Basis>();for(var ref:d.request().candidates()){var c=candidates.require(a,ref,d.request().project_id(),"EVALUATION_RESULTS");if(c.createdBy().equals(a.actorId()))deny();values.put(ref,c.executionPolicy());c.requireUnchanged();}return values;
  }).get());if(policies==null)return null;
  var proofs=new LinkedHashMap<PermissionProtocol.Basis,FormalEvaluationExecutionEvidence.Prepared>();for(var e:policies.entrySet())proofs.put(e.getKey(),executions.prepare(actor,context,evaluation,e.getKey(),e.getValue()));
  return new FinalPrepared(this,actor.user().user_id(),context,evaluation,request.definition_sha256(),policies,proofs);
 }
 void requireFinalization(EnterpriseAccess.Admission a,String evaluation,EvaluationProtocol.Finalize request,FinalPrepared p)throws Exception {
  if(p==null||p.owner!=this||!p.actor.equals(a.actorId())||!p.evaluation.equals(evaluation)||!p.definition.equals(request.definition_sha256()))deny();current(a,p.context);human(a);var definition=evaluations.scoringDefinition(a,evaluation);if(!schema.hash(definition).equals(p.definition)||!new HashSet<>(definition.request().candidates()).equals(p.proofs.keySet()))bad();
  for(var ref:definition.request().candidates()){var c=candidates.require(a,ref,definition.request().project_id(),"EVALUATION_RESULTS");if(c.createdBy().equals(a.actorId()))deny();if(!c.executionPolicy().equals(p.policies.get(ref)))bad();executions.require(a,evaluation,ref,c.executionPolicy(),p.proofs.get(ref)).requireConsumer(a,evaluation,ref,c.executionPolicy());c.requireUnchanged();}
 }
 private void qualified(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String project,String dimension)throws Exception{
  var q=experts.load(a,basis,true);var request=q.definition().request();var now=db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toLocalDateTime();
  if(!"APPROVED".equals(q.head().status())||!a.actorId().equals(request.expert_id())||!"EVALUATION_RESULTS".equals(request.purpose())||!request.dimensions().contains(dimension)||request.project_id()!=null&&!request.project_id().equals(project)||now.isBefore(q.row().validFrom())||!now.isBefore(q.row().validTo()))deny();
 }
 private static void human(EnterpriseAccess.Admission a){if(a.formalWorker()||a.controlWorker()||a.simulationWorker()||a.evaluationWorker()||a.documentReviewWorker()||a.planDraftWorker()||a.recoveryWorker())deny();}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
