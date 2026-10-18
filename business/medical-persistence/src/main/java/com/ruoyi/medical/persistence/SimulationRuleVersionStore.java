package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class SimulationRuleVersionStore {
    private static final String STATEMENT = "com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper.definition";
    private final SimulationRuleVersionMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final SimulationRuleDependencies dependencies;
    private final SimulationRuleVersionSchema schema;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;

    public SimulationRuleVersionStore(SimulationRuleVersionMapper mapper, PermissionMapper permissions, ResourceReadAuthority authority,
            SimulationRuleDependencies dependencies, SimulationRuleVersionSchema schema, PermissionSchema json, IdentityDirectoryPolicy policy) {
        this.mapper = mapper; this.permissions = permissions; this.authority = authority; this.dependencies = dependencies;
        this.schema = schema; this.json = json; this.policy = policy;
    }
    SimulationRuleVersionProtocol.Stored save(EnterpriseAccess.Admission admission, SimulationRuleVersionProtocol.Validated supplied) throws IOException {
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
        values.put("binding", json.canonical(schema.binding(admission.context().tenant_id(), id, validated.content_sha256())));
        values.put("bindingHash", schema.bindingHash(admission.context().tenant_id(), id, validated.content_sha256()));
        values.put("bindingManifest", schema.manifestSha256()); one(mapper.insertBinding(values));
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
    SimulationRuleVersionProtocol.Stored read(EnterpriseAccess.Admission admission, String id) throws IOException { return target(admission, id, "rule.read"); }
    SimulationRuleVersionProtocol.Stored approvalTarget(EnterpriseAccess.Admission admission, String id) throws IOException { return target(admission, id, "rule.approve"); }
    private SimulationRuleVersionProtocol.Stored target(EnterpriseAccess.Admission admission, String id, String action) throws IOException {
        WireValues.uuid(id); var values = scope(admission); values.put("ruleId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "RULE", id), action, TemplateDependencyAuthority.PURPOSE);
        var stored = stored(values); dependencies.requireRule(admission, validated(stored));
        if (action.equals("rule.read") && stored.status().equals("APPROVED")) SimulationRuleApprovalEvidence.require(mapper,schema,json,values,stored);
        return stored;
    }
    SimulationRuleVersionProtocol.Stored originalCreation(EnterpriseAccess.Admission admission, String id, SimulationRuleVersionProtocol.Validated request) throws IOException {
        WireValues.uuid(id); var values = scope(admission); values.put("ruleId", id);
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "ENTERPRISE", admission.context().tenant_id()),
                "rule.write", TemplateDependencyAuthority.PURPOSE);
        var actual = stored(values); dependencies.requireRule(admission, validated(actual));
        if (!actual.created_by().equals(admission.subject().user().user_id()) || !validated(actual).equals(request)) mismatch(); return actual;
    }
    SimulationRuleVersionProtocol.Validated validated(SimulationRuleVersionProtocol.Stored stored) throws IOException {
        return schema.version(new SimulationRuleVersionProtocol.VersionInput("1.0.0", stored.base_version(), SimulationRuleVersionSchema.CONTENT_REF, stored.content(), stored.reason(), SimulationRuleVersionSchema.USE, false));
    }
    private SimulationRuleVersionProtocol.Stored stored(Map<String,Object> values) throws IOException {
        var rows = mapper.definition(values); if (rows.size() != 1) mismatch(); var row = rows.get(0);
        if (!row.tenantId().equals(TenantScope.requireCurrent().tenantId()) || !row.ruleVersion().equals(values.get("ruleId"))
                || !row.schemaVersion().equals("1.0.0") || !row.manifest().equals(schema.contentManifestSha256())
                || !row.classification().equals(policy.classification()) || row.revision().signum() <= 0 || row.authorityRevision().signum() <= 0
                || !List.of("DRAFT", "APPROVED", "RETURNED", "DISABLED").contains(row.status())) mismatch();
        WireValues.uuid(row.createdBy()); WireValues.uuid(row.authorityGrant());
        var base = row.baseVersion() == null ? null : new PermissionProtocol.Basis(row.baseVersion(), row.baseHash());
        if (row.baseVersion() == null && row.baseHash() != null) mismatch();
        var body = json.object(); body.put("schema_version", "1.0.0"); body.set("base_version", json.tree(base));
        body.put("dependency_use", SimulationRuleVersionSchema.USE); body.put("production_eligible", false);
        body.put("content_schema_ref", SimulationRuleVersionSchema.CONTENT_REF); body.set("content", schema.read(row.contentJson())); body.put("reason", row.reason());
        if (!schema.bindingHash(row.tenantId(), row.ruleVersion(), row.contentHash()).equals(row.bindingHash())
                || !schema.binding(row.tenantId(),row.ruleVersion(),row.contentHash()).equals(schema.read(row.bindingJson()))
                || !schema.manifestSha256().equals(row.bindingManifest())) mismatch();
        var validated = schema.version(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); var content = validated.request().content();
        if (!validated.content_sha256().equals(row.contentHash()) || !row.contentHash().equals(row.registryHash())
                || !content.engine_revision().equals(row.engineRevision()) || !json.tree(content.expression()).equals(schema.read(row.expression()))
                || !json.tree(content.basis_manifest()).equals(schema.read(row.basisManifest()))) mismatch();
        if (!mapper.parents(values).equals(content.basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())
                || !mapper.baseParents(values).equals(base == null ? List.of() : List.of(base.version_id()))) mismatch();
        return new SimulationRuleVersionProtocol.Stored(row.tenantId(), row.ruleVersion(), row.status(), row.revision().toString(), content,
                row.contentHash(), base, row.reason(), row.authorityGrant(), row.authorityRevision().toString(), row.createdBy(),
                time(row.createdAt()), validated.dependency_refs(), SimulationRuleVersionSchema.USE, false, row.bindingHash());
    }
    /** Evaluation consumption is separate from author/read governance; no mutation or approval is inferred. */
    RuleVersionProtocol.Validated requireEvaluation(EnterpriseAccess.Admission admission, PermissionProtocol.Basis ref, boolean writing) throws IOException {
        authority.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), null, "RULE", ref.version_id()),
                "rule.read", SimulationRuleVersionSchema.USE);
        var values=scope(admission); values.put("ruleId",ref.version_id()); values.put("upstreamId",ref.version_id());
        var actual=stored(values); if(!actual.content_sha256().equals(ref.content_sha256())) mismatch();
        var verified=validated(actual); dependencies.requireRule(admission,verified);
        if(writing && (!actual.status().equals("APPROVED") || !mapper.approvedWorkflow(values).equals(List.of(ref.version_id()))))
            throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        if(actual.status().equals("APPROVED")) SimulationRuleApprovalEvidence.require(mapper,schema,json,values,actual);
        var input=verified.request();
        return new RuleVersionProtocol.Validated(new RuleVersionProtocol.VersionInput(input.schema_version(), input.base_version(),
            input.content_schema_ref(), input.content(), input.reason()), verified.canonical_content(), verified.content_sha256(), verified.dependency_refs());
    }
    private Map<String,Object> scope(EnterpriseAccess.Admission admission) {
        TenantScope.requireSqlAccess(STATEMENT); Objects.requireNonNull(admission);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var values = new HashMap<String,Object>(); values.put("userId", admission.actorId());
        TenantScope.requireSubjectParameter(STATEMENT, values); return values;
    }
    private static String time(java.time.LocalDateTime value) { return DatabaseTimestamp.format(value); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
