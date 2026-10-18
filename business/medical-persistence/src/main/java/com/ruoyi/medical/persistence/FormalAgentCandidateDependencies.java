package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
/** Typed fixed root checks and a complete, conflict-rejecting provenance closure. */
@Component public final class FormalAgentCandidateDependencies {
 private final AgentExecutionPolicyAuthority policies;private final AgentExecutionPolicySchema policySchema;private final AgentPublicationDependencies dependencies;
 private final FormalCapabilityIdentityAuthority capabilities;private final FormalRuntimeCapabilityIdentityAuthority runtimeCapabilities;
 private final TemplateVersionStore templates;private final EvaluationRuleAuthority rules;private final ArtifactRegistryMapper artifacts;
 private final FormalAgentPublicationSchema schema;private final PermissionSchema json;
 public FormalAgentCandidateDependencies(AgentExecutionPolicyAuthority policies,AgentExecutionPolicySchema policySchema,AgentPublicationDependencies dependencies,FormalCapabilityIdentityAuthority capabilities,FormalRuntimeCapabilityIdentityAuthority runtimeCapabilities,TemplateVersionStore templates,EvaluationRuleAuthority rules,ArtifactRegistryMapper artifacts,FormalAgentPublicationSchema schema,PermissionSchema json){this.policies=policies;this.policySchema=policySchema;this.dependencies=dependencies;this.capabilities=capabilities;this.runtimeCapabilities=runtimeCapabilities;this.templates=templates;this.rules=rules;this.artifacts=artifacts;this.schema=schema;this.json=json;}
 public record Checked(List<String> versions,JsonNode authorizations){public Checked{versions=List.copyOf(versions);authorizations=authorizations.deepCopy();}@Override public JsonNode authorizations(){return authorizations.deepCopy();}}
 Checked require(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis policy,JsonNode manifest,boolean publication)throws IOException {
  return requireInternal(a,project,policy,manifest,publication,false,null);
 }
 Checked requireHistorical(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis policy,JsonNode manifest,String classification)throws IOException{return requireInternal(a,project,policy,manifest,false,true,classification);}
 private Checked requireInternal(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis policy,JsonNode manifest,boolean publication,boolean historical,String classification)throws IOException{
  schema.validate("CandidateManifest",manifest);AgentExecutionPolicyProtocol.Definition definition;JsonNode policyAuth;
  if(historical){var p=policies.requireHistoricalByVersion(a,policy.version_id(),policy.content_sha256(),project,classification,"AGENT_PUBLICATION");definition=p.definition();policyAuth=p.authorizations();p.requireUnchanged();}
  else if(publication){var p=policies.requireByVersion(a,policy.version_id(),policy.content_sha256(),project,"AGENT_PUBLICATION");definition=p.definition();policyAuth=p.authorizations();}
  else{var p=policies.requireAuthored(a,policy.version_id(),policy.content_sha256(),project);definition=p.definition();policyAuth=p.authorizations();}
  var expectedRoles=new HashSet<PermissionProtocol.Basis>();var expectedModels=new HashSet<PermissionProtocol.Basis>();for(var b:definition.bindings()){expectedRoles.add(b.roleVersion());if(b.modelVersion()!=null)expectedModels.add(b.modelVersion());}
  if(!expectedRoles.equals(new HashSet<>(refs(manifest.path("role_versions"))))||!expectedModels.equals(new HashSet<>(refs(manifest.path("model_config_revisions")))))bad();
  if(!definition.sourceSelection().version().equals(basis(manifest.path("release_scope").path("source_scope_policy")))||!definition.retrievalProfiles().stream().map(AgentExecutionPolicyProtocol.RetrievalProfile::version).toList().equals(refs(manifest.path("retrieval_profiles"))))bad();
  var scope=manifest.path("release_scope");if(!a.context().tenant_id().equals(scope.path("tenant_id").asText()))bad();
  var classifications=new HashSet<String>();scope.path("allowed_classifications").forEach(x->classifications.add(x.asText()));if(!definition.allowedClassifications().containsAll(classifications))deny();
  if(project!=null&&(!"PROJECTS".equals(scope.path("project_mode").asText())||scope.path("project_ids").size()!=1||!project.equals(scope.path("project_ids").get(0).asText())))deny();
  var uses=new ArrayList<AgentPublicationDependencies.DependencyUse>();var selected=definition.sourceSelection().sources().stream().map(PermissionProtocol.Basis::version_id).collect(java.util.stream.Collectors.toSet());
  for(var ref:policySchema.dependencies(definition))uses.add(new AgentPublicationDependencies.DependencyUse(ref,selected.contains(ref.version_id())?definition.businessPurpose():"AGENT_PUBLICATION"));
  for(var node:definition.bindings())for(var action:node.actions())if(action.fixedVersion()!=null&&selected.contains(action.fixedVersion().version_id()))uses.add(new AgentPublicationDependencies.DependencyUse(action.fixedVersion(),"AGENT_PUBLICATION"));
  var auth=json.object();auth.set("execution_policy",policyAuth);var typed=auth.putObject("typed_roots");
  for(String field:List.of("role_versions","prompt_revisions","skill_versions","tool_versions"))for(var ref:refs(manifest.path(field))){String kind=switch(field){case "role_versions"->"ROLE";case "prompt_revisions"->"PROMPT";case "skill_versions"->"SKILL";default->"TOOL";};
   if(Set.of("ROLE","PROMPT").contains(kind)){var proof=capabilities.require(a,ref,kind,project,"AGENT_PUBLICATION");typed.set(ref.version_id(),proof.authorizations());}else{var proof=runtimeCapabilities.require(a,ref,kind,project,"AGENT_PUBLICATION");typed.set(ref.version_id(),proof.authorizations());}
   uses.add(new AgentPublicationDependencies.DependencyUse(ref,"AGENT_PUBLICATION"));
  }
  for(var ref:refs(manifest.path("template_versions"))){var template=publication?templates.applicationTarget(a,ref.version_id(),"AGENT_PUBLICATION"):templates.historicalTarget(a,ref.version_id(),"AGENT_PUBLICATION");if(!ref.content_sha256().equals(template.content_sha256()))bad();uses.add(new AgentPublicationDependencies.DependencyUse(ref,"TEMPLATE_MAINTENANCE"));}
  for(var ref:refs(manifest.path("rule_versions"))){rules.require(a,ref,publication);uses.add(new AgentPublicationDependencies.DependencyUse(ref,"TEMPLATE_MAINTENANCE"));}
  var runtimeRoots=new TreeMap<String,PermissionProtocol.Basis>();collect(manifest.path("runtime_manifest"),runtimeRoots,a,project);collect(manifest.path("human_checkpoints"),runtimeRoots,a,project);
  for(var ref:runtimeRoots.values())uses.add(new AgentPublicationDependencies.DependencyUse(ref,"AGENT_PUBLICATION"));
  try{var catalogue=SchemaCatalogue.fromClasspath();schemas(manifest,catalogue);}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
  var full=dependencies.requireByUse(a,project,uses);auth.set("dependencies",full.authorizations());var versions=new TreeSet<>(full.versionIds());versions.add(policy.version_id());return new Checked(List.copyOf(versions),auth);
 }
 private void collect(JsonNode node,Map<String,PermissionProtocol.Basis> result,EnterpriseAccess.Admission a,String project)throws IOException {
  if(node.isObject()){
   if(node.has("version_id")&&node.has("content_sha256"))add(result,basis(node));
   if(node.has("artifact_id")&&node.has("sha256")){String id=node.path("artifact_id").asText();var row=AgentPublicationJournal.one(artifacts.artifact(id));if(!a.context().tenant_id().equals(row.tenantId())||row.projectId()!=null&&!Objects.equals(project,row.projectId())||!node.path("sha256").asText().equals(row.sha256())||!row.contentSha256().equals(row.sha256())||!node.path("size_bytes").asText().equals(row.sizeBytes().toString())||!node.path("media_type").asText().equals(row.mediaType()))bad();StoredObjectReferences.decode(row.storedObject(),row.reference());add(result,new PermissionProtocol.Basis(id,row.sha256()));}
   var iterator=node.elements();while(iterator.hasNext())collect(iterator.next(),result,a,project);
  }else if(node.isArray())for(var child:node)collect(child,result,a,project);
 }
 private static void schemas(JsonNode node,SchemaCatalogue catalogue){if(node.isObject()){if(node.has("schema_ref")&&node.has("schema_sha256"))catalogue.requirePublished(node.path("schema_ref").asText(),node.path("schema_sha256").asText());node.elements().forEachRemaining(n->schemas(n,catalogue));}else if(node.isArray())node.forEach(n->schemas(n,catalogue));}
 static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.path("version_id").asText(),n.path("content_sha256").asText());}
 private static List<PermissionProtocol.Basis> refs(JsonNode array){var result=new ArrayList<PermissionProtocol.Basis>();var ids=new HashSet<String>();for(var n:array){var ref=basis(n);if(!ids.add(ref.version_id()))bad();result.add(ref);}return List.copyOf(result);}
 private static void add(Map<String,PermissionProtocol.Basis> values,PermissionProtocol.Basis ref){var old=values.putIfAbsent(ref.version_id(),ref);if(old!=null&&!old.equals(ref))bad();}
 static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
