package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(TemplateDependencyPolicy.class)
public final class SimulationRuleDependencies {
    static final String PURPOSE = "SIMULATION_EVALUATION";
    private final DependencyLineage lineage;
    private final VersionResourceAuthority versions;
    private final SourceReadAuthority sourceAuthority;
    private final SourceReadMapper sources;
    private final ArtifactRegistryMapper artifacts;
    private final TemplateMapper templates;
    private final SimulationRuleVersionMapper rules;
    private final TemplateDependencyPolicy policy;
    private final SimulationRuleVersionSchema schema;
    private final PermissionSchema json;

    public SimulationRuleDependencies(DependencyLineage lineage, VersionResourceAuthority versions, SourceReadAuthority sourceAuthority,
            SourceReadMapper sources, ArtifactRegistryMapper artifacts, TemplateMapper templates, SimulationRuleVersionMapper rules, TemplateDependencyPolicy policy, SimulationRuleVersionSchema schema, PermissionSchema json) {
        this.lineage = lineage; this.versions = versions; this.sourceAuthority = sourceAuthority; this.sources = sources;
        this.artifacts = artifacts; this.templates = templates; this.rules = rules; this.policy = policy; this.schema = schema; this.json = json;
    }

    List<DependencySourceAuthority.VerifiedDependencies> requireRule(EnterpriseAccess.Admission admission,
            SimulationRuleVersionProtocol.Validated rule) throws IOException {
        var base = rule.request().base_version();
        return requireReferences(admission, rule.dependency_refs(), Map.of(),
                base == null ? java.util.Set.of() : java.util.Set.of(base.version_id()), null, null, PURPOSE);
    }

