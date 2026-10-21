package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.io.IOException;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static com.ruoyi.medical.persistence.ExpertQualificationStore.*;
/** Human-decision and exact original-score proof. It never proves formal execution or production eligibility. */
@Component @ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true")
public final class ExpertQualificationAuthority {
 private final ExpertQualificationMapper mapper;private final ExpertQualificationStore store;private final ExpertQualificationSchema schema;
 private final ResourceReadAuthority resources;private final ExpertQualificationEvidenceReader evidence;private final EvaluationService evaluations;private final EvaluationSchema evaluation;private final ExpertScoreAuthorityRecorder scoreAuthority;
 public ExpertQualificationAuthority(ExpertQualificationMapper mapper,ExpertQualificationStore store,ExpertQualificationSchema schema,ResourceReadAuthority resources,ExpertQualificationEvidenceReader evidence,EvaluationService evaluations,EvaluationSchema evaluation,ExpertScoreAuthorityRecorder scoreAuthority){this.mapper=mapper;this.store=store;this.schema=schema;this.resources=resources;this.evidence=evidence;this.evaluations=evaluations;this.evaluation=evaluation;this.scoreAuthority=scoreAuthority;}
 record Loaded(Qualification row,Definition definition,Head head,Journal author,List<Journal> decisions,ExpertQualificationEvidenceReader.Current evidence){Loaded{decisions=List.copyOf(decisions);}}
 Loaded load(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,boolean lock)throws IOException{return load(a,basis,lock,true);}
 Loaded load(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,boolean lock,boolean readEvidence)throws IOException{
  WireValues.uuid(basis.version_id());WireValues.hash(basis.content_sha256());var v=values(a);v.put("qualificationId",basis.version_id());var row=available(mapper.qualification(v));
  resources.require(a,scope(a.context().tenant_id(),row.projectId()),"configuration.read","AGENT_PUBLICATION");same(a.context().tenant_id(),row.tenantId());same(basis.content_sha256(),row.contentHash());
  var author=store.read(a,basis.version_id());same("AUTHOR",author.kind());same(row.projectId(),author.projectId());same(row.contentHash(),author.contentHash());var definition=schema.bind(store.parse(author.contentJson()),Definition.class);var request=definition.request();
  same(row.expertId(),request.expert_id());same(row.domain(),request.domain());same(row.purpose(),request.purpose());same(row.dimensionsJson(),schema.canonical(schema.tree(request.dimensions())));same(row.validFrom(),utc(request.valid_from()));same(row.validTo(),utc(request.valid_to()));same(row.createdBy(),author.createdBy());same(row.createdAt(),author.createdAt());same(row.classification(),author.classification());same("1.0.0",row.schemaVersion());same(schema.manifest(),row.schemaManifest());
  var head=available(lock?mapper.lockHead(v):mapper.head(v));same(row.tenantId(),head.tenantId());same(row.projectId(),head.projectId());same(row.qualificationId(),head.qualificationId());same(row.createdBy(),head.createdBy());same(row.createdAt(),head.createdAt());same(row.classification(),head.classification());same("1.0.0",head.schemaVersion());same(schema.manifest(),head.schemaManifest());
  var decisions=new ArrayList<>(mapper.decisions(v));if(decisions.size()>2)bad();for(var decision:decisions)store.verify(a,decision);
  decisions.sort(Comparator.comparing(r->new BigInteger(uncheckedExpected(r))));String status="DRAFT",previous=null;BigInteger revision=BigInteger.ONE;LocalDateTime changed=row.createdAt();String latestKind=null;
  for(var journal:decisions){same(row.projectId(),journal.projectId());same(row.qualificationId(),journal.qualificationId());var decision=schema.bind(store.parse(journal.contentJson()),Decision.class);same(basis,decision.qualification());same(previous,decision.previous_decision());same(revision.toString(),decision.expected_revision());same(revision.add(BigInteger.ONE).toString(),decision.resulting_revision());if(journal.createdAt().isBefore(changed))bad();
   String kind=decision.decision();if(status.equals("DRAFT")&&!Set.of("APPROVE","REJECT").contains(kind)||status.equals("APPROVED")&&!kind.equals("REVOKE")||Set.of("REJECTED","REVOKED").contains(status))bad();
   if(kind.equals("APPROVE")){if(journal.createdBy().equals(row.createdBy())||journal.createdBy().equals(row.expertId()))denied();requireReview(request,decision.request());if(!journal.createdAt().isBefore(row.validTo()))bad();}
   status=switch(kind){case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";case "REVOKE"->"REVOKED";default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);};revision=revision.add(BigInteger.ONE);previous=journal.recordId();latestKind=kind;changed=journal.createdAt();
  }
  same(status,head.status());same(revision,head.revision());same(previous,head.decisionId());same(latestKind,head.decisionKind());same(changed,head.updatedAt());
  var current=readEvidence?evidence.current(a,request):null;if(current!=null)same(row.classification(),current.classification());
  // Immutable object identity and lineage stay fixed; current grants may rotate, and are independently rechecked.
  if(current!=null){requireEvidenceIdentity(store.parse(author.authorizationsJson()).path("evidence"),current.witness());for(var decision:decisions)if(decision.kind().equals("APPROVE"))requireEvidenceIdentity(store.parse(decision.authorizationsJson()).path("evidence"),current.witness());}
  return new Loaded(row,definition,head,author,decisions,current);
 }
 private String uncheckedExpected(Journal r){try{return store.parse(r.contentJson()).path("expected_revision").asText();}catch(IOException e){throw new IllegalStateException(e);}}
 static void requireReview(Author author,Decide decision){if(!Boolean.TRUE.equals(decision.originals_reviewed())||!Boolean.TRUE.equals(decision.scope_confirmed())||!author.evidence().stream().map(Evidence::basis).toList().equals(decision.reviewed_evidence()))bad();}
 private void requireEvidenceIdentity(JsonNode old,JsonNode current)throws IOException{
  if(!old.isObject()||old.size()!=current.size())bad();var names=old.fieldNames();while(names.hasNext()){String id=names.next();var before=old.path(id);var after=current.path(id);if(!after.isObject())bad();
   for(String key:List.of("original","artifact","binding"))if(!Objects.equals(before.get(key),after.get(key)))bad();
   if(before.has("source")){same(before.path("source").path("source"),after.path("source").path("source"));same(before.path("source").path("rights"),after.path("source").path("rights"));}
   var beforeClosure=before.path("closure");var afterClosure=after.path("closure");if(!beforeClosure.isObject()||beforeClosure.size()!=afterClosure.size())bad();var versions=beforeClosure.fieldNames();while(versions.hasNext()){String version=versions.next();same(beforeClosure.path(version).path("owner"),afterClosure.path(version).path("owner"));same(beforeClosure.path(version).path("purpose"),afterClosure.path(version).path("purpose"));var bs=beforeClosure.path(version).path("source");var cs=afterClosure.path(version).path("source");same(bs.path("source"),cs.path("source"));same(bs.path("rights"),cs.path("rights"));}
  }
 }
 record ScoreInputs(EvaluationService.AcceptanceSnapshot evaluation,EvaluationProtocol.Score score,Loaded qualification,Journal approval,ScoreAuthorityWitness originalAuthority){}
 ScoreInputs scoreInputs(EnterpriseAccess.Admission a,BindScore request)throws IOException{return scoreInputs(a,request,null);}
 ScoreInputs scoreInputs(EnterpriseAccess.Admission a,BindScore request,EvaluationService.AcceptanceSnapshot supplied)throws IOException{
  var snapshot=supplied==null?evaluations.requireAcceptance(a,request.evaluation_id()):supplied;snapshot.requireConsumer(a,request.evaluation_id());same(request.definition_sha256(),snapshot.row().contentHash());
  var matched=snapshot.scores().stream().filter(s->s.score_id().equals(request.score_id())).toList();if(matched.size()!=1)bad();var score=matched.get(0);same(request.score_sha256(),evaluation.hash(score));same(request.definition_sha256(),score.definition_sha256());
  if(!snapshot.result().effective_scores().contains(score)||!"SCORED".equals(score.observation().status()))throw new TenantAccessException(ErrorCode.ACTION_STALE);
  var sample=snapshot.definition().request().samples().stream().filter(s->s.sample_id().equals(score.observation().cell().sample_id())).toList();if(sample.size()!=1||!"REAL".equals(sample.get(0).evidence_class())||!"ACCEPTANCE".equals(sample.get(0).split()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var loaded=load(a,request.qualification(),true);var definition=loaded.definition().request();if(definition.project_id()!=null&&!definition.project_id().equals(snapshot.row().projectId()))denied();same(request.domain(),definition.domain());same("EVALUATION_RESULTS",definition.purpose());same(score.scorer_id(),definition.expert_id());if(!definition.dimensions().contains(score.observation().cell().dimension()))denied();
  if(!"APPROVED".equals(loaded.head().status()))throw new TenantAccessException(ErrorCode.ACTION_STALE);var approval=loaded.decisions().stream().filter(r->r.kind().equals("APPROVE")).toList();if(approval.size()!=1)bad();var when=utc(score.created_at());if(approval.get(0).createdAt().isAfter(when)||when.isBefore(loaded.row().validFrom())||!when.isBefore(loaded.row().validTo()))denied();
  var v=values(a);var now=mapper.databaseTime(v);if(now==null||now.isBefore(loaded.row().validFrom())||!now.isBefore(loaded.row().validTo()))denied();v.put("expertId",score.scorer_id());var currentExpert=available(mapper.expertMember(v));same(a.context().tenant_id(),currentExpert.tenantId());same(score.scorer_id(),currentExpert.userId());if(!"ACTIVE".equals(currentExpert.status())||currentExpert.validFrom().isAfter(now)||currentExpert.validTo()!=null&&!currentExpert.validTo().isAfter(now))denied();v.put("scoreId",score.score_id());var witness=available(mapper.scoreAuthority(v));same(snapshot.row().projectId(),witness.projectId());scoreAuthority.verify(a,witness,score);
  return new ScoreInputs(snapshot,score,loaded,approval.get(0),witness);
 }
 public static final class VerifiedExpertQualification{
  private final EnterpriseAccess.Admission admission;private final String targetProject;private final ScoreInputs inputs;private final Recheck recheck;
  private VerifiedExpertQualification(EnterpriseAccess.Admission a,String project,ScoreInputs inputs,Recheck recheck){admission=a;targetProject=project;this.inputs=inputs;this.recheck=recheck;}
  public PermissionProtocol.Basis qualification(){return new PermissionProtocol.Basis(inputs.qualification().row().qualificationId(),inputs.qualification().row().contentHash());}
  public PermissionProtocol.Basis approval(){return new PermissionProtocol.Basis(inputs.approval().recordId(),inputs.approval().contentHash());}
  public String expertId(){return inputs.score().scorer_id();}public String domain(){return inputs.qualification().row().domain();}public String projectId(){return inputs.qualification().row().projectId();}public String purpose(){return inputs.qualification().row().purpose();}
  public Instant asOf(){return Instant.parse(inputs.score().created_at());}public Instant approvedAt(){return inputs.approval().createdAt().toInstant(ZoneOffset.UTC);}public Instant validFrom(){return inputs.qualification().row().validFrom().toInstant(ZoneOffset.UTC);}public Instant validTo(){return inputs.qualification().row().validTo().toInstant(ZoneOffset.UTC);}
  public JsonNode authorizations(){return inputs.qualification().evidence().witness();}
  /** Typed original credential roots from the verified qualification, not witness JSON. */
  public List<String> dependencyVersions(){var ids=new TreeSet<String>(inputs.qualification().evidence().requiredVersionIds());ids.add(qualification().version_id());ids.add(approval().version_id());for(var evidence:inputs.qualification().definition().request().evidence())ids.add(evidence.basis().version_id());return List.copyOf(ids);}
  public void requireConsumer(EnterpriseAccess.Admission a,String expert,String domain,String project,String purpose)throws IOException{if(a!=admission||!expertId().equals(expert)||!domain().equals(domain)||!Objects.equals(targetProject,project)||!purpose().equals(purpose))denied();requireUnchanged();}
  public void requireUnchanged()throws IOException{recheck.require();}
 }
 @FunctionalInterface private interface Recheck{void require()throws IOException;}
 public static final class VerifiedQualifiedScore{
  private final EnterpriseAccess.Admission admission;private final PermissionProtocol.Basis binding;private final EvaluationProtocol.Score score;private final VerifiedExpertQualification qualification;private final Recheck recheck;
  private VerifiedQualifiedScore(EnterpriseAccess.Admission admission,PermissionProtocol.Basis binding,EvaluationProtocol.Score score,VerifiedExpertQualification qualification,Recheck recheck){this.admission=admission;this.binding=binding;this.score=score;this.qualification=qualification;this.recheck=recheck;}
  public PermissionProtocol.Basis binding(){return binding;}public EvaluationProtocol.Score score(){return score;}public VerifiedExpertQualification qualification(){return qualification;}
  public void requireConsumer(EnterpriseAccess.Admission a,String evaluationId,String scoreId)throws IOException{if(a!=admission||!score.evaluation_id().equals(evaluationId)||!score.score_id().equals(scoreId))denied();requireUnchanged();}
  public void requireUnchanged()throws IOException{recheck.require();}
 }
 public VerifiedQualifiedScore requireQualifiedScore(EnterpriseAccess.Admission a,PermissionProtocol.Basis binding)throws IOException{return requireQualifiedScore(a,binding,null);}
 public VerifiedQualifiedScore requireQualifiedScore(EnterpriseAccess.Admission a,PermissionProtocol.Basis binding,EvaluationService.AcceptanceSnapshot supplied)throws IOException{
  var row=store.read(a,binding.version_id());same("SCORE_BIND",row.kind());same(binding.content_sha256(),row.contentHash());var content=schema.bind(store.parse(row.contentJson()),BoundScore.class);var inputs=scoreInputs(a,content.request(),supplied);same(inputs.score(),content.score());same(row.createdBy(),inputs.score().scorer_id());same(new PermissionProtocol.Basis(inputs.approval().recordId(),inputs.approval().contentHash()),content.approval());same(inputs.evaluation().row().projectId(),row.projectId());
  var v=values(a);v.put("bindingId",binding.version_id());var stored=one(mapper.scoreBinding(v));same(scoreRow(row,content,inputs),stored);String identity=identity(inputs,row);
  var originalScope=TenantScope.requireCurrent();Recheck recheck=()->{if(TenantScope.requireCurrent()!=originalScope)denied();var currentRow=store.read(a,row.recordId());same(row,currentRow);var now=scoreInputs(a,content.request(),supplied);same(identity,identity(now,currentRow));};
  var qualification=new VerifiedExpertQualification(a,inputs.evaluation().row().projectId(),inputs,recheck);return new VerifiedQualifiedScore(a,binding,inputs.score(),qualification,recheck);
 }
 ScoreBinding scoreRow(Journal row,BoundScore content,ScoreInputs inputs){var score=inputs.score();var cell=score.observation().cell();return new ScoreBinding(row.tenantId(),row.projectId(),row.recordId(),row.contentHash(),content.request().qualification().version_id(),content.request().qualification().content_sha256(),content.approval().version_id(),content.approval().content_sha256(),score.evaluation_id(),score.definition_sha256(),score.score_id(),content.request().score_sha256(),cell.sample_id(),cell.agent_version_id(),cell.dimension(),cell.repeat_index(),score.scorer_id(),utc(score.created_at()),"1.0.0",schema.manifest(),row.createdBy(),row.createdAt(),row.classification());}
 private String identity(ScoreInputs i,Journal row)throws IOException{var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();n.set("binding",schema.tree(row));n.set("qualification",schema.tree(i.qualification().row()));n.set("head",schema.tree(i.qualification().head()));n.set("approval",schema.tree(i.approval()));n.set("evidence",i.qualification().evidence().witness());n.set("score",schema.tree(i.score()));n.set("score_authority",schema.tree(i.originalAuthority()));n.put("result_hash",i.evaluation().row().resultHash());return schema.hash(n);}
 static LocalDateTime utc(String value){return LocalDateTime.ofInstant(Instant.parse(value),ZoneOffset.UTC);}static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
