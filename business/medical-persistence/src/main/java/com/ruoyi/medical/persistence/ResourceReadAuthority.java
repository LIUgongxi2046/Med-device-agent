package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ResourceReadAuthority {
    private final PermissionMapper mapper;
    private final PermissionAuthority authority;
    private final PermissionSchema schema;

    public ResourceReadAuthority(PermissionMapper mapper, PermissionAuthority authority, PermissionSchema schema) {
        this.mapper = mapper; this.authority = authority; this.schema = schema;
    }

    PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission, PermissionProtocol.Scope scope,
            String action, String purpose) throws IOException {
        return find(admission, scope, action, purpose).orElseThrow(() -> new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }

    java.util.Optional<PermissionProtocol.GrantRow> find(EnterpriseAccess.Admission admission, PermissionProtocol.Scope scope,
            String action, String purpose) throws IOException {
        return findCurrent(admission,scope,action,purpose,null,null,null,null);
    }
    PermissionProtocol.GrantRow requireStartRun(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,
            FormalRunControlAccess.StartRun start)throws IOException {
        Objects.requireNonNull(start).require(admission,scope,"RUN_CREATION");
        return findCurrent(admission,scope,"task.run","RUN_CREATION",start,null,null,null).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }
    /** Exact committed N03 artifact only; still performs the normal current owner/grant lookup. */
    PermissionProtocol.GrantRow requireStrictFieldOutput(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,
            String action,String purpose,TaskStrictFieldWriterReadAuthority.CommittedRead proof)throws IOException {
        Objects.requireNonNull(proof).requireResource(admission,scope,action,purpose);
        return findCurrent(admission,scope,action,purpose,null,proof,null,null).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }
    /** Exact genuine N05 committed outputs only, with unchanged current owner/grant checks. */
    PermissionProtocol.GrantRow requireSourceVerifierOutput(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,
            String action,String purpose,TaskSourceVerifierCommittedReviewAuthority.CommittedRead proof)throws IOException {
        Objects.requireNonNull(proof).requireResource(admission,scope,action,purpose);
        return findCurrent(admission,scope,action,purpose,null,null,proof,null).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }
    PermissionProtocol.GrantRow requireAssemblyOriginal(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,String action,String purpose,TaskCandidateAssemblyOriginalReader.ReadAuthority proof)throws IOException {
        proof.requireResource(admission,scope,action,purpose);
        return findCurrent(admission,scope,action,purpose,null,null,null,proof).orElseThrow(()->new TenantAccessException(ErrorCode.ACCESS_DENIED));
    }
    private java.util.Optional<PermissionProtocol.GrantRow> findCurrent(EnterpriseAccess.Admission admission,PermissionProtocol.Scope scope,
            String action,String purpose,FormalRunControlAccess.StartRun start,TaskStrictFieldWriterReadAuthority.CommittedRead strict,TaskSourceVerifierCommittedReviewAuthority.CommittedRead review,TaskCandidateAssemblyOriginalReader.ReadAuthority assembly)throws IOException {
        Objects.requireNonNull(admission); schema.scope(schema.tree(scope));
        if(admission.evaluationWorker())admission.evaluationProof().require(scope,action,purpose);
        if(admission.documentReviewWorker())admission.documentReviewProof().require(scope,action,purpose);
        if(admission.planDraftWorker())admission.planDraftProof().require(scope,action,purpose);
        if(admission.recoveryWorker()){if(assembly==null)admission.recoveryProof().require(scope,action,purpose);else assembly.requireResource(admission,scope,action,purpose);}
        if(admission.controlWorker()) {if(review!=null)review.requireResource(admission,scope,action,purpose);else if(strict!=null)strict.requireResource(admission,scope,action,purpose);else if(start==null)admission.controlProof().require(scope,action,purpose);else start.require(admission,scope,purpose);}
        if(admission.formalWorker()) admission.formalProof().require(scope,action,purpose);
        if(admission.simulationWorker() && !("SIMULATION_EVALUATION".equals(purpose)
                || "MODEL_CONFIGURATION".equals(purpose) && "configuration.read".equals(action))) denied();
        if (!admission.context().tenant_id().equals(scope.tenant_id()) || scope.resource_id() == null) denied();
        if (!TenantScope.requireCurrent().tenantId().equals(scope.tenant_id())) denied();
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants");
        TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants",
            java.util.Map.of("userId", admission.actorId()));
        // resourceOwner查询真实领域实体；版本登记UUID不能替代该实体身份。
        var owners = mapper.resourceOwner(scope.resource_kind(), scope.resource_id());
        if (owners.size() != 1 || !scope.resource_id().equals(owners.get(0).id())
                || !Objects.equals(scope.project_id(), owners.get(0).projectId())) return java.util.Optional.empty();
        return authority.findResource(scope, admission.actorId(), admission.bindingView().mode(),
            action, purpose, mapper.resourceGrants(admission.actorId(), action), mapper.databaseTime());
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
}
