package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;

/** Complete content ancestry derived only from actual approved template/profile and083 originals. No rights are invented
 * for package metadata: every member is tied to an actual reachable SOURCE rights revision. */
public final class FormalEvaluationPlanPreparationDependencies {
    private final AgentPublicationDependencies authority;
    private final DependencyLineage lineage;
    private final SourceReadMapper sources;
    private final ArtifactRegistryMapper artifacts;
    private final PermissionSchema json;
    private static final DependencyLineage.Limits LIMITS = new DependencyLineage.Limits(512, 2048);

    public FormalEvaluationPlanPreparationDependencies(AgentPublicationDependencies authority, DependencyLineage lineage,
            SourceReadMapper sources, ArtifactRegistryMapper artifacts, PermissionSchema json) {
        this.authority = authority;
        this.lineage = lineage;
        this.sources = sources;
        this.artifacts = artifacts;
        this.json = json;
    }

    record Checked(List<PermissionProtocol.Basis> roots, List<ArtifactRegistryProtocol.MemberRow> members, String classification,
            JsonNode authorizations, JsonNode topology) {
        Checked(List<PermissionProtocol.Basis> roots,List<ArtifactRegistryProtocol.MemberRow> members,String classification,JsonNode authorizations){this(roots,members,classification,authorizations,com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());}
        Checked {
            roots = List.copyOf(roots);
            members = List.copyOf(members);
            authorizations = authorizations.deepCopy();
            topology = topology.deepCopy();
        }

        @Override public JsonNode topology(){return topology.deepCopy();}
        @Override
        public JsonNode authorizations() {
            return authorizations.deepCopy();
        }
    }

    Checked require(EnterpriseAccess.Admission a, FormalEvaluationExecutionAdmission.Prepared root, FormalEvaluationExecutionProtocol.Node node,
            PermissionProtocol.Basis template, PermissionProtocol.Basis profile) throws Exception {
        var uses = new ArrayList<AgentPublicationDependencies.DependencyUse>();
        uses.add(new AgentPublicationDependencies.DependencyUse(node.cell().input(), root.policy().businessPurpose()));
        uses.add(new AgentPublicationDependencies.DependencyUse(template, "EVALUATION_RESULTS"));
        uses.add(new AgentPublicationDependencies.DependencyUse(profile, "EVALUATION_RESULTS"));

        var checked = require(a,root.projectId(),root.classification(),uses);
        root.requireUnchanged();return checked;
    }
    /** Current read-only closure. Does not admit an execution, budget, model or registration. */
    Checked require(EnterpriseAccess.Admission a,String project,String classification,List<AgentPublicationDependencies.DependencyUse> uses)throws Exception {
        var checked = authority.requireByUse(a, project, uses);
        var graphs = new ArrayList<DependencyLineage.VerifiedLineage>();
        var versions = new TreeMap<String, ArtifactRegistryProtocol.VersionRow>();
        var edges=new TreeSet<SourceReadProtocol.ParentRow>(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind));
        var rights = new TreeMap<String, String>();
        var roots = new TreeMap<String, PermissionProtocol.Basis>();
        for (var use : uses) {
            var old = roots.putIfAbsent(use.root().version_id(), use.root());
            if (old != null && !old.equals(use.root())) bad();
            var graph = lineage.inspect(use.root().version_id(), LIMITS);
            graphs.add(graph);edges.addAll(graph.graph().edgeSet());
            if(edges.size()>2048)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var v = graph.versions().get(use.root().version_id());
            if (v == null || !v.contentSha256().equals(use.root().content_sha256())) bad();
            for (var entry : graph.versions().values()) {
                var prior = versions.putIfAbsent(entry.id(), entry);
                if (prior != null && !prior.equals(entry)) bad();
            }
        }

        if (!versions.keySet().equals(new TreeSet<>(checked.versionIds())) || versions.size() > 512) bad();
        for (var v : versions.values()) {
            var rows = sources.source(v.id());
            if (rows.size() > 1) bad();
            if (!rows.isEmpty()) {
                var source = rows.get(0);
                var currentRights = one(sources.rights(source.rightsRevision()));
                if (!v.id().equals(currentRights.sourceVersion())) bad();
                rights.put(v.id(), source.rightsRevision());
                classification = FormalEvaluationContextPackages.max(classification, source.classification(), currentRights.classification());
            }

            if ("ARTIFACT".equals(v.entityKind())) classification = FormalEvaluationContextPackages.max(classification,
                    one(artifacts.artifact(v.id())).classification());
        }

        var members = new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        for (var version : versions.keySet()) {
            var reachable = new TreeSet<String>();
            for (var graph : graphs) if (graph.versions().containsKey(version)) reachable.addAll(graph.reachableFrom(version));
            String source = rights.containsKey(version) ? version : reachable.stream().filter(rights::containsKey).findFirst().orElseThrow(() -> new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
            members.add(new ArtifactRegistryProtocol.MemberRow(version, rights.get(source)));
        }

        var topology=json.object();topology.set("versions",json.tree(versions.values()));topology.set("edges",json.tree(edges));
        // Current read fencing only: do not add this to persisted090 original evidence or change its schema hash.
        return new Checked(List.copyOf(roots.values()), members, classification, checked.authorizations(),topology);
    }
    static JsonNode stableEvidence(JsonNode evidence) {
        if(evidence==null||!evidence.isObject())bad();
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)evidence.deepCopy();
        value.remove("artifact_registration");
        ((com.fasterxml.jackson.databind.node.ObjectNode)value.required("preview")).remove("action_summary");
        return value;
    }
    static JsonNode coreEvidence(JsonNode stable) {
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)stable.deepCopy();
        value.remove(List.of("roots","members","dependencies","classification")); return value;
    }

    private static String text(JsonNode n, String k) {
        var v = n.required(k);
        if (!v.isTextual()) bad();
        return v.textValue();
    }

    private static <T > T one(List<T> values) {
        if (values.size() != 1 || values.get(0) == null) bad();
        return values.get(0);
    }

    private static void bad() {
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
}
