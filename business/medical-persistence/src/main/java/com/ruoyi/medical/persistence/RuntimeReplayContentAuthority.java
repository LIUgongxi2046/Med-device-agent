package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.util.*;

/** Complete actual SOURCE-backed content ancestry, kept separate from governance.
 * Every member must resolve to an actually selected source with current FORMAL rights.
 * A configuration-only ancestor cannot be assigned a made-up rights revision. */
public final class RuntimeReplayContentAuthority {
 private final SourceReadAuthority sources;private final DependencyLineage lineage;private final VersionResourceMapper owners;private final DependencyLineage.Limits limits;
 public RuntimeReplayContentAuthority(SourceReadAuthority sources,DependencyLineage lineage,VersionResourceMapper owners,DependencyLineage.Limits limits){this.sources=sources;this.lineage=lineage;this.owners=owners;this.limits=limits;}
 Verified require(EnterpriseAccess.Admission a,RuntimeReplaySchema.VerifiedInputs input)throws Exception {
  var i=a.formalProof().identity();var value=input.content();if(!i.run().equals(input.runId())||!i.tenant().equals(value.required("tenant_id").asText())||!i.project().equals(value.required("project_id").asText()))bad();
  var policy=input.executionPolicy();var selected=new TreeMap<String,SourceReadProtocol.VerifiedSource>();var roots=new TreeSet<String>();
  var decoder=new com.fasterxml.jackson.databind.ObjectMapper();
  for(var m:value.required("scope_snapshot").required("members")){
   var member=decoder.treeToValue(m,TaskProtocol.ScopeSnapshotMember.class);
   var source=sources.require(a,new SourceReadProtocol.ReadRequirement(member.source_version(),member.rights_revision(),i.project(),input.snapshotId(),member.parse_version(),null,SourceReadProtocol.Use.FORMAL,policy.businessPurpose(),policy.environment(),policy.region(),null));
   ScopeSnapshotSchema.requireSource(member,source);
   if(source.selection()==null||!member.index_version().equals(source.selection().indexVersion())||!member.approval_revision().equals(source.selection().approvalRevision())||!member.binding_revision().equals(source.selection().bindingRevision())||selected.put(member.source_version(),source)!=null)bad();
   roots.add(member.source_version());roots.add(member.parse_version());roots.add(member.index_version());
  }
  var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
  for(String root:roots){var graph=lineage.inspect(root,limits);for(var entry:graph.versions().entrySet()){var prior=versions.putIfAbsent(entry.getKey(),entry.getValue());if(prior!=null&&!prior.equals(entry.getValue()))bad();}edges.addAll(graph.graph().edgeSet());if(versions.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
  var declared=new HashMap<String,com.fasterxml.jackson.databind.JsonNode>();for(var v:value.required("dependency_versions"))declared.put(v.required("version_id").asText(),v);
  var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
  if(!versions.isEmpty()){
   var domainRows=owners.ownersAcrossDomains(versions.keySet().stream().toList(),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
   var grouped=domainRows.stream().collect(java.util.stream.Collectors.groupingBy(r->r.owner().versionId()));
   for(var version:versions.values()){
    var d=declared.get(version.id());if(d==null||!version.entityKind().equals(d.required("entity_kind").asText())||!version.contentSha256().equals(d.required("content_sha256").asText())||!Objects.equals(version.projectId(),d.required("project_id").isNull()?null:d.required("project_id").asText()))bad();
    var rows=grouped.getOrDefault(version.id(),List.of());if(rows.size()!=1)bad();var owner=rows.get(0).owner();var source=selected.get(owner.sourceVersion());
    if(source==null||!i.tenant().equals(owner.tenantId())||!Objects.equals(version.projectId(),owner.versionProjectId())||!"SOURCE".equals(owner.resourceKind())||!source.source().sourceId().equals(owner.resourceId())||!Objects.equals(source.source().ownerProjectId(),owner.resourceProjectId())||owner.conversationId()!=null||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256()))bad();
    if(!reachable(version.id(),owner.sourceVersion(),edges))bad();members.add(new ArtifactRegistryProtocol.MemberRow(version.id(),source.rights().rightsRevision()));
   }
  }
  return new Verified(this,a,input,List.copyOf(roots),List.copyOf(members),Map.copyOf(versions),Set.copyOf(edges));
 }
 static boolean reachable(String root,String target,Set<SourceReadProtocol.ParentRow> edges){var pending=new ArrayDeque<String>();var seen=new HashSet<String>();pending.add(root);while(!pending.isEmpty()){String at=pending.removeFirst();if(!seen.add(at))continue;if(at.equals(target))return true;for(var edge:edges)if(edge.consumerVersionId().equals(at))pending.add(edge.upstreamVersionId());}return false;}
 public static final class Verified {
  private final RuntimeReplayContentAuthority owner;private final EnterpriseAccess.Admission admission;private final RuntimeReplaySchema.VerifiedInputs input;private final List<String> roots;private final List<ArtifactRegistryProtocol.MemberRow> members;private final Map<String,ArtifactRegistryProtocol.VersionRow> versions;private final Set<SourceReadProtocol.ParentRow> edges;
  private Verified(RuntimeReplayContentAuthority owner,EnterpriseAccess.Admission admission,RuntimeReplaySchema.VerifiedInputs input,List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,Map<String,ArtifactRegistryProtocol.VersionRow> versions,Set<SourceReadProtocol.ParentRow> edges){this.owner=owner;this.admission=admission;this.input=input;this.roots=roots;this.members=members;this.versions=versions;this.edges=edges;}
  void requireUnchanged()throws Exception{var fresh=owner.require(admission,input);if(!roots.equals(fresh.roots)||!members.equals(fresh.members)||!versions.equals(fresh.versions)||!edges.equals(fresh.edges))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
  List<String> roots(){admission.formalProof().requireActive();return roots;}List<ArtifactRegistryProtocol.MemberRow> members(){admission.formalProof().requireActive();return members;}RuntimeReplaySchema.VerifiedInputs input(){admission.formalProof().requireActive();return input;}
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
