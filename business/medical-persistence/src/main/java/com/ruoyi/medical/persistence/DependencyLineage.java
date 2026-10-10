package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.springframework.stereotype.Component;

@Component
public final class DependencyLineage {
    private final SourceReadMapper sources;
    private final ArtifactRegistryMapper registry;

    public DependencyLineage(SourceReadMapper sources, ArtifactRegistryMapper registry) { this.sources = sources; this.registry = registry; }
    public record Limits(int maximumVersions, int maximumRelations) {
        public Limits {
            if (maximumVersions <= 0 || maximumRelations <= 0) throw new IllegalArgumentException("完整关系容量必须为正数");
        }
    }
    record VerifiedLineage(String root, Graph<String, SourceReadProtocol.ParentRow> graph,
            Map<String, ArtifactRegistryProtocol.VersionRow> versions) {
        VerifiedLineage { graph = new AsUnmodifiableGraph<>(graph); versions = Map.copyOf(versions); }
        Set<String> reachableFrom(String version) {
            if (!graph.containsVertex(version)) mismatch();
            var reached = new TreeSet<String>(); var iterator = new BreadthFirstIterator<>(graph, version);
            while (iterator.hasNext()) reached.add(iterator.next());
            return Set.copyOf(reached);
        }
    }

    VerifiedLineage require(String root, List<ArtifactRegistryProtocol.MemberRow> members, Limits limits) {
        Objects.requireNonNull(members);
        var result = inspect(root, limits);
        var expected = new TreeSet<String>(result.versions().keySet()); expected.remove(root);
        var declared = new TreeSet<String>();
        for (var member : members) {
            WireValues.uuid(member.upstreamVersionId()); WireValues.uuid(member.rightsRevision());
            if (!declared.add(member.upstreamVersionId())) mismatch();
        }
        if (!expected.equals(declared)) mismatch();
        return result;
    }

    VerifiedLineage inspect(String root, Limits limits) {
        WireValues.uuid(root); Objects.requireNonNull(limits);
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.SourceReadMapper.parents");
        var graph = new DirectedPseudograph<String, SourceReadProtocol.ParentRow>(null, null, false);
        var versions = new LinkedHashMap<String, ArtifactRegistryProtocol.VersionRow>();
        var pending = new ArrayDeque<String>(); graph.addVertex(root); pending.add(root);
        while (!pending.isEmpty()) {
            String versionId = pending.removeFirst();
            var registered = registry.version(versionId);
            if (registered.size() != 1) denied();
            var version = registered.get(0);
            if (!versionId.equals(version.id())) mismatch();
            WireValues.hash(version.contentSha256());
            versions.put(versionId, version);
            for (var relation : sources.parents(versionId)) {
                if (!versionId.equals(relation.consumerVersionId())) mismatch();
                WireValues.uuid(relation.upstreamVersionId());
                if (relation.relationKind() == null || relation.relationKind().isBlank()) mismatch();
                if (graph.edgeSet().size() >= limits.maximumRelations()) capacity();
                if (graph.addVertex(relation.upstreamVersionId())) {
                    if (graph.vertexSet().size() > limits.maximumVersions()) capacity();
                    pending.addLast(relation.upstreamVersionId());
                }
                if (!graph.addEdge(versionId, relation.upstreamVersionId(), relation)) mismatch();
            }
        }
        // 检查全部关系，不能仅检查根节点是否位于循环中。
        if (new CycleDetector<>(graph).detectCycles()) mismatch();
        return new VerifiedLineage(root, graph, versions);
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void capacity() { throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); }
}
