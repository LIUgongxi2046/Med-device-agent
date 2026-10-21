package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;import java.util.*;
import org.springframework.beans.factory.ObjectProvider;import org.springframework.stereotype.Component;
/** Private current whole-publication proof; fixed local metadata alone can never produce one. */
@Component public final class FormalAgentPublicationAuthorization {
 private final FormalAgentAcceptanceStore acceptances;private final FormalAgentCandidateDependencies dependencies;private final FormalAgentPublicationSchema schema;
 private final ResourceReadAuthority resources;private final ObjectProvider<PublicationEvidenceRevalidation> evidence;private final PermissionMapper permissions;private final PermissionAuthority grants;private final PermissionSchema json;
 public FormalAgentPublicationAuthorization(FormalAgentAcceptanceStore acceptances,FormalAgentCandidateDependencies dependencies,FormalAgentPublicationSchema schema,ResourceReadAuthority resources,ObjectProvider<PublicationEvidenceRevalidation> evidence,PermissionMapper permissions,PermissionAuthority grants,PermissionSchema json){this.acceptances=acceptances;this.dependencies=dependencies;this.schema=schema;this.resources=resources;this.evidence=evidence;this.permissions=permissions;this.grants=grants;this.json=json;}
 Verified require(EnterpriseAccess.Admission a,PermissionProtocol.Basis acceptance,boolean writing,boolean historical,PublicationEvidenceRevalidation.Prepared prepared)throws Exception{
  if(writing&&historical||a.simulationWorker())deny(ErrorCode.ACCESS_DENIED);
  var adapter=evidence.getIfAvailable();if(adapter==null)deny(ErrorCode.DEPENDENCY_UNAVAILABLE);
  var loaded=acceptances.metadata(a,acceptance,"AGENT_PUBLICATION");if(!"ACCEPT".equals(loaded.row().decision()))deny(ErrorCode.SOURCE_NOT_READY);
  var candidate=loaded.candidate();if(!loaded.row().evaluationId().equals(candidate.agent().evaluationId())||(!historical&&!Set.of("REVIEW_PENDING","PUBLISHED").contains(candidate.agent().status())))deny(ErrorCode.SOURCE_NOT_READY);var policy=new PermissionProtocol.Basis(candidate.binding().policyVersionId(),candidate.binding().policySha256());
  var deps=historical?dependencies.requireHistorical(a,loaded.row().projectId(),policy,candidate.fixed().required("manifest"),loaded.row().classification()):dependencies.require(a,loaded.row().projectId(),policy,candidate.fixed().required("manifest"),true);
  var authorization=json.object();authorization.set("dependencies",deps.authorizations());authorization.set("current_read",AgentPublicationWitness.tree(loaded.readGrant()));
  if(!historical)authorization.set("acceptance_approval",AgentPublicationWitness.tree(currentApproval(a,loaded)));
  if(writing){if(candidate.binding().createdBy().equals(a.actorId()))deny(ErrorCode.ACCESS_DENIED);authorization.set("publication_write",AgentPublicationWitness.tree(resources.require(a,FormalAgentCandidateService.governance(a,loaded.row().projectId()),"agent.publish","AGENT_PUBLICATION")));resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),loaded.row().projectId(),"CONFIGURATION",candidate.agent().agentId()),"artifact.register","AGENT_PUBLICATION");}
  var allVersions=new TreeSet<String>(deps.versions());allVersions.add(acceptance.version_id());allVersions.addAll(adapter.requireCurrentDependencies(a,loaded.content().deepCopy(),historical,prepared));if(allVersions.size()>10000)deny(ErrorCode.CONTEXT_LIMIT);
  var identity=json.object();identity.set("candidate",AgentPublicationWitness.tree(candidate.agent()));identity.set("acceptance",loaded.content());identity.set("authorization",authorization);
  return new Verified(this,a,acceptance,writing,historical,loaded,List.copyOf(allVersions),authorization,schema.hash(identity),prepared);
 }
 private PermissionProtocol.GrantRow currentApproval(EnterpriseAccess.Admission a,FormalAgentAcceptanceStore.Loaded loaded)throws Exception{
  var original=schema.read(loaded.decision().authorizationsJson()).required("governance");String actor=loaded.row().createdBy(),grantId=original.required("grant_id").asText();
  var member=AgentPublicationJournal.one(permissions.targetMember(actor));var binding=AgentPublicationJournal.one(permissions.targetBinding(actor));var now=Objects.requireNonNull(permissions.databaseTime());
  if(!a.context().tenant_id().equals(member.tenantId())||!actor.equals(member.userId())||!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!actor.equals(binding.userId())||!"ACTIVE".equals(binding.status()))deny(ErrorCode.ACCESS_DENIED);
  var grant=AgentPublicationJournal.one(permissions.currentGrant(grantId));var scope=FormalAgentCandidateService.governance(a,loaded.row().projectId());grants.requireResource(scope,actor,binding.mode(),"agent.publish","AGENT_PUBLICATION",List.of(grant),now);
  if(!grant.revision().toString().equals(original.required("revision").asText()))deny(ErrorCode.SCOPE_STALE);return grant;
 }
 public static final class Verified {
  private final FormalAgentPublicationAuthorization owner;private final EnterpriseAccess.Admission admission;private final PermissionProtocol.Basis acceptance;private final boolean writing,historical;private final FormalAgentAcceptanceStore.Loaded loaded;private final List<String> versions;private final JsonNode auth;private final String fingerprint;private final PublicationEvidenceRevalidation.Prepared prepared;
  private Verified(FormalAgentPublicationAuthorization owner,EnterpriseAccess.Admission admission,PermissionProtocol.Basis acceptance,boolean writing,boolean historical,FormalAgentAcceptanceStore.Loaded loaded,List<String> versions,JsonNode auth,String fingerprint,PublicationEvidenceRevalidation.Prepared prepared){this.owner=owner;this.admission=admission;this.acceptance=acceptance;this.writing=writing;this.historical=historical;this.loaded=loaded;this.versions=List.copyOf(versions);this.auth=auth.deepCopy();this.fingerprint=fingerprint;this.prepared=prepared;}
  public PermissionProtocol.Basis executionPolicy(){return new PermissionProtocol.Basis(loaded.candidate().binding().policyVersionId(),loaded.candidate().binding().policySha256());}
  public AgentReleaseProtocol.Validated manifest(){return loaded.manifest();}public String projectId(){return loaded.row().projectId();}public String tenantId(){return loaded.row().tenantId();}public String actorId(){return admission.actorId();}public String classification(){return loaded.row().classification();}public PermissionProtocol.Basis acceptance(){return acceptance;}public String agentId(){return loaded.candidate().agent().agentId();}public String candidateRevision(){return loaded.candidate().agent().revision().toString();}public String candidateStatus(){return loaded.candidate().agent().status();}public List<String> dependencyVersions(){return versions;}public JsonNode authorizations(){return auth.deepCopy();}
  void requireWrite(EnterpriseAccess.Admission a)throws Exception{if(admission!=a||!writing||historical)deny(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  void requireRead(EnterpriseAccess.Admission a)throws Exception{if(admission!=a)deny(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws Exception{var now=owner.require(admission,acceptance,writing,historical,prepared);if(!fingerprint.equals(now.fingerprint))deny(ErrorCode.SCOPE_STALE);}
 }
 private static void deny(ErrorCode code){throw new TenantAccessException(code);}
}
