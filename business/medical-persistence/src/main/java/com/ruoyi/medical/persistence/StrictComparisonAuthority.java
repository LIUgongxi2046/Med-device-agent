package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.persistence.StrictComparisonStore.*;

/** Live task/plan/template/source authority shared by the separate typed family. */
final class StrictComparisonAuthority {
    static final String PURPOSE="STRICT_FIELD_COMPARISON";
    private final TaskFieldTargetBasis tasks;
    private final ResourceReadAuthority resources;
    private final StrictComparisonStore store;
    private final StrictComparisonCodec codec;
    private final com.ruoyi.medical.persistence.mapper.PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities;
    StrictComparisonAuthority(TaskFieldTargetBasis tasks,ResourceReadAuthority resources,StrictComparisonStore store,StrictComparisonCodec codec,
            com.ruoyi.medical.persistence.mapper.PermissionMapper permissions,PermissionAuthority grants,
            com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities) {
        this.tasks=tasks;this.resources=resources;this.store=store;this.codec=codec;this.permissions=permissions;this.grants=grants;this.identities=identities;
    }
    @FunctionalInterface interface Check { void require() throws Exception; }
    record Current(TemplateApplicabilityService.Target template, List<SourceReadProtocol.VerifiedSource> sources, PermissionProtocol.GrantRow grant, JsonNode witness, Check check) {
        void requireUnchanged() throws Exception { check.require(); }
    }
    Current require(EnterpriseAccess.Admission a,String project,StrictComparisonProtocol.Scope scope,String action) throws Exception {
        WireValues.uuid(project);
        var task=tasks.require(a,scope.taskId(),"task.read");var head=task.task().head();
        if(!head.projectId().equals(project)||!head.taskRevision().equals(scope.taskRevision())||!Objects.equals(head.planRevision(),scope.planRevision())||!head.templateVersion().equals(scope.templateVersion())||!head.snapshotId().equals(scope.scopeSnapshot()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var grant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),action,PURPOSE);
        return new Current(task.execution().template(),task.execution().sources(),grant,codec.tree(Map.of("task",task.authorizations(),"grant",TaskFieldAuthorityWitness.tree(grant),"action",action,"purpose",PURPOSE,"identity_mode",a.bindingView().mode())),task::requireUnchanged);
    }
    Current requireRun(StrictComparisonRunScope.Verified run,StrictComparisonProtocol.Scope scope,String action) throws Exception {
        run.requireUnchanged();if(!run.scope().equals(scope))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        var grant=project(run.admission(),run.project(),action);
        return new Current(run.template(),run.sources(),grant,codec.tree(Map.of("run",run.witness(),"grant",TaskFieldAuthorityWitness.tree(grant),"action",action,"purpose",PURPOSE,"identity_mode",run.admission().bindingView().mode())),run::requireUnchanged);
    }
    PermissionProtocol.GrantRow project(EnterpriseAccess.Admission a,String project,String action) throws Exception {
        WireValues.uuid(project);return resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),action,PURPOSE);
    }
    /** Exact original governance actors and grants, checked independently of the consuming worker. */
    void requireCurrentActor(EnterpriseAccess.Admission a,String project,String actor,String mode,JsonNode original,String action)throws Exception {
        WireValues.uuid(actor);var members=permissions.targetMember(actor);var bindings=permissions.targetBinding(actor);
        if(members.size()!=1||bindings.size()!=1)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var member=members.get(0);var binding=bindings.get(0);var now=Objects.requireNonNull(permissions.databaseTime());
        if(!a.context().tenant_id().equals(member.tenantId())||!actor.equals(member.userId())||!"ACTIVE".equals(member.status())
                ||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)
                ||!actor.equals(binding.userId())||!"ACTIVE".equals(binding.status())||!mode.equals(binding.mode())
                ||!Set.of("local","oidc").contains(mode)||"local".equals(mode)&&!actor.equals(binding.localUserId())
                ||"oidc".equals(mode)&&binding.localUserId()!=null
                ||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(mode,actor,binding.issuer(),binding.subject())))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var rows=permissions.currentGrant(original.required("grant_id").textValue());
        if(rows.size()!=1||!rows.get(0).equals(TaskFieldAuthorityWitness.grant(original)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        grants.requireResource(new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),actor,mode,action,PURPOSE,rows,now);
    }
    void definition(EnterpriseAccess.Admission a,Current current,StrictComparisonProtocol.Author request,int maximum) throws Exception {
        var d=request.definition();if(d.coverage().size()>maximum||d.basis().size()>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var template=current.template().view().template();
        var fields=new HashMap<String,TemplateProtocol.Field>();template.content().field_definitions().forEach(f->fields.put(f.field_id(),f));
        for(var combination:d.coverage()) {
            var field=fields.get(combination.fieldDefinitionId());
            if(field==null||!field.value_type().equals(d.operator()==StrictComparisonPolicyDefinition.Operator.TEXT_EXACT?"TEXT":"DECIMAL"))mismatch();
            // Field/node applicability is computed independently by the approved template executor.
            var ancestors=new HashSet<String>();String node=field.node_id();
            while(node!=null){if(!ancestors.add(node))mismatch();String selected=node;var definitions=template.content().nodes().stream().filter(n->selected.equals(n.node_id())).toList();if(definitions.size()!=1)mismatch();node=definitions.get(0).parent_id();}
            var relevant=current.template().view().conditions().stream().filter(c->("FIELD".equals(c.scope())&&field.field_id().equals(c.target_id()))||("NODE".equals(c.scope())&&ancestors.contains(c.target_id()))).toList();
            if(relevant.size()!=ancestors.size()+1||relevant.stream().map(c->c.scope()+"/"+c.target_id()).distinct().count()!=relevant.size())mismatch();
            if(d.applicability().state()==StrictComparisonPolicyDefinition.Applicability.APPLICABLE&&relevant.stream().anyMatch(c->!"APPLICABLE".equals(c.status())))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            var conditions=relevant.stream().filter(c->"FIELD".equals(c.scope())).toList();
            if(conditions.size()!=1)mismatch();var condition=conditions.get(0);
            if(d.applicability().state()==StrictComparisonPolicyDefinition.Applicability.APPLICABLE&&!"APPLICABLE".equals(condition.status()))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            var rule=d.applicability().rule();var actual=condition.rule_revision();
            if(rule==null ? actual!=null : actual==null||!rule.revision().equals(actual.version_id())||!rule.sha256().equals(actual.content_sha256()))mismatch();
        }
        var scope=d.applicability().scope();if(!scope.revision().equals(request.scope().scopeSnapshot()))mismatch();registered(a,scope);
        // Initial supported basis is the current confirmed plan's authorized original sources,
        // the exact template, or its exact applicability rule. Other kinds need their own reader.
        var allowed=new HashMap<String,String>();allowed.put(template.template_version(),template.content_sha256());
        for(var source:current.sources())allowed.put(source.source().sourceVersion(),source.source().originalSha256());
        for(var condition:current.template().view().conditions())if(condition.rule_revision()!=null)allowed.put(condition.rule_revision().version_id(),condition.rule_revision().content_sha256());
        for(var basis:d.basis())if(!basis.sha256().equals(allowed.get(basis.revision())))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        current.requireUnchanged();
    }
    void registered(EnterpriseAccess.Admission a,StrictComparisonPolicyDefinition.Version version) {
        WireValues.uuid(version.revision());var row=single(store.db.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?",a.context().tenant_id(),version.revision()));
        if(!version.sha256().equals(text(row,"content_sha256")))mismatch();
    }
    static void human(EnterpriseAccess.Admission a,String context) {
        if(a.simulationWorker()||a.formalWorker()||a.controlWorker()||a.recoveryWorker()||a.planDraftWorker()||a.documentReviewWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
}
