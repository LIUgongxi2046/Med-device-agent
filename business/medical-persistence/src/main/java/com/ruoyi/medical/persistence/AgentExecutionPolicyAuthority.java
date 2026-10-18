package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;

/** A proof can only be obtained from the real immutable policy, independent approval and current dependencies. */
@Component public final class AgentExecutionPolicyAuthority {
 private final AgentExecutionPolicyStore store;private final AgentExecutionPolicyDependencies dependencies;private final AgentExecutionPolicySchema schema;private final PermissionSchema json;
 public AgentExecutionPolicyAuthority(AgentExecutionPolicyStore store,AgentExecutionPolicyDependencies dependencies,AgentExecutionPolicySchema schema,PermissionSchema json ){this.store=store;this.dependencies=dependencies;this.schema=schema;this.json=json;}
 public VerifiedPolicy requireByVersion(EnterpriseAccess.Admission a,String id,String hash,String project,String purpose)throws IOException {return requireByVersion(a,id,hash,project,null,purpose);}
 public VerifiedPolicy requireByVersion(EnterpriseAccess.Admission a,String id,String hash,String project,String classification,String purpose)throws IOException {
  WireValues.uuid(id);WireValues.hash(hash);if(project!=null)WireValues.uuid(project);
  if(!Set.of("AGENT_PUBLICATION","TASK_CREATION","PLAN_MAINTENANCE","RUN_CREATION").contains(purpose)||a.simulationWorker())deny(ErrorCode.ACCESS_DENIED);
  var current=check(a,id,hash,project,classification,purpose);return new VerifiedPolicy(this,a,id,hash,project,classification,purpose,current);
 }
 private Snapshot check(EnterpriseAccess.Admission a,String id,String hash,String project,String classification,String purpose)throws IOException {
  var loaded=store.read(a,id,purpose);var fixed=loaded.fixed();var definition=fixed.definition();if(!hash.equals(fixed.definitionSha256()))deny(ErrorCode.CONTENT_MISMATCH);
  if(definition.projectId()!=null&&!definition.projectId().equals(project)||classification!=null&&!definition.allowedClassifications().contains(classification))deny(ErrorCode.ACCESS_DENIED);
  var now=store.now().toInstant(java.time.ZoneOffset.UTC);if(now.isBefore(Instant.parse(definition.validFrom()))||definition.validUntil()!=null&&!now.isBefore(Instant.parse(definition.validUntil())))deny(ErrorCode.SOURCE_NOT_READY);
  var approval=store.approval(a,loaded);var checked=dependencies.require(a,definition,project,purpose,true);
  var auth=json.object();auth.set("read",AgentPublicationWitness.tree(loaded.readGrant()));auth.set("approval",AgentPublicationWitness.tree(approval));auth.set("dependencies",checked.authorizations());
  var identity=json.object();identity.set("fixed",schema.tree(fixed));identity.set("state",AgentPublicationWitness.tree(loaded.state()));identity.set("authorizations",auth);
  return new Snapshot(fixed.policyId(),definition,allVersions(loaded,checked.versions()),auth,schema.hash(identity));
 }
 private record Snapshot(String policyId,Definition definition,List<String> versions,JsonNode auth,String hash){private Snapshot{versions=List.copyOf(versions);auth=auth.deepCopy();}}
 public static final class VerifiedPolicy {
  private final AgentExecutionPolicyAuthority owner;private final EnterpriseAccess.Admission admission;private final String id,hash,project,classification,purpose;private final Snapshot snapshot;
  private VerifiedPolicy(AgentExecutionPolicyAuthority owner,EnterpriseAccess.Admission admission,String id,String hash,String project,String classification,String purpose,Snapshot snapshot){this.owner=owner;this.admission=admission;this.id=id;this.hash=hash;this.project=project;this.classification=classification;this.purpose=purpose;this.snapshot=snapshot;}

