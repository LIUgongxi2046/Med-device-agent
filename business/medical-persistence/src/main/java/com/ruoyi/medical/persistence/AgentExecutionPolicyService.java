package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyStore.*;

/** Call only in EnterpriseAccess's current-identity, tenant-locked transaction. */
@Component public final class AgentExecutionPolicyService {
 private final AgentExecutionPolicyMapper mapper;private final AgentExecutionPolicyStore store;private final AgentExecutionPolicyDependencies dependencies;
 private final AgentExecutionPolicySchema schema;private final AgentPublicationJournal journal;private final PermissionSchema json;private final IdentityDirectoryPolicy settings;
 public AgentExecutionPolicyService(AgentExecutionPolicyMapper mapper,AgentExecutionPolicyStore store,AgentExecutionPolicyDependencies dependencies,
  AgentExecutionPolicySchema schema,AgentPublicationJournal journal,PermissionSchema json,IdentityDirectoryPolicy settings){this.mapper=mapper;this.store=store;this.dependencies=dependencies;this.schema=schema;this.journal=journal;this.json=json;this.settings=settings;}
 public JsonNode author(EnterpriseAccess.Admission a,String key,byte[] bytes)throws Exception {
  var request=schema.author(bytes);String project=request.definition().projectId();var grant=store.authorize(a,project,"agent.configure");var requestTree=schema.tree(request);
  var prior=journal.prior(a,"EXECUTION_POLICY_AUTHOR",key,requestTree);
  if(prior!=null){var original=schema.fixed(prior.contentJson());var loaded=store.read(a,original.versionId(),AgentExecutionPolicySchema.PURPOSE);if(!loaded.row().authorRecordId().equals(prior.recordId()))bad();dependencies.require(a,loaded.fixed().definition(),project,AgentExecutionPolicySchema.PURPOSE,false);return schema.read(prior.receiptJson());}
  var definition=schema.materialize(request.definition());var deps=dependencies.require(a,definition,project,AgentExecutionPolicySchema.PURPOSE,false);
  String id=id(),version=id(),record=id();var now=store.now();String hash=schema.hash(definition),classification=settings.classification();
  var fixed=new Fixed("1.0.0",a.context().tenant_id(),id,version,record,definition,hash,a.actorId(),instant(now),classification);schema.fixed(schema.canonical(fixed));
  var auth=json.object();auth.set("governance",AgentPublicationWitness.tree(grant));auth.put("identity_mode",a.bindingView().mode());auth.set("dependencies",deps.authorizations());
  registry(a,project,version,"AGENT_EXECUTION_POLICY",hash);registry(a,project,record,"AGENT_PUBLICATION_RECORD",schema.hash(fixed));
  var components=schema.components(definition);for(var c:components)registry(a,project,c.version().version_id(),AgentExecutionPolicySchema.entityKind(c.kind()),c.version().content_sha256());
  var ledger=journal.save(a,project,record,"EXECUTION_POLICY_AUTHOR",id,key,requestTree,schema.tree(fixed),auth,classification,now);
  var values=values(a,version);values.put("projectId",project);values.put("policyId",id);values.put("authorRecordId",record);values.put("fixedJson",schema.canonical(fixed));values.put("contentHash",hash);values.put("schemaManifest",schema.manifest());values.put("classification",classification);values.put("now",now);
  one(mapper.insertPolicy(values));one(mapper.insertState(values));
  var parentIds=new TreeSet<String>();for(var b:schema.dependencies(definition))parentIds.add(b.version_id());
  for(var c:components){var cv=new HashMap<>(values);cv.put("policyHash",hash);cv.put("componentId",c.version().version_id());cv.put("kind",c.kind());cv.put("contentHash",c.version().content_sha256());cv.put("contentJson",schema.canonical(c.content()));one(mapper.insertComponent(cv));parentIds.add(c.version().version_id());for(String upstream:componentParents(definition,c.version().version_id()))edge(a,c.version().version_id(),upstream,now);}
  for(String upstream:parentIds)edge(a,version,upstream,now);
  var actual=store.read(a,version,AgentExecutionPolicySchema.PURPOSE);if(!fixed.equals(actual.fixed()))bad();
  var checked=dependencies.require(a,definition,project,AgentExecutionPolicySchema.PURPOSE,false);if(!schema.hash(deps.authorizations()).equals(schema.hash(checked.authorizations())))deny(ErrorCode.SCOPE_STALE);
  advance(a);return schema.read(ledger.receiptJson());
 }
 public JsonNode decide(EnterpriseAccess.Admission a,String key,byte[] bytes)throws Exception {
  var request=schema.decide(bytes);var requestTree=schema.tree(request);var loaded=store.read(a,request.policyVersion().version_id(),AgentExecutionPolicySchema.PURPOSE);
  if(!request.policyVersion().content_sha256().equals(loaded.fixed().definitionSha256()))bad();var grant=store.authorize(a,loaded.row().projectId(),"agent.publish");
  var prior=journal.prior(a,"EXECUTION_POLICY_DECISION",key,requestTree);if(prior!=null){if(!prior.resourceId().equals(loaded.row().policyId()))bad();var previous=schema.decision(prior.contentJson());if(!mapper.decisions(values(a,loaded.row().versionId())).stream().anyMatch(x->x.decisionRecordId().equals(previous.decisionRecordId())))bad();return schema.read(prior.receiptJson());}
  if(!loaded.state().revision().toString().equals(request.expectedRevision()))deny(ErrorCode.REVISION_CONFLICT);
  String status=transition(loaded.state().status(),request.decision());if("APPROVE".equals(request.decision())&&loaded.row().createdBy().equals(a.actorId()))deny(ErrorCode.ACCESS_DENIED);
  AgentExecutionPolicyDependencies.Checked deps=null;
  if("APPROVE".equals(request.decision())){deps=dependencies.require(a,loaded.fixed().definition(),loaded.row().projectId(),AgentExecutionPolicySchema.PURPOSE,true);if(loaded.fixed().definition().validUntil()!=null&&!store.now().toInstant(ZoneOffset.UTC).isBefore(Instant.parse(loaded.fixed().definition().validUntil())))deny(ErrorCode.SOURCE_NOT_READY);}
  String record=id();var now=store.now();var revision=loaded.state().revision().add(BigInteger.ONE);
  var decision=new Decision("1.0.0",a.context().tenant_id(),loaded.row().projectId(),loaded.row().policyId(),loaded.row().versionId(),loaded.row().definitionHash(),record,loaded.state().status(),loaded.state().revision().toString(),request.decision(),status,revision.toString(),a.actorId(),instant(now),grant.grantId(),grant.revision().toString(),request.reason());
  schema.decision(schema.canonical(decision));var auth=json.object();auth.set("governance",AgentPublicationWitness.tree(grant));auth.put("identity_mode",a.bindingView().mode());if(deps!=null)auth.set("dependencies",deps.authorizations());else auth.set("dependencies",json.object());
  registry(a,loaded.row().projectId(),record,"AGENT_PUBLICATION_RECORD",schema.hash(decision));var ledger=journal.save(a,loaded.row().projectId(),record,"EXECUTION_POLICY_DECISION",loaded.row().policyId(),key,requestTree,schema.tree(decision),auth,loaded.row().classification(),now);
  var v=values(a,loaded.row().versionId());v.put("projectId",loaded.row().projectId());v.put("policyId",loaded.row().policyId());v.put("contentHash",loaded.row().definitionHash());v.put("decisionRecordId",record);v.put("decisionJson",schema.canonical(decision));v.put("decisionHash",schema.hash(decision));v.put("decision",request.decision());v.put("beforeStatus",loaded.state().status());v.put("status",status);v.put("beforeRevision",loaded.state().revision());v.put("afterRevision",revision);v.put("schemaManifest",schema.manifest());v.put("classification",loaded.row().classification());v.put("now",now);
  one(mapper.insertDecision(v));if(mapper.advanceState(v)!=1)deny(ErrorCode.REVISION_CONFLICT);
  var actual=store.read(a,loaded.row().versionId(),AgentExecutionPolicySchema.PURPOSE);if(!decision.equals(actual.latest()))bad();
  if(deps!=null){store.approval(a,actual);var checked=dependencies.require(a,actual.fixed().definition(),actual.row().projectId(),AgentExecutionPolicySchema.PURPOSE,true);if(!schema.hash(deps.authorizations()).equals(schema.hash(checked.authorizations())))deny(ErrorCode.SCOPE_STALE);}
  advance(a);return schema.read(ledger.receiptJson());
 }
 public JsonNode read(EnterpriseAccess.Admission a,String id)throws Exception {
  var loaded=store.read(a,id,AgentExecutionPolicySchema.PURPOSE);var view=json.object().put("schema_version","1.0.0").put("status",loaded.state().status()).put("revision",loaded.state().revision().toString());view.set("fixed",schema.tree(loaded.fixed()));view.set("latest_decision",loaded.latest()==null?json.object().nullNode():schema.tree(loaded.latest()));var reasons=view.putArray("missing_input_reasons");
  if(!"APPROVED".equals(loaded.state().status()))reasons.add("POLICY_"+loaded.state().status());
  var now=store.now().toInstant(ZoneOffset.UTC);if(now.isBefore(Instant.parse(loaded.fixed().definition().validFrom())))reasons.add("POLICY_NOT_YET_VALID");if(loaded.fixed().definition().validUntil()!=null&&!now.isBefore(Instant.parse(loaded.fixed().definition().validUntil())))reasons.add("POLICY_EXPIRED");
  try{dependencies.require(a,loaded.fixed().definition(),loaded.row().projectId(),AgentExecutionPolicySchema.PURPOSE,true);if("APPROVED".equals(loaded.state().status()))store.approval(a,loaded);}catch(TenantAccessException e){if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.SOURCE_NOT_READY,ErrorCode.DEPENDENCY_UNAVAILABLE,ErrorCode.SCHEMA_UNSUPPORTED,ErrorCode.SCOPE_STALE,ErrorCode.SOURCE_MISSING,ErrorCode.OBJECT_UNAVAILABLE).contains(e.code()))throw e;reasons.add(e.code().name());}
  return schema.validate("View",view);
 }
 public JsonNode record(EnterpriseAccess.Admission a,String recordId)throws Exception {
  var row=journal.read(a,recordId);
  if(!row.createdBy().equals(a.actorId())||!Set.of("EXECUTION_POLICY_AUTHOR","EXECUTION_POLICY_DECISION").contains(row.kind()))deny(ErrorCode.ACCESS_DENIED);
  String version="EXECUTION_POLICY_AUTHOR".equals(row.kind())?schema.fixed(row.contentJson()).versionId():schema.decision(row.contentJson()).versionId();
  var loaded=store.read(a,version,AgentExecutionPolicySchema.PURPOSE);
  if(!loaded.row().policyId().equals(row.resourceId()))bad();
  if("EXECUTION_POLICY_AUTHOR".equals(row.kind())){if(!loaded.row().authorRecordId().equals(recordId))bad();}
  else if(mapper.decisions(values(a,version)).stream().noneMatch(d->d.decisionRecordId().equals(recordId)))bad();
  return read(a,version);
 }
 public JsonNode receipt(EnterpriseAccess.Admission a,String version,String record,String operation)throws Exception {store.read(a,version,AgentExecutionPolicySchema.PURPOSE);var row=journal.read(a,record);if(!row.createdBy().equals(a.actorId())||!row.operationId().equals(operation)||!Set.of("EXECUTION_POLICY_AUTHOR","EXECUTION_POLICY_DECISION").contains(row.kind()))deny(ErrorCode.ACCESS_DENIED);var policy=store.read(a,version,AgentExecutionPolicySchema.PURPOSE);if(!policy.row().policyId().equals(row.resourceId()))deny(ErrorCode.ACCESS_DENIED);return schema.read(row.receiptJson());}
 private void registry(EnterpriseAccess.Admission a,String project,String id,String kind,String hash){var v=values(a,id);v.put("projectId",project);v.put("entityKind",kind);v.put("contentHash",hash);one(mapper.insertRegistry(v));}
 private void edge(EnterpriseAccess.Admission a,String version,String upstream,LocalDateTime now){var v=values(a,version);v.put("upstreamId",upstream);v.put("now",now);one(mapper.insertLineage(v));}
 private void advance(EnterpriseAccess.Admission a){var v=values(a,null);v.put("authEpoch",a.watermarks().authEpoch());v.put("governanceEpoch",a.watermarks().governanceEpoch());if(mapper.advanceGovernance(v)!=1)deny(ErrorCode.CONTEXT_CHANGED);}
 private static String id(){return UUID.randomUUID().toString();}
}
