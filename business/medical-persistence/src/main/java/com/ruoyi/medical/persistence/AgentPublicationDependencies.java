package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

/** Complete current dependency proof. No caller-supplied artifact read profile and no purpose fallback. */
@Component public final class AgentPublicationDependencies {
 private static final Set<String> PURPOSES=Set.of("MODEL_VERIFICATION","AGENT_PUBLICATION","EVALUATION_RESULTS","TASK_CREATION","PLAN_MAINTENANCE","RUN_CREATION");
 private final DependencyLineage lineage;private final VersionResourceAuthority versions;private final SourceReadAuthority sources;
 private final SourceReadMapper sourceRows;private final ArtifactRegistryMapper artifacts;private final EvaluationReferences profiles;
 private final EvaluationDependencyPolicy limits;private final PermissionSchema json;
 public AgentPublicationDependencies(DependencyLineage lineage,VersionResourceAuthority versions,SourceReadAuthority sources,SourceReadMapper sourceRows,
  ArtifactRegistryMapper artifacts,EvaluationReferences profiles,EvaluationDependencyPolicy limits,PermissionSchema json){this.lineage=lineage;this.versions=versions;this.sources=sources;this.sourceRows=sourceRows;this.artifacts=artifacts;this.profiles=profiles;this.limits=limits;this.json=json;}
 /** Source rights are verified side dependencies, not fabricated lineage ancestors. */
 public record Checked(List<String> versionIds,JsonNode authorizations,List<PermissionProtocol.Basis> sourceRights){
  public Checked(List<String> versionIds,JsonNode authorizations){this(versionIds,authorizations,List.of());}
  public Checked{versionIds=List.copyOf(versionIds);authorizations=authorizations.deepCopy();sourceRights=List.copyOf(sourceRights);}
  @Override public JsonNode authorizations(){return authorizations.deepCopy();}
  public List<String> requiredVersionIds(){var ids=new TreeSet<String>(versionIds);for(var rights:sourceRights)ids.add(rights.version_id());return List.copyOf(ids);}
 }
 public record DependencyUse(PermissionProtocol.Basis root,String purpose) {public DependencyUse {Objects.requireNonNull(root);if(purpose==null||!purpose.matches("[A-Z][A-Z0-9_]{0,127}"))throw new IllegalArgumentException("Explicit dependency purpose required");}}
 public Checked requireByUse(EnterpriseAccess.Admission a,String project,List<DependencyUse> uses)throws IOException {
  limits.requireConfigured();if(uses.size()>limits.maximumVersions())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  var all=new TreeMap<String,JsonNode>();var rightsPins=new TreeMap<String,PermissionProtocol.Basis>();var selected=new HashMap<String,String>();var allEdges=new HashSet<SourceReadProtocol.ParentRow>();
  for(var use:uses){var graph=lineage.inspect(use.root().version_id(),limits.limits());allEdges.addAll(graph.graph().edgeSet());for(var version:graph.versions().keySet()){String old=selected.putIfAbsent(version,use.purpose());if(old!=null&&!old.equals(use.purpose()))bad();}
   if(selected.size()>limits.maximumVersions()||allEdges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
   var checked=requireSinglePurpose(a,project,List.of(use.root()),use.purpose());for(var right:checked.sourceRights())addRight(rightsPins,right);for(var id:checked.versionIds()){var value=checked.authorizations().path(id);var old=all.putIfAbsent(id,value);if(old!=null&&!json.canonical(old).equals(json.canonical(value)))bad();}
  }var auth=json.object();all.forEach(auth::set);return new Checked(List.copyOf(all.keySet()),auth,List.copyOf(rightsPins.values()));
 }
 public Checked require(EnterpriseAccess.Admission a,String project,List<PermissionProtocol.Basis> roots,String purpose)throws IOException {
  if(!PURPOSES.contains(purpose))bad();return requireSinglePurpose(a,project,roots,purpose);
 }
 private Checked requireSinglePurpose(EnterpriseAccess.Admission a,String project,List<PermissionProtocol.Basis> roots,String purpose)throws IOException {
  limits.requireConfigured();var seen=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();var proofs=json.object();var rightsPins=new TreeMap<String,PermissionProtocol.Basis>();
  for(var ref:roots){var graph=lineage.inspect(ref.version_id(),limits.limits());var root=graph.versions().get(ref.version_id());if(root==null||!ref.content_sha256().equals(root.contentSha256()))bad();
   for(var v:graph.versions().values()){var old=seen.putIfAbsent(v.id(),v);if(old!=null&&!old.equals(v))bad();if(v.projectId()!=null&&!v.projectId().equals(project))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
   edges.addAll(graph.graph().edgeSet());if(seen.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
   var sourceProofs=new TreeMap<String,SourceReadProtocol.VerifiedSource>();var required=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
   for(var v:graph.versions().values()){
    var source=sourceRows.source(v.id());if(source.size()>1)bad();if(source.size()==1){if(!"SOURCE_VERSION".equals(v.entityKind()))bad();var row=source.get(0);sourceProofs.put(v.id(),sources.require(a,new SourceReadProtocol.ReadRequirement(row.sourceVersion(),row.rightsRevision(),project,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,limits.environment(),limits.region(),null)));}
    if("ARTIFACT".equals(v.entityKind())){var binding=AgentPublicationJournal.one(artifacts.binding(v.id()));var profile=profiles.profile(binding.schemaRef(),purpose,binding.resourceKind());if(!profile.resourceKind().equals(binding.resourceKind()))bad();
     required.put(v.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(a.context().tenant_id(),binding.projectId(),binding.resourceKind(),binding.resourceId()),profile.action(),purpose,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),limits.limits()));}
   }
   for(var source:sourceProofs.values())addRight(rightsPins,sourceRight(source));
   requirePrimarySources(graph,required,sourceProofs);
   var checked=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),required);
   for(var v:checked.values()){
    if(v.owner().sourceVersion()!=null){var source=sourceProofs.get(v.owner().sourceVersion());if(source==null||!source.source().sourceId().equals(v.owner().resourceId())||!graph.reachableFrom(v.version().id()).contains(v.owner().sourceVersion()))bad();}
    if(v.artifactRegistration()!=null)for(var member:v.artifactRegistration().manifest().members()){var rights=AgentPublicationJournal.one(sourceRows.rights(member.rightsRevision()));var source=sourceProofs.get(rights.sourceVersion());if(source==null||!source.rights().rightsRevision().equals(member.rightsRevision())||!graph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion()))bad();}
    var proof=json.object().put("purpose",purpose);proof.set("resource",AgentPublicationWitness.tree(v.resourceGrant()));proof.set("version_project",AgentPublicationWitness.tree(v.versionProjectGrant()));proof.set("owner",json.tree(v.owner()));proof.set("source",AgentPublicationWitness.tree(sourceProofs.get(v.version().id())));
    var old=proofs.get(v.version().id());if(old!=null&&!json.canonical(old).equals(json.canonical(proof)))bad();proofs.set(v.version().id(),proof);
   }
  }return new Checked(List.copyOf(seen.keySet()),proofs,List.copyOf(rightsPins.values()));
 }
 /** Composite metadata roots have their own read action, while all real upstream uses are explicitly typed.
  * The caller must derive child roots from validated immutable content, never from rights.purpose. */
 public Checked requireTypedAggregate(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis root,String rootPurpose,
   List<DependencyUse> typedChildren,boolean sourceOwnedArtifact)throws IOException {
  return requireTypedTree(a,project,new TypedUse(root,rootPurpose,typedChildren.stream().map(x->new TypedUse(x.root(),x.purpose(),List.of(),false)).toList(),sourceOwnedArtifact));
 }
 /** Descriptive typed input, not a permission proof; real document/domain validators must derive this tree. */
 public record TypedUse(PermissionProtocol.Basis root,String purpose,List<TypedUse> children,boolean sourceOwnedArtifact){public TypedUse{Objects.requireNonNull(root);children=List.copyOf(children);if(purpose==null||!purpose.matches("[A-Z][A-Z0-9_]{0,127}"))throw new IllegalArgumentException("Explicit purpose required");}}
 public Checked requireTypedTree(EnterpriseAccess.Admission a,String project,TypedUse use)throws IOException{return requireTypedTree(a,project,use,0,new int[]{0});}
 private Checked requireTypedTree(EnterpriseAccess.Admission a,String project,TypedUse use,int depth,int[] budget)throws IOException{
  if(depth>16||++budget[0]>512)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
  if(use.children().isEmpty())return requireByUse(a,project,List.of(new DependencyUse(use.root(),use.purpose())));
  var root=use.root();String rootPurpose=use.purpose();var typedChildren=use.children();boolean sourceOwnedArtifact=use.sourceOwnedArtifact();
  limits.requireConfigured();var graph=lineage.inspect(root.version_id(),limits.limits());var registered=graph.versions().get(root.version_id());
  if(registered==null||!root.content_sha256().equals(registered.contentSha256())||typedChildren.isEmpty())bad();
  var uses=new ArrayList<>(typedChildren);var purposes=new HashMap<String,String>();purposes.put(root.version_id(),rootPurpose);var covered=new HashSet<String>();covered.add(root.version_id());var edges=new HashSet<SourceReadProtocol.ParentRow>();
  if(sourceOwnedArtifact){
   if(!"ARTIFACT".equals(registered.entityKind()))bad();var binding=AgentPublicationJournal.one(artifacts.binding(root.version_id()));if(!"SOURCE".equals(binding.resourceKind()))bad();
   var primary=new ArrayList<SourceReadProtocol.SourceRow>();for(var id:graph.versions().keySet())for(var source:sourceRows.source(id))if(binding.resourceId().equals(source.sourceId()))primary.add(source);
   if(primary.size()!=1)bad();var source=primary.get(0);uses.add(new TypedUse(new PermissionProtocol.Basis(source.sourceVersion(),source.originalSha256()),rootPurpose,List.of(),false));
  }
  for(var childUse:uses){
   var child=lineage.inspect(childUse.root().version_id(),limits.limits());var fixed=child.versions().get(childUse.root().version_id());if(fixed==null||!childUse.root().content_sha256().equals(fixed.contentSha256()))bad();
   var childProof=requireTypedTree(a,project,childUse,depth+1,budget);if(!new HashSet<>(childProof.versionIds()).equals(child.versions().keySet()))bad();
   for(var version:child.versions().values()){
    String purpose=childProof.authorizations().path(version.id()).path("purpose").asText();if(purpose.isEmpty())bad();
    if(!version.equals(graph.versions().get(version.id())))bad();String previous=purposes.putIfAbsent(version.id(),purpose);if(previous!=null&&!previous.equals(purpose))bad();covered.add(version.id());
   }edges.addAll(child.graph().edgeSet());
  }
  if(!covered.equals(graph.versions().keySet()))bad();
  for(var edge:graph.graph().edgeSet())if(!root.version_id().equals(edge.consumerVersionId())&&!edges.contains(edge))bad();
  var sourceProofs=new TreeMap<String,SourceReadProtocol.VerifiedSource>();var required=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
  for(var version:graph.versions().values()){
   if(version.projectId()!=null&&!version.projectId().equals(project))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   String purpose=purposes.get(version.id());var rows=sourceRows.source(version.id());if(rows.size()>1)bad();
   if(rows.size()==1){if(!"SOURCE_VERSION".equals(version.entityKind()))bad();var source=rows.get(0);sourceProofs.put(version.id(),sources.require(a,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),project,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,limits.environment(),limits.region(),null)));}
   if("ARTIFACT".equals(version.entityKind())){var binding=AgentPublicationJournal.one(artifacts.binding(version.id()));var profile=profiles.profile(binding.schemaRef(),purpose,binding.resourceKind());required.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(a.context().tenant_id(),binding.projectId(),profile.resourceKind(),binding.resourceId()),profile.action(),purpose,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),limits.limits()));}
  }
  requirePrimarySources(graph,required,sourceProofs);
  var checked=versions.requireWithPurposes(a,graph,purposes,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),required);var proofs=json.object();
  for(var version:checked.values()){
   if(version.owner().sourceVersion()!=null){var source=sourceProofs.get(version.owner().sourceVersion());if(source==null||!source.source().sourceId().equals(version.owner().resourceId())||!graph.reachableFrom(version.version().id()).contains(version.owner().sourceVersion()))bad();}
   if(version.artifactRegistration()!=null)for(var member:version.artifactRegistration().manifest().members()){var rights=AgentPublicationJournal.one(sourceRows.rights(member.rightsRevision()));var source=sourceProofs.get(rights.sourceVersion());if(source==null||!source.rights().rightsRevision().equals(member.rightsRevision())||!graph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion()))bad();}
   var proof=json.object().put("purpose",purposes.get(version.version().id()));proof.set("resource",AgentPublicationWitness.tree(version.resourceGrant()));proof.set("version_project",AgentPublicationWitness.tree(version.versionProjectGrant()));proof.set("owner",json.tree(version.owner()));proof.set("source",AgentPublicationWitness.tree(sourceProofs.get(version.version().id())));proofs.set(version.version().id(),proof);
  }
  return new Checked(graph.versions().keySet().stream().sorted().toList(),proofs,sourceRights(sourceProofs.values()));
 }

 private void requirePrimarySources(DependencyLineage.VerifiedLineage graph,Map<String,ArtifactRegistryProtocol.ReadRequirement> required,Map<String,SourceReadProtocol.VerifiedSource> sources){
  for(var entry:required.entrySet()){var requirement=entry.getValue();if(!"SOURCE".equals(requirement.scope().resource_kind()))continue;
   var reachable=graph.reachableFrom(entry.getKey());var candidates=new ArrayList<SourceReadProtocol.VerifiedSource>();
   for(var source:sources.values())if(reachable.contains(source.source().sourceVersion())&&requirement.scope().resource_id().equals(source.source().sourceId()))candidates.add(source);
   if(candidates.size()!=1||!requirement.purpose().equals(candidates.get(0).rights().purpose()))bad();
  }
 }
 private static List<PermissionProtocol.Basis> sourceRights(Collection<SourceReadProtocol.VerifiedSource> sources){var rights=new TreeMap<String,PermissionProtocol.Basis>();for(var source:sources)addRight(rights,sourceRight(source));return List.copyOf(rights.values());}
 private static PermissionProtocol.Basis sourceRight(SourceReadProtocol.VerifiedSource source){return new PermissionProtocol.Basis(source.rights().rightsRevision(),source.rights().contentSha256());}
 private static void addRight(Map<String,PermissionProtocol.Basis> all,PermissionProtocol.Basis right){var old=all.putIfAbsent(right.version_id(),right);if(old!=null&&!old.equals(right))bad();}
 static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
