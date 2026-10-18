package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;
/** Authored candidate identity and current read only; deliberately not an execution or publication approval. */
@Component public final class FormalAgentCandidateAuthority {
 private final FormalAgentCandidateStore store;private final FormalAgentPublicationSchema schema;
 public FormalAgentCandidateAuthority(FormalAgentCandidateStore store,FormalAgentPublicationSchema schema){this.store=store;this.schema=schema;}
 public VerifiedCandidateIdentity require(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String targetProject,String purpose)throws IOException {
  if(!java.util.Set.of("AGENT_PUBLICATION","EVALUATION_RESULTS").contains(purpose))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  var loaded=store.read(a,basis,purpose);if(loaded.binding().projectId()!=null&&!Objects.equals(targetProject,loaded.binding().projectId()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
  return new VerifiedCandidateIdentity(this,a,basis,targetProject,purpose,loaded,snapshot(loaded));
 }
 private String snapshot(FormalAgentCandidateStore.Loaded value)throws IOException{return schema.hash(java.util.Map.of("binding",AgentPublicationWitness.tree(value.binding()),"agent",AgentPublicationWitness.tree(value.agent()),"read_grant",AgentPublicationWitness.tree(value.readGrant())));}
 public static final class VerifiedCandidateIdentity {
  private final FormalAgentCandidateAuthority owner;private final EnterpriseAccess.Admission admission;private final PermissionProtocol.Basis basis;private final String project,purpose,digest;private final FormalAgentCandidateStore.Loaded loaded;
  private VerifiedCandidateIdentity(FormalAgentCandidateAuthority owner,EnterpriseAccess.Admission admission,PermissionProtocol.Basis basis,String project,String purpose,FormalAgentCandidateStore.Loaded loaded,String digest){this.owner=owner;this.admission=admission;this.basis=basis;this.project=project;this.purpose=purpose;this.loaded=loaded;this.digest=digest;}
  public PermissionProtocol.Basis candidate(){return basis;}public String agentId(){return loaded.agent().agentId();}public String projectId(){return loaded.binding().projectId();}public String createdBy(){return loaded.binding().createdBy();}public String status(){return loaded.agent().status();}public String classification(){return loaded.binding().classification();}
  public PermissionProtocol.Basis executionPolicy(){return new PermissionProtocol.Basis(loaded.binding().policyVersionId(),loaded.binding().policySha256());}
  public JsonNode manifestDefinition(){return loaded.fixed().required("manifest").deepCopy();}
  public JsonNode runtimeManifest(){return manifestDefinition().required("runtime_manifest");}
  public String runtimeManifestSha256()throws IOException{return owner.schema.hash(runtimeManifest());}
  public String dependencyManifestSha256()throws IOException{return owner.schema.hash(manifestDefinition());}
  public JsonNode authorizations(){return AgentPublicationWitness.tree(loaded.readGrant());}
  public void requireConsumer(EnterpriseAccess.Admission a,PermissionProtocol.Basis candidate,String project,String purpose)throws IOException{if(admission!=a||!basis.equals(candidate)||!Objects.equals(this.project,project)||!this.purpose.equals(purpose))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);requireUnchanged();}
  public void requireUnchanged()throws IOException{var current=owner.require(admission,basis,project,purpose);if(!digest.equals(current.digest))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
}
