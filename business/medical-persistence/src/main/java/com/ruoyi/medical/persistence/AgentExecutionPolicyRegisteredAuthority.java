package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;
/** Narrow real control-plane policy identity. Never authorizes reading model or source content. */
@Component public final class AgentExecutionPolicyRegisteredAuthority {
 private final AgentExecutionPolicyStore store;private final AgentExecutionPolicySchema schema;private final PermissionSchema json;private final FormalCapabilityIdentityAuthority formalCapabilities;
 public AgentExecutionPolicyRegisteredAuthority(AgentExecutionPolicyStore store,AgentExecutionPolicySchema schema,PermissionSchema json,FormalCapabilityIdentityAuthority formalCapabilities){this.store=store;this.schema=schema;this.json=json;this.formalCapabilities=formalCapabilities;}
 /** Metadata-only worker bootstrap; deliberately returns a different type from executable human admission. */
 public RegisteredPolicyIdentity requireRegisteredPolicyIdentity(FormalWorkerControlAuthority.VerifiedControl control)throws IOException {
  var current=registered(control);return new RegisteredPolicyIdentity(this,control,current);
 }
 private RegisteredSnapshot registered(FormalWorkerControlAuthority.VerifiedControl control)throws IOException {
  control.requireUnchanged();var loaded=store.metadata(control);var d=loaded.fixed().definition();
  if(!loaded.row().versionId().equals(control.policyVersion())||!loaded.row().definitionHash().equals(control.policySha256()))deny(ErrorCode.CONTENT_MISMATCH);
  if(d.projectId()!=null&&!d.projectId().equals(control.projectId())||!d.businessPurpose().equals(control.businessPurpose())||!d.worker().serviceUri().equals(control.serviceUri())||!d.worker().audience().equals(control.audience()))deny(ErrorCode.ACCESS_DENIED);
  var now=store.now().toInstant(java.time.ZoneOffset.UTC);if(now.isBefore(Instant.parse(d.validFrom()))||d.validUntil()!=null&&!now.isBefore(Instant.parse(d.validUntil())))deny(ErrorCode.SOURCE_NOT_READY);
  var approval=store.approval(control.tenantId(),loaded);
  if(!control.runOnly()){var nodes=d.bindings().stream().filter(n->n.nodeKind().equals(control.nodeKind())&&n.roleVersion().version_id().equals(control.roleVersion())&&n.roleVersion().content_sha256().equals(control.roleRawSha())).toList();if(nodes.size()!=1)deny(ErrorCode.ACCESS_DENIED);}
  var snapshot=json.object();snapshot.set("fixed",schema.tree(loaded.fixed()));snapshot.set("state",AgentPublicationWitness.tree(loaded.state()));snapshot.set("read",AgentPublicationWitness.tree(loaded.readGrant()));snapshot.set("approval",AgentPublicationWitness.tree(approval));
  var roleIdentities=snapshot.putArray("formal_role_identities");for(var node:d.bindings())if(control.runOnly()||node.nodeKind().equals(control.nodeKind())){var registered=formalCapabilities.requireRegisteredIdentity(control.tenantId(),control.actorId(),node.roleVersion(),"ROLE",control.projectId());roleIdentities.addObject().put("version_id",registered.version().version_id()).put("metadata_sha256",registered.metadataSha256());}control.requireUnchanged();
  return new RegisteredSnapshot(loaded.fixed(),schema.hash(snapshot));
 }
 private record RegisteredSnapshot(Fixed fixed,String hash){}
 public static final class RegisteredPolicyIdentity {
  private final AgentExecutionPolicyRegisteredAuthority owner;private final FormalWorkerControlAuthority.VerifiedControl control;private final RegisteredSnapshot snapshot;
  private RegisteredPolicyIdentity(AgentExecutionPolicyRegisteredAuthority owner,FormalWorkerControlAuthority.VerifiedControl control,RegisteredSnapshot snapshot){this.owner=owner;this.control=control;this.snapshot=snapshot;}
  public String policyId(){control.requireUnchanged();return snapshot.fixed().policyId();}public String versionId(){control.requireUnchanged();return snapshot.fixed().versionId();}public String contentSha256(){control.requireUnchanged();return snapshot.fixed().definitionSha256();}
  public PermissionProtocol.Scope ownerScope(){requireUnchanged();var fixed=snapshot.fixed();return new PermissionProtocol.Scope(fixed.tenantId(),fixed.definition().projectId(),"CONFIGURATION",fixed.policyId());}
  /** Exact fixed membership only; current control and the complete immutable policy ledger are rechecked first. */
  public boolean ownsVersion(String versionId,String contentSha256){
   requireUnchanged();if(versionId==null||contentSha256==null)return false;
   if(snapshot.fixed().versionId().equals(versionId))return snapshot.fixed().definitionSha256().equals(contentSha256);
   try{return owner.schema.components(snapshot.fixed().definition()).stream().anyMatch(c->c.version().version_id().equals(versionId)&&c.version().content_sha256().equals(contentSha256));}
   catch(IOException e){throw new IllegalStateException("REGISTERED_POLICY_COMPONENT_RECHECK_FAILED",e);}
  }
  public String businessPurpose(){control.requireUnchanged();return snapshot.fixed().definition().businessPurpose();}public Worker worker(){control.requireUnchanged();return snapshot.fixed().definition().worker();}
  public List<NodeBinding> bindings(){requireUnchanged();return snapshot.fixed().definition().bindings();}
  public void requireUnchanged(){try{control.requireUnchanged();if(!snapshot.hash().equals(owner.registered(control).hash()))deny(ErrorCode.SCOPE_STALE);}catch(RuntimeException e){throw e;}catch(IOException e){throw new IllegalStateException("REGISTERED_POLICY_RECHECK_FAILED",e);}}
  public NodeBinding requireNode(String nodeKind,PermissionProtocol.Basis roleVersion,String serviceUri,String audience){requireUnchanged();if(!worker().serviceUri().equals(serviceUri)||!worker().audience().equals(audience))deny(ErrorCode.ACCESS_DENIED);if(!control.runOnly()&&(!control.nodeKind().equals(nodeKind)||!control.roleVersion().equals(roleVersion.version_id())||!control.roleRawSha().equals(roleVersion.content_sha256())))deny(ErrorCode.ACCESS_DENIED);var nodes=snapshot.fixed().definition().bindings().stream().filter(n->n.nodeKind().equals(nodeKind)&&n.roleVersion().equals(roleVersion)).toList();if(nodes.size()!=1)deny(ErrorCode.ACCESS_DENIED);return nodes.get(0);}
  public ActionBinding requireDerivedArtifact(String nodeKind,String artifactKind,String action,String purpose){requireUnchanged();if(control.runOnly()||!control.nodeKind().equals(nodeKind))deny(ErrorCode.ACCESS_DENIED);var bindings=snapshot.fixed().definition().bindings().stream().filter(n->n.nodeKind().equals(nodeKind)).flatMap(n->n.actions().stream()).filter(a->"RUN_REGISTERED_ARTIFACT".equals(a.targetSelector())&&"ARTIFACT".equals(a.resourceKind())&&a.artifactKinds().contains(artifactKind)&&a.action().equals(action)&&a.purpose().equals(purpose)).toList();if(bindings.size()!=1)deny(ErrorCode.ACCESS_DENIED);return bindings.get(0);}
 }
 private static void deny(ErrorCode code){throw new TenantAccessException(code);}
}
