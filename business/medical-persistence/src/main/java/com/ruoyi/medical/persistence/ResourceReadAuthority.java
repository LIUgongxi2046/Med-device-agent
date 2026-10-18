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
        Objects.requireNonNull(admission); schema.scope(schema.tree(scope));
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