    private List<DependencySourceAuthority.VerifiedDependencies> requireReferences(EnterpriseAccess.Admission admission,
            List<PermissionProtocol.Basis> refs, Map<String,String> roles, java.util.Set<String> ruleIds,
            PermissionProtocol.Basis base, String projectId, String purpose) throws IOException {
        var result = new ArrayList<DependencySourceAuthority.VerifiedDependencies>();
        var allVersions = new java.util.HashSet<String>(); var allRelations = new java.util.HashSet<SourceReadProtocol.ParentRow>();
        // 依赖声明只固定原版本；全部归属、来源权利及许可从当前主库取得。
        for (var ref : refs) {
            var graph = lineage.inspect(ref.version_id(), policy.limits());
            allVersions.addAll(graph.versions().keySet()); allRelations.addAll(graph.graph().edgeSet());
            if (allVersions.size() > policy.maximumVersions() || allRelations.size() > policy.maximumRelations())
                throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var root = graph.versions().get(ref.version_id());
            if (root == null || !ref.content_sha256().equals(root.contentSha256())) mismatch();
            var requirements = new LinkedHashMap<String, ArtifactRegistryProtocol.ReadRequirement>();
            var verifiedSources = new LinkedHashMap<String, SourceReadProtocol.VerifiedSource>();
            for (var version : graph.versions().values()) {
                if (version.projectId() != null && !version.projectId().equals(projectId)) denied();
                var rawSources = sources.source(version.id());
                if (rawSources.size() > 1) mismatch();
                if (!rawSources.isEmpty()) {
                    var source = rawSources.get(0);
                    var requirement = new SourceReadProtocol.ReadRequirement(source.sourceVersion(), source.rightsRevision(),
                            projectId, null, null, null, SourceReadProtocol.Use.REVIEW, purpose, policy.environment(), policy.region(), null);
                    verifiedSources.put(version.id(), sourceAuthority.require(admission, requirement));
                    if (sources.gates(version.id()).stream().noneMatch(gate -> gate.uploadStatus().equals("RECEIVED")
                            && gate.approvalStatus().equals("APPROVED") && gate.lifecycle().equals("ACTIVE"))) notReady();
                }
                if (version.entityKind().equals("ARTIFACT")) {
                    var binding = single(artifacts.binding(version.id()));
                    String role = Objects.toString(roles.get(version.id()), "DEPENDENCY");
                    var profiles = policy.artifactProfiles().stream().filter(profile -> profile.role().equals(role)
                            && profile.schemaRef().equals(binding.schemaRef())).toList();
                    var known=SimulationArtifactProfiles.known(binding.schemaRef(),binding.resourceKind());
                    EvaluationDependencyPolicy.ArtifactProfile profile;
                    if(known.isPresent()) {
                        if(!"DEPENDENCY".equals(role)||profiles.size()>1)mismatch();
                        profile=known.get();
                        if(!profiles.isEmpty()) {
                            var configured=profiles.get(0);
                            if(!profile.equals(new EvaluationDependencyPolicy.ArtifactProfile(configured.resourceKind(),configured.action(),
                                configured.schemaRef(),configured.schemaDocumentSha256(),configured.artifactSchemaManifest())))mismatch();
                        }
                    } else {
                        if(profiles.size()!=1)throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
                        var configured=profiles.get(0);
                        profile=new EvaluationDependencyPolicy.ArtifactProfile(configured.resourceKind(),configured.action(),
                            configured.schemaRef(),configured.schemaDocumentSha256(),configured.artifactSchemaManifest());
                    }
                    if (!profile.resourceKind().equals(binding.resourceKind())) mismatch();
                    requirements.put(version.id(), new ArtifactRegistryProtocol.ReadRequirement(
                            new PermissionProtocol.Scope(admission.context().tenant_id(), binding.projectId(), profile.resourceKind(), binding.resourceId()),
                            profile.action(), purpose, profile.schemaRef(), profile.schemaDocumentSha256(), profile.artifactSchemaManifest(), policy.limits()));
                }
            }
            var verifiedVersions = versions.require(admission, graph, purpose, new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW, null), requirements);
            for (var version : verifiedVersions.values()) {
                if (version.owner().sourceVersion() != null) {
                    var source = verifiedSources.get(version.owner().sourceVersion());
                    if (source == null || !source.source().sourceId().equals(version.owner().resourceId())
                            || !graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion())) mismatch();
                }
                if (version.domain() == VersionResourceProtocol.Domain.RULE_VERSION) {
                    var values = values(admission); values.put("upstreamId", version.version().id()); values.put("ruleId", version.version().id());
                    var bound=single(rules.definition(values));
                    if(!bound.contentHash().equals(version.version().contentSha256())
                        || !schema.bindingHash(bound.tenantId(),bound.ruleVersion(),bound.contentHash()).equals(bound.bindingHash())
                        || !schema.binding(bound.tenantId(),bound.ruleVersion(),bound.contentHash()).equals(schema.read(bound.bindingJson()))
                        || !schema.manifestSha256().equals(bound.bindingManifest())) mismatch();
                    if (!templates.ruleState(values).equals(List.of("APPROVED"))) notReady();
                    // 新流程生成的版本必须具有当前人工决定和其事务回执；旧版本保留原核查边界。
                    if (!rules.approvedWorkflow(values).equals(List.of(version.version().id()))) notReady();
                    var body=json.object().put("schema_version","1.0.0").put("content_schema_ref",SimulationRuleVersionSchema.CONTENT_REF)
                        .put("dependency_use",PURPOSE).put("production_eligible",false).put("reason",bound.reason());
                    var baseRef=bound.baseVersion()==null?null:new PermissionProtocol.Basis(bound.baseVersion(),bound.baseHash());
                    body.set("base_version",json.tree(baseRef));body.set("content",schema.read(bound.contentJson()));
                    var checked=schema.version(json.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if(!checked.content_sha256().equals(bound.contentHash())||!bound.contentHash().equals(bound.registryHash())
                        ||!bound.manifest().equals(schema.contentManifestSha256())||!bound.schemaVersion().equals("1.0.0")
                        ||!checked.request().content().engine_revision().equals(bound.engineRevision())
                        ||!json.tree(checked.request().content().expression()).equals(schema.read(bound.expression()))
                        ||!json.tree(checked.request().content().basis_manifest()).equals(schema.read(bound.basisManifest()))
                        ||!rules.parents(values).equals(checked.request().content().basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())
                        ||!rules.baseParents(values).equals(baseRef==null?List.of():List.of(baseRef.version_id()))) mismatch();
                    var actual=new SimulationRuleVersionProtocol.Stored(bound.tenantId(),bound.ruleVersion(),bound.status(),bound.revision().toString(),
                        checked.request().content(),bound.contentHash(),baseRef,bound.reason(),bound.authorityGrant(),bound.authorityRevision().toString(),
                        bound.createdBy(),DatabaseTimestamp.format(bound.createdAt()),checked.dependency_refs(),PURPOSE,false,bound.bindingHash());
                    SimulationRuleApprovalEvidence.require(rules,schema,json,values,actual);
                }
                if (version.artifactRegistration() != null) for (var member : version.artifactRegistration().manifest().members()) {
                    var rights = single(sources.rights(member.rightsRevision()));
                    var source = verifiedSources.get(rights.sourceVersion());
                    if (source == null || !source.rights().rightsRevision().equals(member.rightsRevision())
                            || !graph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion())) mismatch();
                }
            }
            if ("FONT".equals(roles.get(ref.version_id())) && verifiedVersions.get(ref.version_id()).domain() != VersionResourceProtocol.Domain.ARTIFACT) mismatch();
            if (ruleIds.contains(ref.version_id()) && verifiedVersions.get(ref.version_id()).domain() != VersionResourceProtocol.Domain.RULE_VERSION) mismatch();
            if (base != null && base.version_id().equals(ref.version_id())
                    && verifiedVersions.get(ref.version_id()).domain() != VersionResourceProtocol.Domain.TEMPLATE_VERSION) mismatch();
            result.add(new DependencySourceAuthority.VerifiedDependencies(admission, ref.version_id(), verifiedSources, verifiedVersions));
        }
        return List.copyOf(result);
    }

    com.fasterxml.jackson.databind.JsonNode snapshot(EnterpriseAccess.Admission admission,
            List<DependencySourceAuthority.VerifiedDependencies> proofs, PermissionSchema json) {
        var result = json.object(); result.put("context_revision", admission.context().context_revision());
        result.put("auth_epoch", admission.watermarks().authEpoch().toString()); result.put("governance_epoch", admission.watermarks().governanceEpoch().toString());
        result.set("execution_policy", json.tree(policy)); var roots = json.object().arrayNode();
        for (var proof : proofs) {
            if (proof.admission() != admission) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var root = json.object(); root.put("root_version", proof.root()); var values = json.object().arrayNode();
            for (var entry : new java.util.TreeMap<>(proof.versions()).values()) {
                var value = json.object(); value.put("version_id", entry.version().id()); value.put("content_sha256", entry.version().contentSha256());
                value.put("domain", entry.domain().name()); value.put("resource_kind", entry.owner().resourceKind()); value.put("resource_id", entry.owner().resourceId());
                value.put("grant_id", entry.resourceGrant().grantId()); value.put("grant_revision", entry.resourceGrant().revision().toString());
                values.add(value);
            }
            root.set("versions", values); var sources = json.object().arrayNode();
            for (var source : new java.util.TreeMap<>(proof.sources()).values()) {
                var value = json.object(); value.put("source_version", source.source().sourceVersion()); value.put("rights_revision", source.rights().rightsRevision());
                value.put("rights_sha256", source.rights().contentSha256()); value.put("source_grant_id", source.sourceGrant().grantId());
                value.put("source_grant_revision", source.sourceGrant().revision().toString()); sources.add(value);
            }
            root.set("sources", sources); roots.add(root);
        }
        result.set("dependencies", roots); return result;
    }

    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var result = new HashMap<String,Object>(); result.put("userId", admission.actorId()); return result;
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void notReady() { throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY); }
}
