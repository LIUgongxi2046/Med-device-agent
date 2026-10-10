package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.DocumentReadMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
@EnableConfigurationProperties(DocumentReadPolicy.class)
public final class DocumentReadService {
    private static final String PURPOSE = "DOCUMENT_BODY";
    private final EnterpriseAccess access;
    private final DocumentReadMapper documents;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final TaskMapper tasks;
    private final ScopeSnapshotSchema snapshots;
    private final ArtifactRegistryMapper artifacts;
    private final DependencyLineage lineage;
    private final ArtifactSourceAuthority authority;
    private final ArtifactContentService content;
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy storage;
    private final DocumentReadPolicy paging;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;
    private final String interfacesHash;

    public DocumentReadService(EnterpriseAccess access, DocumentReadMapper documents, PermissionMapper permissions,
            ResourceReadAuthority resources, TaskMapper tasks, ScopeSnapshotSchema snapshots, ArtifactRegistryMapper artifacts,
            DependencyLineage lineage, ArtifactSourceAuthority authority, ArtifactContentService content, SourceUploadTransport transport,
            DocumentUploadPolicy storage, DocumentReadPolicy paging, PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.documents = documents; this.permissions = permissions; this.resources = resources;
        this.tasks = tasks; this.snapshots = snapshots; this.artifacts = artifacts; this.lineage = lineage; this.authority = authority;
        this.content = content; this.transport = transport; this.storage = storage; this.paging = paging; this.json = json;
        schemas = SchemaCatalogue.fromClasspath();
        interfacesHash = schemas.bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256();
    }

    JsonNode snapshot(ApplicationSessionIdentity.Verified subject, String context, String documentId) throws Exception {
        return snapshotFixed(subject, context, documentId, null);
    }

    JsonNode snapshotFixed(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision) throws Exception {
        var initial = prepare(subject, context, documentId, revision);
        String requestId = UUID.randomUUID().toString(); var loaded = load(subject, initial, requestId);
        var state = current(subject, context, documentId, initial.header().documentRevision(), initial);
        // RUN游标必须具有实际资源清单和原授权游标，缺少生产者时明确报告依赖条件。
        if (!state.streams().isEmpty()) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var header = state.header(); var watermarks = state.watermarks();
        var value = new InterfaceProtocol.DocumentSnapshot(documentId, header.projectId(), header.documentRevision(), header.revision().toString(),
            header.planRevision(), header.templateVersion(), reference(state, "DOCUMENT_BODY"), reference(state, "DOCUMENT_FIELDS"),
            reference(state, "DOCUMENT_EVIDENCE"), header.bodySchemaRevision(), state.checks(), state.reviews(), state.signatures(), state.reasons(),
            watermarks.issueWatermark().toString(), watermarks.evidenceWatermark().toString(), state.authEpoch(), state.governanceEpoch(),
            state.actions(), List.of());
        var tree = json.tree(value); schemas.validate("interfaces", tree, "DocumentSnapshot");
        requireProjections(loaded, header); return envelope(tree, requestId, header.revision().toString());
    }

