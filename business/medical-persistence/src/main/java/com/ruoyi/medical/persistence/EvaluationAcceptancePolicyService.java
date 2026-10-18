package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** 084 human statistical policy. All byte reads precede the transaction; private proofs are rechecked within it. */
@Component public final class EvaluationAcceptancePolicyService {
 private final PermissionSchema permissionJson;private final EnterpriseAccess access;private final AgentPublicationJournal journal;private final AgentPublicationMapper mapper;private final FormalEvaluationPolicyMapper snapshots;
 private final AgentPublicationSchema json;private final FormalEvaluationPolicySchema schema;private final EvaluationAcceptanceEvidenceSchema evidence;
 private final FormalEvaluationPolicyInputs inputs;private final ResourceReadAuthority resources;private final IdentityDirectoryPolicy settings;
 public EvaluationAcceptancePolicyService(PermissionSchema permissionJson,EnterpriseAccess access,AgentPublicationJournal journal,AgentPublicationMapper mapper,FormalEvaluationPolicyMapper snapshots,AgentPublicationSchema json,
  FormalEvaluationPolicySchema schema,EvaluationAcceptanceEvidenceSchema evidence,FormalEvaluationPolicyInputs inputs,ResourceReadAuthority resources,IdentityDirectoryPolicy settings){this.permissionJson=permissionJson;this.access=access;this.journal=journal;this.mapper=mapper;this.snapshots=snapshots;this.json=json;this.schema=schema;this.evidence=evidence;this.inputs=inputs;this.resources=resources;this.settings=settings;}
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  request=schema.validate("Author",request).deepCopy();semantics(request.required("specification"));SimulationInputArtifactSchema.key(key);
  var fixed=request;access.execute(subject,a->checked(()->{current(a,context);authorize(a,project(fixed),"agent.configure");return null;}));
  var prepared=inputs.prepare(subject,context,request,true);
  return access.execute(subject,a->checked(()->{current(a,context);var grant=authorize(a,project(fixed),"agent.configure");prepared.requireConsumer(a,fixed,true);
   var prior=journal.prior(a,"POLICY_DRAFT",key,fixed);if(prior!=null){var original=load(a,prior.recordId(),false);same(prepared.snapshot(),original.snapshot());return json.read(prior.receiptJson());}
   String id=UUID.randomUUID().toString();var content=object().put("schema_version","1.0.0").put("status","DRAFT").put("policy_draft_id",id).put("policy_schema_sha256",schema.schemaSha256()).put("typed_input_sha256",json.hash(prepared.snapshot()));content.set("author",fixed);
   return persist(a,id,"POLICY_DRAFT",id,key,fixed,content,authorization(a,grant,prepared),prepared.snapshot(),(LocalDateTime)journal.values(a).get("now"));
  }));
 }
 public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,JsonNode request)throws Exception{
  var fixed=schema.validate("Decide",request).deepCopy();SimulationInputArtifactSchema.key(key);
  var draft=access.execute(subject,a->checked(()->{current(a,context);var loaded=load(a,fixed.required("draft_id").asText(),false);authorize(a,loaded.row().projectId(),"agent.publish");if(!loaded.row().contentSha256().equals(fixed.required("draft_sha256").asText()))bad();if(a.actorId().equals(loaded.row().createdBy()))deny();return loaded;}));
  var prepared=inputs.prepare(subject,context,draft.author(),true);same(draft.snapshot(),prepared.snapshot());
  return access.execute(subject,a->checked(()->{current(a,context);var now=load(a,draft.row().recordId(),false);if(!draft.row().equals(now.row()))bad();var grant=authorize(a,now.row().projectId(),"agent.publish");if(a.actorId().equals(now.row().createdBy()))deny();prepared.requireConsumer(a,now.author(),true);same(now.snapshot(),prepared.snapshot());independent(a.actorId(),now.snapshot());
   var prior=journal.prior(a,"POLICY",key,fixed);if(prior!=null){load(a,prior.recordId(),false);return json.read(prior.receiptJson());}
   var lookup=journal.values(a);lookup.put("draftId",now.row().recordId());if(!mapper.policyDecision(lookup).isEmpty())throw new TenantAccessException(ErrorCode.ACTION_STALE);
   String id=UUID.randomUUID().toString();LocalDateTime time=(LocalDateTime)journal.values(a).get("now");var policy=policy(now,id,time);evidence.policy(json.canonical(policy).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var content=object().put("schema_version","1.0.0").put("status",fixed.required("decision").asText()).put("draft_id",now.row().recordId()).put("draft_sha256",now.row().contentSha256()).put("policy_schema_sha256",schema.schemaSha256()).put("typed_input_sha256",json.hash(prepared.snapshot()));content.set("policy",policy);content.put("sampling_assessment",fixed.required("sampling_assessment").asText());
   var auth=authorization(a,grant,prepared);auth.put("draft_authorizations_sha256",now.row().authorizationsSha256());return persist(a,id,"POLICY",now.row().recordId(),key,fixed,content,auth,prepared.snapshot(),time);
  }));
 }
 /** Two-phase consumer preparation performs actual fixed-object GET without holding a database transaction. */
 public PreparedPolicy prepare(ApplicationSessionIdentity.Verified subject,String context,String id,String hash)throws Exception{
  return prepare(subject,context,id,hash,false);
 }
 /** Historical inspection keeps the original approval, but does not request current execution eligibility. */
 public PreparedPolicy prepareHistorical(ApplicationSessionIdentity.Verified subject,String context,String id,String hash)throws Exception{return prepare(subject,context,id,hash,true);}
 private PreparedPolicy prepare(ApplicationSessionIdentity.Verified subject,String context,String id,String hash,boolean historical)throws Exception{
  var loaded=access.execute(subject,a->checked(()->{current(a,context);var row=load(a,id,!historical);approvedDecision(row);if(!hash.equals(row.row().contentSha256()))bad();return row;}));
  var prepared=inputs.prepare(subject,context,loaded.author(),!historical);same(loaded.snapshot(),prepared.snapshot());return new PreparedPolicy(this,loaded,prepared,context,historical);
 }
 public static final class PreparedPolicy {private final EvaluationAcceptancePolicyService owner;private final Loaded loaded;private final FormalEvaluationPolicyInputs.Prepared inputs;private final String context;private final boolean historical;private PreparedPolicy(EvaluationAcceptancePolicyService owner,Loaded loaded,FormalEvaluationPolicyInputs.Prepared inputs,String context,boolean historical){this.owner=owner;this.loaded=loaded;this.inputs=inputs;this.context=context;this.historical=historical;}}
 public VerifiedPolicy require(EnterpriseAccess.Admission a,String id,String hash,PreparedPolicy prepared)throws IOException{
  if(prepared==null||prepared.owner!=this||prepared.historical)deny();current(a,prepared.context);var loaded=load(a,id,true);if(!loaded.row().equals(prepared.loaded.row())||!hash.equals(loaded.row().contentSha256()))bad();prepared.inputs.requireConsumer(a,loaded.author(),true);same(loaded.snapshot(),prepared.inputs.snapshot());
  var policy=evidence.policy(json.canonical(json.read(loaded.row().contentJson()).required("policy")).getBytes(java.nio.charset.StandardCharsets.UTF_8));return new VerifiedPolicy(this,a,loaded,prepared,policy,AgentPublicationWitness.tree(authorize(a,loaded.row().projectId(),"configuration.read")));
 }
 public static final class VerifiedPolicy {
  private final EvaluationAcceptancePolicyService owner;private final EnterpriseAccess.Admission admission;private final Loaded loaded;private final PreparedPolicy prepared;private final EvaluationAcceptanceStatisticsProtocol.Policy policy;private final JsonNode readGrant;
  private VerifiedPolicy(EvaluationAcceptancePolicyService owner,EnterpriseAccess.Admission a,Loaded loaded,PreparedPolicy prepared,EvaluationAcceptanceStatisticsProtocol.Policy policy,JsonNode readGrant){this.owner=owner;admission=a;this.loaded=loaded;this.prepared=prepared;this.policy=policy;this.readGrant=readGrant.deepCopy();}
  public EvaluationAcceptanceStatisticsProtocol.Policy policy(){return policy;}public String expertDomain(String dimension){return owner.expertDomain(loaded,dimension);}public String contentSha256(){return loaded.row().contentSha256();}public String decisionId(){return loaded.row().recordId();}public String classification(){return loaded.row().classification();}public JsonNode authorizations(){var n=object();n.set("read",readGrant.deepCopy());n.set("typed_inputs",prepared.inputs.authorizations());try{n.set("original_decision",owner.json.read(loaded.row().authorizationsJson()));}catch(IOException e){throw new IllegalStateException(e);}return n;}public List<String> dependencyVersions(){return prepared.inputs.dependencyVersions();}
  private void requireProject(EnterpriseAccess.Admission a,String project)throws IOException{if(a!=admission||!Objects.equals(project,loaded.row().projectId()))deny();requireUnchanged();}
  public void requireConsumer(EnterpriseAccess.Admission a,EvaluationProtocol.Definition definition)throws IOException{
   requireProject(a,definition.request().project_id());owner.definition(a,definition,loaded,prepared,policy);
  }
  public void requireUnchanged()throws IOException{var current=owner.require(admission,decisionId(),contentSha256(),prepared);if(!owner.json.canonical(policy).equals(owner.json.canonical(current.policy))||!owner.json.canonical(readGrant).equals(owner.json.canonical(current.readGrant)))bad();}
 }
 /** Distinct private historical proof. It cannot satisfy the active acceptance API. */
 public HistoricalPolicy requireHistorical(EnterpriseAccess.Admission a,String id,String hash,PreparedPolicy prepared)throws IOException{
  if(prepared==null||prepared.owner!=this||!prepared.historical)deny();current(a,prepared.context);var loaded=load(a,id,false);approvedDecision(loaded);
  if(!loaded.row().equals(prepared.loaded.row())||!hash.equals(loaded.row().contentSha256()))bad();prepared.inputs.requireConsumer(a,loaded.author(),false);same(loaded.snapshot(),prepared.inputs.snapshot());
  var policy=evidence.policy(json.canonical(json.read(loaded.row().contentJson()).required("policy")).getBytes(java.nio.charset.StandardCharsets.UTF_8));return new HistoricalPolicy(this,a,loaded,prepared,policy,AgentPublicationWitness.tree(authorize(a,loaded.row().projectId(),"configuration.read")));
 }
 public static final class HistoricalPolicy {
  private final EvaluationAcceptancePolicyService owner;private final EnterpriseAccess.Admission admission;private final Loaded loaded;private final PreparedPolicy prepared;private final EvaluationAcceptanceStatisticsProtocol.Policy policy;private final JsonNode readGrant;
  private HistoricalPolicy(EvaluationAcceptancePolicyService owner,EnterpriseAccess.Admission a,Loaded loaded,PreparedPolicy prepared,EvaluationAcceptanceStatisticsProtocol.Policy policy,JsonNode readGrant){this.owner=owner;admission=a;this.loaded=loaded;this.prepared=prepared;this.policy=policy;this.readGrant=readGrant.deepCopy();}
  public EvaluationAcceptanceStatisticsProtocol.Policy policy(){return policy;}public String decisionId(){return loaded.row().recordId();}public String classification(){return loaded.row().classification();}public String contentSha256(){return loaded.row().contentSha256();}public String expertDomain(String dimension){return owner.expertDomain(loaded,dimension);}
  public JsonNode authorizations(){var n=object();n.set("read",readGrant.deepCopy());n.set("typed_inputs",prepared.inputs.authorizations());try{n.set("original_decision",owner.json.read(loaded.row().authorizationsJson()));}catch(IOException e){throw new IllegalStateException(e);}return n;}public List<String> dependencyVersions(){return prepared.inputs.dependencyVersions();}
  public void requireConsumer(EnterpriseAccess.Admission a,EvaluationProtocol.Definition definition)throws IOException{if(a!=admission||!Objects.equals(definition.request().project_id(),loaded.row().projectId()))deny();requireUnchanged();owner.definition(a,definition,loaded,prepared,policy);}
  public void requireUnchanged()throws IOException{var current=owner.requireHistorical(admission,decisionId(),contentSha256(),prepared);if(!owner.json.canonical(policy).equals(owner.json.canonical(current.policy))||!owner.json.canonical(readGrant).equals(owner.json.canonical(current.readGrant)))bad();}
 }
 private void approvedDecision(Loaded loaded)throws IOException{if(!"POLICY".equals(loaded.row().kind())||!"APPROVE".equals(json.read(loaded.row().contentJson()).required("status").asText()))deny();}
 private void definition(EnterpriseAccess.Admission a,EvaluationProtocol.Definition definition,Loaded loaded,PreparedPolicy prepared,EvaluationAcceptanceStatisticsProtocol.Policy policy)throws IOException{
  var r=definition.request();var spec=loaded.author().required("specification");
  if(!definition.tenant_id().equals(a.context().tenant_id())||!Instant.parse(policy.approved_at()).isBefore(Instant.parse(definition.created_at()))||!r.dataset().equals(policy.dataset())||!r.sample_manifest_artifact().equals(policy.sample_manifest_artifact())||!r.rule_revision().equals(policy.rule_revision())||!r.candidates().equals(policy.candidate_refs())||!"PRODUCTION".equals(r.environment())||!json.canonical(r.samples()).equals(prepared.inputs.checked().canonicalSampleManifest()))bad();
  var dimensions=new TreeSet<String>();for(var rule:spec.required("dimensions")){dimensions.add(rule.required("dimension").asText());if(rule.required("repeat_count").intValue()!=r.variance_policy().repeat_count())bad();}if(!dimensions.equals(new TreeSet<>(r.required_dimensions())))bad();
 }
 /** Historical read has current source/read permission, but never creates an executable/acceptance proof. */
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id)throws Exception{
  var loaded=access.execute(subject,a->checked(()->{current(a,context);return load(a,id,false);}));var prepared=inputs.prepare(subject,context,loaded.author(),false);same(loaded.snapshot(),prepared.snapshot());
  return access.execute(subject,a->checked(()->{current(a,context);var now=load(a,id,false);if(!loaded.row().equals(now.row()))bad();prepared.requireConsumer(a,now.author(),false);var result=object().put("schema_version","1.0.0").put("record_id",id).put("content_sha256",now.row().contentSha256()).put("production_eligible",false);result.set("content",json.read(now.row().contentJson()));return result;}));
 }
 public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String id,String operation)throws Exception{
  read(subject,context,id);return access.execute(subject,a->checked(()->{current(a,context);var row=load(a,id,false).row();if(!a.actorId().equals(row.createdBy())||!operation.equals(row.operationId()))deny();return json.read(row.receiptJson());}));
 }
 private record Loaded(AgentPublicationMapper.Journal row,JsonNode author,JsonNode snapshot){}
 private Loaded load(EnterpriseAccess.Admission a,String id,boolean approved)throws IOException{return load(a,id,approved,0);}
 private Loaded load(EnterpriseAccess.Admission a,String id,boolean approved,int depth)throws IOException{
  if(depth>1)bad();
  var row=journal.read(a,id);if(!Set.of("POLICY_DRAFT","POLICY").contains(row.kind()))bad();authorize(a,row.projectId(),"configuration.read");
  var content=json.read(row.contentJson());if(!schema.schemaSha256().equals(content.path("policy_schema_sha256").asText()))bad();var values=journal.values(a);values.put("recordId",id);var stored=AgentPublicationJournal.one(snapshots.snapshot(values));
  var snapshot=json.read(stored.snapshotJson());if(!row.classification().equals(content.path("classification").asText())||!row.classification().equals(FormalEvaluationPolicyInputs.maxClassification(row.classification(),snapshot.path("classification").asText())))bad();if(!a.context().tenant_id().equals(stored.tenantId())||!id.equals(stored.recordId())||!row.contentSha256().equals(stored.contentSha256())||!json.canonical(snapshot).equals(stored.snapshotJson())||!json.hash(snapshot).equals(stored.snapshotSha256())||!stored.snapshotSha256().equals(content.required("typed_input_sha256").asText())||!schema.schemaSha256().equals(stored.policySchemaSha256())||!stored.inputSchemaSha256().equals(snapshot.required("input_schema_sha256").asText()))bad();
  AgentPublicationWitness.requireGovernance(row,json.read(row.authorizationsJson()),"POLICY".equals(row.kind())?"agent.publish":"agent.configure","EVALUATION_RESULTS",permissionJson);
  if("POLICY_DRAFT".equals(row.kind())){if(approved)deny();var author=schema.validate("Author",json.read(row.requestJson()));semantics(author.required("specification"));if(!"DRAFT".equals(content.required("status").asText())||!id.equals(content.required("policy_draft_id").asText())||!Objects.equals(project(author),row.projectId()))bad();same(author,content.required("author"));return new Loaded(row,author,snapshot);}
  var decision=schema.validate("Decide",json.read(row.requestJson()));var draft=load(a,decision.required("draft_id").asText(),false,depth+1);if(!"POLICY_DRAFT".equals(draft.row().kind())||draft.row().createdBy().equals(row.createdBy())||!Objects.equals(row.projectId(),draft.row().projectId())||!draft.row().recordId().equals(row.resourceId())||!draft.row().contentSha256().equals(decision.required("draft_sha256").asText())||!draft.row().recordId().equals(content.required("draft_id").asText())||!draft.row().contentSha256().equals(content.required("draft_sha256").asText())||!decision.required("decision").equals(content.required("status"))||!decision.required("sampling_assessment").equals(content.required("sampling_assessment"))||row.createdAt().isBefore(draft.row().createdAt()))bad();
  if(!row.classification().equals(FormalEvaluationPolicyInputs.maxClassification(row.classification(),draft.row().classification())))bad();values.put("draftId",draft.row().recordId());if(!row.equals(AgentPublicationJournal.one(mapper.policyDecision(values))))bad();same(snapshot,draft.snapshot());independent(row.createdBy(),snapshot);same(policy(draft,id,row.createdAt()),content.required("policy"));
  if(approved&&!"APPROVE".equals(content.required("status").asText()))deny();return new Loaded(row,draft.author(),snapshot);
 }
 private JsonNode persist(EnterpriseAccess.Admission a,String id,String kind,String resource,String key,JsonNode request,JsonNode content,JsonNode auth,JsonNode snapshot,LocalDateTime time)throws IOException{
  var draft="POLICY_DRAFT".equals(kind)?null:load(a,resource,false);String project=draft==null?project(request):draft.row().projectId();String classification=FormalEvaluationPolicyInputs.maxClassification(settings.classification(),snapshot.required("classification").asText(),draft==null?"PUBLIC":draft.row().classification());((ObjectNode)content).put("classification",classification);var values=journal.values(a);values.put("versionId",id);values.put("projectId",project);values.put("entityKind","AGENT_PUBLICATION_RECORD");values.put("contentHash",json.hash(content));AgentPublicationJournal.one(mapper.insertVersion(values));
  var row=journal.save(a,project,id,kind,resource,key,request,content,auth,classification,time);var fixed=new FormalEvaluationPolicyMapper.Snapshot(a.context().tenant_id(),id,row.contentSha256(),json.canonical(snapshot),json.hash(snapshot),snapshot.required("input_schema_sha256").asText(),schema.schemaSha256());values.put("snapshot",fixed);AgentPublicationJournal.one(snapshots.insertSnapshot(values));
  if("POLICY".equals(kind)){values.put("row",row);AgentPublicationJournal.one(mapper.insertPolicyDecision(values));}load(a,id,false);return json.read(row.receiptJson());
 }
 private static void independent(String actor,JsonNode snapshot){for(var candidate:snapshot.path("candidates"))if(actor.equals(candidate.path("created_by").asText()))deny();}
 private ObjectNode authorization(EnterpriseAccess.Admission a,PermissionProtocol.GrantRow grant,FormalEvaluationPolicyInputs.Prepared prepared){var auth=object().put("identity_mode",a.bindingView().mode());auth.set("governance",AgentPublicationWitness.tree(grant));auth.set("typed_inputs",prepared.authorizations());auth.set("dependency_versions",AgentPublicationWitness.tree(prepared.dependencyVersions()));return auth;}
 private JsonNode policy(Loaded draft,String id,LocalDateTime time){var fixed=(ObjectNode)draft.author().required("specification").deepCopy();fixed.remove(List.of("reason","expert_domains"));fixed.put("policy_id",id).put("tenant_id",draft.row().tenantId()).put("created_at",draft.row().createdAt().toInstant(ZoneOffset.UTC).toString()).put("approved_at",time.toInstant(ZoneOffset.UTC).toString());return fixed;}
 private String expertDomain(Loaded loaded,String dimension){var spec=loaded.author().required("specification");FormalEvaluationPolicySchema.expertDomains(spec);var value=dimension==null?null:spec.required("expert_domains").get(dimension);if(value==null||!value.isTextual()||value.asText().isBlank())bad();return value.asText();}
 private PermissionProtocol.GrantRow authorize(EnterpriseAccess.Admission a,String project,String action)throws IOException{if(a.simulationWorker())deny();return resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,project==null?"ENTERPRISE":"PROJECT",project==null?a.context().tenant_id():project),action,"EVALUATION_RESULTS");}
 private void same(Object a,Object b)throws IOException{if(!json.canonical(a).equals(json.canonical(b)))bad();}
 private static String project(JsonNode author){var value=author.required("specification").required("project_id");return value.isNull()?null:value.asText();}
 private static ObjectNode object(){return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T checked(ThrowingSupplier<T> call){return call.get();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
 private static void semantics(JsonNode request){
  var confidence=new java.math.BigDecimal(request.path("familywise_confidence").asText());if(confidence.signum()<=0||confidence.compareTo(java.math.BigDecimal.ONE)>=0||confidence.scale()>18)AgentPublicationSchema.invalid();
  var dimensions=new HashSet<String>();Integer repeats=null;for(var rule:request.path("dimensions")){String dimension=rule.path("dimension").asText();if(!dimensions.add(dimension))AgentPublicationSchema.invalid();int repeat=rule.path("repeat_count").intValue();if(repeats!=null&&repeats!=repeat)AgentPublicationSchema.invalid();repeats=repeat;var threshold=new java.math.BigDecimal(rule.path("threshold").asText());if(threshold.signum()<0||threshold.compareTo(java.math.BigDecimal.ONE)>0||threshold.scale()>18)AgentPublicationSchema.invalid();
   if(Set.of("EVAL-02","EVAL-06","EVAL-07","EVAL-11","EVAL-12").contains(dimension)){if(!"DETERMINISTIC_ALL_REQUIRED_RUNS".equals(rule.path("method").asText())||!"MIN".equals(rule.path("direction").asText())||threshold.compareTo(java.math.BigDecimal.ONE)!=0)AgentPublicationSchema.invalid();}
   else if(!"SOURCE_FAMILY_HOEFFDING_V1".equals(rule.path("method").asText())||rule.path("minimum_families").intValue()<2)AgentPublicationSchema.invalid();
  }
 }
}
