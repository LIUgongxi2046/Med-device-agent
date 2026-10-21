package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.util.*;

/** Actual source-backed ancestry, separate from immutable job governance pins. No invented rights. */
public final class TaskPlanDraftContentAuthority {
    private final DependencyLineage lineage;private final VersionResourceMapper owners;private final DependencyLineage.Limits limits;
    public TaskPlanDraftContentAuthority(DependencyLineage lineage,VersionResourceMapper owners,DependencyLineage.Limits limits){this.lineage=lineage;this.owners=owners;this.limits=limits;}
    Verified require(TaskPlanDraftJobAuthority.Verified proof)throws Exception {
        proof.requireUnchanged();var selected=new HashMap<String,SourceReadProtocol.VerifiedSource>();var roots=new TreeSet<String>();
        for(var source:proof.current().sources()) {
            if(selected.put(source.source().sourceVersion(),source)!=null||source.selection()==null)bad();
            roots.add(source.source().sourceVersion());roots.add(source.selection().parseVersion());roots.add(source.selection().indexVersion());
        }
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(String root:roots){var graph=lineage.inspect(root,limits);for(var entry:graph.versions().entrySet()) {
            var old=versions.putIfAbsent(entry.getKey(),entry.getValue());if(old!=null&&!old.equals(entry.getValue()))bad();
        }edges.addAll(graph.graph().edgeSet());if(versions.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        if(!versions.isEmpty()) {
            var all=owners.ownersAcrossDomains(List.copyOf(versions.keySet()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
            var grouped=all.stream().collect(java.util.stream.Collectors.groupingBy(v->v.owner().versionId()));
            for(var version:versions.values()) {
                var rows=grouped.getOrDefault(version.id(),List.of());if(rows.size()!=1)bad();var owner=rows.get(0).owner();
                var source=selected.get(owner.sourceVersion());
                if(source==null||!proof.job().tenant().equals(owner.tenantId())||!Objects.equals(version.projectId(),owner.versionProjectId())
                        ||!"SOURCE".equals(owner.resourceKind())||!source.source().sourceId().equals(owner.resourceId())
                        ||!Objects.equals(source.source().ownerProjectId(),owner.resourceProjectId())||owner.conversationId()!=null
                        ||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256())||!reachable(version.id(),owner.sourceVersion(),edges))bad();
                members.add(new ArtifactRegistryProtocol.MemberRow(version.id(),source.rights().rightsRevision()));
            }
        }
        return new Verified(proof,List.copyOf(roots),List.copyOf(members),Map.copyOf(versions),Set.copyOf(edges));
    }
    public final class Verified {
        private final TaskPlanDraftJobAuthority.Verified proof;private final List<String> roots;private final List<ArtifactRegistryProtocol.MemberRow> members;
        private final Map<String,ArtifactRegistryProtocol.VersionRow> versions;private final Set<SourceReadProtocol.ParentRow> edges;
        private Verified(TaskPlanDraftJobAuthority.Verified proof,List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,Map<String,ArtifactRegistryProtocol.VersionRow> versions,Set<SourceReadProtocol.ParentRow> edges){this.proof=proof;this.roots=roots;this.members=members;this.versions=versions;this.edges=edges;}
        void requireUnchanged()throws Exception{var fresh=require(proof);if(!roots.equals(fresh.roots)||!members.equals(fresh.members)||!versions.equals(fresh.versions)||!edges.equals(fresh.edges))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        List<String> roots(){return roots;}List<ArtifactRegistryProtocol.MemberRow> members(){return members;}
    }
    private static boolean reachable(String from,String to,Set<SourceReadProtocol.ParentRow> edges){var seen=new HashSet<String>();var queue=new ArrayDeque<String>();queue.add(from);while(!queue.isEmpty()){var at=queue.remove();if(!seen.add(at))continue;if(at.equals(to))return true;for(var edge:edges)if(edge.consumerVersionId().equals(at))queue.add(edge.upstreamVersionId());}return false;}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
