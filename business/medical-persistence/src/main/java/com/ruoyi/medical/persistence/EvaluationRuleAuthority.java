package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.RuleVersionMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** A readable version of some other domain is never an evaluation rule. */
@Component
public final class EvaluationRuleAuthority {
    private final RuleVersionMapper mapper;
    private final ResourceReadAuthority authority;
    private final RuleVersionSchema schema;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;
    private final org.springframework.beans.factory.ObjectProvider<SimulationRuleVersionStore> simulationRules;
    public EvaluationRuleAuthority(RuleVersionMapper mapper,ResourceReadAuthority authority,RuleVersionSchema schema,
            PermissionSchema json,IdentityDirectoryPolicy policy,org.springframework.beans.factory.ObjectProvider<SimulationRuleVersionStore> simulationRules) {
        this.mapper=mapper;this.authority=authority;this.schema=schema;this.json=json;this.policy=policy;this.simulationRules=simulationRules;
    }
    RuleVersionProtocol.Validated require(EnterpriseAccess.Admission admission,PermissionProtocol.Basis ref,boolean writing)throws IOException {
        return require(admission,ref,writing,EvaluationDependencyUse.RESULTS);
    }
    RuleVersionProtocol.Validated require(EnterpriseAccess.Admission admission,PermissionProtocol.Basis ref,boolean writing,EvaluationDependencyUse use)throws IOException {
        if(use==null)mismatch();
        if(use==EvaluationDependencyUse.SIMULATION) {
            var store=simulationRules.getIfAvailable();
            if(store==null)denied(ErrorCode.SCHEMA_UNSUPPORTED);
            return store.requireEvaluation(admission,ref,writing);
        }
        String tenant=admission.context().tenant_id();
        if(!tenant.equals(TenantScope.requireCurrent().tenantId()))denied(ErrorCode.ACCESS_DENIED);
        authority.require(admission,new PermissionProtocol.Scope(tenant,null,"RULE",ref.version_id()),"rule.read",EvaluationReferences.PURPOSE);
        var values=Map.<String,Object>of("userId",admission.actorId(),"ruleId",ref.version_id(),"upstreamId",ref.version_id());
        // This query joins rule_version, rule_definition and registry entity_kind='RULE_VERSION', and locks the fixed row.
        if(!mapper.simulationBinding(values).isEmpty())mismatch();
        var rows=mapper.definition(values);
        var validated=validate(tenant,ref,rows,writing,writing?mapper.approvedWorkflow(values):List.of());
        if(!mapper.parents(values).equals(validated.request().content().basis_manifest().stream().map(PermissionProtocol.Basis::version_id).sorted().toList())
            ||!mapper.baseParents(values).equals(validated.request().base_version()==null?List.of():List.of(validated.request().base_version().version_id())))mismatch();
        // Complete current dependency/source authorization is also required by EvaluationReferences.
        return validated;
    }
    RuleVersionProtocol.Validated validate(String tenant,PermissionProtocol.Basis ref,List<RuleVersionProtocol.StorageRow> rows,
            boolean writing,List<String> approved)throws IOException {
        if(rows.size()!=1)mismatch();var row=rows.get(0);
        if(!tenant.equals(row.tenantId())||!ref.version_id().equals(row.ruleVersion())||!ref.content_sha256().equals(row.contentHash())
            ||!row.contentHash().equals(row.registryHash())||!row.schemaVersion().equals("1.0.0")||!row.manifest().equals(schema.contentManifestSha256())
            ||!row.classification().equals(policy.classification())||row.revision().signum()<=0||row.authorityRevision().signum()<=0
            ||!List.of("DRAFT","APPROVED","RETURNED","DISABLED").contains(row.status()))mismatch();
        var base=row.baseVersion()==null?null:new PermissionProtocol.Basis(row.baseVersion(),row.baseHash());
        if(row.baseVersion()==null&&row.baseHash()!=null)mismatch();
        var request=json.object().put("schema_version","1.0.0").put("content_schema_ref",RuleVersionSchema.CONTENT_REF).put("reason",row.reason());
        request.set("base_version",json.tree(base));request.set("content",schema.read(row.contentJson()));
        var validated=schema.version(request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));var content=validated.request().content();
        if(!validated.content_sha256().equals(ref.content_sha256())||!content.engine_revision().equals(row.engineRevision())
            ||!json.tree(content.expression()).equals(schema.read(row.expression()))||!json.tree(content.basis_manifest()).equals(schema.read(row.basisManifest())))mismatch();
        // An APPROVED text label alone does not establish approval: the current decision/operation/audit/outbox chain must exist.
        if(writing&&(!row.status().equals("APPROVED")||!approved.equals(List.of(ref.version_id()))))denied(ErrorCode.SOURCE_NOT_READY);
        return validated;
    }
    List<String> boundSamples(EvaluationProtocol.Create request,RuleVersionProtocol.Validated rule) {
        // The approved rule must explicitly bind the entire exact expected-item artifact by immutable ID and hash.
        return request.samples().stream().filter(sample->rule.request().content().basis_manifest().contains(sample.expected_ref()))
            .map(EvaluationProtocol.Sample::sample_id).sorted().toList();
    }
    private static void mismatch(){denied(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(ErrorCode code){throw new TenantAccessException(code);}
}
