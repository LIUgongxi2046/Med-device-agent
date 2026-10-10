package com.ruoyi.medical.persistence;

import java.math.BigDecimal;
import java.util.List;

public final class AgentReleaseProtocol {
    private AgentReleaseProtocol() { }

    public record VersionRef(String versionId, String contentSha256) { }
    public record PublishedSchema(String schemaRef, String schemaSha256) { }
    public record Artifact(String artifactId, String sha256, String sizeBytes, String mediaType) { }
    public record SchemaBoundArtifact(Artifact artifact, PublishedSchema schema) { }
    public record SchemaBinding(String consumerVersionId, String direction, PublishedSchema schema) { }
    public record HumanCheckpoint(String checkpointId, VersionRef policyRevision, String operationId, String requiredAction,
            PublishedSchema triggerSchema, PublishedSchema decisionSchema, List<String> roleVersionIds) {
        public HumanCheckpoint { roleVersionIds = List.copyOf(roleVersionIds); }
    }
    public record Limits(int activityStartToCloseSeconds, int heartbeatIntervalSeconds, int heartbeatTimeoutSeconds,
            int sdkInitializeTimeoutSeconds, int sdkRequestTimeoutSeconds, int modelRequestTimeoutSeconds,
            int paidRequestExtraRetries, int modelSpawnedSubagents, int maxWallTimeSeconds,
            String maxModelRequests, String maxToolCalls, String maxResearchQuestions, String maxResearchRounds,
            String maxRevisionRounds, String maxParallelSteps, String maxInputTokens, String maxOutputTokens,
            String maxTotalTokens, String maxCostAmount, String currency, String maxConsecutiveNoProgress, String maxRepeatedFailureSignature) { }
    public record Platform(String operatingSystem, String architecture, String pythonAbi) { }
    public record Software(String name, String version, Artifact artifact) { }
    public record Plugin(String pluginId, String version, Artifact artifact, List<String> dependencyIds) {
        public Plugin { dependencyIds = List.copyOf(dependencyIds); }
    }
    public record Validator(String ruleId, VersionRef version, Artifact implementation) { }
    public record EventAdapter(String component, VersionRef version, Artifact implementation,
            PublishedSchema inputSchema, PublishedSchema outputSchema) { }
    public record RuntimeManifest(String sourceRepository, String sourceCommit, String sourceVersion,
            Software harnessDistribution, Software pythonRuntime, Platform platform, List<Plugin> plugins,
            SchemaBoundArtifact profile, SchemaBoundArtifact sdkConfiguration, PublishedSchema sessionFormat,
            PublishedSchema artifactSchema, PublishedSchema modelStreamSchema, PublishedSchema bodySchema,
            List<Validator> validators, List<EventAdapter> eventAdapters) {
        public RuntimeManifest { plugins = List.copyOf(plugins); validators = List.copyOf(validators); eventAdapters = List.copyOf(eventAdapters); }
    }
    public record ReleaseScope(String tenantId, String projectMode, List<String> projectIds, String audienceMode,
            List<String> memberIds, List<String> roleIds, VersionRef sourceScopePolicy, List<String> allowedClassifications) {
        public ReleaseScope {
            projectIds = List.copyOf(projectIds); memberIds = List.copyOf(memberIds); roleIds = List.copyOf(roleIds);
            allowedClassifications = List.copyOf(allowedClassifications);
        }
    }
    public record Manifest(String schemaVersion, String tenantId, String agentVersionId, String agentVersionSha256, String releaseId,
            List<VersionRef> roleVersions, List<VersionRef> skillVersions, List<VersionRef> toolVersions, List<VersionRef> promptRevisions,
            List<SchemaBinding> inputOutputSchemaRefs, List<VersionRef> templateVersions, List<VersionRef> ruleVersions,
            List<VersionRef> retrievalProfiles, List<VersionRef> modelConfigRevisions, Limits limits,
            List<HumanCheckpoint> humanCheckpoints, RuntimeManifest runtimeManifest, String evaluationId, String evaluationSha256,
            ReleaseScope releaseScope) {
        public Manifest {
            roleVersions = List.copyOf(roleVersions); skillVersions = List.copyOf(skillVersions); toolVersions = List.copyOf(toolVersions);
            promptRevisions = List.copyOf(promptRevisions); inputOutputSchemaRefs = List.copyOf(inputOutputSchemaRefs);
            templateVersions = List.copyOf(templateVersions); ruleVersions = List.copyOf(ruleVersions);
            retrievalProfiles = List.copyOf(retrievalProfiles); modelConfigRevisions = List.copyOf(modelConfigRevisions);
            humanCheckpoints = List.copyOf(humanCheckpoints);
        }
    }
    public record Validated(Manifest manifest, String canonicalJson, String sha256, String schemaSha256,
            List<VersionRef> dependencies, List<Artifact> artifacts, List<PublishedSchema> schemas, List<String> pluginOrder,
            BigDecimal maximumCost) {
        public Validated {
            dependencies = List.copyOf(dependencies); artifacts = List.copyOf(artifacts); schemas = List.copyOf(schemas);
            pluginOrder = List.copyOf(pluginOrder);
        }
    }
}
