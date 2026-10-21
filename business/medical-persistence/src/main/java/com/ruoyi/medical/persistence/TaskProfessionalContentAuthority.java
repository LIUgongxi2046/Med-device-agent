package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.util.*;

/** Complete SOURCE-backed ancestry, deliberately separate from the governance graph.
 * The private 097 reader rechecks every fixed source's current FORMAL rights. Configuration,
 * producer, input-artifact and session identities are never given invented source rights. */
public final class TaskProfessionalContentAuthority {
    private final ArtifactRegistryMapper registry;
    private final VersionResourceMapper owners;
    private final DependencyManifestSchema manifests;
    private final DependencyLineage lineage;
    private final DependencyLineage.Limits limits;
    public TaskProfessionalContentAuthority(ArtifactRegistryMapper registry,VersionResourceMapper owners,
            DependencyManifestSchema manifests,DependencyLineage lineage,DependencyLineage.Limits limits) {
        this.registry=registry;this.owners=owners;this.manifests=manifests;this.lineage=lineage;this.limits=limits;
    }
    Verified require(TaskNodeInputAuthority.Verified input)throws Exception {
        input.requireUnchanged();var roots=new TreeSet<String>();var selected=new HashMap<String,TaskNodeInputProtocol.SourceSelection>();
        for(var source:input.fixedSources()) {
            if(selected.put(source.sourceVersion(),source)!=null)bad();
            roots.add(source.sourceVersion());roots.add(source.parseVersion());roots.add(source.indexVersion());
        }
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(String root:roots) {
            var graph=lineage.inspect(root,limits);
            for(var entry:graph.versions().entrySet()) {
                var previous=versions.putIfAbsent(entry.getKey(),entry.getValue());if(previous!=null&&!previous.equals(entry.getValue()))bad();
            }
            edges.addAll(graph.graph().edgeSet());
            if(versions.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        }
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        if(!versions.isEmpty()) {
            var ownership=owners.ownersAcrossDomains(List.copyOf(versions.keySet()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());
            var grouped=ownership.stream().collect(java.util.stream.Collectors.groupingBy(v->v.owner().versionId()));
            for(var version:versions.values()) {
                var owner=one(grouped.getOrDefault(version.id(),List.of())).owner();var source=selected.get(owner.sourceVersion());
                if(source==null||!input.tenantId().equals(owner.tenantId())||!"SOURCE".equals(owner.resourceKind())
                        ||!Objects.equals(version.projectId(),owner.versionProjectId())||owner.conversationId()!=null
                        ||owner.contentSha256()!=null&&!version.contentSha256().equals(owner.contentSha256())
                        ||!TaskRunReplayContentAuthority.reachable(version.id(),owner.sourceVersion(),edges))bad();
                members.add(new ArtifactRegistryProtocol.MemberRow(version.id(),source.rightsRevision()));
            }
        }
        var original=one(registry.artifact(input.inputId()));
        if(!input.objectReference().equals(original.reference())||!TaskNodeInputRegistration.KIND.equals(original.artifactKind()))bad();
        var manifest=manifests.verify(one(registry.manifest(original.accessSetId())),registry.members(original.accessSetId()));
        if(!input.tenantId().equals(manifest.tenant_id())||!input.projectId().equals(manifest.project_id())||!members.equals(manifest.members()))bad();
        var graph=lineage.require(input.inputId(),members,limits);var direct=new TreeSet<String>();
        for(var edge:graph.graph().outgoingEdgesOf(input.inputId())) {
            if(!"RUNTIME_CONTENT_INPUT".equals(edge.relationKind())||!direct.add(edge.upstreamVersionId()))bad();
        }
        if(!direct.equals(roots))bad();input.requireUnchanged();
        return new Verified(this,input,List.copyOf(roots),List.copyOf(members),Map.copyOf(versions),Set.copyOf(edges));
    }
    public static final class Verified {
        private final TaskProfessionalContentAuthority owner;private final TaskNodeInputAuthority.Verified input;
        private final List<String> roots;private final List<ArtifactRegistryProtocol.MemberRow> members;
        private final Map<String,ArtifactRegistryProtocol.VersionRow> versions;private final Set<SourceReadProtocol.ParentRow> edges;
        private Verified(TaskProfessionalContentAuthority owner,TaskNodeInputAuthority.Verified input,List<String> roots,
                List<ArtifactRegistryProtocol.MemberRow> members,Map<String,ArtifactRegistryProtocol.VersionRow> versions,Set<SourceReadProtocol.ParentRow> edges) {
            this.owner=owner;this.input=input;this.roots=roots;this.members=members;this.versions=versions;this.edges=edges;
        }
        void requireUnchanged()throws Exception {var fresh=owner.require(input);if(!roots.equals(fresh.roots)||!members.equals(fresh.members)||!versions.equals(fresh.versions)||!edges.equals(fresh.edges))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        List<String> roots(){return roots;}List<ArtifactRegistryProtocol.MemberRow> members(){return members;}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
