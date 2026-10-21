package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;

/** Complete content ancestry derived only from the private compiler proof. No rights are invented
 * for package metadata: every member is tied to an actual reachable SOURCE rights revision. */
public final class FormalEvaluationNodeInputObjectDependencies {
    private final AgentPublicationDependencies authority;
    private final DependencyLineage lineage;
    private final SourceReadMapper sources;
    private final ArtifactRegistryMapper artifacts;
    private final PermissionSchema json;
    private static final DependencyLineage.Limits LIMITS = new DependencyLineage.Limits(512, 2048);

    public FormalEvaluationNodeInputObjectDependencies(AgentPublicationDependencies authority, DependencyLineage lineage,
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
            authorizations = authorizations.deepCopy();topology=topology.deepCopy();
        }

        @Override public JsonNode topology(){return topology.deepCopy();}
        @Override
        public JsonNode authorizations() {
            return authorizations.deepCopy();
        }
    }

    Checked require(EnterpriseAccess.Admission a, FormalEvaluationExecutionAdmission.Prepared root, FormalEvaluationExecutionProtocol.Node node,
            JsonNode evidence) throws Exception {
        var value=require(a,root.projectId(),root.classification(),node.cell().input(),root.policy().businessPurpose(),evidence);
        root.requireUnchanged();return value;
    }
    Checked require(EnterpriseAccess.Admission a,String project,String initialClassification,PermissionProtocol.Basis input,String inputPurpose,JsonNode evidence)throws Exception {
        var uses = new ArrayList<AgentPublicationDependencies.DependencyUse>();
        uses.add(new AgentPublicationDependencies.DependencyUse(input, inputPurpose));
        for (String field : List.of("tool_original", "prompt_original", "tokenizer_artifact_original")) {
            var value = evidence.required(field);
            uses.add(new AgentPublicationDependencies.DependencyUse(new PermissionProtocol.Basis(text(value, "artifact_id"),
                    text(value, "sha256")), "AGENT_PUBLICATION"));
        }

        var planning=evidence.required("planning_original");var data=planning.required("planning");
        var template=new PermissionProtocol.Basis(text(data.required("template"),"version_id"),text(data.required("template"),"content_sha256"));
        var profile=new PermissionProtocol.Basis(text(data,"profile_revision"),text(data,"profile_sha256"));
        var planRef=new PermissionProtocol.Basis(text(planning.required("receipt"),"artifact_id"),text(planning.required("receipt"),"artifact_sha256"));
        // The dedicated090 proof was independently rechecked by the caller. Never feed its owned
        // artifact through generic ArtifactRegistry (which correctly rejects090 ownership).
        uses.add(new AgentPublicationDependencies.DependencyUse(template,"EVALUATION_RESULTS"));
        uses.add(new AgentPublicationDependencies.DependencyUse(profile,"EVALUATION_RESULTS"));
        var checked=authority.requireByUse(a,project,List.copyOf(uses));
        var planGraph=lineage.inspect(planRef.version_id(),LIMITS);
        var expectedPlanVersions=new TreeSet<String>();var expectedPlanEdges=new HashSet<SourceReadProtocol.ParentRow>();
        for(var original:List.of(input,template,profile)){
            var graph=lineage.inspect(original.version_id(),LIMITS);expectedPlanVersions.addAll(graph.versions().keySet());expectedPlanEdges.addAll(graph.graph().edgeSet());
        }
        expectedPlanVersions.add(planRef.version_id());
        if(!expectedPlanVersions.equals(planGraph.versions().keySet()))bad();
        var actualParents=new TreeSet<String>();for(var edge:planGraph.graph().edgeSet()){
            if(planRef.version_id().equals(edge.consumerVersionId())){if(!"EVALUATION_PLAN_PREPARATION".equals(edge.relationKind()))bad();actualParents.add(edge.upstreamVersionId());}
            else if(!expectedPlanEdges.contains(edge))bad();
        }
        if(!actualParents.equals(new TreeSet<>(List.of(input.version_id(),template.version_id(),profile.version_id()))))bad();
        var checkedIds=new TreeSet<>(checked.versionIds());checkedIds.add(planRef.version_id());
        var authorizations=json.object();authorizations.set("compiler_and_planning_ancestors",checked.authorizations());
        authorizations.set("original_plan",planning.deepCopy());
        uses.add(new AgentPublicationDependencies.DependencyUse(planRef,"EVALUATION_RESULTS"));
        var graphs = new ArrayList<DependencyLineage.VerifiedLineage>();
        var versions = new TreeMap<String, ArtifactRegistryProtocol.VersionRow>();
        var edges=new TreeSet<SourceReadProtocol.ParentRow>(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId).thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind));
        var rights = new TreeMap<String, String>();
        String classification = initialClassification;
        var roots = new TreeMap<String, PermissionProtocol.Basis>();
        for (var use : uses) {
            var old = roots.putIfAbsent(use.root().version_id(), use.root());
            if (old != null && !old.equals(use.root())) bad();
            var graph = lineage.inspect(use.root().version_id(), LIMITS);
            graphs.add(graph);edges.addAll(graph.graph().edgeSet());
            var v = graph.versions().get(use.root().version_id());
            if (v == null || !v.contentSha256().equals(use.root().content_sha256())) bad();
            for (var entry : graph.versions().values()) {
                var prior = versions.putIfAbsent(entry.id(), entry);
                if (prior != null && !prior.equals(entry)) bad();
            }
        }

        if (!versions.keySet().equals(checkedIds) || versions.size() > 512) bad();
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
        return new Checked(List.copyOf(roots.values()), members, classification, authorizations,topology);
    }
    /** Only the three newly issued artifact GET receipt arrays vary on a fresh physical re-read.
     * Object version, raw digest, read profile/rights authorization, actor/context, compiler and tokenizer
     * evidence all remain in equality. SOURCE originals currently have no durable public receipt. */
    static JsonNode stableEvidence(JsonNode evidence) {
        if (evidence == null || !evidence.isObject()) bad();
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) evidence.deepCopy();
        for (String field : List.of("tool_original", "prompt_original", "tokenizer_artifact_original")) {
            var raw = result.required(field);
            if (!raw.isObject() || raw.size() != 5) bad();
            for (String name : List.of("artifact_id", "sha256", "authorization_sha256", "object", "receipt_ids")) if (!raw.has(name)) bad();
            WireValues.uuid(text(raw, "artifact_id"));
            WireValues.hash(text(raw, "sha256"));
            WireValues.hash(text(raw, "authorization_sha256"));
            var ids = raw.required("receipt_ids");
            if (!ids.isArray() || ids.isEmpty()) bad();
            var unique = new HashSet<String>();
            for (var id : ids) {
                if (!id.isTextual()) bad();
                WireValues.uuid(id.textValue());
                if (!unique.add(id.textValue())) bad();
            }

            ((com.fasterxml.jackson.databind.node.ObjectNode) raw).remove("receipt_ids");
        }

        return result;
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
