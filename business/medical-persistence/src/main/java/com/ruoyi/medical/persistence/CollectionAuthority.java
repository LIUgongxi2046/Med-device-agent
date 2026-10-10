package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.CollectionMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(CollectionPolicy.class)
public final class CollectionAuthority {
    private final CollectionMapper mapper;
    private final SourceReadMapper sources;
    private final SourceReadAuthority sourceAuthority;
    private final ResourceReadAuthority resources;
    private final DependencyLineage lineage;
    private final VersionResourceAuthority versionAuthority;
    private final ArtifactRegistryMapper artifacts;
    private final CollectionSchema schema;
    private final PermissionSchema json;
    private final CollectionPolicy policy;

    public CollectionAuthority(CollectionMapper mapper, SourceReadMapper sources, SourceReadAuthority sourceAuthority,
            ResourceReadAuthority resources, DependencyLineage lineage, VersionResourceAuthority versionAuthority,
            ArtifactRegistryMapper artifacts, CollectionSchema schema, PermissionSchema json, CollectionPolicy policy) {
        this.mapper = mapper; this.sources = sources; this.sourceAuthority = sourceAuthority; this.resources = resources;
        this.lineage = lineage; this.versionAuthority = versionAuthority; this.artifacts = artifacts;
        this.schema = schema; this.json = json; this.policy = policy;
    }
    record Proof(CollectionProtocol.Head head, CollectionProtocol.Version version, CollectionProtocol.Manifest manifest,
            DependencyLineage.VerifiedLineage lineage, Map<String,SourceReadProtocol.VerifiedSource> sources,
            Map<String,VersionResourceAuthority.VerifiedVersion> versions) {
        Proof { sources = Map.copyOf(sources); versions = Map.copyOf(versions); }
    }

    PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission admission, String project, String action, String purpose) throws IOException {
        String tenant = admission.context().tenant_id();
        return resources.require(admission, new PermissionProtocol.Scope(tenant, project,
                project == null ? "ENTERPRISE" : "PROJECT", project == null ? tenant : project), action, purpose);
    }

    CollectionProtocol.Head head(EnterpriseAccess.Admission admission, String collectionId) {
        var values = values(admission); values.put("collectionId", collectionId);
        var head = single(mapper.head(values));
        if (!head.tenantId().equals(admission.context().tenant_id()) || !head.collectionId().equals(collectionId)) mismatch();
        CollectionSchema.positive(head.revision().toString()); return head;
    }

    List<CollectionProtocol.Member> prepare(EnterpriseAccess.Admission admission, String project,
            CollectionProtocol.Content content, PermissionProtocol.Basis base) throws IOException {
        if (content.members().size() > policy.maximumMembers()) capacity();
        var result = new ArrayList<CollectionProtocol.Member>();
        var allVersions = new HashSet<String>(); var allRelations = new HashSet<SourceReadProtocol.ParentRow>();
        for (var selection : content.members()) {
            var source = single(sources.source(selection.source_version()));
            var verified = sourceAuthority.require(admission, new SourceReadProtocol.ReadRequirement(source.sourceVersion(),
                    source.rightsRevision(), project, null, null, null, SourceReadProtocol.Use.REVIEW,
                    content.purpose(), policy.environment(), policy.region(), null));
            owner(verified.source(), project);
            var parse = parse(admission, selection.source_version(), selection.parse_version(), verified.source());
            if (sources.gates(source.sourceVersion()).stream().noneMatch(gate -> gate.parseVersion().equals(parse.parseVersion())
                    && gate.uploadStatus().equals("RECEIVED") && gate.lifecycle().equals("ACTIVE"))) notReady();
            result.add(new CollectionProtocol.Member(source.sourceId(), source.sourceVersion(), parse.parseVersion(),
                    verified.rights().rightsRevision(), source.originalSha256(), parse.contentSha256()));
            for (String root : List.of(source.sourceVersion(), parse.parseVersion(), verified.rights().rightsRevision())) {
                var graph = lineage.inspect(root, policy.limits());
                accumulate(graph, allVersions, allRelations); authorize(admission, graph, project, content.purpose(), policy.environment(), policy.region());
            }
        }
        if (base != null) {
            var proof = require(admission, base.version_id(), content.purpose(), policy.environment(), policy.region(), policy.limits());
            if (!base.content_sha256().equals(proof.version().contentSha256()) || !Objects.equals(project, proof.head().projectId())) mismatch();
            accumulate(proof.lineage(), allVersions, allRelations);
        }
        return List.copyOf(result);
    }

    Proof require(EnterpriseAccess.Admission admission, String versionId, String purpose, String environment,
            String region, DependencyLineage.Limits limits) throws IOException {
        var values = values(admission); values.put("versionId", versionId);
        var row = single(mapper.version(values)); var head = head(admission, row.collectionId());
        grant(admission, head.projectId(), "collection.read", purpose);
        var graph = lineage.inspect(versionId, limits);
        var verified = authorize(admission, graph, head.projectId(), purpose, environment, region);
        var root = verified.versions().get(versionId);
        if (root == null || root.domain() != VersionResourceProtocol.Domain.COLLECTION_VERSION) mismatch();
        CollectionProtocol.Manifest manifest = null;
        for (var version : verified.versions().values()) if (version.domain() == VersionResourceProtocol.Domain.COLLECTION_VERSION) {
            values.put("versionId", version.version().id());
            var actual = version.version().id().equals(versionId) ? row : single(mapper.version(values));
            var owner = actual.collectionId().equals(head.collectionId()) ? head : head(admission, actual.collectionId());
            var content = stored(admission, actual, owner, graph, verified.sources());
            if (actual.versionId().equals(versionId)) manifest = content;
        }
        if (manifest == null) mismatch();
        return new Proof(head, row, manifest, graph, verified.sources(), verified.versions());
    }

    private record Dependencies(Map<String,SourceReadProtocol.VerifiedSource> sources,
            Map<String,VersionResourceAuthority.VerifiedVersion> versions) { }
    private Dependencies authorize(EnterpriseAccess.Admission admission, DependencyLineage.VerifiedLineage graph,
            String project, String purpose, String environment, String region) throws IOException {
        var authorizedSources = new LinkedHashMap<String,SourceReadProtocol.VerifiedSource>();
        var requirements = new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
        for (var version : graph.versions().values()) {
            if (version.projectId() != null && !version.projectId().equals(project)) denied();
            var rows = sources.source(version.id());
            if (rows.size() > 1) mismatch();
            if (!rows.isEmpty()) {
                var source = rows.get(0);
                var verified = sourceAuthority.require(admission, new SourceReadProtocol.ReadRequirement(source.sourceVersion(),
                        source.rightsRevision(), project, null, null, null, SourceReadProtocol.Use.REVIEW, purpose, environment, region, null));
                owner(verified.source(), project); authorizedSources.put(version.id(), verified);
            }
            if (version.entityKind().equals("ARTIFACT")) {
                var binding = single(artifacts.binding(version.id()));
                var profiles = policy.artifactProfiles().stream().filter(profile -> profile.schemaRef().equals(binding.schemaRef())).toList();
                if (profiles.size() != 1) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
                var profile = profiles.get(0);
                if (!binding.tenantId().equals(admission.context().tenant_id()) || !profile.resourceKind().equals(binding.resourceKind())
                        || !profile.schemaDocumentSha256().equals(binding.schemaDocumentSha256())
                        || binding.projectId() != null && !binding.projectId().equals(project)) mismatch();
                requirements.put(version.id(), new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(
                        binding.tenantId(), binding.projectId(), binding.resourceKind(), binding.resourceId()), profile.action(), purpose,
                        profile.schemaRef(), profile.schemaDocumentSha256(), profile.artifactSchemaManifest(), policy.limits()));
            }
        }
        var versions = versionAuthority.require(admission, graph, purpose,
                new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW, null), requirements);
        for (var version : versions.values()) {
            if (version.owner().sourceVersion() != null) {
                var source = authorizedSources.get(version.owner().sourceVersion());
                if (source == null || !source.source().sourceId().equals(version.owner().resourceId())
                        || !graph.reachableFrom(version.version().id()).contains(source.source().sourceVersion())) mismatch();
            }
            if (version.artifactRegistration() != null) for (var member : version.artifactRegistration().manifest().members()) {
                var rights = single(sources.rights(member.rightsRevision())); var source = authorizedSources.get(rights.sourceVersion());
                if (source == null || !source.rights().rightsRevision().equals(member.rightsRevision())
                        || !graph.reachableFrom(member.upstreamVersionId()).contains(rights.sourceVersion())) mismatch();
            }
        }
        return new Dependencies(Map.copyOf(authorizedSources), versions);
    }

    private CollectionProtocol.Manifest stored(EnterpriseAccess.Admission admission, CollectionProtocol.Version row,
            CollectionProtocol.Head head, DependencyLineage.VerifiedLineage graph,
            Map<String,SourceReadProtocol.VerifiedSource> authorizedSources) throws IOException {
        var content = schema.manifest(row.manifest());
        if (!row.tenantId().equals(admission.context().tenant_id()) || !row.tenantId().equals(content.tenant_id())
                || !row.versionId().equals(content.collection_version()) || !row.collectionId().equals(content.collection_id())
                || !head.collectionId().equals(content.collection_id()) || !Objects.equals(head.projectId(), content.owner_project_id())
                || !Objects.equals(head.projectId(), row.registeredProjectId()) || !row.purpose().equals(content.purpose())
                || !row.createdBy().equals(content.created_by()) || !row.createdAt().equals(java.time.LocalDateTime.ofInstant(
                        WireValues.utcTime(content.created_at()), java.time.ZoneOffset.UTC))
                || !head.classification().equals(row.classification()) || !row.schemaVersion().equals("1.0.0")
                || !row.schemaManifest().equals(schema.manifestSha256()) || !row.contentSha256().equals(row.registeredSha256())
                || !row.contentSha256().equals(DigestUtils.sha256Hex(json.canonicalObject(content)))
                || head.revision().compareTo(PermissionSchema.revision(content.revision())) < 0) mismatch();
        if (row.versionId().equals(head.currentVersion()) && (!head.revision().toString().equals(content.revision())
                || !head.title().equals(content.title()) || !head.updatedAt().equals(row.createdAt()))) mismatch();
        if (content.base_version() == null && (!head.createdBy().equals(row.createdBy()) || !head.createdAt().equals(row.createdAt()))) mismatch();
        if (content.members().size() > policy.maximumMembers()) capacity();
        var values = values(admission); values.put("versionId", row.versionId()); values.put("limit", policy.maximumMembers() + 1);
        var physical = mapper.members(values);
        if (physical.size() > policy.maximumMembers()) capacity();
        if (physical.size() != content.members().size()) mismatch();
        var selected = new HashMap<String,CollectionProtocol.Member>();
        for (var member : content.members()) if (selected.putIfAbsent(member.source_version(), member) != null) mismatch();
        var relationSet = new HashSet<SourceReadProtocol.ParentRow>();
        for (var member : physical) {
            var fixed = selected.remove(member.sourceVersion());
            if (fixed == null || !member.tenantId().equals(row.tenantId()) || !member.versionId().equals(row.versionId())
                    || !member.parseVersion().equals(fixed.parse_version()) || !member.createdBy().equals(row.createdBy())
                    || !member.createdAt().equals(row.createdAt()) || !member.classification().equals(row.classification())) mismatch();
            var source = authorizedSources.get(fixed.source_version());
            if (source == null || !source.source().sourceId().equals(fixed.source_id()) || !source.source().originalSha256().equals(fixed.original_sha256())
                    || !source.rights().rightsRevision().equals(fixed.rights_revision())) mismatch();
            owner(source.source(), head.projectId());
            var parse = parse(admission, fixed.source_version(), fixed.parse_version(), source.source());
            if (!parse.contentSha256().equals(fixed.parse_sha256())) mismatch();
            relationSet.add(new SourceReadProtocol.ParentRow(row.versionId(), fixed.source_version(), "COLLECTION_SOURCE"));
            relationSet.add(new SourceReadProtocol.ParentRow(row.versionId(), fixed.parse_version(), "COLLECTION_PARSE"));
            relationSet.add(new SourceReadProtocol.ParentRow(row.versionId(), fixed.rights_revision(), "COLLECTION_RIGHTS"));
        }
        if (!selected.isEmpty()) mismatch();
        if (content.base_version() != null) {
            String id = content.base_version().version_id(); var base = graph.versions().get(id);
            if (base == null || !base.entityKind().equals("COLLECTION_VERSION") || !base.contentSha256().equals(content.base_version().content_sha256())) mismatch();
            values.put("versionId", id); var previous = single(mapper.version(values));
            if (!previous.collectionId().equals(row.collectionId())) mismatch();
            var before = schema.manifest(previous.manifest());
            if (!PermissionSchema.next(before.revision()).equals(content.revision())) mismatch();
            relationSet.add(new SourceReadProtocol.ParentRow(row.versionId(), id, "COLLECTION_BASE"));
        } else if (!content.revision().equals("1")) mismatch();
        if (!relationSet.equals(graph.graph().outgoingEdgesOf(row.versionId()))) mismatch();
        return content;
    }

    private CollectionProtocol.Parse parse(EnterpriseAccess.Admission admission, String sourceVersion, String parseVersion,
            SourceReadProtocol.SourceRow source) {
        var values = values(admission); values.put("sourceVersion", sourceVersion); values.put("parseVersion", parseVersion);
        var row = single(mapper.parse(values));
        if (!row.tenantId().equals(admission.context().tenant_id()) || !row.sourceVersion().equals(sourceVersion)
                || !row.parseVersion().equals(parseVersion) || !row.contentSha256().equals(row.registeredSha256())
                || !Objects.equals(source.ownerProjectId(), row.registeredProjectId())) mismatch();
        if (!row.status().equals("COMPLETE")) notReady();
        return row;
    }
    private void accumulate(DependencyLineage.VerifiedLineage graph, Set<String> versions, Set<SourceReadProtocol.ParentRow> relations) {
        versions.addAll(graph.versions().keySet()); relations.addAll(graph.graph().edgeSet());
        if (versions.size() >= policy.maximumVersions() || relations.size() > policy.maximumRelations()) capacity();
    }
    CollectionProtocol.View view(Proof proof) {
        var result = new CollectionProtocol.View("1.0.0", proof.manifest(), proof.version().contentSha256(),
                CollectionSchema.MANIFEST_REF, proof.version().schemaManifest(), List.of("collection.read"));
        schema.verify("View", json.tree(result)); return result;
    }
    static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        String statement = "com.ruoyi.medical.persistence.mapper.CollectionMapper.head"; TenantScope.requireSqlAccess(statement);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        var result = new HashMap<String,Object>(); result.put("userId", admission.subject().user().user_id());
        TenantScope.requireSubjectParameter(statement, result); return result;
    }
    private static void owner(SourceReadProtocol.SourceRow source, String project) {
        if (!source.ownership().equals("ENTERPRISE") && !(source.ownership().equals("PROJECT") && Objects.equals(project, source.ownerProjectId()))) denied();
    }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) denied(); return rows.get(0); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void notReady() { throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY); }
    private static void capacity() { throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); }
}
