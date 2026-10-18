package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;

/** Real complete lineage/current-rights checks; server profiles cannot be supplied by a policy author. */
@Component public final class AgentExecutionPolicyDependencies {
 private final FormalCapabilityIdentityAuthority formalCapabilities;private final AgentPublicationDependencies dependencies;private final AgentExecutionPolicyMapper mapper;private final ArtifactRegistryMapper artifacts;
 private final SourceGovernanceMapper indexConfigurations;private final ObjectProvider<SourceIndexPolicy> indexPolicies;
 private final ObjectProvider<AgentExecutionPolicyModelPort> models;private final EvaluationDependencyPolicy deployment;
 private final AgentExecutionPolicySchema schema;private final PermissionSchema json;
 public AgentExecutionPolicyDependencies(AgentPublicationDependencies dependencies,AgentExecutionPolicyMapper mapper,ArtifactRegistryMapper artifacts,
   SourceGovernanceMapper indexConfigurations,ObjectProvider<SourceIndexPolicy> indexPolicies,ObjectProvider<AgentExecutionPolicyModelPort> models,
   EvaluationDependencyPolicy deployment,AgentExecutionPolicySchema schema,PermissionSchema json,FormalCapabilityIdentityAuthority formalCapabilities){
  this.formalCapabilities=formalCapabilities;this.dependencies=dependencies;this.mapper=mapper;this.artifacts=artifacts;this.indexConfigurations=indexConfigurations;this.indexPolicies=indexPolicies;this.models=models;this.deployment=deployment;this.schema=schema;this.json=json;
 }
 public record Checked(List<String> versions,JsonNode authorizations) {public Checked{versions=List.copyOf(versions);authorizations=authorizations.deepCopy();}@Override public JsonNode authorizations(){return authorizations.deepCopy();}}
 Checked require(EnterpriseAccess.Admission a,Definition definition,String actualProject,String purpose,boolean executionReady)throws IOException {
  try{return requireChecked(a,definition,actualProject,purpose,executionReady,false);}catch(RuntimeException e){throw e;}catch(IOException e){throw e;}catch(Exception e){throw new IOException("EXECUTION_POLICY_DEPENDENCY_INITIALIZATION_FAILED",e);}
 }
 Checked requireHistorical(EnterpriseAccess.Admission a,Definition definition,String actualProject,String purpose)throws IOException {
  try{return requireChecked(a,definition,actualProject,purpose,false,true);}catch(RuntimeException e){throw e;}catch(IOException e){throw e;}catch(Exception e){throw new IOException("EXECUTION_POLICY_DEPENDENCY_INITIALIZATION_FAILED",e);}
 }
 private Checked requireChecked(EnterpriseAccess.Admission a,Definition definition,String actualProject,String purpose,boolean executionReady,boolean historical)throws Exception {
  if(executionReady&&definition.bindings().stream().anyMatch(n->n.modelVersion()!=null)&&models.getIfAvailable()==null)deny(ErrorCode.DEPENDENCY_UNAVAILABLE);
  deployment.requireConfigured();if(!deployment.environment().equals(definition.environment())||!deployment.region().equals(definition.region()))deny(ErrorCode.ACCESS_DENIED);
  if(definition.projectId()!=null&&!definition.projectId().equals(actualProject))deny(ErrorCode.ACCESS_DENIED);
  var selectedSources=definition.sourceSelection().sources().stream().map(PermissionProtocol.Basis::version_id).collect(java.util.stream.Collectors.toSet());
  var uses=new ArrayList<AgentPublicationDependencies.DependencyUse>();for(var root:schema.dependencies(definition))uses.add(new AgentPublicationDependencies.DependencyUse(root,selectedSources.contains(root.version_id())?definition.businessPurpose():AgentExecutionPolicySchema.PURPOSE));
  // A root used in both roles must retain both explicit uses so conflicting closures are rejected.
  for(var node:definition.bindings())for(var action:node.actions())if(action.fixedVersion()!=null&&selectedSources.contains(action.fixedVersion().version_id()))uses.add(new AgentPublicationDependencies.DependencyUse(action.fixedVersion(),AgentExecutionPolicySchema.PURPOSE));
  var checked=dependencies.requireByUse(a,actualProject,uses);var authorizations=json.object();authorizations.set("dependencies",checked.authorizations());
  var runtime=json.object().arrayNode();
  for(var source:definition.sourceSelection().sources()) {var row=one(mapper.source(values(a,source.version_id())));domain(row,source,"SOURCE_VERSION",actualProject);var sourceProof=checked.authorizations().path(source.version_id()).path("source");if(!definition.businessPurpose().equals(sourceProof.path("rights").path("purpose").asText()))deny(ErrorCode.ACCESS_DENIED);}
  for(var retrieval:definition.retrievalProfiles()) {
   if(historical)continue;
   var policy=indexPolicies.getIfAvailable();if(policy==null)deny(ErrorCode.DEPENDENCY_UNAVAILABLE);
   if(!policy.environment().equals(definition.environment())||!policy.region().equals(definition.region()))deny(ErrorCode.ACCESS_DENIED);
   var v=values(a,retrieval.indexConfiguration().version_id());v.put("configRevision",retrieval.indexConfiguration().version_id());
   var row=one(indexConfigurations.processingConfig(v));new SourceIndexManifest().requireCapability(row,policy);
   if(!retrieval.indexConfiguration().content_sha256().equals(row.contentSha256()))deny(ErrorCode.CONTENT_MISMATCH);
   runtime.addObject().put("kind","FULLTEXT_INDEX_CONFIGURATION").put("version_id",row.versionId()).put("content_sha256",row.contentSha256());
  }
  for(var node:definition.bindings()) {
   var role=one(mapper.role(values(a,node.roleVersion().version_id())));domain(role,node.roleVersion(),"CAPABILITY_VERSION",actualProject);
   if(!"ROLE".equals(role.kind())||!historical&&!"DRAFT".equals(role.status()))deny(ErrorCode.SOURCE_NOT_READY);
   var proof=runtime.addObject().put("node_kind",node.nodeKind()).put("role_version",role.versionId()).put("role_status",role.status());
   if(!historical){var formal=formalCapabilities.require(a,node.roleVersion(),"ROLE",actualProject,AgentExecutionPolicySchema.PURPOSE);if(!formal.version().equals(node.roleVersion())||!"DRAFT".equals(formal.status()))deny(ErrorCode.CONTENT_MISMATCH);formal.requireUnchanged();proof.set("formal_role",formal.canonicalDefinition());proof.set("formal_role_authorizations",formal.authorizations());proof.put("authorization_scope","EXACT_POLICY_NODE_BINDING");}
   for(var action:node.actions())if(action.fixedVersion()!=null) {
    var fixed=checked.authorizations().get(action.fixedVersion().version_id());if(fixed==null||!action.resourceKind().equals(fixed.required("owner").required("resource_kind").textValue()))deny(ErrorCode.CONTENT_MISMATCH);
   }
   if(node.modelVersion()!=null) {
    var model=one(mapper.model(values(a,node.modelVersion().version_id())));domain(model,node.modelVersion(),"MODEL_CONFIG",actualProject);
    if(!"EXTERNAL".equals(model.executionMode())||!historical&&"DISABLED".equals(model.status()))deny(ErrorCode.SOURCE_NOT_READY);
    var artifact=one(artifacts.artifact(node.tokenizerVersion().version_id()));var expected=node.tokenizerArtifact().artifact();var binding=one(artifacts.binding(node.tokenizerVersion().version_id()));
    if(!a.context().tenant_id().equals(artifact.tenantId())||!expected.artifactId().equals(artifact.artifactId())||!expected.sha256().equals(artifact.sha256())
      ||!artifact.contentSha256().equals(artifact.sha256())||!expected.sizeBytes().equals(artifact.sizeBytes().toString())||!expected.mediaType().equals(artifact.mediaType())
      ||!node.tokenizerArtifact().schema().schemaRef().equals(binding.schemaRef())||!node.tokenizerArtifact().schema().schemaSha256().equals(binding.schemaDocumentSha256()))deny(ErrorCode.CONTENT_MISMATCH);
    StoredObjectReferences.decode(artifact.storedObject(),artifact.reference());
    proof.put("model_revision",model.versionId()).put("tokenizer_artifact",artifact.artifactId()).put("tokenizer_sha256",artifact.sha256());
    if(executionReady) {
     var authority=models.getIfAvailable();if(authority==null)deny(ErrorCode.DEPENDENCY_UNAVAILABLE);
     var modelProof=authority.require(a,node,actualProject,definition.allowedClassifications(),definition.region());
     if(modelProof==null||!modelProof.isObject()||modelProof.size()!=2||!modelProof.has("model_binding")||!modelProof.has("model_authorizations"))deny(ErrorCode.CONTENT_MISMATCH);
     proof.set("model_binding",modelProof.required("model_binding"));proof.set("model_authorizations",modelProof.required("model_authorizations"));
    }
   }
  }
  authorizations.set("runtime",runtime);return new Checked(checked.versionIds(),authorizations);
 }
 private static Map<String,Object> values(EnterpriseAccess.Admission a,String id){var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("versionId",id);return v;}
 private static void domain(DomainRow row,PermissionProtocol.Basis ref,String kind,String project){if(!row.versionId().equals(ref.version_id())||!row.contentHash().equals(ref.content_sha256())||!row.entityKind().equals(kind))deny(ErrorCode.CONTENT_MISMATCH);if(row.projectId()!=null&&!row.projectId().equals(project))deny(ErrorCode.ACCESS_DENIED);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)deny(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}private static void deny(ErrorCode code){throw new TenantAccessException(code);}
}
