package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.AgentReleaseMapper;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class AgentReleaseStore {
    private final com.fasterxml.jackson.databind.json.JsonMapper reader = com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.AgentReleaseMapper.release";
    private final AgentReleaseMapper mapper;
    private final ArtifactRegistryMapper artifacts;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final PermissionSchema json;
    private final AgentReleaseManifestSchema schema;
    private final String manifestIdentity;

    public AgentReleaseStore(AgentReleaseMapper mapper, ArtifactRegistryMapper artifacts, PermissionMapper permissions,
            ResourceReadAuthority authority, PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.mapper = Objects.requireNonNull(mapper); this.artifacts = Objects.requireNonNull(artifacts);
        this.permissions = Objects.requireNonNull(permissions); this.authority = Objects.requireNonNull(authority);
        this.json = Objects.requireNonNull(json);
        byte[] definition;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/agent-release.schema.json"))) {
            definition = input.readAllBytes();
        }
        schema = new AgentReleaseManifestSchema(definition, SchemaCatalogue.fromClasspath());
        var identity = json.object(); identity.put("schema_version", "1.0.0");
        identity.put("agent-release.schema.json", DigestUtils.sha256Hex(definition));
        identity.put("permission_manifest", json.manifestSha256());
        manifestIdentity = DigestUtils.sha256Hex(json.canonical(identity));
    }

    // 本结果保存固定元数据；发布范围、评测判定和运行准入由业务消费者分别核查。
    static final class Stored {
        private final EnterpriseAccess.Admission admission;
        private final String purpose;
        private final AgentReleaseStoredProtocol.ReleaseRow release;
        private final AgentReleaseStoredProtocol.AgentRow agent;
        private final AgentReleaseStoredProtocol.EvaluationRow evaluation;
        private final AgentReleaseProtocol.Validated content;
        private final ArtifactRegistryProtocol.ArtifactRow artifact;
        private final List<PermissionProtocol.GrantRow> grants;

        private Stored(EnterpriseAccess.Admission admission, String purpose,
                AgentReleaseStoredProtocol.ReleaseRow release, AgentReleaseStoredProtocol.AgentRow agent,
                AgentReleaseStoredProtocol.EvaluationRow evaluation, AgentReleaseProtocol.Validated content,
                ArtifactRegistryProtocol.ArtifactRow artifact, List<PermissionProtocol.GrantRow> grants) {
            this.admission = admission; this.purpose = purpose; this.release = release; this.agent = agent;
            this.evaluation = evaluation; this.content = content; this.artifact = artifact; this.grants = List.copyOf(grants);
        }
        EnterpriseAccess.Admission admission() { return admission; }
        String purpose() { return purpose; }
        AgentReleaseStoredProtocol.ReleaseRow release() { return release; }
        AgentReleaseStoredProtocol.AgentRow agent() { return agent; }
        AgentReleaseStoredProtocol.EvaluationRow evaluation() { return evaluation; }
        AgentReleaseProtocol.Validated content() { return content; }
        ArtifactRegistryProtocol.ArtifactRow artifact() { return artifact; }
        List<PermissionProtocol.GrantRow> grants() { return grants; }
    }

    String manifestSha256() { return manifestIdentity; }

    Stored read(EnterpriseAccess.Admission admission, String releaseId, String purpose) throws IOException {
        Objects.requireNonNull(admission); WireValues.uuid(releaseId); ProjectSchema.text(purpose, 128);
        if (!purpose.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("发布读取用途无效");
        TenantScope.requireSqlAccess(STATEMENT);
        String tenant = admission.context().tenant_id(), user = admission.actorId();
        if (!tenant.equals(TenantScope.requireCurrent().tenantId()) || !user.equals(TenantScope.requireCurrent().subjectId())) denied();
        var values = new HashMap<String,Object>(); values.put("userId", user); values.put("id", releaseId);
        TenantScope.requireSubjectParameter(STATEMENT, values);
        var release = single(mapper.release(values));
        version(tenant, release.tenantId(), release.projectId(), release.registeredProjectId(), "RELEASE_RECORD",
            release.registeredKind(), release.contentSha256(), release.registeredSha256(), release.schemaVersion(), release.schemaManifest());
        if (!releaseId.equals(release.releaseId()) || !release.manifestSha256().equals(release.contentSha256())
                || !manifestIdentity.equals(release.schemaManifest())) mismatch();
        if(admission.controlWorker())admission.controlProof().requireVersion(releaseId,release.contentSha256());
        var bound = schema.bind(release.manifest().getBytes(StandardCharsets.UTF_8)); var manifest = bound.manifest();
        if (!tenant.equals(manifest.tenantId()) || !releaseId.equals(manifest.releaseId())
                || !release.agentVersionId().equals(manifest.agentVersionId()) || !release.manifestSha256().equals(bound.sha256())) mismatch();
        values.put("id", manifest.agentVersionId()); var agent = single(mapper.agent(values));
        version(tenant, agent.tenantId(), agent.projectId(), agent.registeredProjectId(), "AGENT_VERSION", agent.registeredKind(),
            agent.contentSha256(), agent.registeredSha256(), agent.schemaVersion(), agent.schemaManifest());
        if (!manifest.agentVersionId().equals(agent.agentVersionId()) || !manifest.agentVersionSha256().equals(agent.contentSha256())
                || !manifest.evaluationId().equals(agent.evaluationId()) || !Objects.equals(release.projectId(), agent.projectId())
                || !Set.of("PUBLISHED", "DISABLED").contains(agent.status())
                || !json.tree(manifest.limits()).equals(reader.readTree(agent.limits()))) mismatch();
        WireValues.uuid(agent.agentId()); positive(agent.revision());
        values.put("id", manifest.evaluationId()); var evaluation = single(mapper.evaluation(values));
        version(tenant, evaluation.tenantId(), evaluation.projectId(), evaluation.registeredProjectId(), "EVALUATION",
            evaluation.registeredKind(), evaluation.contentSha256(), evaluation.registeredSha256(), evaluation.schemaVersion(), evaluation.schemaManifest());
        if (!manifest.evaluationId().equals(evaluation.evaluationId()) || !manifest.evaluationSha256().equals(evaluation.contentSha256())
                || !Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED", "INSUFFICIENT_EVIDENCE").contains(evaluation.status())) mismatch();
        positive(evaluation.revision());
        WireValues.uuid(evaluation.datasetVersion()); WireValues.uuid(evaluation.ruleRevision()); WireValues.uuid(evaluation.sampleManifestArtifactId());
        metadata(release.createdBy(), release.createdAt(), release.classification());
        metadata(agent.createdBy(), agent.createdAt(), agent.classification());
        metadata(evaluation.createdBy(), evaluation.createdAt(), evaluation.classification());
        WireValues.uuid(release.approvedBy()); Objects.requireNonNull(release.releasedAt());
        var approved = single(permissions.targetMember(release.approvedBy()));
        if (!tenant.equals(approved.tenantId()) || !release.approvedBy().equals(approved.userId())
                || release.releasedAt().isBefore(release.createdAt())) mismatch();
        var configuration = new PermissionProtocol.Scope(tenant, agent.projectId(), "CONFIGURATION", agent.agentId());
        var configurationGrant = authority.require(admission, configuration, "configuration.read", purpose);
        var evaluationGrant = authority.require(admission,
            new PermissionProtocol.Scope(tenant, evaluation.projectId(), "EVALUATION", evaluation.evaluationId()), "evaluation.read", purpose);
        var artifact = single(artifacts.artifact(release.manifestArtifactId()));
        if (!tenant.equals(artifact.tenantId()) || !release.manifestArtifactId().equals(artifact.artifactId())
                || !Objects.equals(release.projectId(), artifact.projectId()) || !release.manifestSha256().equals(artifact.sha256())
                || !artifact.sha256().equals(artifact.contentSha256()) || !"1.0.0".equals(artifact.schemaVersion())) mismatch();
        WireValues.hash(artifact.schemaManifest()); StoredObjectReferences.decode(artifact.storedObject(), artifact.reference());
        var artifactVersion = single(artifacts.version(artifact.artifactId()));
        if (!artifact.artifactId().equals(artifactVersion.id()) || !"ARTIFACT".equals(artifactVersion.entityKind())
                || !Objects.equals(artifact.projectId(), artifactVersion.projectId()) || !artifact.sha256().equals(artifactVersion.contentSha256())) mismatch();
        var artifactGrant = authority.require(admission,
            new PermissionProtocol.Scope(tenant, artifact.projectId(), "ARTIFACT", artifact.artifactId()), "artifact.read", purpose);
        return new Stored(admission, purpose, release, agent, evaluation, bound, artifact,
            List.of(configurationGrant, evaluationGrant, artifactGrant));
    }

    void requireUnchanged(Stored original) throws IOException {
        Objects.requireNonNull(original);
        var current = read(original.admission(), original.release().releaseId(), original.purpose());
        if (!original.release().equals(current.release()) || !original.agent().equals(current.agent())
                || !original.evaluation().equals(current.evaluation()) || !original.content().equals(current.content())
                || !original.artifact().equals(current.artifact()) || !original.grants().equals(current.grants()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }

    private static void version(String tenant, String actualTenant, String project, String registeredProject, String kind,
            String actualKind, String hash, String registeredHash, String version, String identity) {
        if (!tenant.equals(actualTenant) || !Objects.equals(project, registeredProject) || !kind.equals(actualKind)
                || !hash.equals(registeredHash) || !"1.0.0".equals(version)) mismatch();
        WireValues.hash(hash); WireValues.hash(identity); if (project != null) WireValues.uuid(project);
    }
    private static void metadata(String actor, java.time.LocalDateTime createdAt, String classification) {
        WireValues.uuid(actor); Objects.requireNonNull(createdAt); ProjectSchema.text(classification, 24);
    }
    private static void positive(java.math.BigInteger revision) {
        WireValues.revision(Objects.requireNonNull(revision).toString()); if (revision.signum() <= 0) mismatch();
    }
    private static <T> T single(List<T> values) { if (values.size() != 1) denied(); return values.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
