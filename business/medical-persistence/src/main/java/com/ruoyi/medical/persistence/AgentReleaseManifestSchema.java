package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

public final class AgentReleaseManifestSchema {
    static final String ID = "urn:medical:agent-release:1.0.0";
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final String schemaSha256;
    private final SchemaCatalogue shared;

    public AgentReleaseManifestSchema(byte[] definition, SchemaCatalogue shared) throws IOException {
        this.shared = java.util.Objects.requireNonNull(shared);
        var document = reader.readTree(definition);
        if (document == null || !ID.equals(document.path("$id").textValue())) invalid();
        schemaSha256 = DigestUtils.sha256Hex(definition);
        shared.requirePublished(ID, schemaSha256);
    }

    public AgentReleaseProtocol.Validated bind(byte[] content) throws IOException {
        // 完整共享Schema负责封闭字段及UTF8；语义核查继续固定同一原输入。
        var binding = shared.bindPublished(ID, schemaSha256, content);
        var manifest = reader.readValue(binding.canonicalJson(), AgentReleaseProtocol.Manifest.class);
        if (!manifest.tenantId().equals(manifest.releaseScope().tenantId()) || manifest.releaseId().equals(manifest.agentVersionId())) invalid();
        var versions = new TreeMap<String, AgentReleaseProtocol.VersionRef>();
        var kinds = new LinkedHashMap<String, String>();
        group(versions, kinds, "ROLE", manifest.roleVersions()); group(versions, kinds, "SKILL", manifest.skillVersions());
        group(versions, kinds, "TOOL", manifest.toolVersions()); group(versions, kinds, "PROMPT", manifest.promptRevisions());
        group(versions, kinds, "TEMPLATE", manifest.templateVersions()); group(versions, kinds, "RULE", manifest.ruleVersions());
        group(versions, kinds, "RETRIEVAL", manifest.retrievalProfiles()); group(versions, kinds, "MODEL", manifest.modelConfigRevisions());
        group(versions, kinds, "AGENT", List.of(new AgentReleaseProtocol.VersionRef(manifest.agentVersionId(), manifest.agentVersionSha256())));
        group(versions, kinds, "EVALUATION", List.of(new AgentReleaseProtocol.VersionRef(manifest.evaluationId(), manifest.evaluationSha256())));
        group(versions, kinds, "SOURCE_SCOPE_POLICY", List.of(manifest.releaseScope().sourceScopePolicy()));
        var schemas = new TreeMap<String, AgentReleaseProtocol.PublishedSchema>();
        var bindings = new HashSet<String>();
        var directions = new LinkedHashMap<String, Set<String>>();
        for (var schemaBinding : manifest.inputOutputSchemaRefs()) {
            if (!versions.containsKey(schemaBinding.consumerVersionId())
                    || !bindings.add(schemaBinding.consumerVersionId() + ":" + schemaBinding.direction() + ":" + schemaBinding.schema().schemaRef())) invalid();
            directions.computeIfAbsent(schemaBinding.consumerVersionId(), ignored -> new HashSet<>()).add(schemaBinding.direction());
            addSchema(schemas, schemaBinding.schema());
        }
        for (var item : kinds.entrySet()) if (Set.of("ROLE", "TOOL").contains(item.getValue())
                && (!directions.containsKey(item.getKey()) || !directions.get(item.getKey()).containsAll(Set.of("INPUT", "OUTPUT")))) invalid();
        var checkpointIds = new HashSet<String>();
        for (var checkpoint : manifest.humanCheckpoints()) {
            if (!checkpointIds.add(checkpoint.checkpointId())) invalid();
            for (String id : checkpoint.roleVersionIds()) if (!"ROLE".equals(kinds.get(id))) invalid();
            addVersion(versions, checkpoint.policyRevision()); addSchema(schemas, checkpoint.triggerSchema()); addSchema(schemas, checkpoint.decisionSchema());
        }
        var runtime = manifest.runtimeManifest();
        var artifacts = new TreeMap<String, AgentReleaseProtocol.Artifact>();
        addArtifact(artifacts, runtime.harnessDistribution().artifact()); addArtifact(artifacts, runtime.pythonRuntime().artifact());
        addArtifact(artifacts, runtime.profile().artifact()); addArtifact(artifacts, runtime.sdkConfiguration().artifact());
        addSchema(schemas, runtime.profile().schema()); addSchema(schemas, runtime.sdkConfiguration().schema());
        addSchema(schemas, runtime.sessionFormat()); addSchema(schemas, runtime.artifactSchema());
        addSchema(schemas, runtime.modelStreamSchema()); addSchema(schemas, runtime.bodySchema());
        if (!shared.schemaRef("artifacts", null).equals(runtime.artifactSchema().schemaRef())
                || !shared.schemaRef("model-stream", null).equals(runtime.modelStreamSchema().schemaRef())) invalid();
        var validators = new HashSet<String>();
        for (var rule : runtime.validators()) {
            if (!validators.add(rule.ruleId())) invalid();
            addVersion(versions, rule.version()); addArtifact(artifacts, rule.implementation());
        }
        if (!validators.equals(Set.of("AS-01", "AS-02", "AS-03", "AS-04", "AS-05", "AS-06", "AS-07", "AS-08",
                "AS-09", "AS-10", "AS-11", "AS-12", "AS-13", "AS-14", "AS-15"))) invalid();
        var adapters = new HashSet<String>();
        for (var adapter : runtime.eventAdapters()) {
            if (!adapters.add(adapter.component())) invalid();
            addVersion(versions, adapter.version()); addArtifact(artifacts, adapter.implementation());
            addSchema(schemas, adapter.inputSchema()); addSchema(schemas, adapter.outputSchema());
        }
        if (!adapters.equals(Set.of("C07", "C12"))) invalid();
        var pluginOrder = plugins(runtime.plugins(), artifacts);
        if (versions.containsKey(manifest.releaseId())) invalid();
        BigDecimal cost = limits(manifest.limits());
        return new AgentReleaseProtocol.Validated(manifest, binding.canonicalJson(), DigestUtils.sha256Hex(binding.canonicalBytes()),
            schemaSha256, List.copyOf(versions.values()), List.copyOf(artifacts.values()), List.copyOf(schemas.values()), pluginOrder, cost);
    }

