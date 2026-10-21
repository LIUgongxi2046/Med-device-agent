package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;

/** Original human author/independent approver grants remain current; no worker impersonation. */
final class TaskStrictFieldTypedActors {
    private final PermissionMapper permissions;private final PermissionAuthority grants;private final PlatformMemberIdentityStatus identities;private final ResourceReadAuthority resources;private final StrictComparisonCodec codec;
    TaskStrictFieldTypedActors(PermissionMapper permissions,PermissionAuthority grants,PlatformMemberIdentityStatus identities,ResourceReadAuthority resources,StrictComparisonCodec codec){this.permissions=permissions;this.grants=grants;this.identities=identities;this.resources=resources;this.codec=codec;}
    JsonNode witness(EnterpriseAccess.Admission a,String project,String action)throws Exception {
        if(a.formalWorker()||a.controlWorker()||a.simulationWorker()||a.recoveryWorker()||a.planDraftWorker()||a.documentReviewWorker())deny();
        var grant=resources.require(a,scope(a,project),action,StrictComparisonAuthority.PURPOSE);
        return codec.tree(Map.of("actor",a.actorId(),"identity_mode",a.bindingView().mode(),"grant",TaskFieldAuthorityWitness.tree(grant),"action",action));
    }
    void require(EnterpriseAccess.Admission a,String project,String action,JsonNode witness)throws Exception {
        String actor=witness.required("actor").asText(),mode=witness.required("identity_mode").asText();if(!action.equals(witness.required("action").asText()))deny();
        var member=one(permissions.targetMember(actor));var binding=one(permissions.targetBinding(actor));var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!actor.equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)
            ||!"ACTIVE".equals(binding.status())||!actor.equals(binding.userId())||!mode.equals(binding.mode())||!Set.of("local","oidc").contains(mode)||"local".equals(mode)&&!actor.equals(binding.localUserId())||"oidc".equals(mode)&&binding.localUserId()!=null
            ||!identities.active(new AuthenticatedIdentityBinding.View(mode,actor,binding.issuer(),binding.subject())))deny();
        var original=witness.required("grant");var grant=one(permissions.currentGrant(original.required("grant_id").asText()));if(!grant.equals(TaskFieldAuthorityWitness.grant(original)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        grants.requireResource(scope(a,project),actor,mode,action,StrictComparisonAuthority.PURPOSE,List.of(grant),now);
    }
    private static PermissionProtocol.Scope scope(EnterpriseAccess.Admission a,String project){return new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project);}
    private static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
