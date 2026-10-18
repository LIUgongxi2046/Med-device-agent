package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.stereotype.Component;

@Component
public final class TemplateVersionStore {
    private final com.fasterxml.jackson.databind.json.JsonMapper reader = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String PURPOSE = "TEMPLATE_MAINTENANCE";
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.TemplateMapper.definition";
    private final TemplateMapper mapper;
    private final PermissionMapper permissions;
    private final SourceReadMapper sources;
    private final ResourceReadAuthority authority;
    private final PermissionSchema json;
    private final TemplateDefinitionSchema schema;
    private final IdentityDirectoryPolicy policy;

    public TemplateVersionStore(TemplateMapper mapper, PermissionMapper permissions, SourceReadMapper sources,
            ResourceReadAuthority authority, PermissionSchema json, TemplateDefinitionSchema schema, IdentityDirectoryPolicy policy) {
        this.mapper = mapper; this.permissions = permissions; this.sources = sources; this.authority = authority;
        this.json = json; this.schema = schema; this.policy = policy;
    }

    TemplateProtocol.Stored save(EnterpriseAccess.Admission admission, TemplateProtocol.Validated supplied,
            List<DependencySourceAuthority.VerifiedDependencies> dependencyProofs) throws IOException {
        var values = scope(admission);
        var grant = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                "ENTERPRISE", admission.context().tenant_id()), "template.write", PURPOSE);
        // 完整定义重新校验；调用方不能通过自行构造Validated跳过结构或关系核查。
        var validated = schema.version(json.canonicalObject(supplied.request()).getBytes(StandardCharsets.UTF_8));
        if (!validated.equals(supplied)) mismatch();
        dependencies(admission, validated.dependency_refs(), dependencyProofs, true);
        var request = validated.request(); var content = request.content();
        String id = UUID.randomUUID().toString(); values.put("templateId", id);
        values.put("content", validated.canonical_content()); values.put("hash", validated.content_sha256());
        values.put("documentType", content.document_type()); values.put("applicationPath", content.application_path());
        values.put("basis", json.canonicalObject(content.basis_manifest())); values.put("format", json.canonicalObject(content.format_profile()));
        values.put("conditions", json.canonicalObject(content.condition_schema())); values.put("reason", request.reason());
        values.put("baseId", request.base_version() == null ? null : request.base_version().version_id());
        values.put("baseHash", request.base_version() == null ? null : request.base_version().content_sha256());
        if (request.base_version() != null) {
            var proof = dependencyProofs.stream().filter(item -> item.root().equals(request.base_version().version_id())).findFirst().orElseThrow();
            if (proof.versions().get(proof.root()).domain() != VersionResourceProtocol.Domain.TEMPLATE_VERSION) mismatch();
        }
        var now = Objects.requireNonNull(permissions.databaseTime());
        values.put("now", now); values.put("manifest", schema.manifestSha256()); values.put("classification", policy.classification());
        values.put("authorityGrant", grant.grantId()); values.put("authorityRevision", grant.revision().toString());
        one(mapper.insertVersion(values)); one(mapper.insertTemplate(values)); one(mapper.insertDefinition(values));
        var nodes = index(content.nodes(), TemplateProtocol.Node::node_id);
        var graph = new DirectedAcyclicGraph<String, DefaultEdge>(DefaultEdge.class);
        nodes.keySet().forEach(graph::addVertex);
        for (var node : content.nodes()) if (node.parent_id() != null) graph.addEdge(node.parent_id(), node.node_id());
        var order = new TopologicalOrderIterator<>(graph);
        while (order.hasNext()) {
            var node = nodes.get(order.next()); values.put("item", node);
            values.put("condition", json.canonicalObject(node.condition())); values.put("evidence", json.canonicalObject(node.evidence_rule()));
            one(mapper.insertNode(values));
        }
        for (var field : content.field_definitions()) {
            values.put("item", field); values.put("constraints", json.canonicalObject(field.constraints()));
            values.put("condition", json.canonicalObject(field.condition())); values.put("evidence", json.canonicalObject(field.evidence_rule()));
            values.put("output", json.canonicalObject(field.output_mapping())); one(mapper.insertField(values));
        }
        for (var repeat : content.repeat_definitions()) {
            values.put("item", repeat); values.put("rowSchema", json.canonical(repeated(content, repeat))); one(mapper.insertRepeat(values));
        }
        for (var attachment : content.attachment_requirements()) {
            String requirement = json.canonicalObject(attachment); values.put("item", attachment); values.put("requirement", requirement);
            values.put("partHash", DigestUtils.sha256Hex(requirement)); one(mapper.insertAttachment(values));
        }
        int ordinal = 0;
        for (var dependency : validated.dependency_refs()) {
            values.put("item", dependency); values.put("ordinal", ++ordinal);
            one(mapper.insertDependency(values)); one(mapper.insertLineage(values));
        }
        var stored = stored(values);
        if (!stored.template_version().equals(id) || !stored.status().equals("DRAFT") || !stored.revision().equals("1")
                || !stored.content().equals(content) || !stored.content_sha256().equals(validated.content_sha256())
                || !Objects.equals(stored.base_version(), request.base_version()) || !stored.reason().equals(request.reason())
                || !stored.authority_grant_id().equals(grant.grantId()) || !stored.authority_revision().equals(grant.revision().toString())
                || !stored.created_by().equals(admission.subject().user().user_id()) || !stored.created_at().equals(time(now))) mismatch();
        dependencies(admission, validated.dependency_refs(), dependencyProofs, true);
        return stored;
    }

    TemplateProtocol.Stored read(EnterpriseAccess.Admission admission, String id,
            DependencySourceAuthority.VerifiedDependencies proof) throws IOException {
        var values = scope(admission); WireValues.uuid(id); values.put("templateId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", id), "template.read", PURPOSE);
        proof(admission, proof);
        if (!id.equals(proof.root())) mismatch();
        var stored = stored(values); var root = proof.versions().get(id);
        if (root == null || root.domain() != VersionResourceProtocol.Domain.TEMPLATE_VERSION
                || !stored.content_sha256().equals(root.version().contentSha256())) mismatch();
        for (var ref : stored.dependency_refs()) {
            var version = proof.versions().get(ref.version_id());
            if (version == null || !version.version().contentSha256().equals(ref.content_sha256())) mismatch();
        }
        return stored;
    }

    TemplateProtocol.Stored originalCreation(EnterpriseAccess.Admission admission, String id, TemplateProtocol.Validated supplied,
            List<DependencySourceAuthority.VerifiedDependencies> dependencyProofs) throws IOException {
        var values = scope(admission); WireValues.uuid(id); values.put("templateId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null,
                "ENTERPRISE", admission.context().tenant_id()), "template.write", PURPOSE);
        dependencies(admission, supplied.dependency_refs(), dependencyProofs, true);
        var stored = stored(values);
        if (!stored.created_by().equals(admission.subject().user().user_id()) || !stored.content().equals(supplied.request().content())
                || !stored.content_sha256().equals(supplied.content_sha256()) || !Objects.equals(stored.base_version(), supplied.request().base_version())
                || !stored.reason().equals(supplied.request().reason()) || !stored.dependency_refs().equals(supplied.dependency_refs())) mismatch();
        return stored;
    }

    TemplateProtocol.Stored approvalTarget(EnterpriseAccess.Admission admission, String id) throws IOException {
        var values = scope(admission); WireValues.uuid(id); values.put("templateId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", id), "template.approve", PURPOSE);
        return stored(values);
    }

    TemplateProtocol.Stored applicationTarget(EnterpriseAccess.Admission admission, String id, String purpose) throws IOException {
        var values = scope(admission); WireValues.uuid(id); values.put("templateId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", id), "template.read", purpose);
        var value = stored(values);
        if (!value.status().equals("APPROVED")) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        return value;
    }

    TemplateProtocol.Stored historicalTarget(EnterpriseAccess.Admission admission, String id, String purpose) throws IOException {
        var values = scope(admission); WireValues.uuid(id); values.put("templateId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "TEMPLATE", id), "template.read", purpose);
        return stored(values);
    }

    private TemplateProtocol.Stored stored(Map<String,Object> values) throws IOException {
        var rows = mapper.definition(values); if (rows.size() != 1) mismatch(); var row = rows.get(0);
        if (!row.tenantId().equals(TenantScope.requireCurrent().tenantId()) || !row.templateVersion().equals(values.get("templateId"))
                || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(schema.manifestSha256())
                || !row.classification().equals(policy.classification()) || row.revision().signum() <= 0
                || !java.util.Set.of("DRAFT", "APPROVED", "RETURNED", "DISABLED").contains(row.status())) mismatch();
        var content = schema.content(row.contentJson().getBytes(StandardCharsets.UTF_8));
        String canonical = json.canonicalObject(content), hash = DigestUtils.sha256Hex(canonical);
        if (!hash.equals(row.contentSha256()) || !hash.equals(row.registeredSha256())
                || !content.document_type().equals(row.documentType()) || !content.application_path().equals(row.applicationPath())
                || !tree(row.basisManifest()).equals(json.tree(content.basis_manifest()))
                || !tree(row.formatProfile()).equals(json.tree(content.format_profile()))
                || !tree(row.conditionSchema()).equals(json.tree(content.condition_schema()))) mismatch();
        var base = row.baseVersion() == null ? null : new PermissionProtocol.Basis(row.baseVersion(), row.baseVersionSha256());
        var validated = schema.version(json.canonicalObject(new TemplateProtocol.VersionInput("1.0.0", base,
                "urn:medical:templates:1.0.0#/$defs/Content", content, row.reason())).getBytes(StandardCharsets.UTF_8));
        var nodes = new HashMap<String, JsonNode>();
        for (var node : content.nodes()) {
            var value = json.tree(node).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) value)
                    .remove(List.of("basis_refs", "nature", "content_requirements")); nodes.put(node.node_id(), value);
        }
        compare(row, mapper.nodes(values), nodes);
        var fields = new HashMap<String, JsonNode>();
        for (var field : content.field_definitions()) {
            var value = json.tree(field).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) value)
                    .remove(List.of("key", "name", "description", "required", "repeat_id", "attachment_requirement_id", "basis_refs")); fields.put(field.field_id(), value);
        }
        compare(row, mapper.fields(values), fields);
        var repeats = new HashMap<String, JsonNode>();
        for (var repeat : content.repeat_definitions()) {
            var value = json.object(); value.put("repeat_id", repeat.repeat_id()); value.set("row_schema", repeated(content, repeat));
            value.put("min_count", repeat.min_count()); value.set("max_count", json.tree(repeat.max_count())); repeats.put(repeat.repeat_id(), value);
        }
        compare(row, mapper.repeats(values), repeats);
        var attachments = new HashMap<String, JsonNode>();
        for (var attachment : content.attachment_requirements()) {
            var value = json.object(); value.set("requirement", json.tree(attachment));
            value.put("content_sha256", DigestUtils.sha256Hex(json.canonicalObject(attachment))); attachments.put(attachment.attachment_requirement_id(), value);
        }
        compare(row, mapper.attachments(values), attachments);
        var dependencies = new HashMap<String, JsonNode>(); int ordinal = 0;
        for (var dependency : validated.dependency_refs()) {
            var value = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(dependency); value.put("ordinal", ++ordinal); dependencies.put(dependency.version_id(), value);
        }
        compare(row, mapper.dependencies(values), dependencies);
        if (!mapper.parents(values).equals(validated.dependency_refs().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())) mismatch();
        return new TemplateProtocol.Stored(row.tenantId(), row.templateVersion(), row.status(), row.revision().toString(), content,
                hash, base, row.reason(), row.authorityGrant(), row.authorityRevision().toString(), row.createdBy(), time(row.createdAt()), validated.dependency_refs());
    }

    private void dependencies(EnterpriseAccess.Admission admission, List<PermissionProtocol.Basis> refs,
            List<DependencySourceAuthority.VerifiedDependencies> proofs, boolean newUse) throws IOException {
        var expected = new HashSet<>(refs.stream().map(PermissionProtocol.Basis::version_id).toList()); var actual = new HashSet<String>();
        for (var proof : proofs) {
            proof(admission, proof); if (!actual.add(proof.root()) || !expected.contains(proof.root())) mismatch();
            var basis = refs.stream().filter(item -> item.version_id().equals(proof.root())).findFirst().orElseThrow();
            var root = proof.versions().get(proof.root());
            if (root == null || root.version().projectId() != null || !basis.content_sha256().equals(root.version().contentSha256())) mismatch();
            if (newUse && root.domain() == VersionResourceProtocol.Domain.RULE_VERSION) {
                var values = scope(admission); values.put("upstreamId", proof.root());
                if (!mapper.ruleState(values).equals(List.of("APPROVED"))) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
            }
            for (var source : proof.sources().values()) {
                if (source.source().ownerProjectId() != null || !source.source().ownership().equals("ENTERPRISE")) denied();
                if (newUse && sources.gates(source.source().sourceVersion()).stream().noneMatch(gate -> gate.uploadStatus().equals("RECEIVED")
                        && gate.approvalStatus().equals("APPROVED") && gate.lifecycle().equals("ACTIVE"))) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
            }
        }
        if (!actual.equals(expected)) mismatch();
    }

    private void proof(EnterpriseAccess.Admission admission, DependencySourceAuthority.VerifiedDependencies proof) throws IOException {
        if (proof.admission() != admission) throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        for (var version : proof.versions().values()) {
            var owner = version.owner();
            if (!owner.tenantId().equals(admission.context().tenant_id())) denied();
            authority.require(admission, new PermissionProtocol.Scope(owner.tenantId(), owner.resourceProjectId(), owner.resourceKind(), owner.resourceId()),
                    version.resourceGrant().action(), PURPOSE);
        }
        for (var source : proof.sources().values()) {
            var rights = source.rights();
            if (!rights.purpose().equals(PURPOSE) || !rights.status().equals("APPROVED") || rights.validFrom() != null && rights.validFrom().isAfter(now)
                    || rights.validTo() != null && !rights.validTo().isAfter(now)) denied();
        }
    }

    private JsonNode repeated(TemplateProtocol.Content content, TemplateProtocol.Repeat repeat) {
        var value = json.object(); value.put("schema_version", "1.0.0"); value.set("repeat_definition", json.tree(repeat));
        var fields = index(content.field_definitions(), TemplateProtocol.Field::field_id);
        value.set("field_definitions", json.tree(repeat.field_ids().stream().map(fields::get).toList())); return value;
    }
    private void compare(TemplateProtocol.StorageRow definition, List<TemplateProtocol.Projection> actual, Map<String, JsonNode> expected) throws IOException {
        if (actual.size() != expected.size()) mismatch(); var ids = new HashSet<String>();
        for (var row : actual) {
            if (!ids.add(row.id()) || !expected.containsKey(row.id()) || !tree(row.content()).equals(expected.get(row.id()))
                    || !row.schemaVersion().equals(definition.schemaVersion()) || !row.manifest().equals(definition.manifest())
                    || !row.createdBy().equals(definition.createdBy()) || !row.createdAt().equals(definition.createdAt())
                    || !row.classification().equals(definition.classification())) mismatch();
        }
    }
    private static <T> Map<String,T> index(List<T> values, Function<T,String> key) {
        var result = new HashMap<String,T>(); for (T value : values) if (result.putIfAbsent(key.apply(value), value) != null) mismatch(); return result;
    }
    private Map<String,Object> scope(EnterpriseAccess.Admission admission) {
        TenantScope.requireSqlAccess(STATEMENT); Objects.requireNonNull(admission);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        TenantScope.requireSubjectParameter(STATEMENT, values); return values;
    }
    private JsonNode tree(String value) throws IOException { return reader.readTree(value); }
    private static String time(LocalDateTime value) { return DatabaseTimestamp.format(value); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