    JsonNode dependency(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision,
            String expectedRevision) {
        var state = prepare(subject, context, documentId, revision);
        var header = state.header();
        if (!header.documentRevision().equals(header.currentDocumentRevision()) || !header.revision().toString().equals(expectedRevision))
            throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!state.context().equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            resources.require(admission, new PermissionProtocol.Scope(header.tenantId(), header.projectId(), "DOCUMENT", documentId), "document.write", PURPOSE);
            return null;
        }).get());
        current(subject, context, documentId, revision, state);
        var value = json.tree(header.accessSetId()); schemas.validate("interfaces", value, "Id");
        return envelope(value, UUID.randomUUID().toString(), header.revision().toString());
    }

    private JsonNode envelope(JsonNode data, String requestId, String revision) {
        var value = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, data));
        schemas.validate("interfaces", value, "Envelope"); return value;
    }

    JsonNode body(ApplicationSessionIdentity.Verified subject, String context, String documentId, String documentRevision,
            List<String> nodeIds, int limit, String cursor) throws Exception {
        var initial = prepare(subject, context, documentId, documentRevision);
        paging.requireConfigured(); String requestId = UUID.randomUUID().toString(); var loaded = load(subject, initial, requestId);
        var body = loaded.body(); var requested = new HashSet<>(nodeIds);
        var selected = body.nodes().stream().filter(node -> requested.isEmpty() || requested.contains(node.nodeId())).toList();
        if (!requested.isEmpty() && selected.size() != requested.size()) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        String selection = WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("document_revision", documentRevision,
            "body_sha256", initial.header().bodySha256(), "node_ids", selected.stream().map(InterfaceProtocol.Node::nodeId).toList())));
        var cursors = new DocumentBodyCursor(paging);
        var position = cursors.position(cursor, initial.digest(), selection, limit, selected.size());
        int end = Math.min(selected.size(), Math.addExact(position.start(), limit));
        var nodes = selected.subList(position.start(), end);
        boolean first = position.start() == 0; var ids = new HashSet<>(selected.stream().map(InterfaceProtocol.Node::nodeId).toList());
        var fields = first ? body.fields().stream().filter(field -> requested.isEmpty() || ids.contains(field.nodeId())).toList() : List.<InterfaceProtocol.FieldValue>of();
        var links = first ? body.evidenceLinks().stream().filter(link -> requested.isEmpty() || link.nodeIds().stream().anyMatch(ids::contains)).toList() : List.<InterfaceProtocol.EvidenceLink>of();
        var facts = first ? body.factUsages().stream().filter(fact -> requested.isEmpty() || fact.nodeIds().stream().anyMatch(ids::contains)
            || fields.stream().anyMatch(field -> fact.fieldIds().contains(field.fieldId()))).toList() : List.<InterfaceProtocol.FactUsage>of();
        String delivered = WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("document_revision", documentRevision,
            "body_sha256", initial.header().bodySha256(), "nodes", nodes, "fields", fields, "evidence_links", links, "fact_usages", facts)));
        boolean complete = end == selected.size();
        String next = complete ? null : cursors.next(position, end, initial.digest(), selection);
        var result = new InterfaceProtocol.BodyPage(documentRevision, initial.header().bodySha256(), nodes, fields, links, facts,
            new CommonProtocol.Coverage(selection, delivered, complete, next));
        var tree = json.tree(result); schemas.validate("interfaces", tree, "BodyPage");
        var envelope = envelope(tree, requestId, initial.header().revision().toString());
        if (ContentReadAuthorization.canonicalBytes(envelope).length > paging.maximumPageBytes())
            throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        cursors.current(position); current(subject, context, documentId, documentRevision, initial); return envelope;
    }

    private Loaded load(ApplicationSessionIdentity.Verified subject, State state, String requestId) throws Exception {
        return transport.readDocument(subject, state.header().tenantId(), storage.maximumBytes().intValueExact(),
            () -> requireTransferHead(subject, state), (reader, admission) -> {
                var values = new HashMap<String,SchemaCatalogue.ValidatedPayload>();
                for (var entry : state.intents().entrySet()) {
                    var read = content.readVerifiedComplete(subject, entry.getValue(), reader, admission, storage.maximumBytes().intValueExact(), requestId);
                    var payload = read.jsonPayload();
                    if (!WireValues.sha256(payload.canonicalBytes()).equals(state.verified().get(entry.getKey()).registration().artifact().sha256())) mismatch();
                    values.put(entry.getKey(), payload);
                }
                var bodyPayload = values.get("DOCUMENT_BODY");
                DocumentBodySemantics.published(schemas, schemas.requirePublished(bodyPayload.schemaRef(), bodyPayload.schemaSha256()),
                    bodyPayload.canonicalBytes(), storage.bodyLimits());
                var body = TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_BODY,
                    new com.fasterxml.jackson.databind.json.JsonMapper().readTree(bodyPayload.canonicalJson())).read();
                var fields = TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_FIELD_PROJECTION,
                    new com.fasterxml.jackson.databind.json.JsonMapper().readTree(values.get("DOCUMENT_FIELDS").canonicalJson())).read();
                var evidence = TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_EVIDENCE_PROJECTION,
                    new com.fasterxml.jackson.databind.json.JsonMapper().readTree(values.get("DOCUMENT_EVIDENCE").canonicalJson())).read();
                var result = new Loaded(body, fields, evidence); requireProjections(result, state.header()); return result;
            });
    }

    private static void requireProjections(Loaded loaded, DocumentReadProtocol.Header header) {
        if (!header.templateVersion().equals(loaded.body().templateVersion())
                || !header.documentRevision().equals(loaded.fields().documentRevision())
                || !header.documentRevision().equals(loaded.evidence().documentRevision())
                || !header.bodySha256().equals(loaded.fields().bodySha256()) || !header.bodySha256().equals(loaded.evidence().bodySha256())
                || !loaded.body().fields().equals(loaded.fields().fields())
                || !loaded.body().evidenceLinks().equals(loaded.evidence().evidenceLinks())
                || !loaded.body().factUsages().equals(loaded.evidence().factUsages())) mismatch();
    }

    private State current(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision, State original) {
        var result = prepare(subject, context, documentId, revision);
        if (!original.digest().equals(result.digest())) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return result;
    }

    private void requireTransferHead(ApplicationSessionIdentity.Verified subject, State state) {
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!state.context().equals(admission.context().context_revision()) || !state.authEpoch().equals(admission.watermarks().authEpoch().toString())
                    || !state.governanceEpoch().equals(admission.watermarks().governanceEpoch().toString())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var header = state.header();
            resources.require(admission, new PermissionProtocol.Scope(header.tenantId(), header.projectId(), "DOCUMENT", header.documentId()), "document.read", PURPOSE);
            var values = Map.<String,Object>of("userId", subject.user().user_id(), "projectId", header.projectId(),
                "documentId", header.documentId(), "documentRevision", header.documentRevision());
            if (!header.equals(one(documents.header(values)))) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            return null;
        }).get());
    }

    private State prepare(ApplicationSessionIdentity.Verified subject, String context, String documentId, String revision) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var owners = permissions.resourceOwner("DOCUMENT", documentId);
            if (owners.size() != 1 || owners.get(0).projectId() == null) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            String project = owners.get(0).projectId(), tenant = admission.context().tenant_id(), user = subject.user().user_id();
            var scope = new PermissionProtocol.Scope(tenant, project, "DOCUMENT", documentId);
            var grant = resources.require(admission, scope, "document.read", PURPOSE); storage.requireConfigured();
            var values = new HashMap<String,Object>(); values.put("userId", user); values.put("projectId", project);
            values.put("documentId", documentId); values.put("documentRevision", revision);
            var header = one(documents.header(values)); values.put("documentRevision", header.documentRevision());
            validateHeader(header, tenant, project, documentId, revision);
            var snapshot = one(tasks.snapshot(user, project, header.snapshotId())); values.put("snapshotId", header.snapshotId());
            long count = Objects.requireNonNull(documents.sourceCount(values));
            if (count > storage.maximumSources()) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var members = tasks.members(user, project, header.snapshotId());
            var manifest = snapshots.require(snapshot.manifest(), snapshot.manifestHash(), tenant, project, header.snapshotId(), header.profileRevision(),
                snapshot.authEpoch(), snapshot.governanceEpoch(), null, members);
            if (!snapshot.manifestHash().equals(snapshot.registryHash()) || count != members.size()) mismatch();
            var sourceRequirements = new ArrayList<SourceReadProtocol.ReadRequirement>();
            var use = header.documentRevision().equals(header.currentDocumentRevision()) ? SourceReadProtocol.Use.FORMAL : SourceReadProtocol.Use.HISTORY;
            for (var member : manifest.members()) sourceRequirements.add(new SourceReadProtocol.ReadRequirement(member.source_version(), member.rights_revision(),
                project, header.snapshotId(), member.parse_version(), null, use, PURPOSE, storage.environment(), storage.region(), null));
            var contextRead = new VersionResourceProtocol.ReadContext(use, null);
            var selected = new LinkedHashMap<String,String>(); selected.put("DOCUMENT_BODY", header.bodyArtifactId());
            for (var projection : documents.projections(values)) if (selected.putIfAbsent(projection.artifactKind(), projection.artifactId()) != null) mismatch();
            if (!selected.keySet().equals(java.util.Set.of("DOCUMENT_BODY", "DOCUMENT_FIELDS", "DOCUMENT_EVIDENCE")))
                throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var intents = new LinkedHashMap<String,ContentReadProtocol.ReadIntent>();
            var verified = new LinkedHashMap<String,ArtifactSourceAuthority.VerifiedSources>();
            var authorizations = new LinkedHashMap<String,String>();
            for (var entry : selected.entrySet()) {
                String definition = switch (entry.getKey()) {
                    case "DOCUMENT_BODY" -> "DocumentBody"; case "DOCUMENT_FIELDS" -> "DocumentFieldProjection";
                    case "DOCUMENT_EVIDENCE" -> "DocumentEvidenceProjection"; default -> throw new IllegalArgumentException("正文投影类型无效");
                };
                var required = new ArtifactRegistryProtocol.ReadRequirement(scope, "document.read", PURPOSE, schemas.schemaRef("interfaces", definition),
                    interfacesHash, storage.artifactSchemaManifest(), storage.limits());
                var upstream = upstream(entry.getValue(), admission);
                var intent = new ContentReadProtocol.ReadIntent(entry.getValue(), context, required, sourceRequirements, contextRead, upstream);
                var current = authority.require(admission, entry.getValue(), required, sourceRequirements, contextRead, upstream);
                var registered = current.registration();
                if (!entry.getKey().equals(registered.artifact().artifactKind()) || !header.accessSetId().equals(registered.artifact().accessSetId())
                        || !documentId.equals(registered.binding().resourceId()) || !project.equals(registered.artifact().projectId())) mismatch();
                if (entry.getKey().equals("DOCUMENT_BODY") && (!header.bodySha256().equals(registered.artifact().sha256())
                        || !header.objectKey().equals(registered.artifact().objectKey()) || !header.objectVersion().equals(registered.artifact().objectVersion()))) mismatch();
                for (var member : manifest.members()) ScopeSnapshotSchema.requireSource(member, Objects.requireNonNull(current.sources().get(member.source_version())));
                intents.put(entry.getKey(), intent); verified.put(entry.getKey(), current);
                authorizations.put(entry.getKey(), ContentReadAuthorization.digest(admission, intent, current));
            }
            var marks = one(documents.watermarks(values)); WireValues.revision(marks.issueWatermark().toString()); WireValues.revision(marks.evidenceWatermark().toString());
            var checks = documents.checks(values); var reviews = documents.reviews(values); var signatures = documents.signatures(values);
            var reasons = documents.invalidationReasons(values); var streams = documents.streams(values);
            var actions = new ArrayList<String>();
            if (header.documentRevision().equals(header.currentDocumentRevision()) && "ACTIVE".equals(header.projectStatus()) && !"SIGNED".equals(header.status())
                    && resources.find(admission, scope, "document.write", PURPOSE).isPresent()) actions.add("document.edit");
            var identity = new LinkedHashMap<String,Object>(); identity.put("header", header); identity.put("snapshot", snapshot); identity.put("members", members);
            identity.put("grant", grant); identity.put("authorizations", authorizations); identity.put("watermarks", marks);
            identity.put("checks", checks); identity.put("reviews", reviews); identity.put("signatures", signatures); identity.put("reasons", reasons);
            identity.put("streams", streams); identity.put("actions", actions);
            return new State(header, context, Map.copyOf(intents), Map.copyOf(verified), WireValues.sha256(ContentReadAuthorization.canonicalBytes(identity)),
                marks, List.copyOf(checks), List.copyOf(reviews), List.copyOf(signatures), List.copyOf(reasons), List.copyOf(streams), List.copyOf(actions),
                admission.watermarks().authEpoch().toString(), admission.watermarks().governanceEpoch().toString());
        }).get());
    }

    private Map<String,ArtifactRegistryProtocol.ReadRequirement> upstream(String id, EnterpriseAccess.Admission admission) throws IOException {
        var graph = lineage.inspect(id, storage.limits()); var result = new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
        for (var version : graph.versions().values()) {
            if (!"ARTIFACT".equals(version.entityKind()) || id.equals(version.id())) continue;
            var binding = one(artifacts.binding(version.id()));
            var profiles = storage.artifactProfiles().stream().filter(profile -> profile.resourceKind().equals(binding.resourceKind())
                && profile.schemaRef().equals(binding.schemaRef())).toList();
            if (profiles.size() != 1) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
            var profile = profiles.get(0); schemas.requirePublished(profile.schemaRef(), profile.schemaDocumentSha256());
            result.put(version.id(), new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(admission.context().tenant_id(),
                binding.projectId(), profile.resourceKind(), binding.resourceId()), profile.action(), PURPOSE, profile.schemaRef(),
                profile.schemaDocumentSha256(), profile.artifactSchemaManifest(), storage.limits()));
        }
        return result;
    }

    private static CommonProtocol.ArtifactRef reference(State state, String kind) throws Exception {
        var registration = state.verified().get(kind).registration(); var artifact = registration.artifact();
        String range = ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(artifact.reference()), 0, artifact.sizeBytes().longValueExact());
        return new CommonProtocol.ArtifactRef(artifact.artifactId(), artifact.sha256(), registration.binding().schemaRef(),
            new CommonProtocol.Coverage(range, range, true, null));
    }

    private void validateHeader(DocumentReadProtocol.Header header, String tenant, String project, String id, String revision) {
        if (!tenant.equals(header.tenantId()) || !project.equals(header.projectId()) || !id.equals(header.documentId())
                || revision != null && !revision.equals(header.documentRevision())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        for (String value : List.of(header.documentRevision(), header.currentDocumentRevision(), header.taskId(), header.taskRevision(), header.snapshotId(),
                header.profileRevision(), header.bodyArtifactId(), header.bodySchemaRevision(), header.planRevision(), header.templateVersion(), header.accessSetId(), header.createdBy())) WireValues.uuid(value);
        WireValues.hash(header.bodySha256()); WireValues.hash(header.contentSha256());
        if (!header.bodySha256().equals(header.contentSha256()) || header.revision().signum() <= 0 || header.createdAt() == null) mismatch();
        var registered = one(artifacts.version(header.documentRevision()));
        if (!header.contentSha256().equals(registered.contentSha256()) || !project.equals(registered.projectId())) mismatch();
    }

    private static <T> T one(List<T> rows) {
        if (rows.size() != 1) throw new TenantAccessException(ErrorCode.ACCESS_DENIED); return rows.get(0);
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private record Loaded(InterfaceProtocol.DocumentBody body, InterfaceProtocol.DocumentFieldProjection fields,
            InterfaceProtocol.DocumentEvidenceProjection evidence) { }
    private record State(DocumentReadProtocol.Header header, String context, Map<String,ContentReadProtocol.ReadIntent> intents,
            Map<String,ArtifactSourceAuthority.VerifiedSources> verified, String digest, DocumentReadProtocol.Watermarks watermarks,
            List<String> checks, List<String> reviews, List<String> signatures, List<String> reasons, List<DocumentReadProtocol.StreamRow> streams,
            List<String> actions, String authEpoch, String governanceEpoch) { }
}
