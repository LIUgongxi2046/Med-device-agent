package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;

/** Typed readback of all immutable rows, original operations, audit/outbox and the complete decision chain. */
@Component public final class AgentExecutionPolicyStore {
 private final AgentExecutionPolicyMapper mapper;private final AgentExecutionPolicySchema schema;private final AgentPublicationJournal journal;
 private final AgentPublicationMapper publicationRows;private final ResourceReadAuthority resources;private final PermissionMapper permissions;private final PermissionAuthority grants;private final PermissionSchema json;
 public AgentExecutionPolicyStore(AgentExecutionPolicyMapper mapper,AgentExecutionPolicySchema schema,AgentPublicationJournal journal,
   ResourceReadAuthority resources,PermissionMapper permissions,PermissionAuthority grants,PermissionSchema json,AgentPublicationMapper publicationRows){this.mapper=mapper;this.schema=schema;this.journal=journal;this.resources=resources;this.permissions=permissions;this.grants=grants;this.json=json;this.publicationRows=publicationRows;}
 record Loaded(Row row,Fixed fixed,State state,Decision latest,PermissionProtocol.GrantRow readGrant){}
 Loaded read(EnterpriseAccess.Admission a,String versionId,String purpose)throws IOException {
  WireValues.uuid(versionId);var v=values(a,versionId);var rows=mapper.policy(v);if(rows.isEmpty())deny(ErrorCode.OBJECT_UNAVAILABLE);var row=one(rows);
  if(!a.context().tenant_id().equals(row.tenantId()))bad();
  var read=resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"CONFIGURATION",row.policyId()),"configuration.read",purpose);
  return validate(a.context().tenant_id(),a.actorId(),row,read);
 }
 Loaded metadata(FormalWorkerControlAuthority.VerifiedControl control)throws IOException {
  control.requireUnchanged();String tenant=control.tenantId(),actor=control.actorId();
  var row=one(mapper.policy(values(actor,control.policyVersion())));if(!tenant.equals(row.tenantId())||row.projectId()!=null&&!row.projectId().equals(control.projectId()))deny(ErrorCode.ACCESS_DENIED);
  var scope=new PermissionProtocol.Scope(tenant,row.projectId(),"CONFIGURATION",row.policyId());
  control.requireLiteralMetadataPermission(scope,"TASK_EXECUTION");
  var owner=one(permissions.resourceOwner("CONFIGURATION",row.policyId()));if(!owner.id().equals(row.policyId())||!Objects.equals(owner.projectId(),row.projectId()))bad();
  var grant=grants.requireResource(scope,actor,control.identityMode(),"configuration.read","TASK_EXECUTION",permissions.resourceGrants(actor,"configuration.read"),now());
  var loaded=validate(tenant,actor,row,grant);control.requireUnchanged();return loaded;
 }
 private Loaded validate(String tenant,String actor,Row row,PermissionProtocol.GrantRow read)throws IOException {
  String versionId=row.versionId();var v=values(actor,versionId);
  var fixed=schema.fixed(row.fixedJson());
  if(!row.versionId().equals(versionId)||!fixed.versionId().equals(versionId)||!row.tenantId().equals(fixed.tenantId())||!row.policyId().equals(fixed.policyId())||!row.authorRecordId().equals(fixed.authorRecordId())
    ||!Objects.equals(row.projectId(),fixed.definition().projectId())||!row.definitionHash().equals(fixed.definitionSha256())||!row.registryHash().equals(row.definitionHash())
    ||!"AGENT_EXECUTION_POLICY".equals(row.registryKind())||!schema.manifest().equals(row.schemaManifest())||!"1.0.0".equals(row.schemaVersion())
    ||!row.createdBy().equals(fixed.createdBy())||!row.classification().equals(fixed.classification())||!instant(row.createdAt()).equals(fixed.createdAt())||!row.fixedJson().equals(schema.canonical(fixed)))bad();
  var author=ledger(tenant,actor,row.authorRecordId());
  if(!"EXECUTION_POLICY_AUTHOR".equals(author.kind())||!row.policyId().equals(author.resourceId())||!Objects.equals(row.projectId(),author.projectId())
    ||!row.createdBy().equals(author.createdBy())||!row.createdAt().equals(author.createdAt())||!row.classification().equals(author.classification())||!row.fixedJson().equals(author.contentJson()))bad();
  AgentPublicationWitness.requireGovernance(author,schema.read(author.authorizationsJson()),"agent.configure",AgentExecutionPolicySchema.PURPOSE,json);
  var request=schema.author(author.requestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  same(schema.tree(request.definition()),schema.tree(schema.asInput(fixed.definition())));
  var actualComponents=mapper.components(v);var expectedComponents=schema.components(fixed.definition());if(actualComponents.size()!=expectedComponents.size())bad();
  var byId=new HashMap<String,AgentExecutionPolicyProtocol.Component>();for(var c:actualComponents)if(byId.put(c.componentId(),c)!=null)bad();
  var parents=new TreeSet<String>();for(var ref:schema.dependencies(fixed.definition()))parents.add(ref.version_id());
  for(var expected:expectedComponents){var c=byId.get(expected.version().version_id());if(c==null)bad();
   if(!c.tenantId().equals(row.tenantId())||!Objects.equals(c.projectId(),row.projectId())||!c.policyId().equals(row.policyId())||!c.policyVersionId().equals(row.versionId())
    ||!c.kind().equals(expected.kind())||!c.contentHash().equals(expected.version().content_sha256())||!c.registryHash().equals(c.contentHash())||!c.registryKind().equals(AgentExecutionPolicySchema.entityKind(c.kind()))
    ||!c.schemaVersion().equals(row.schemaVersion())||!c.schemaManifest().equals(row.schemaManifest())||!c.createdBy().equals(row.createdBy())||!c.createdAt().equals(row.createdAt())||!c.classification().equals(row.classification())
    ||!c.contentJson().equals(schema.canonical(expected.content())))bad();
   var cv=values(actor,c.componentId());var cp=componentParents(fixed.definition(),expected.version().version_id());equalIds(cp.stream().map(x->"DEPENDS_ON:"+x).toList(),mapper.componentParents(cv));parents.add(c.componentId());
  }
  equalIds(parents.stream().map(x->"DEPENDS_ON:"+x).toList(),mapper.parents(v));
  var state=one(mapper.state(v));if(!state.tenantId().equals(row.tenantId())||!Objects.equals(state.projectId(),row.projectId())||!state.policyId().equals(row.policyId())||!state.versionId().equals(versionId))bad();
  var decisions=mapper.decisions(v);if(decisions.size()>2)bad();String status="DRAFT";BigInteger revision=BigInteger.ONE;String last=null;LocalDateTime updated=row.createdAt();Decision latest=null;
  for(var d:decisions){var decision=schema.decision(d.decisionJson());var ledger=ledger(tenant,actor,d.decisionRecordId());
   if(!d.tenantId().equals(row.tenantId())||!Objects.equals(d.projectId(),row.projectId())||!d.policyId().equals(row.policyId())||!d.versionId().equals(versionId)||!d.definitionHash().equals(row.definitionHash())
    ||!"1.0.0".equals(d.schemaVersion())||!schema.manifest().equals(d.schemaManifest())||!d.classification().equals(row.classification())||!d.decisionHash().equals(schema.hash(decision))
    ||!d.decisionJson().equals(schema.canonical(decision))||!d.beforeRevision().equals(revision)||!d.afterRevision().equals(revision.add(BigInteger.ONE))||d.decidedAt().isBefore(updated))bad();
   if(!decision.tenantId().equals(row.tenantId())||!Objects.equals(decision.projectId(),row.projectId())||!decision.policyId().equals(row.policyId())||!decision.versionId().equals(versionId)
    ||!decision.definitionSha256().equals(row.definitionHash())||!decision.decisionRecordId().equals(d.decisionRecordId())||!decision.previousStatus().equals(status)||!decision.previousRevision().equals(revision.toString())
    ||!decision.decision().equals(d.decision())||!decision.status().equals(d.status())||!decision.status().equals(transition(status,d.decision()))||!decision.revision().equals(d.afterRevision().toString())
    ||!decision.actorId().equals(d.actorId())||!decision.decidedAt().equals(instant(d.decidedAt()))||"APPROVE".equals(decision.decision())&&row.createdBy().equals(decision.actorId()))bad();
   if(!"EXECUTION_POLICY_DECISION".equals(ledger.kind())||!ledger.resourceId().equals(row.policyId())||!Objects.equals(ledger.projectId(),row.projectId())||!ledger.createdBy().equals(d.actorId())
    ||!ledger.createdAt().equals(d.decidedAt())||!ledger.classification().equals(row.classification())||!ledger.contentJson().equals(d.decisionJson()))bad();
   AgentPublicationWitness.requireGovernance(ledger,schema.read(ledger.authorizationsJson()),"agent.publish",AgentExecutionPolicySchema.PURPOSE,json);
   var intent=schema.decide(ledger.requestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   if(!intent.expectedRevision().equals(decision.previousRevision())||!intent.policyVersion().equals(new PermissionProtocol.Basis(versionId,row.definitionHash()))||!intent.decision().equals(decision.decision())||!intent.reason().equals(decision.reason()))bad();
   var approval=schema.read(ledger.authorizationsJson()).required("governance");if(!approval.required("grant_id").asText().equals(decision.authorityGrantId())||!approval.required("revision").asText().equals(decision.authorityRevision())||!approval.required("subject_id").asText().equals(decision.actorId()))bad();
   status=decision.status();revision=d.afterRevision();last=d.decisionRecordId();updated=d.decidedAt();latest=decision;
  }
  if(!status.equals(state.status())||!revision.equals(state.revision())||!Objects.equals(last,state.latestDecisionId())||!updated.equals(state.updatedAt()))bad();
  return new Loaded(row,fixed,state,latest,read);
 }
 PermissionProtocol.GrantRow approval(EnterpriseAccess.Admission a,Loaded value)throws IOException {return approval(a.context().tenant_id(),value);}
 PermissionProtocol.GrantRow approval(String tenant,Loaded value)throws IOException {
  if(!"APPROVED".equals(value.state().status())||value.latest()==null||!"APPROVE".equals(value.latest().decision()))deny(ErrorCode.SOURCE_NOT_READY);
  var d=value.latest();var member=one(permissions.targetMember(d.actorId()));var binding=one(permissions.targetBinding(d.actorId()));var now=now();
  if(!member.userId().equals(d.actorId())||!member.tenantId().equals(tenant)||!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)
    ||!binding.userId().equals(d.actorId())||!"ACTIVE".equals(binding.status()))deny(ErrorCode.ACCESS_DENIED);
  var grant=one(permissions.currentGrant(d.authorityGrantId()));grants.requireResource(governance(tenant,value.row().projectId()),d.actorId(),binding.mode(),"agent.publish",AgentExecutionPolicySchema.PURPOSE,List.of(grant),now);
  if(!grant.revision().toString().equals(d.authorityRevision()))deny(ErrorCode.SCOPE_STALE);return grant;
 }
 PermissionProtocol.GrantRow authorize(EnterpriseAccess.Admission a,String project,String action)throws IOException {if(a.simulationWorker())deny(ErrorCode.ACCESS_DENIED);return resources.require(a,governance(a,project),action,AgentExecutionPolicySchema.PURPOSE);}
 static PermissionProtocol.Scope governance(EnterpriseAccess.Admission a,String project){return governance(a.context().tenant_id(),project);}
 static PermissionProtocol.Scope governance(String tenant,String project){return new PermissionProtocol.Scope(tenant,project,project==null?"ENTERPRISE":"PROJECT",project==null?tenant:project);}
 LocalDateTime now(){return Objects.requireNonNull(permissions.databaseTime());}
 static String instant(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}
 static String transition(String previous,String decision){return switch(previous+":"+decision){case "DRAFT:APPROVE"->"APPROVED";case "DRAFT:REJECT"->"REJECTED";case "APPROVED:DISABLE"->"DISABLED";default->throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);};}
 static Set<String> componentParents(Definition d,String id){if(d.sourceSelection().version().version_id().equals(id))return d.sourceSelection().sources().stream().map(PermissionProtocol.Basis::version_id).collect(java.util.stream.Collectors.toCollection(TreeSet::new));for(var r:d.retrievalProfiles())if(r.version().version_id().equals(id))return Set.of(r.indexConfiguration().version_id());if(d.networkPolicy().version().version_id().equals(id))return Set.of();bad();return Set.of();}
 static Map<String,Object> values(EnterpriseAccess.Admission a,String id){return values(a.actorId(),id);}
 static Map<String,Object> values(String actor,String id){var v=new HashMap<String,Object>();v.put("userId",actor);v.put("versionId",id);return v;}
 private AgentPublicationMapper.Journal ledger(String tenant,String actor,String id)throws IOException{var v=values(actor,null);v.put("recordId",id);var row=one(publicationRows.journal(v));journal.verifyMetadata(tenant,actor,row);return row;}
 private void same(JsonNode a,JsonNode b)throws IOException{if(!schema.canonical(a).equals(schema.canonical(b)))bad();}
 static void equalIds(Collection<String> expected,List<String> actual){if(actual.size()!=new HashSet<>(actual).size()||!new TreeSet<>(expected).equals(new TreeSet<>(actual)))bad();}
 static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}static void one(int n){if(n!=1)bad();}static void bad(){deny(ErrorCode.CONTENT_MISMATCH);}static void deny(ErrorCode code){throw new TenantAccessException(code);}
}
