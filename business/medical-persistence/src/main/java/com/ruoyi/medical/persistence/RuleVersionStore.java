package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.RuleVersionMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class RuleVersionStore {
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.RuleVersionMapper.definition";
    private final RuleVersionMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final TemplateDependencyAuthority dependencies;
    private final RuleVersionSchema schema;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;

    public RuleVersionStore(RuleVersionMapper mapper, PermissionMapper permissions, ResourceReadAuthority authority,
            TemplateDependencyAuthority dependencies, RuleVersionSchema schema, PermissionSchema json, IdentityDirectoryPolicy policy) {
        this.mapper = mapper; this.permissions = permissions; this.authority = authority; this.dependencies = dependencies;
        this.schema = schema; this.json = json; this.policy = policy;
    }
    RuleVersionProtocol.Stored save(EnterpriseAccess.Admission admission, RuleVersionProtocol.Validated supplied) throws IOException {
        var values = scope(admission);
        var grant = authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "ENTERPRISE", admission.context().tenant_id()),
                "rule.write", TemplateDependencyAuthority.PURPOSE);
        var validated = schema.version(supplied.request()); if (!validated.equals(supplied)) mismatch();
        dependencies.requireRule(admission, validated);
        var request = validated.request(); var content = request.content();
        String id = UUID.randomUUID().toString(); values.put("ruleId", id); values.put("content", validated.canonical_content());
        values.put("hash", validated.content_sha256()); values.put("engine", content.engine_revision());
        values.put("expression", json.canonicalObject(content.expression())); values.put("basis", json.canonicalObject(content.basis_manifest()));
        values.put("baseId", request.base_version() == null ? null : request.base_version().version_id());
        values.put("baseHash", request.base_version() == null ? null : request.base_version().content_sha256()); values.put("reason", request.reason());
        var now = Objects.requireNonNull(permissions.databaseTime()); values.put("now", now);
        values.put("manifest", schema.contentManifestSha256()); values.put("classification", policy.classification());
        values.put("authorityGrant", grant.grantId()); values.put("authorityRevision", grant.revision().toString());
        one(mapper.insertVersion(values)); one(mapper.insertRule(values)); one(mapper.insertDefinition(values));
        for (var ref : content.basis_manifest()) { values.put("item", ref); one(mapper.insertLineage(values)); }
        if (request.base_version() != null) one(mapper.insertBaseLineage(values));
        var stored = stored(values);
        if (!stored.status().equals("DRAFT") || !stored.revision().equals("1") || !stored.content().equals(content)
                || !stored.content_sha256().equals(validated.content_sha256()) || !Objects.equals(stored.base_version(), request.base_version())
                || !stored.reason().equals(request.reason()) || !stored.authority_grant_id().equals(grant.grantId())
                || !stored.authority_revision().equals(grant.revision().toString()) || !stored.created_by().equals(admission.subject().user().user_id())
                || !stored.created_at().equals(time(now))) mismatch();
        dependencies.requireRule(admission, validated); return stored;
    }
    RuleVersionProtocol.Stored read(EnterpriseAccess.Admission admission, String id) throws IOException { return target(admission, id, "rule.read"); }
    RuleVersionProtocol.Stored approvalTarget(EnterpriseAccess.Admission admission, String id) throws IOException { return target(admission, id, "rule.approve"); }
    private RuleVersionProtocol.Stored target(EnterpriseAccess.Admission admission, String id, String action) throws IOException {
        WireValues.uuid(id); var values = scope(admission); values.put("ruleId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "RULE", id), action, TemplateDependencyAuthority.PURPOSE);
        var stored = stored(values); dependencies.requireRule(admission, validated(stored)); return stored;
    }
    RuleVersionProtocol.Stored originalCreation(EnterpriseAccess.Admission admission, String id, RuleVersionProtocol.Validated request) throws IOException {
        WireValues.uuid(id); var values = scope(admission); values.put("ruleId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "ENTERPRISE", admission.context().tenant_id()),
                "rule.write", TemplateDependencyAuthority.PURPOSE);
        var actual = stored(values); dependencies.requireRule(admission, validated(actual));
        if (!actual.created_by().equals(admission.subject().user().user_id()) || !validated(actual).equals(request)) mismatch(); return actual;
    }
    RuleVersionProtocol.Validated validated(RuleVersionProtocol.Stored stored) throws IOException {
        return schema.version(new RuleVersionProtocol.VersionInput("1.0.0", stored.base_version(), RuleVersionSchema.CONTENT_REF, stored.content(), stored.reason()));
    }
    private RuleVersionProtocol.Stored stored(Map<String,Object> values) throws IOException {
        if (!mapper.simulationBinding(values).isEmpty()) mismatch();
        var rows = mapper.definition(values); if (rows.size() != 1) mismatch(); var row = rows.get(0);
        if (!row.tenantId().equals(TenantScope.requireCurrent().tenantId()) || !row.ruleVersion().equals(values.get("ruleId"))
                || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(schema.contentManifestSha256())
                || !row.classification().equals(policy.classification()) || row.revision().signum() <= 0 || row.authorityRevision().signum() <= 0
                || !List.of("DRAFT", "APPROVED", "RETURNED", "DISABLED").contains(row.status())) mismatch();
        WireValues.uuid(row.createdBy()); WireValues.uuid(row.authorityGrant());
        var base = row.baseVersion() == null ? null : new PermissionProtocol.Basis(row.baseVersion(), row.baseHash());
        if (row.baseVersion() == null && row.baseHash() != null) mismatch();
        var body = json.object(); body.put("schema_version", "1.0.0"); body.set("base_version", json.tree(base));
        body.put("content_schema_ref", RuleVersionSchema.CONTENT_REF); body.set("content", schema.read(row.contentJson())); body.put("reason", row.reason());
        var validated = schema.version(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); var content = validated.request().content();
        if (!validated.content_sha256().equals(row.contentHash()) || !row.contentHash().equals(row.registryHash())
                || !content.engine_revision().equals(row.engineRevision()) || !json.tree(content.expression()).equals(schema.read(row.expression()))
                || !json.tree(content.basis_manifest()).equals(schema.read(row.basisManifest()))) mismatch();
        if (!mapper.parents(values).equals(content.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())
                || !mapper.baseParents(values).equals(base == null ? List.of() : List.of(base.version_id()))) mismatch();
        return new RuleVersionProtocol.Stored(row.tenantId(), row.ruleVersion(), row.status(), row.revision().toString(), content,
                row.contentHash(), base, row.reason(), row.authorityGrant(), row.authorityRevision().toString(), row.createdBy(),
                time(row.createdAt()), validated.dependency_refs());
    }
    private Map<String,Object> scope(EnterpriseAccess.Admission admission) {
        TenantScope.requireSqlAccess(STATEMENT); Objects.requireNonNull(admission);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        TenantScope.requireSubjectParameter(STATEMENT, values); return values;
    }
    private static String time(java.time.LocalDateTime value) { return DatabaseTimestamp.format(value); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
