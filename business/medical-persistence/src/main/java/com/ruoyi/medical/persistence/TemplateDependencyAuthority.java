package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
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
public final class TemplateDependencyAuthority {
    static final String PURPOSE = "TEMPLATE_MAINTENANCE";
    private final DependencyLineage lineage;
    private final VersionResourceAuthority versions;
    private final SourceReadAuthority sourceAuthority;
    private final SourceReadMapper sources;
    private final ArtifactRegistryMapper artifacts;
    private final TemplateMapper templates;
    private final TemplateDependencyPolicy policy;

    public TemplateDependencyAuthority(DependencyLineage lineage, VersionResourceAuthority versions, SourceReadAuthority sourceAuthority,
            SourceReadMapper sources, ArtifactRegistryMapper artifacts, TemplateMapper templates, TemplateDependencyPolicy policy) {
        this.lineage = lineage; this.versions = versions; this.sourceAuthority = sourceAuthority; this.sources = sources;
        this.artifacts = artifacts; this.templates = templates; this.policy = policy;
    }

    List<DependencySourceAuthority.VerifiedDependencies> require(EnterpriseAccess.Admission admission, TemplateProtocol.Validated request) throws IOException {
        return require(admission, request, PURPOSE);
    }

    List<DependencySourceAuthority.VerifiedDependencies> require(EnterpriseAccess.Admission admission, TemplateProtocol.Validated request, String purpose) throws IOException {
        var content = request.request().content(); var roles = new HashMap<String,String>();
        content.format_profile().font_artifacts().forEach(ref -> roles.put(ref.version_id(), "FONT"));
        if (roles.putIfAbsent(content.format_profile().format_revision().version_id(), "FORMAT") != null) mismatch();
        return requireReferences(admission, request.dependency_refs(), roles, ruleIds(content), request.request().base_version(), null, purpose);
    }

    List<DependencySourceAuthority.VerifiedDependencies> requireProfile(EnterpriseAccess.Admission admission,
            ProjectProtocol.VerifiedProfile profile, String purpose) throws IOException {
        return requireReferences(admission, profile.authorityRefs(), Map.of(), java.util.Set.of(), null, profile.project().projectId(), purpose);
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
                            projectId, null, null, null, SourceReadProtocol.Use.TEMPLATE, purpose, policy.environment(), policy.region(), null);
                    verifiedSources.put(version.id(), sourceAuthority.require(admission, requirement));
                    if (sources.gates(version.id()).stream().noneMatch(gate -> gate.uploadStatus().equals("RECEIVED")
                            && gate.approvalStatus().equals("APPROVED") && gate.lifecycle().equals("ACTIVE"))) notReady();
                }
                if (version.entityKind().equals("ARTIFACT")) {
                    var binding = single(artifacts.binding(version.id()));
                    String role = Objects.toString(roles.get(version.id()), "DEPENDENCY");
                    var profiles = policy.artifactProfiles().stream().filter(profile -> profile.role().equals(role)
                            && profile.schemaRef().equals(binding.schemaRef())).toList();
                    if (profiles.size() != 1) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
                    var profile = profiles.get(0);
                    if (!profile.resourceKind().equals(binding.resourceKind())) mismatch();
                    requirements.put(version.id(), new ArtifactRegistryProtocol.ReadRequirement(
                            new PermissionProtocol.Scope(admission.context().tenant_id(), binding.projectId(), profile.resourceKind(), binding.resourceId()),
                            profile.action(), purpose, profile.schemaRef(), profile.schemaDocumentSha256(), profile.artifactSchemaManifest(), policy.limits()));
                }
            }
            var verifiedVersions = versions.require(admission, graph, purpose, new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.TEMPLATE, null), requirements);
            for (var version : verifiedVersions.values()) {
                if (version.owner().sourceVersion() != null) {
                    var source = verifiedSources.get(version.owner().sourceVersion());
                    if (source == null || !source.source().sourceId().equals(version.owner().resourceId())
                            || !graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion())) mismatch();
                }
                if (version.domain() == VersionResourceProtocol.Domain.RULE_VERSION) {
                    var values = values(admission); values.put("upstreamId", version.version().id());
                    if (!templates.ruleState(values).equals(List.of("APPROVED"))) notReady();
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

    private static java.util.Set<String> ruleIds(TemplateProtocol.Content content) {
        var ids = new java.util.HashSet<String>();
        java.util.function.Consumer<TemplateProtocol.Condition> condition = value -> {
            if (value.rule_revision() != null) ids.add(value.rule_revision().version_id());
        };
        java.util.function.Consumer<TemplateProtocol.EvidenceRule> evidence = value -> {
            value.verification_rule_revisions().forEach(ref -> ids.add(ref.version_id()));
            if (value.calculation_rule_revision() != null) ids.add(value.calculation_rule_revision().version_id());
        };
        condition.accept(content.condition_schema().applicability());
        content.nodes().forEach(node -> { condition.accept(node.condition()); evidence.accept(node.evidence_rule()); });
        content.field_definitions().forEach(field -> { condition.accept(field.condition()); evidence.accept(field.evidence_rule()); });
        content.attachment_requirements().forEach(item -> { condition.accept(item.condition()); evidence.accept(item.evidence_rule()); });
        content.cross_document_rules().forEach(rule -> ids.add(rule.rule_revision().version_id()));
        ids.add(content.format_profile().numbering_rule_revision().version_id());
        content.format_profile().node_displays().forEach(display -> ids.add(display.rule_revision().version_id()));
        return java.util.Set.copyOf(ids);
    }

    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var result = new HashMap<String,Object>(); result.put("userId", admission.subject().user().user_id()); return result;
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void notReady() { throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY); }
}
