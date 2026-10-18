package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.ExpertQualificationStore.*;
/** Human submission, independent decision and exact original score binding. All writes share original transaction. */
@Component @ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true")
public final class ExpertQualificationService {
 private final EnterpriseAccess access;private final ExpertQualificationMapper mapper;private final ExpertQualificationSchema schema;private final ExpertQualificationStore store;
 private final ExpertQualificationAuthority authority;private final ExpertQualificationEvidenceReader evidence;private final ResourceReadAuthority resources;
 public ExpertQualificationService(EnterpriseAccess access,ExpertQualificationMapper mapper,ExpertQualificationSchema schema,ExpertQualificationStore store,ExpertQualificationAuthority authority,ExpertQualificationEvidenceReader evidence,ResourceReadAuthority resources){this.access=access;this.mapper=mapper;this.schema=schema;this.store=store;this.authority=authority;this.evidence=evidence;this.resources=resources;}
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode input)throws Exception{
  schema.validate("Author",input);Author request=schema.bind(input,Author.class);ExpertQualificationSchema.semantics(request);headers(context,key);
  access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);resources.require(a,scope(a.context().tenant_id(),request.project_id()),"agent.configure","AGENT_PUBLICATION");return null;}).get());
  var originals=evidence.read(subject,context,request);
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   context(a,context);var v=values(a);same(a.actorId(),one(mapper.lockActor(v)));var grant=resources.require(a,scope(a.context().tenant_id(),request.project_id()),"agent.configure","AGENT_PUBLICATION");originals.requireCurrent(a);
   var old=store.prior(a,"AUTHOR",key,input);if(old!=null){authority.load(a,new PermissionProtocol.Basis(old.qualificationId(),old.contentHash()),false);return store.parse(old.receiptJson());}
   var now=Objects.requireNonNull(mapper.databaseTime(v));v.put("expertId",request.expert_id());var member=available(mapper.expertMember(v));same(a.context().tenant_id(),member.tenantId());same(request.expert_id(),member.userId());if(!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   if(!ExpertQualificationAuthority.utc(request.valid_to()).isAfter(now))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
   String id=UUID.randomUUID().toString();var definition=new Definition("1.0.0",a.context().tenant_id(),id,request,a.actorId(),time(now));var auth=auth(a,grant,originals.witness());
   var journal=store.save(a,request.project_id(),id,id,"AUTHOR",key,context,subject.sessionId(),input,schema.tree(definition),auth,originals.classification(),now);
   var row=new Qualification(a.context().tenant_id(),request.project_id(),id,journal.contentHash(),request.expert_id(),request.domain(),request.purpose(),schema.canonical(schema.tree(request.dimensions())),ExpertQualificationAuthority.utc(request.valid_from()),ExpertQualificationAuthority.utc(request.valid_to()),"1.0.0",schema.manifest(),a.actorId(),now,originals.classification());
   v.put("qualification",row);count(mapper.insertQualification(v));v.put("head",new Head(a.context().tenant_id(),request.project_id(),id,"DRAFT",BigInteger.ONE,null,null,"1.0.0",schema.manifest(),a.actorId(),now,originals.classification(),now));count(mapper.insertHead(v));v.put("qualificationId",id);same(row,one(mapper.qualification(v)));originals.requireCurrent(a);return store.parse(journal.receiptJson());
  }).get());
 }
 public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode input)throws Exception{
  schema.validate("Decide",input);var request=schema.bind(input,Decide.class);headers(context,key);PermissionSchema.revision(request.expected_revision());if(request.reason().isBlank())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var preliminary=access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var loaded=authority.load(a,request.qualification(),false,request.decision().equals("APPROVE"));resources.require(a,scope(a.context().tenant_id(),loaded.row().projectId()),"agent.publish","AGENT_PUBLICATION");return loaded;}).get());
  // Revocation and rejection must remain possible when evidence has become unreadable; only approval performs a new GET.
  var originals=request.decision().equals("APPROVE")?evidence.read(subject,context,preliminary.definition().request()):null;
  return access.execute(subject,a->ThrowingSupplier.of(()->{
   context(a,context);var v=values(a);same(a.actorId(),one(mapper.lockActor(v)));var loaded=authority.load(a,request.qualification(),true,request.decision().equals("APPROVE"));var grant=resources.require(a,scope(a.context().tenant_id(),loaded.row().projectId()),"agent.publish","AGENT_PUBLICATION");
   var prior=store.prior(a,request.decision(),key,input);if(prior!=null){same(loaded.row().qualificationId(),prior.qualificationId());return store.parse(prior.receiptJson());}
   same(preliminary.author(),loaded.author());if(!loaded.head().revision().toString().equals(request.expected_revision()))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
   String expected=request.decision().equals("REVOKE")?"APPROVED":"DRAFT";if(!expected.equals(loaded.head().status()))throw new TenantAccessException(ErrorCode.ACTION_STALE);
   var now=Objects.requireNonNull(mapper.databaseTime(v));if(request.decision().equals("APPROVE")){
    if(a.actorId().equals(loaded.row().createdBy())||a.actorId().equals(loaded.row().expertId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);ExpertQualificationAuthority.requireReview(loaded.definition().request(),request);if(!now.isBefore(loaded.row().validTo()))throw new TenantAccessException(ErrorCode.ACTION_STALE);originals.requireCurrent(a);
   }else if(!request.reviewed_evidence().isEmpty()||request.originals_reviewed()||request.scope_confirmed())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
   var decision=new Decision("1.0.0",request.qualification(),loaded.head().decisionId(),request.expected_revision(),loaded.head().revision().add(BigInteger.ONE).toString(),request.decision(),request,a.actorId(),time(now));String id=UUID.randomUUID().toString();
   var journal=store.save(a,loaded.row().projectId(),id,loaded.row().qualificationId(),request.decision(),key,context,subject.sessionId(),input,schema.tree(decision),auth(a,grant,originals==null?store.parse(loaded.author().authorizationsJson()).path("evidence"):originals.witness()),loaded.row().classification(),now);
   v.put("qualificationId",loaded.row().qualificationId());v.put("expected",request.expected_revision());v.put("previousDecision",loaded.head().decisionId());v.put("previousStatus",loaded.head().status());v.put("decisionId",id);v.put("decisionKind",request.decision());v.put("status",switch(request.decision()){case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";default->"REVOKED";});v.put("now",now);count(mapper.advanceHead(v));authority.load(a,request.qualification(),false,request.decision().equals("APPROVE"));if(originals!=null)originals.requireCurrent(a);return store.parse(journal.receiptJson());
  }).get());
 }
 public JsonNode bindScore(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode input)throws Exception{
  schema.validate("BindScore",input);headers(context,key);return access.execute(subject,a->ThrowingSupplier.of(()->bindScoreInAdmission(a,subject,context,key,input,null,null,null)).get());
 }
 /** Only the genuine private formal assembler can supply the alternate fully verified evaluation snapshot. */
 public JsonNode bindScoreFormal(EnterpriseAccess.Admission a,ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode input,FormalAgentAcceptanceEvidence.ScoreBindingPermit permit)throws Exception{
  if(permit==null||!a.actorId().equals(subject.user().user_id())||!a.subject().sessionId().equals(subject.sessionId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var snapshot=permit.requireConsumer(a,input);return bindScoreInAdmission(a,subject,context,key,input,snapshot,permit.witness(),permit.classification());
 }
 private JsonNode bindScoreInAdmission(EnterpriseAccess.Admission a,ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode input,EvaluationService.AcceptanceSnapshot supplied,JsonNode formalWitness,String formalClassification)throws Exception{
  schema.validate("BindScore",input);var request=schema.bind(input,BindScore.class);headers(context,key);if(request.reason().isBlank())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
   context(a,context);var v=values(a);same(a.actorId(),one(mapper.lockActor(v)));authority.load(a,request.qualification(),true);var current=authority.scoreInputs(a,request,supplied);if(!a.actorId().equals(current.score().scorer_id()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var scope=new PermissionProtocol.Scope(a.context().tenant_id(),current.evaluation().row().projectId(),"EVALUATION",request.evaluation_id());var grant=resources.require(a,scope,"evaluation.score","EVALUATION_RESULTS");
   var prior=store.prior(a,"SCORE_BIND",key,input);if(prior!=null){requireFormalPreparation(prior,formalWitness);authority.requireQualifiedScore(a,new PermissionProtocol.Basis(prior.recordId(),prior.contentHash()),supplied);return store.parse(prior.receiptJson());}
   v.put("scoreId",request.score_id());v.put("qualificationId",request.qualification().version_id());if(!mapper.bindingForScore(v).isEmpty())throw new TenantAccessException(ErrorCode.ACTION_STALE);
   var now=Objects.requireNonNull(mapper.databaseTime(v));String id=UUID.randomUUID().toString();var bound=new BoundScore("1.0.0",request,new PermissionProtocol.Basis(current.approval().recordId(),current.approval().contentHash()),current.score(),a.actorId(),time(now));
   var auth=auth(a,grant,current.qualification().evidence().witness());auth.set("original_score_authority",schema.tree(current.originalAuthority()));if(formalWitness!=null)auth.set("formal_preparation",formalWitness.deepCopy());String classification=ExpertQualificationEvidenceReader.maxClassification(ExpertQualificationEvidenceReader.maxClassification(current.qualification().row().classification(),current.evaluation().row().classification()),formalClassification==null?"PUBLIC":formalClassification);
   var row=store.save(a,current.evaluation().row().projectId(),id,current.qualification().row().qualificationId(),"SCORE_BIND",key,context,subject.sessionId(),input,schema.tree(bound),auth,classification,now);v.put("binding",authority.scoreRow(row,bound,current));count(mapper.insertScoreBinding(v));authority.requireQualifiedScore(a,new PermissionProtocol.Basis(id,row.contentHash()),supplied);current.evaluation().requireUnchanged();return store.parse(row.receiptJson());
 }
 private void requireFormalPreparation(Journal prior,JsonNode expected)throws Exception{
  var stored=store.parse(prior.authorizationsJson()).get("formal_preparation");if(expected==null){if(stored!=null)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return;}if(stored==null||!stored.isObject())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  for(String field:List.of("schema_version","request","request_body_sha256","statistics_policy","definition_sha256"))if(!(field.equals("request")?schema.canonical(stored.required(field)).equals(schema.canonical(expected.required(field))):stored.required(field).equals(expected.required(field))))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String qualificationId)throws Exception{
  WireValues.uuid(qualificationId);PermissionSchema.revision(context);var loaded=access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("qualificationId",qualificationId);var row=available(mapper.qualification(v));return authority.load(a,new PermissionProtocol.Basis(row.qualificationId(),row.contentHash()),false);}).get());
  var originals=evidence.read(subject,context,loaded.definition().request());return access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var current=authority.load(a,new PermissionProtocol.Basis(loaded.row().qualificationId(),loaded.row().contentHash()),false);same(loaded.head(),current.head());originals.requireCurrent(a);return view(current);}).get());
 }
 public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String operationId)throws Exception{
  WireValues.uuid(operationId);PermissionSchema.revision(context);return access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var v=values(a);v.put("operationId",operationId);v.put("creatorId",a.actorId());var rows=mapper.journalByOperation(v);var row=available(rows);same(a.actorId(),row.createdBy());same(operationId,row.operationId());store.verify(a,row);
   if(row.kind().equals("SCORE_BIND"))authority.requireQualifiedScore(a,new PermissionProtocol.Basis(row.recordId(),row.contentHash()));else{v.put("qualificationId",row.qualificationId());var q=one(mapper.qualification(v));authority.load(a,new PermissionProtocol.Basis(q.qualificationId(),q.contentHash()),false);}
   var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();result.set("receipt",store.parse(row.receiptJson()));result.set("request",store.parse(row.requestJson()));result.set("content",store.parse(row.contentJson()));return schema.validate("Recovery",result);
  }).get());
 }
 private JsonNode view(ExpertQualificationAuthority.Loaded loaded)throws Exception{var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();n.set("qualification",schema.tree(new PermissionProtocol.Basis(loaded.row().qualificationId(),loaded.row().contentHash())));n.set("definition",schema.tree(loaded.definition()));n.put("status",loaded.head().status());n.put("revision",loaded.head().revision().toString());var decisions=n.putArray("decisions");for(var d:loaded.decisions())decisions.add(store.parse(d.contentJson()));n.put("creates_professional_licence",false);n.put("proves_formal_execution",false);return schema.validate("Read",n);}
 private com.fasterxml.jackson.databind.node.ObjectNode auth(EnterpriseAccess.Admission a,PermissionProtocol.GrantRow grant,JsonNode evidence){var n=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("identity_mode",a.bindingView().mode());n.set("governance",schema.tree(grant));n.set("evidence",evidence);return n;}
 private static void headers(String context,String key){PermissionSchema.revision(context);ExpertQualificationSchema.key(key);}private static void context(EnterpriseAccess.Admission a,String context){if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
}