  public String policyId(){return snapshot.policyId();}public String businessPurpose(){return definition().businessPurpose();}public String versionId(){return id;}public String contentSha256(){return hash;}public Definition definition(){return snapshot.definition();}
  public JsonNode authorizations(){return snapshot.auth().deepCopy();}public List<String> dependencyVersions(){return snapshot.versions();}
  public void requireConsumer(EnterpriseAccess.Admission a,String targetProject,String entryPurpose){if(admission!=a||!Objects.equals(project,targetProject)||!purpose.equals(entryPurpose))deny(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged(){try{if(!snapshot.hash().equals(owner.check(admission,id,hash,project,classification,purpose).hash()))deny(ErrorCode.SCOPE_STALE);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("EXECUTION_POLICY_RECHECK_FAILED",e);}}
  public NodeBinding requireNode(String nodeKind,PermissionProtocol.Basis roleVersion,String serviceUri,String audience){requireUnchanged();if(!definition().worker().serviceUri().equals(serviceUri)||!definition().worker().audience().equals(audience))deny(ErrorCode.ACCESS_DENIED);var matches=definition().bindings().stream().filter(n->n.nodeKind().equals(nodeKind)&&n.roleVersion().equals(roleVersion)).toList();if(matches.size()!=1)deny(ErrorCode.ACCESS_DENIED);return matches.get(0);}
  public NodeBinding requireNode(String nodeKind,AgentReleaseProtocol.VersionRef roleVersion,String serviceUri,String audience){return requireNode(nodeKind,new PermissionProtocol.Basis(roleVersion.versionId(),roleVersion.contentSha256()),serviceUri,audience);}
  public ActionBinding requireDerivedArtifact(String nodeKind,String artifactKind,String action,String purpose){requireUnchanged();var matches=definition().bindings().stream().filter(n->n.nodeKind().equals(nodeKind)).flatMap(n->n.actions().stream()).filter(x->"RUN_REGISTERED_ARTIFACT".equals(x.targetSelector())&&"ARTIFACT".equals(x.resourceKind())&&x.artifactKinds().contains(artifactKind)&&x.action().equals(action)&&x.purpose().equals(purpose)).toList();if(matches.size()!=1)deny(ErrorCode.ACCESS_DENIED);return matches.get(0);}
  /** This bounded producer has no external-query approval producer; a caller value cannot create one. */
  public void requireApprovedQuery(String fixedQueryRevision){WireValues.uuid(fixedQueryRevision);requireUnchanged();deny(ErrorCode.NETWORK_SCOPE_DENIED);}
 }
 public AuthoredPolicy requireAuthored(EnterpriseAccess.Admission a,String id,String hash,String actualProject)throws IOException {
  WireValues.uuid(id);WireValues.hash(hash);if(a.simulationWorker())deny(ErrorCode.ACCESS_DENIED);return new AuthoredPolicy(this,a,id,hash,actualProject,authored(a,id,hash,actualProject));
 }
 private Snapshot authored(EnterpriseAccess.Admission a,String id,String hash,String project)throws IOException {
  var loaded=store.read(a,id,AgentExecutionPolicySchema.PURPOSE);if(!hash.equals(loaded.fixed().definitionSha256()))deny(ErrorCode.CONTENT_MISMATCH);
  if(!Set.of("DRAFT","APPROVED").contains(loaded.state().status())||loaded.row().projectId()!=null&&!loaded.row().projectId().equals(project))deny(ErrorCode.ACCESS_DENIED);
  var checked=dependencies.require(a,loaded.fixed().definition(),project,AgentExecutionPolicySchema.PURPOSE,false);var auth=json.object();auth.set("read",AgentPublicationWitness.tree(loaded.readGrant()));auth.set("dependencies",checked.authorizations());var identity=json.object();identity.set("fixed",schema.tree(loaded.fixed()));identity.set("state",AgentPublicationWitness.tree(loaded.state()));identity.set("authorizations",auth);
  return new Snapshot(loaded.fixed().policyId(),loaded.fixed().definition(),allVersions(loaded,checked.versions()),auth,schema.hash(identity));
 }
 /** Fixed authored configuration only. It has no approval claim and is not accepted by execution APIs. */
 public static final class AuthoredPolicy {
  private final AgentExecutionPolicyAuthority owner;private final EnterpriseAccess.Admission admission;private final String id,hash,project;private final Snapshot snapshot;
  private AuthoredPolicy(AgentExecutionPolicyAuthority owner,EnterpriseAccess.Admission admission,String id,String hash,String project,Snapshot snapshot){this.owner=owner;this.admission=admission;this.id=id;this.hash=hash;this.project=project;this.snapshot=snapshot;}
  public String policyId(){return snapshot.policyId();}public String versionId(){return id;}public String contentSha256(){return hash;}public Definition definition(){return snapshot.definition();}public JsonNode authorizations(){return snapshot.auth().deepCopy();}public List<String> dependencyVersions(){return snapshot.versions();}
  public void requireConsumer(EnterpriseAccess.Admission a,String targetProject)throws IOException{if(admission!=a||!Objects.equals(project,targetProject))deny(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws IOException{if(!snapshot.hash().equals(owner.authored(admission,id,hash,project).hash()))deny(ErrorCode.SCOPE_STALE);}
 }
 private static List<String> allVersions(AgentExecutionPolicyStore.Loaded loaded,List<String> ancestors){var ids=new TreeSet<>(ancestors);ids.add(loaded.row().versionId());ids.add(loaded.fixed().definition().sourceSelection().version().version_id());ids.add(loaded.fixed().definition().networkPolicy().version().version_id());for(var profile:loaded.fixed().definition().retrievalProfiles())ids.add(profile.version().version_id());return List.copyOf(ids);}
 /** Historical policy metadata is a read-only proof and cannot enter the executable proof API. */
 public HistoricalPolicy requireHistoricalByVersion(EnterpriseAccess.Admission a,String id,String hash,String project,String classification,String purpose)throws IOException{
  WireValues.uuid(id);WireValues.hash(hash);if(a.simulationWorker())deny(ErrorCode.ACCESS_DENIED);
  return new HistoricalPolicy(this,a,id,hash,project,classification,purpose,historical(a,id,hash,project,classification,purpose));
 }
 private Snapshot historical(EnterpriseAccess.Admission a,String id,String hash,String project,String classification,String purpose)throws IOException{
  var loaded=store.read(a,id,purpose);var d=loaded.fixed().definition();if(!hash.equals(loaded.fixed().definitionSha256()))deny(ErrorCode.CONTENT_MISMATCH);
  if(!Set.of("APPROVED","DISABLED").contains(loaded.state().status()))deny(ErrorCode.ACCESS_DENIED);
  if(d.projectId()!=null&&!d.projectId().equals(project)||classification==null||!d.allowedClassifications().contains(classification))deny(ErrorCode.ACCESS_DENIED);
  var checked=dependencies.requireHistorical(a,d,project,purpose);var auth=json.object();auth.set("read",AgentPublicationWitness.tree(loaded.readGrant()));auth.set("dependencies",checked.authorizations());
  var identity=json.object();identity.set("fixed",schema.tree(loaded.fixed()));identity.set("state",AgentPublicationWitness.tree(loaded.state()));identity.set("authorizations",auth);
  return new Snapshot(loaded.fixed().policyId(),d,allVersions(loaded,checked.versions()),auth,schema.hash(identity));
 }
 public static final class HistoricalPolicy{
  private final AgentExecutionPolicyAuthority owner;private final EnterpriseAccess.Admission admission;private final String id,hash,project,classification,purpose;private final Snapshot snapshot;
  private HistoricalPolicy(AgentExecutionPolicyAuthority owner,EnterpriseAccess.Admission admission,String id,String hash,String project,String classification,String purpose,Snapshot snapshot){this.owner=owner;this.admission=admission;this.id=id;this.hash=hash;this.project=project;this.classification=classification;this.purpose=purpose;this.snapshot=snapshot;}
  public JsonNode authorizations(){return snapshot.auth().deepCopy();}public List<String> dependencyVersions(){return snapshot.versions();}
  public String profileEvidencePurpose(){return snapshot.definition().profileEvidencePurpose();}public String businessPurpose(){return snapshot.definition().businessPurpose();}public String versionId(){return id;}public String contentSha256(){return hash;}public Definition definition(){return snapshot.definition();}
  public void requireConsumer(EnterpriseAccess.Admission a,String targetProject,String readPurpose)throws IOException{if(admission!=a||!Objects.equals(project,targetProject)||!purpose.equals(readPurpose))deny(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws IOException{if(!snapshot.hash().equals(owner.historical(admission,id,hash,project,classification,purpose).hash()))deny(ErrorCode.SCOPE_STALE);}
 }
 public static void requireManifest(Definition policy,AgentReleaseProtocol.Manifest manifest){
  var source=manifest.releaseScope().sourceScopePolicy();if(!policy.sourceSelection().version().equals(new PermissionProtocol.Basis(source.versionId(),source.contentSha256())))deny(ErrorCode.CONTENT_MISMATCH);
  var expected=policy.retrievalProfiles().stream().map(r->new AgentReleaseProtocol.VersionRef(r.version().version_id(),r.version().content_sha256())).toList();if(!expected.equals(manifest.retrievalProfiles()))deny(ErrorCode.CONTENT_MISMATCH);
  var roles=new HashSet<AgentReleaseProtocol.VersionRef>();var models=new HashSet<AgentReleaseProtocol.VersionRef>();for(var n:policy.bindings()){
   roles.add(new AgentReleaseProtocol.VersionRef(n.roleVersion().version_id(),n.roleVersion().content_sha256()));
   if(n.modelVersion()!=null)models.add(new AgentReleaseProtocol.VersionRef(n.modelVersion().version_id(),n.modelVersion().content_sha256()));
  }
  if(!roles.equals(new HashSet<>(manifest.roleVersions()))||!models.equals(new HashSet<>(manifest.modelConfigRevisions())))deny(ErrorCode.CONTENT_MISMATCH);
  if(!policy.allowedClassifications().containsAll(manifest.releaseScope().allowedClassifications()))deny(ErrorCode.ACCESS_DENIED);
  if(policy.projectId()!=null&&(!"PROJECTS".equals(manifest.releaseScope().projectMode())||!manifest.releaseScope().projectIds().equals(List.of(policy.projectId()))))deny(ErrorCode.ACCESS_DENIED);
 }
 private static void deny(ErrorCode code){throw new TenantAccessException(code);}
}