    public AgentReleaseProtocol.ReleaseScope bindScope(byte[] content) throws IOException {
        var binding = shared.bindPublished(ID + "#/$defs/ReleaseScope", schemaSha256, content);
        return reader.readValue(binding.canonicalJson(), AgentReleaseProtocol.ReleaseScope.class);
    }

    private static void group(Map<String, AgentReleaseProtocol.VersionRef> versions, Map<String, String> kinds, String kind,
            List<AgentReleaseProtocol.VersionRef> items) {
        for (var ref : items) {
            if (kinds.putIfAbsent(ref.versionId(), kind) != null) invalid();
            addVersion(versions, ref);
        }
    }
    private static void addVersion(Map<String, AgentReleaseProtocol.VersionRef> values, AgentReleaseProtocol.VersionRef ref) {
        WireValues.uuid(ref.versionId()); WireValues.hash(ref.contentSha256());
        var original = values.putIfAbsent(ref.versionId(), ref);
        if (original != null && !original.equals(ref)) invalid();
    }
    private void addSchema(Map<String, AgentReleaseProtocol.PublishedSchema> values, AgentReleaseProtocol.PublishedSchema schema) {
        shared.requirePublished(schema.schemaRef(), schema.schemaSha256());
        var original = values.putIfAbsent(schema.schemaRef(), schema);
        if (original != null && !original.equals(schema)) invalid();
    }
    private static void addArtifact(Map<String, AgentReleaseProtocol.Artifact> values, AgentReleaseProtocol.Artifact artifact) {
        WireValues.uuid(artifact.artifactId()); WireValues.hash(artifact.sha256());
        if (WireValues.revision(artifact.sizeBytes()).signum() <= 0) invalid();
        org.springframework.util.MimeTypeUtils.parseMimeType(artifact.mediaType());
        var original = values.putIfAbsent(artifact.artifactId(), artifact);
        if (original != null && !original.equals(artifact)) invalid();
    }
    private static List<String> plugins(List<AgentReleaseProtocol.Plugin> plugins, Map<String, AgentReleaseProtocol.Artifact> artifacts) {
        var graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
        for (var plugin : plugins) {
            if (!graph.addVertex(plugin.pluginId())) invalid();
            addArtifact(artifacts, plugin.artifact());
        }
        for (var plugin : plugins) for (String dependency : plugin.dependencyIds()) {
            if (!graph.containsVertex(dependency) || dependency.equals(plugin.pluginId()) || graph.containsEdge(dependency, plugin.pluginId())) invalid();
            graph.addEdge(dependency, plugin.pluginId());
        }
        if (new CycleDetector<>(graph).detectCycles()) invalid();
        var order = new ArrayList<String>();
        new TopologicalOrderIterator<>(graph, java.util.Comparator.naturalOrder()).forEachRemaining(order::add);
        return List.copyOf(order);
    }
    private static BigDecimal limits(AgentReleaseProtocol.Limits limits) {
        for (String count : List.of(limits.maxModelRequests(), limits.maxToolCalls(), limits.maxResearchQuestions(), limits.maxResearchRounds(),
                limits.maxRevisionRounds(), limits.maxParallelSteps(), limits.maxInputTokens(), limits.maxOutputTokens(), limits.maxTotalTokens(),
                limits.maxConsecutiveNoProgress(), limits.maxRepeatedFailureSignature())) WireValues.revision(count);
        if (limits.heartbeatIntervalSeconds() >= limits.heartbeatTimeoutSeconds()
                || limits.heartbeatTimeoutSeconds() >= limits.activityStartToCloseSeconds()
                || (long) limits.sdkInitializeTimeoutSeconds() + limits.sdkRequestTimeoutSeconds() > limits.activityStartToCloseSeconds()
                || limits.modelRequestTimeoutSeconds() > limits.sdkRequestTimeoutSeconds()
                || WireValues.revision(limits.maxInputTokens()).compareTo(WireValues.revision(limits.maxTotalTokens())) > 0
                || WireValues.revision(limits.maxOutputTokens()).compareTo(WireValues.revision(limits.maxTotalTokens())) > 0) invalid();
        return WireValues.decimal(limits.maxCostAmount(), 24, 8);
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
